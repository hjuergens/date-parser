"""The evaluator of rule files, without a parser: copied unchanged into every Python module that parses rule files
(date-rule-lark: rules.py with rules.lark; date-rule-antlr-python: RuleFile.g4 with a visitor). The parser builds the
declarations with the functions of this module and gives them to RuleCatalog.

RuleCatalog(declarations).holidays(scope, year) evaluates the rules of a scope for a year in two phases: each rule on its
own, then, for the rules that read the holidays of their own scope (a business day, a roll or a holiday condition
without ON, or ON the own scope), passes in the order of the nominal dates until nothing changes; a rule sees the dates
of the other rules, never its own. catalog.evaluate(key, base, scope) evaluates a rule with a base date, for trade-date
rules such as spot; catalog.schedule(text) parses one expression for catalog.context(base, scope).

Not computed here: HINDU (no Hindu calendar library) is an error at evaluation; the Hijri methods other than UMMALQURA
are projected with Umm al-Qura and marked as projections.

Rules only project. Announcements are data, not grammar: the dates an authority published for a key and year replace
the dates of the rule with that key for that year, and in the other years its dates are projections. A key without a
rule (an AGM, which no rule can project) holds only announced dates, and other rules can refer to it with @key.
"""
import math
from dataclasses import dataclass, field, replace
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from . import semantics
from .semantics import WEEKDAYS, Context, Market

CLOSED = {"closed", "non_working"}   # kinds that close a day; working_day opens one
ESTIMATED = {"SIGHTING", "DIYANET", "FOLLOW"}
MAX_PASSES = 8


# ================= markets that answer more than business days =================

def is_holiday(market, d):
    """a holiday of the market: closed although not a weekend day"""
    check = getattr(market, "is_holiday", None)
    if check:
        return check(d)
    return d.weekday() < 5 and not market.is_business_day(d)


class Open(Market):
    """every day a business day: the calendar for the nominal dates, which give the order of the passes"""

    def is_business_day(self, d):
        return True

    def is_holiday(self, d):
        return False


class ScopeCalendar(Market):
    """the weekend regime of a scope and the dates of its rules, from the evaluations of the year of a date and of the
    years before and after (an observed holiday can fall into the neighbouring year). During the evaluation of a year
    (home) skip leaves out the rule being evaluated, the other years are read shallow (evaluated with their own year
    only), and a shallow calendar reads its home year only, so no evaluation starts a chain of years"""

    def __init__(self, catalog, scope, skip=None, home=None, shallow=False):
        super().__init__()
        self.catalog, self.scope, self.skip, self.home, self.shallow = catalog, scope, skip, home, shallow

    def _days(self, d):
        closed, opened = set(), set()
        for year in (d.year - 1, d.year, d.year + 1):
            if self.shallow and year != self.home:
                continue
            state = self.catalog._state(self.scope, year, shallow=self.home is not None and year != self.home)
            for rule, (dates, _) in state.items():
                if (rule, year) == self.skip:
                    continue
                if rule.kind in CLOSED:
                    closed.update(dates)
                elif rule.kind == "working_day":
                    opened.update(dates)
        return closed, opened

    def is_holiday(self, d):
        return d in self._days(d)[0]

    def is_business_day(self, d):
        closed, opened = self._days(d)
        if d in opened:
            return True
        return WEEKDAYS[d.weekday()] not in self.catalog.weekend(self.scope, d) and d not in closed


class CombinedCalendar(Market):
    """A & B: a business day in every term; a term with ignore_at does not count on the first business day after T
    of the other terms, unless the currency pair of the evaluation contains one of its currencies"""

    def __init__(self, terms, base, pair):
        super().__init__()
        self.terms = terms   # (market, ignore) with ignore None or (days, currencies)
        self.ignored = set()
        for i, (_, ignore) in enumerate(terms):
            if ignore and base and not (pair and any(c in pair for c in ignore[1])):
                others = [m for j, (m, _) in enumerate(terms) if j != i]
                day = base
                for _ in range(ignore[0]):
                    day = next_day(day, lambda x: all(m.is_business_day(x) for m in others))
                self.ignored.add((i, day))

    def is_business_day(self, d):
        return all(m.is_business_day(d) for i, (m, _) in enumerate(self.terms) if (i, d) not in self.ignored)

    def is_holiday(self, d):
        return any(is_holiday(m, d) for m, _ in self.terms)


def next_day(d, test, direction=1, limit=3700):
    """the first day strictly after (before) d that passes the test"""
    for _ in range(limit):
        d += timedelta(days=direction)
        if test(d):
            return d
    raise ValueError(f"no such day within {limit} days of {d}")


# ================= the context of a rule =================

@dataclass(frozen=True)
class RuleContext(Context):
    """Context with the catalog (calendars, references), the scope and the rule being evaluated and the currency pair of
    a spot"""
    catalog: object = None
    scope: str | None = None
    rule: str | None = None
    pair: str | None = None
    instant: datetime | None = None   # the base with its time of day, for > CUTOFF


def market(on, context, pair=None):
    """the calendar of ON ...; without ON the market of the context (in a rule: its own scope)"""
    if on is None:
        return context.market
    if not isinstance(context, RuleContext) or context.catalog is None:
        raise ValueError("ON needs a rule catalog")
    return context.catalog.resolve(on, context, pair or context.pair)


class References:
    """@key: the dates of a rule (by key), of the rules of an event class or of a key with announced dates only, in the
    year of the base date; during the evaluation of a year (home) other years are read shallow, or not at all from a
    shallow evaluation"""

    def __init__(self, catalog, scope, home=None, shallow=False):
        self.catalog, self.scope, self.home, self.shallow = catalog, scope, home, shallow

    def __contains__(self, name):
        return bool(self.catalog._referenced(name)) or name in self.catalog.announcements

    def __getitem__(self, name):
        def dates(context):
            year = semantics.base(context).year
            if self.shallow and year != self.home:
                return []
            return self.catalog.reference_dates(name, self.scope, year, self.home is not None and year != self.home)
        return dates



@dataclass(frozen=True)
class IfClause:
    condition: object
    shifts: tuple


class Postpone:
    """> POSTPONE FOLLOWING ... MAX 14D [CUMULATIVE]: rolled, but at most the period from the date (with CUMULATIVE
    from the date before the first postponement of the expression)"""

    def __init__(self, convention, on, limit, cumulative):
        self.convention, self.on, self.limit, self.cumulative = convention, on, limit, cumulative

    def apply(self, d, context, origin):
        target = roll(d, self.convention, market(self.on, context))
        if target > d:
            return min(target, semantics.plus(origin, self.limit, 1, context.market))
        return max(target, semantics.plus(origin, self.limit, -1, context.market))


def roll(d, convention, m):
    """the conventions of the schedule language, MODPRECEDING (preceding unless that leaves the month) and NEAREST
    (a non-business Sunday or Monday follows, any other non-business day precedes)"""
    if convention == "NEAREST":
        if m.is_business_day(d):
            return d
        return semantics.roll(d, "FOLLOWING" if d.weekday() in (6, 0) else "PRECEDING", m)
    if convention == "MODPRECEDING":
        preceding = semantics.roll(d, "PRECEDING", m)
        return preceding if preceding.month == d.month else semantics.roll(d, "FOLLOWING", m)
    return semantics.roll(d, convention, m)


def orthodox_easter(year):
    """Orthodox Easter Sunday: the Julian computus (Meeus), converted to the Gregorian calendar"""
    a, b, c = year % 4, year % 7, year % 19
    d = (19 * c + 15) % 30
    e = (2 * a + 4 * b - d + 34) % 7
    month, day = divmod(d + e + 114, 31)
    k = (14 - month) // 12
    y, m = year + 4800 - k, month + 12 * k - 3
    jdn = day + 1 + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083
    return date.fromordinal(jdn - 1721425)


def sun_in_zone(degrees, year, zone_name):
    """the local day (IANA time zone) on which the sun's apparent longitude reaches the degrees"""
    day = 79 + degrees * 365.2422 / 360
    if day > 365.2422:
        day -= 365.2422
    jde = date(year, 1, 1).toordinal() - date(1970, 1, 1).toordinal() + 2440587.5 + day
    for _ in range(50):
        step = 58 * math.sin(math.radians(degrees - semantics.sun_longitude(jde)))
        jde += step
        if abs(step) < 1e-7:
            break
    utc = jde - semantics.delta_t(year + day / 365.25) / 86400 - 2440587.5
    instant = datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(days=utc)
    return instant.astimezone(ZoneInfo(zone_name)).date()


def zoned(degrees_of, zone):
    """an astronomical annual date in UTC, a fixed offset (UTC+9) or an IANA zone ("Asia/Tokyo")"""
    if zone and not zone.startswith("UTC"):
        return lambda year: sun_in_zone(degrees_of(year), year, zone)
    return None



# ================= building the rules: the parser calls these =================

def shifted(start, shifts):
    """a date expression: the start, then the shifts from left to right"""
    return lambda context: apply_shifts(start(context), shifts, context)


def rule_item(expr, combines, clause=None):
    """an item: the expression, include/exclude steps (include, operand, shifts) and an IfClause or None"""
    for include, operand, shifts in combines:
        expr = shifted(semantics.combined(expr, include, operand), shifts)
    if clause is None:
        return expr

    def at(context):
        d = expr(context)
        if d is None or not clause.condition(d, context):
            return None
        return apply_shifts(d, clause.shifts, context)
    return at


def exprs(items):
    """a list of items: all their dates, in order"""
    return lambda context: [d for item in items for d in semantics.values(item, context) if d is not None]


# conditions: functions of (date, context)

def any_condition(parts):
    return lambda d, context: any(p(d, context) for p in parts)


def all_conditions(parts):
    return lambda d, context: all(p(d, context) for p in parts)


def not_condition(part):
    return lambda d, context: not part(d, context)


def on_weekday(weekdays):
    return lambda d, context: WEEKDAYS[d.weekday()] in weekdays


def on_day(first, last, month=None):
    days = semantics.on_days(first, last, month)
    return lambda d, context: days(d) is not None


def is_weekday(subject, weekdays):
    return lambda d, context: WEEKDAYS[semantics.single(subject, context).weekday()] in weekdays


def holiday_condition(on):
    return lambda d, context: is_holiday(market(on, context), d)


def date_is_holiday(subject, on):
    return lambda d, context: is_holiday(market(on, context), semantics.single(subject, context))


def between_holidays(on):
    def test(d, context):
        m = market(on, context)
        return is_holiday(m, d - timedelta(days=1)) and is_holiday(m, d + timedelta(days=1))
    return test


def every(period, first):
    if period.days or period.business or period.fixing or period.months % 12 or period.months <= 0:
        raise ValueError("EVERY needs whole years: EVERY 4Y FROM 1965")
    years = period.months // 12
    return lambda d, context: d.year >= first and (d.year - first) % years == 0


# dates

def spot_date(pair, on):
    return lambda context: market(on, context, pair).spot(semantics.base(context), pair)


def month_day(day, month, method=None):
    if method and month not in semantics.HIJRI:
        raise ValueError(f"BY {method} only for Hijri months, not {month}")
    return semantics.month_day(day, month)


def hindu(key):
    def in_year(year):
        raise ValueError(f"HINDU {key}: no Hindu calendar in this implementation")
    return in_year


def season(kind, month, zone):
    months = ["MAR", "JUN", "SEP", "DEC"]
    if month not in months or (months.index(month) % 2 == 0) != (kind == "EQUINOX"):
        raise ValueError(f"no {kind} in {month}")
    return zoned(lambda year: 90 * months.index(month), zone) or semantics.season(kind, month, semantics.zone(zone))


def solar_term(term, zone):
    if not 1 <= term <= 24:
        raise ValueError(f"no solar term {term}")
    return zoned(lambda year: (term + 20) % 24 * 15, zone) or semantics.solar_term(term, semantics.zone(zone))


# shifts: functions of (date, context), or Postpone

def plus(period, sign, endend=False, on=None):
    def shift(d, context):
        m = market(on, context)
        moved = semantics.plus(d, period, sign, m, context.fixing)
        if endend and d == last_business_day(d, m):
            return last_business_day(moved, m)
        return moved
    return shift


def spot(pair, on):
    return lambda d, context: market(on, context, pair).spot(d, pair)


def roll_shift(convention, on=None):
    return lambda d, context: roll(d, convention, market(on, context))


def nearest_shift(anchor):
    return lambda d, context: nearest(anchor, d, context)


def observance(entries):
    """{SAT - 1D, TUE WED > PREV MON}: entries (weekdays, move) — the first entry for the weekday moves the date"""
    def shift(d, context):
        for weekdays, move in entries:
            if WEEKDAYS[d.weekday()] in weekdays:
                return move(d, context)
        return d
    return shift


def observe_plus(weekdays, period, sign):
    return frozenset(weekdays), lambda d, context: semantics.plus(d, period, sign, context.market, context.fixing)


def observe_anchor(weekdays, anchor, direction):
    return frozenset(weekdays), lambda d, context: anchor(d, direction, context)


def observe_nearest(weekdays, anchor):
    return frozenset(weekdays), lambda d, context: nearest(anchor, d, context)


def observe_roll(weekdays, convention, on):
    return frozenset(weekdays), lambda d, context: roll(d, convention, market(on, context))


# anchors: functions of (date, direction, context)

def business_day(on=None):
    return lambda d, direction, context: semantics.plus_business_days(d, direction, market(on, context))


def holiday(on):
    return lambda d, direction, context: next_day(d, lambda x: is_holiday(market(on, context), x), direction)


def nonholiday(on):
    return lambda d, direction, context: next_day(d, lambda x: not is_holiday(market(on, context), x), direction)


# declarations; a calendar is ("scope", scope, ignore), ("named", key) or ("combined", terms), ignore (days, currencies)

def calendar(terms):
    return terms[0] if len(terms) == 1 else ("combined", tuple(terms))


def one_of(alternatives):
    """alternatives: (name, exprs) pairs"""
    names = [name for name, _ in alternatives]
    if len(set(names)) != len(names):
        raise ValueError(f"ONE OF: names twice in {names}")
    return OneOf(tuple(alternatives))


def rule(key, event_class, include, exclude, valid_from, valid_to, body, attributes, citations, references=frozenset(),
         estimated=False):
    """a rule declaration; body is an expression list or OneOf, attributes (name, value) pairs, references the @keys"""
    alternatives = ()
    if isinstance(body, OneOf):
        alternatives, body = body.alternatives, body.union
    return RuleDecl(key, event_class, tuple(include), tuple(exclude), valid_from, valid_to, body, dict(attributes),
                    tuple(citations), frozenset(references), estimated or bool(alternatives), alternatives)


def weekend(scope, weekdays, valid_from=None):
    return ("weekend", scope, frozenset(weekdays), valid_from or date.min)


def calendar_declaration(key, spec, cut=None):
    """calendar key = spec [cutoff 10:00 UTC+0]; cut is the > CUTOFF shift of the attribute"""
    return ("calendar", key, spec, cut)


def session(start, end, tz):
    """session 09:30 - 13:00, or a single time such as a release or a decision: session 14:30 (start = end)"""
    return "session", (start, end or start, tz)


def time(hours, minutes):
    return f"{hours:02}:{minutes:02}"


def tz(zone):
    """a fixed offset (UTC+9) or an IANA zone ("Asia/Tokyo")"""
    return timezone(timedelta(hours=int(zone[3:]))) if zone.startswith("UTC") else ZoneInfo(zone)


def cutoff_on(on):
    """> CUTOFF ON name: the cutoff attribute of the named calendar"""
    def shift(d, context):
        if on[0] != "named" or on[1] not in context.catalog.cutoffs:
            raise ValueError(f"no cutoff for {on[1]}")
        return context.catalog.cutoffs[on[1]](d, context)
    return shift


def cutoff(at, zone):
    """> CUTOFF 17:00 "America/New_York": the date of the base instant in the zone, the next one from the cutoff on"""
    hours, minutes = map(int, at.split(":"))
    if hours > 23 or minutes > 59:
        raise ValueError(f"not a time of day: {at}")
    zone_info = tz(zone)

    def shift(d, context):
        if getattr(context, "instant", None) is None:
            raise ValueError("CUTOFF needs a base with a time of day")
        local = context.instant.astimezone(zone_info)
        return local.date() + timedelta(days=1) if local.strftime("%H:%M") >= at else local.date()
    return shift


def apply_shifts(d, shifts, context):
    """the shifts from left to right; a postponement is limited from the date before it (CUMULATIVE: before the first
    postponement)"""
    origin = None
    for shift in shifts:
        if isinstance(shift, Postpone):
            origin = d if origin is None else origin
            d = shift.apply(d, context, origin if shift.cumulative else d)
        else:
            d = shift(d, context)
    return d


def nearest(anchor, d, context):
    """the date itself if it matches, else the nearer of the next and the previous match; ties go forward"""
    if anchor(d - timedelta(days=1), 1, context) == d:
        return d
    after, before = anchor(d, 1, context), anchor(d, -1, context)
    return after if after - d <= d - before else before


def last_business_day(d, m):
    """the last business day of the month of d"""
    return semantics.roll(semantics.roll(d, "EOM", m), "PRECEDING", m)


# ================= the rule catalog =================

@dataclass(frozen=True)
class Citation:
    source: str
    article: str | None
    url: str | None
    label: str


@dataclass(frozen=True)
class OneOf:
    """ONE OF {item AS name, ...}: alternatives of which exactly one will happen; their dates are projections, each
    under its scenario name, and together they are the rule's dates for the other rules (references, calendars)"""
    alternatives: tuple   # (name, exprs) pairs

    def union(self, context):
        return [d for _, expr in self.alternatives for d in expr(context)]


@dataclass(frozen=True, eq=False)
class RuleDecl:
    key: str
    event_class: str
    include: tuple
    exclude: tuple
    valid_from: date | None
    valid_to: date | None
    expr: object = field(repr=False)
    attributes: dict = field(default_factory=dict, repr=False)
    citations: tuple = ()
    references: frozenset = frozenset()
    estimated: bool = False
    alternatives: tuple = ()   # (scenario name, exprs) of ONE OF

    @property
    def kind(self):
        return self.attributes.get("kind", "closed")

    def valid(self, d):
        return (self.valid_from is None or d >= self.valid_from) and (self.valid_to is None or d <= self.valid_to)

    def in_year(self, year):
        return self.valid(date(year, 12, 31)) or self.valid(date(year, 1, 1)) or (
            self.valid_from is not None and self.valid_from.year == year)


@dataclass(frozen=True)
class Occurrence:
    """a date with the rule that produced it: its kind, precision, session and citations; projected when it comes
    from a projection (a Hijri date by sighting, a rule with announced dates in a year without them)"""
    date: date
    rule: str
    event_class: str
    kind: str
    precision: str
    session: tuple | None
    citations: tuple
    projected: bool
    scenario: str | None = None   # the name of the ONE OF alternative the date belongs to


def contains(outer, inner):
    """country:AU contains country:AU and region:AU-NSW"""
    return outer == inner or inner.split(":", 1)[1].startswith(outer.split(":", 1)[1] + "-")


class RuleCatalog:
    """the declarations of a rule file; announcements maps a key to the dates an authority published for it: a rule's key, or a key without a rule that @key reads"""

    def __init__(self, declarations, announcements=None, parse=None):
        self._parse = parse   # text -> expression, for schedule()
        self.rules = [d for d in declarations if isinstance(d, RuleDecl)]
        self.weekends, self.calendars, self.cutoffs = {}, {}, {}
        for d in declarations:
            if isinstance(d, tuple) and d[0] == "weekend":
                self.weekends.setdefault(d[1], []).append((d[3], d[2]))
            elif isinstance(d, tuple) and d[0] == "calendar":
                if d[1] in self.calendars:
                    raise ValueError(f"calendar {d[1]} declared twice")
                self.calendars[d[1]] = d[2]
                if d[3]:
                    self.cutoffs[d[1]] = d[3]
        for regimes in self.weekends.values():
            regimes.sort(key=lambda r: r[0])
        self.announcements = {k: sorted(v) for k, v in (announcements or {}).items()}
        self._states, self._shallow, self._running = {}, {}, {}
        self._check_versions()
        self._check_references()

    # ---------- checks at load ----------

    def _check_versions(self):
        by_key = {}
        for rule in self.rules:
            by_key.setdefault(rule.key, []).append(rule)
        for key, versions in by_key.items():
            for a in versions:
                for b in versions:
                    if a is not b and (a.valid_from or date.min) <= (b.valid_to or date.max) \
                            and (b.valid_from or date.min) <= (a.valid_to or date.max):
                        raise ValueError(f"rule {key}: versions overlap in time")

    def _referenced(self, name):
        return [r for r in self.rules if r.key == name or r.event_class == name]

    def _check_references(self):
        graph = {}
        for rule in self.rules:
            targets = set()
            for name in rule.references:
                found = self._referenced(name)
                if not found and name not in self.announcements:
                    raise ValueError(f"rule {rule.key}: no rule, event class or announced key {name}")
                targets.update(r.key for r in found)
            graph.setdefault(rule.key, set()).update(targets)
        done, path = set(), []

        def visit(key):
            if key in path:
                raise ValueError(f"cyclic references: {' -> '.join(path[path.index(key):] + [key])}")
            if key in done:
                return
            path.append(key)
            for target in graph.get(key, ()):
                visit(target)
            path.pop()
            done.add(key)
        for key in graph:
            visit(key)

    # ---------- scopes and calendars ----------

    def applies(self, rule, scope):
        return any(contains(s, scope) for s in rule.include) and not any(contains(s, scope) for s in rule.exclude)

    def weekend(self, scope, d):
        """the weekend days of the scope on d: its own regime, else that of the scope containing it, else SAT SUN"""
        for candidate in sorted(self.weekends, key=len, reverse=True):
            if contains(candidate, scope):
                regimes = [days for start, days in self.weekends[candidate] if start <= d]
                if regimes:
                    return regimes[-1]
        return frozenset({"SAT", "SUN"})

    def calendar(self, scope):
        """the calendar of a scope, for contexts of the schedule language"""
        return ScopeCalendar(self, scope)

    def resolve(self, spec, context, pair=None):
        if spec[0] == "named":
            if spec[1] not in self.calendars:
                raise ValueError(f"no calendar {spec[1]}")
            return self.resolve(self.calendars[spec[1]], context, pair)
        if spec[0] == "scope" and not spec[2]:
            if getattr(context, "scope", None) == spec[1]:
                return context.market
            return ScopeCalendar(self, spec[1])
        terms = spec[1] if spec[0] == "combined" else (spec,)
        resolved = [(self.resolve(("scope", t[1], None), context) if t[0] == "scope"
                     else self.resolve(t, context, pair), t[2] if t[0] == "scope" else None) for t in terms]
        return CombinedCalendar(resolved, context.base, pair)

    def announcement(self, rule, year):
        return [d for d in self.announcements.get(rule, ()) if d.year == year]

    # ---------- evaluation ----------

    def _context(self, rule, scope, year, shallow, market=None):
        return RuleContext(base=date(year, 1, 1),
                           market=market or ScopeCalendar(self, scope, skip=(rule, year), home=year, shallow=shallow),
                           events=References(self, scope, year, shallow), catalog=self, scope=scope, rule=rule.key)

    def _evaluate(self, rule, scope, year, shallow, market=None):
        """(dates, projected) of the rule evaluated in the year; an announcement for the year always wins"""
        published = self.announcement(rule.key, year)
        if published:
            return [d for d in published if rule.valid(d)], False
        context = self._context(rule, scope, year, shallow, market)
        dates = rule.expr(context)
        dates = sorted({d for d in dates if rule.valid(d)})
        return dates, rule.estimated or rule.key in self.announcements

    def _state(self, scope, year, shallow=False):
        """rule -> (dates, projected) of the rules of the scope evaluated with the first of January of the year"""
        key = (scope, year)
        cache = self._shallow if shallow else self._states
        if key in self._states:
            return self._states[key]
        if key in cache:
            return cache[key]
        if key in self._running:
            return self._running[key]
        rules = [r for r in self.rules if self.applies(r, scope) and r.in_year(year)]
        state = {r: ([], r.estimated or r.key in self.announcements) for r in rules}
        self._running[key] = state
        try:
            order = sorted(range(len(rules)), key=lambda i: (self._nominal(rules[i], scope, year), i))
            for _ in range(MAX_PASSES):
                changed = False
                for i in order:
                    result = self._evaluate(rules[i], scope, year, shallow)
                    if result != state[rules[i]]:
                        state[rules[i]], changed = result, True
                if not changed:
                    break
            else:
                raise ValueError(f"the rules of {scope} in {year} do not settle in {MAX_PASSES} passes")
        finally:
            del self._running[key]
        cache[key] = state
        return state

    def _nominal(self, rule, scope, year):
        """the first date of the rule with every day open, which orders the passes; date.max if it needs more"""
        try:
            dates, _ = self._evaluate(rule, scope, year, True, market=Open())
        except Exception:
            return date.max
        return dates[0] if dates else date.max

    def holidays(self, scope, year):
        """the occurrences of the rules of the scope in the year (also those evaluated with a neighbouring year)"""
        found = []
        for y in (year - 1, year, year + 1):
            for rule, (dates, projected) in self._state(scope, y).items():
                attributes = rule.attributes
                for d, scenario in self._scenarios(rule, scope, y, dates):
                    if d.year == year:
                        found.append(Occurrence(d, rule.key, rule.event_class, rule.kind,
                                                attributes.get("precision", "day"), attributes.get("session"),
                                                rule.citations, projected, scenario))
        return sorted(dict.fromkeys(found), key=lambda o: (o.date, o.rule))   # alternatives in the order written

    def _scenarios(self, rule, scope, year, dates):
        """(date, scenario) for the dates of the rule: per alternative of ONE OF (a date can belong to several), else
        with no scenario; announced dates have none"""
        if not rule.alternatives or self.announcement(rule.key, year):
            return [(d, None) for d in dates]
        context = self._context(rule, scope, year, False)
        out = []
        for name, expr in rule.alternatives:
            out.extend((d, name) for d in sorted(set(expr(context))) if rule.valid(d))
        return out

    def reference_dates(self, name, scope, year, shallow=False):
        dates = set() if self._referenced(name) else set(self.announcement(name, year))   # a key with data only
        for rule in self._referenced(name):
            in_scope = scope if self.applies(rule, scope) else rule.include[0]
            dates.update(self._state(in_scope, year, shallow).get(rule, ((), False))[0])
        return sorted(dates)

    def context(self, base, scope=None, instant=None):
        """a context for catalog.schedule(...): the base date, the calendar of the scope and, for > CUTOFF, the base
        with its time of day (an aware datetime)"""
        return RuleContext(base=base, market=ScopeCalendar(self, scope) if scope else semantics.WEEKENDS,
                           events=References(self, scope), catalog=self, scope=scope, instant=instant)

    def evaluate(self, key, base, scope=None):
        """the dates of a rule with a base date, such as a trade date for spot and fixing rules"""
        versions = [r for r in self.rules if r.key == key and r.valid(base)] or [r for r in self.rules if r.key == key]
        if not versions:
            raise ValueError(f"no rule {key}")
        rule = versions[0]
        scope = scope or rule.include[0]
        context = replace(self.context(base, scope), rule=key)
        return sorted(rule.expr(context))

    def schedule(self, text):
        """one expression of the rule language, evaluated with catalog.context(base, scope)"""
        return self._parse(text)


@dataclass(frozen=True)
class Body:
    """what follows "=" in a rule: an expression list or ONE OF; references are the @keys it reads, calendar whether
    it needs business days (else every evaluation is independent of the holidays)"""
    text: str
    expr: object = field(repr=False)
    alternatives: tuple = field(default=(), repr=False)
    references: frozenset = frozenset()
    calendar: bool = False

    def dates(self, context):
        """(date, scenario) pairs: the alternatives each under their name, else the dates without a scenario"""
        if self.alternatives:
            return [(d, name) for name, expr in self.alternatives for d in expr(context)]
        return [(d, None) for d in self.expr(context)]
