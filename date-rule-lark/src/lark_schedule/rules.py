"""Rule files (rules.lark): the schedule language inside declarations, as drafted for the rule catalog of
holidays-events (docs/rule-grammar.md, O-74).

parse_rules(text) returns a RuleBook. book.holidays(scope, year) evaluates the rules of a scope for a year in two
phases: each rule on its own, then, for the rules that read the holidays of their own scope (a business day, a roll or
a holiday condition without ON, or ON the own scope), passes in the order of the nominal dates until nothing changes;
a rule sees the dates of the other rules, never its own. book.evaluate(key, base, scope) evaluates a rule with a base
date, for trade-date rules such as spot; book.schedule(text) parses one expression for book.context(base, scope).

Not computed here: HINDU (no Hindu calendar library in this module) is an error at evaluation; the Hijri methods other
than UMMALQURA are projected with Umm al-Qura and marked as projections.

Announcements are data, not grammar: the dates an authority published for a rule and year always replace the dates the
rule gives for that year; in the years without an announcement, the dates of an announced rule are projections.
"""
import math
from dataclasses import dataclass, field, replace
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from lark import Lark, Token, v_args
from lark.exceptions import VisitError

from . import Rules, semantics
from .semantics import WEEKDAYS, Context, Market

PARSER = Lark.open("rules.lark", rel_to=__file__, parser="lalr", start=["rule_file", "exprs", "body"])

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

    def __init__(self, book, scope, skip=None, home=None, shallow=False):
        super().__init__()
        self.book, self.scope, self.skip, self.home, self.shallow = book, scope, skip, home, shallow

    def _days(self, d):
        closed, opened = set(), set()
        for year in (d.year - 1, d.year, d.year + 1):
            if self.shallow and year != self.home:
                continue
            state = self.book._state(self.scope, year, shallow=self.home is not None and year != self.home)
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
        return WEEKDAYS[d.weekday()] not in self.book.weekend(self.scope, d) and d not in closed


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
    """Context with the book (calendars, references), the scope and the rule being evaluated and the currency pair of
    a spot"""
    book: object = None
    scope: str | None = None
    rule: str | None = None
    pair: str | None = None


def market(on, context, pair=None):
    """the calendar of ON ...; without ON the market of the context (in a rule: its own scope)"""
    if on is None:
        return context.market
    if not isinstance(context, RuleContext) or context.book is None:
        raise ValueError("ON needs a rule book")
    return context.book.resolve(on, context, pair or context.pair)


class References:
    """@key: the dates of a rule (by key) or of the rules of an event class, in the year of the base date; during the
    evaluation of a year (home) other years are read shallow, or not at all from a shallow evaluation"""

    def __init__(self, book, scope, home=None, shallow=False):
        self.book, self.scope, self.home, self.shallow = book, scope, home, shallow

    def __contains__(self, name):
        return bool(self.book._referenced(name))

    def __getitem__(self, name):
        def dates(context):
            year = semantics.base(context).year
            if self.shallow and year != self.home:
                return []
            return self.book.reference_dates(name, self.scope, year, self.home is not None and year != self.home)
        return dates


# ================= the transformer =================

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
    """the conventions of the schedule language and MODPRECEDING (preceding unless that leaves the month)"""
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


@v_args(inline=True)
class RuleTransformer(Rules):
    """the schedule language of Rules with the extensions of rules.lark, and the declarations"""

    # ---------- items and conditions ----------

    def item(self, expr, *rest):
        return self._rule_item(expr, rest)

    def picked_item(self, pick, start, *rest):
        if isinstance(start, Token):
            start = semantics.event(str(start)[1:])
        shifts = [r for r in rest if callable(r) or isinstance(r, Postpone)]
        others = [r for r in rest if not (callable(r) or isinstance(r, Postpone))]
        return semantics.pick(*pick, self._rule_item(self.expr(start, *shifts), others))

    def _rule_item(self, expr, rest):
        clause = None
        for step in rest:
            if isinstance(step, IfClause):
                clause = step
            else:
                include, operand, shifts = step
                expr = self.expr(semantics.combined(expr, include, operand), *shifts)
        if clause is None:
            return expr

        def at(context):
            d = expr(context)
            if d is None or not clause.condition(d, context):
                return None
            return apply_shifts(d, clause.shifts, context)
        return at

    def if_clause(self, condition, *shifts):
        return IfClause(condition, shifts)

    def expr(self, start, *shifts):
        return lambda context: apply_shifts(start(context), shifts, context)

    def condition(self, *parts):
        return lambda d, context: any(p(d, context) for p in parts)

    def cond_and(self, *parts):
        return lambda d, context: all(p(d, context) for p in parts)

    def not_(self, part):
        return lambda d, context: not part(d, context)

    def weekday_set(self, *weekdays):
        return frozenset(str(w) for w in weekdays)

    def on_weekday(self, weekdays):
        return lambda d, context: WEEKDAYS[d.weekday()] in weekdays

    def on_day(self, first, last, month):
        days = semantics.on_days(int(first), int(last), str(month) if month else None)
        return lambda d, context: days(d) is not None

    def subject(self, start, *shifts):
        return self.expr(start, *shifts)

    def is_weekday(self, subject, weekdays):
        return lambda d, context: WEEKDAYS[semantics.single(subject, context).weekday()] in weekdays

    def is_holiday(self, on):
        return lambda d, context: is_holiday(market(on, context), d)

    def date_is_holiday(self, subject, on):
        return lambda d, context: is_holiday(market(on, context), semantics.single(subject, context))

    def between_holidays(self, on):
        def test(d, context):
            m = market(on, context)
            return is_holiday(m, d - timedelta(days=1)) and is_holiday(m, d + timedelta(days=1))
        return test

    def every(self, period, start):
        if period.days or period.business or period.fixing or period.months % 12 or period.months <= 0:
            raise ValueError("EVERY needs whole years: EVERY 4Y FROM 1965")
        years, first = period.months // 12, int(start)
        return lambda d, context: d.year >= first and (d.year - first) % years == 0

    # ---------- dates ----------

    def spot_date(self, pair, on):
        pair = pair and str(pair)
        return lambda context: market(on, context, pair).spot(semantics.base(context), pair)

    def ref(self, name):
        return semantics.event(str(name)[1:])

    def ref_anchor(self, name):
        return semantics.event_anchor(str(name)[1:])

    def month_day(self, day, month, method=None):
        if method and str(month) not in semantics.HIJRI:
            raise ValueError(f"BY {method} only for Hijri months, not {month}")
        return semantics.month_day(int(day), str(month))

    def hijri_method(self, *parts):
        return " ".join(str(p) for p in parts)

    def orthodox_easter(self):
        return orthodox_easter

    def hindu(self, key):
        def in_year(year):
            raise ValueError(f"HINDU {key}: no Hindu calendar in this implementation")
        return in_year

    def zone(self, text):
        text = str(text)
        return text[1:-1] if text.startswith('"') else text

    def season(self, kind, month, zone):
        kind, month = str(kind), str(month)
        months = ["MAR", "JUN", "SEP", "DEC"]
        if month not in months or (months.index(month) % 2 == 0) != (kind == "EQUINOX"):
            raise ValueError(f"no {kind} in {month}")
        return zoned(lambda year: 90 * months.index(month), zone) or semantics.season(kind, month, semantics.zone(zone))

    def solar_term(self, term, zone):
        term = int(term)
        if not 1 <= term <= 24:
            raise ValueError(f"no solar term {term}")
        return zoned(lambda year: (term + 20) % 24 * 15, zone) or semantics.solar_term(term, semantics.zone(zone))

    def last_weekday_anchor(self, weekday, month):
        return semantics.annual_anchor(semantics.last_weekday(str(weekday), str(month)))

    # ---------- shifts ----------

    def plus(self, period, endend=None, on=None):
        return self._plus(period, 1, endend, on)

    def minus(self, period, endend=None, on=None):
        return self._plus(period, -1, endend, on)

    def _plus(self, period, sign, endend, on):
        def shift(d, context):
            m = market(on, context)
            moved = semantics.plus(d, period, sign, m, context.fixing)
            if endend and d == last_business_day(d, m):
                return last_business_day(moved, m)
            return moved
        return shift

    def spot(self, pair, on):
        pair = pair and str(pair)
        return lambda d, context: market(on, context, pair).spot(d, pair)

    def roll(self, convention, on=None):
        convention = str(convention)
        return lambda d, context: roll(d, convention, market(on, context))

    def nearest(self, anchor):
        return lambda d, context: nearest(anchor, d, context)

    def postpone(self, convention, on, limit, cumulative):
        return Postpone(str(convention), on, limit, cumulative is not None)

    def observance(self, *entries):
        def shift(d, context):
            for weekdays, move in entries:
                if WEEKDAYS[d.weekday()] in weekdays:
                    return move(d, context)
            return d
        return shift

    @staticmethod
    def _weekdays(parts):
        return frozenset(str(p) for p in parts if isinstance(p, Token) and p.type == "WEEKDAY")

    def observe_plus(self, *parts):
        period = parts[-1]
        return self._weekdays(parts), lambda d, context: semantics.plus(d, period, 1, context.market, context.fixing)

    def observe_minus(self, *parts):
        period = parts[-1]
        return self._weekdays(parts), lambda d, context: semantics.plus(d, period, -1, context.market, context.fixing)

    def observe_next(self, *parts):
        anchor = parts[-1]
        return self._weekdays(parts), lambda d, context: anchor(d, 1, context)

    def observe_prev(self, *parts):
        anchor = parts[-1]
        return self._weekdays(parts), lambda d, context: anchor(d, -1, context)

    def observe_nearest(self, *parts):
        anchor = parts[-1]
        return self._weekdays(parts), lambda d, context: nearest(anchor, d, context)

    def observe_roll(self, *parts):
        convention, on = str(parts[-2]), parts[-1]
        return self._weekdays(parts[:-2]), lambda d, context: roll(d, convention, market(on, context))

    # ---------- anchors ----------

    def business_day(self, on=None):
        return lambda d, direction, context: semantics.plus_business_days(d, direction, market(on, context))

    def holiday(self, on):
        return lambda d, direction, context: next_day(d, lambda x: is_holiday(market(on, context), x), direction)

    def nonholiday(self, on):
        return lambda d, direction, context: next_day(d, lambda x: not is_holiday(market(on, context), x), direction)

    # ---------- calendars ----------

    def on(self, spec):
        if isinstance(spec, Token):
            return ("scope", str(spec), None) if spec.type == "SCOPE" else ("named", str(spec))
        return spec

    def calendar(self, *terms):
        return terms[0] if len(terms) == 1 else ("combined", terms)

    def scope_calendar(self, scope, ignore):
        return ("scope", str(scope), ignore)

    def named_calendar(self, key):
        return ("named", str(key))

    def cal_term(self, spec):
        return spec

    def ignore(self, days, *currencies):
        return int(days), frozenset(str(c) for c in currencies)

    # ---------- declarations ----------

    def rule_file(self, *declarations):
        return list(declarations)

    def rule(self, key, event_class, scopes, validity, exprs, attributes, provenance):
        include, exclude = scopes
        valid_from, valid_to = validity or (None, None)
        attributes = dict(attributes or ())
        alternatives = ()
        if isinstance(exprs, Announced):
            attributes["announced_by"] = exprs.authority
            alternatives = exprs.alternatives.alternatives if exprs.alternatives else ()
            exprs = exprs.projection or (lambda context: [])
        elif isinstance(exprs, OneOf):
            alternatives, exprs = exprs.alternatives, exprs.union
        return RuleDecl(str(key), str(event_class), tuple(include), tuple(exclude), valid_from, valid_to, exprs,
                        attributes, tuple(provenance or ()), alternatives=alternatives,
                        estimated=bool(alternatives))

    def announced(self, authority, projection):
        if isinstance(projection, OneOf):
            return Announced(str(authority), projection.union, projection)
        return Announced(str(authority), projection)

    def body(self, node):
        return node

    def one_of(self, *alternatives):
        names = [name for name, _ in alternatives]
        if len(set(names)) != len(names):
            raise ValueError(f"ONE OF: names twice in {names}")
        return OneOf(tuple(alternatives))

    def alternative(self, item, name):
        return str(name), self.exprs(item)

    def scope_sel(self, include, exclude):
        return include, exclude or []

    def scopes(self, *scopes):
        return [str(s) for s in scopes]

    def validity(self, valid_from, valid_to):
        return valid_from, valid_to

    def iso_date(self, y, m, d):
        return date(int(y), int(m), int(d))

    def weekend_decl(self, scope, weekdays, valid_from):
        return ("weekend", str(scope), weekdays, valid_from or date.min)

    def calendar_decl(self, key, spec):
        return ("calendar", str(key), spec)

    def attributes(self, *attributes):
        return attributes

    def kind_attribute(self, kind):
        return "kind", kind

    def kind(self, token):
        return str(token)

    def session(self, start, end, tz):
        """session 09:30 - 13:00, or a single time such as a release or a decision: session 14:30 (start = end)"""
        return "session", (start, end or start, tz and str(tz)[1:-1])

    def time(self, h, m):
        return f"{int(h):02}:{int(m):02}"

    def precision_attribute(self, precision):
        return "precision", precision

    def precision(self, token):
        return str(token)

    def note(self, text):
        return "note", str(text)[1:-1]

    def provenance(self, *citations):
        return citations

    def citation(self, source, article, url, label):
        return Citation(str(source)[1:-1], article and str(article)[1:-1], url and str(url)[1:-1], label)

    def label(self, token):
        return str(token)


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
    """the nearer of the next and the previous match; ties go forward"""
    after, before = anchor(d, 1, context), anchor(d, -1, context)
    return after if after - d <= d - before else before


def last_business_day(d, m):
    """the last business day of the month of d"""
    return semantics.roll(semantics.roll(d, "EOM", m), "PRECEDING", m)


# ================= the rule book =================

@dataclass(frozen=True)
class Citation:
    source: str
    article: str | None
    url: str | None
    label: str


@dataclass(frozen=True)
class Announced:
    """ANNOUNCED BY authority [ELSE projection]: the dates come from the authority; the projection fills the years
    it has not published yet"""
    authority: str
    projection: object = None
    alternatives: object = None


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

    @property
    def announced_by(self):
        return self.attributes.get("announced_by")

    def valid(self, d):
        return (self.valid_from is None or d >= self.valid_from) and (self.valid_to is None or d <= self.valid_to)

    def in_year(self, year):
        return self.valid(date(year, 12, 31)) or self.valid(date(year, 1, 1)) or (
            self.valid_from is not None and self.valid_from.year == year)


@dataclass(frozen=True)
class Occurrence:
    """a date with the rule that produced it: its kind, precision, session and citations; projected when it comes
    from a projection (a Hijri date by sighting, an announced rule in a year without announcement)"""
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


class RuleBook:
    """the declarations of a rule file; announcements maps a rule key to the dates an authority published for it"""

    def __init__(self, declarations, announcements=None):
        self.rules = [d for d in declarations if isinstance(d, RuleDecl)]
        self.weekends, self.calendars = {}, {}
        for d in declarations:
            if isinstance(d, tuple) and d[0] == "weekend":
                self.weekends.setdefault(d[1], []).append((d[3], d[2]))
            elif isinstance(d, tuple) and d[0] == "calendar":
                if d[1] in self.calendars:
                    raise ValueError(f"calendar {d[1]} declared twice")
                self.calendars[d[1]] = d[2]
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
                if not found:
                    raise ValueError(f"rule {rule.key}: no rule or event class {name}")
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
                           events=References(self, scope, year, shallow), book=self, scope=scope, rule=rule.key)

    def _evaluate(self, rule, scope, year, shallow, market=None):
        """(dates, projected) of the rule evaluated in the year; an announcement for the year always wins"""
        published = self.announcement(rule.key, year)
        if published:
            return [d for d in published if rule.valid(d)], False
        context = self._context(rule, scope, year, shallow, market)
        try:
            dates = rule.expr(context)
        except VisitError as e:
            raise e.orig_exc from None
        dates = sorted({d for d in dates if rule.valid(d)})
        return dates, rule.estimated or rule.key in self.announcements or rule.announced_by is not None

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
        state = {r: ([], r.estimated or r.key in self.announcements or r.announced_by is not None) for r in rules}
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
        return sorted(set(found), key=lambda o: (o.date, o.rule))

    def _scenarios(self, rule, scope, year, dates):
        """(date, scenario) for the dates of the rule: per alternative of ONE OF (a date can belong to several), else
        with no scenario; announced dates have none"""
        if not rule.alternatives or self.announcement(rule.key, year):
            return [(d, None) for d in dates]
        context = self._context(rule, scope, year, False)
        out = []
        for name, expr in rule.alternatives:
            try:
                out.extend((d, name) for d in sorted(set(expr(context))) if rule.valid(d))
            except VisitError as e:
                raise e.orig_exc from None
        return out

    def reference_dates(self, name, scope, year, shallow=False):
        dates = set()
        for rule in self._referenced(name):
            in_scope = scope if self.applies(rule, scope) else rule.include[0]
            dates.update(self._state(in_scope, year, shallow).get(rule, ((), False))[0])
        return sorted(dates)

    def context(self, base, scope=None):
        """a context for book.schedule(...): the base date and the calendar of the scope"""
        return RuleContext(base=base, market=ScopeCalendar(self, scope) if scope else semantics.WEEKENDS,
                           events=References(self, scope), book=self, scope=scope)

    def evaluate(self, key, base, scope=None):
        """the dates of a rule with a base date, such as a trade date for spot and fixing rules"""
        versions = [r for r in self.rules if r.key == key and r.valid(base)] or [r for r in self.rules if r.key == key]
        if not versions:
            raise ValueError(f"no rule {key}")
        rule = versions[0]
        scope = scope or rule.include[0]
        context = replace(self.context(base, scope), rule=key)
        try:
            return sorted(rule.expr(context))
        except VisitError as e:
            raise e.orig_exc from None

    def schedule(self, text):
        """one expression of the rule language, evaluated with book.context(base, scope)"""
        return _transform(PARSER.parse(text, start="exprs"))


def _transform(tree):
    try:
        return RuleTransformer().transform(tree)
    except VisitError as e:
        raise e.orig_exc from None


def parse_rules(text, announcements=None):
    """the rule book of a rule file

    :raises lark.exceptions.UnexpectedInput: on a syntax error
    :raises ValueError: on an invalid date, overlapping versions, an unknown or a cyclic reference
    """
    tree = PARSER.parse(text, start="rule_file")
    extras = []
    for node in tree.children:
        if node.data == "rule":
            references = frozenset(str(t)[1:] for t in node.scan_values(lambda t: isinstance(t, Token) and t.type == "REF"))
            estimated = any(not (m.children and getattr(m.children[0], "type", None) == "UMMALQURA")
                            for m in node.find_data("hijri_method"))
            extras.append((references, estimated))
    declarations, rules = _transform(tree), iter(extras)
    for i, declaration in enumerate(declarations):
        if isinstance(declaration, RuleDecl):
            references, estimated = next(rules)
            declarations[i] = replace(declaration, references=references,
                                      estimated=estimated or declaration.estimated)
    return RuleBook(declarations, announcements)


# ================= one rule body, for hosts that store rules themselves =================

# tokens that read a business-day calendar: business or fixing days, rolls, BD, holidays, spot, ON another calendar
CALENDAR_WORDS = {"BD", "FD", "HOLIDAY", "NONHOLIDAY", "HOLIDAYS", "SPOT", "TOM", "FIX", "ON", "POSTPONE"}


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
        try:
            if self.alternatives:
                return [(d, name) for name, expr in self.alternatives for d in expr(context)]
            return [(d, None) for d in self.expr(context)]
        except VisitError as e:
            raise e.orig_exc from None


def parse_body(text):
    """one rule body, such as "3 MON APR", "EASTER + 60D > PRECEDING" or "ONE OF { 1 RAMADAN + 29D AS a, ... }";
    evaluate it with Body.dates(RuleContext(base, market, events, book=..., scope=...)), where a book needs resolve() only
    for ON

    :raises lark.exceptions.UnexpectedInput: on a syntax error
    :raises ValueError: on an invalid date or the same name twice in ONE OF
    """
    tree = PARSER.parse(text, start="body")
    tokens = list(tree.scan_values(lambda t: isinstance(t, Token)))
    references = frozenset(str(t)[1:] for t in tokens if t.type == "REF")
    calendar = any(t.type == "ROLL" or t.type == "UNIT" and str(t) in "BF" or str(t) in CALENDAR_WORDS for t in tokens)
    node = _transform(tree)
    if isinstance(node, OneOf):
        return Body(text, node.union, node.alternatives, references, calendar)
    return Body(text, node, (), references, calendar)
