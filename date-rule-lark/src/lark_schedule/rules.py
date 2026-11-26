"""Rule files (rules.lark): the schedule language inside declarations, as drafted for the rule catalog of
holidays-events (docs/rule-grammar.md, O-74).

parse_rules(text) returns a catalog.RuleCatalog; this module only turns the parse tree into its declarations with the
functions of catalog.py, which the other Python modules share. parse_body(text) parses what follows "=" in a rule, for
hosts that store rules themselves.
"""
from dataclasses import replace
from datetime import date

from lark import Lark, Token, v_args
from lark.exceptions import VisitError

from . import Rules, catalog, semantics
from .catalog import Citation, IfClause, OneOf, Postpone, RuleCatalog, RuleContext, RuleDecl   # noqa: F401

PARSER = Lark.open("rules.lark", rel_to=__file__, parser="lalr", start=["rule_file", "exprs", "body"])


def _weekdays(parts):
    return frozenset(str(p) for p in parts if isinstance(p, Token) and p.type == "WEEKDAY")


def _text(token):
    """the text of a STRING token without its quotes"""
    return token and str(token)[1:-1]


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
        return semantics.pick(*pick, self._rule_item(catalog.shifted(start, shifts), others))

    @staticmethod
    def _rule_item(expr, rest):
        clauses = [r for r in rest if isinstance(r, IfClause)]
        return catalog.rule_item(expr, [r for r in rest if not isinstance(r, IfClause)], clauses[0] if clauses else None)

    def if_clause(self, condition, *shifts):
        return IfClause(condition, shifts)

    def expr(self, start, *shifts):
        return catalog.shifted(start, shifts)

    def condition(self, *parts):
        return catalog.any_condition(parts)

    def cond_and(self, *parts):
        return catalog.all_conditions(parts)

    def not_(self, part):
        return catalog.not_condition(part)

    def weekday_set(self, *weekdays):
        return frozenset(str(w) for w in weekdays)

    def on_weekday(self, weekdays):
        return catalog.on_weekday(weekdays)

    def on_day(self, first, last, month):
        return catalog.on_day(int(first), int(last), str(month) if month else None)

    def subject(self, start, *shifts):
        return catalog.shifted(start, shifts)

    def is_weekday(self, subject, weekdays):
        return catalog.is_weekday(subject, weekdays)

    def is_holiday(self, on):
        return catalog.holiday_condition(on)

    def date_is_holiday(self, subject, on):
        return catalog.date_is_holiday(subject, on)

    def between_holidays(self, on):
        return catalog.between_holidays(on)

    def every(self, period, start):
        return catalog.every(period, int(start))

    # ---------- dates ----------

    def spot_date(self, pair, on):
        return catalog.spot_date(pair and str(pair), on)

    def ref(self, name):
        return semantics.event(str(name)[1:])

    def ref_anchor(self, name):
        return semantics.event_anchor(str(name)[1:])

    def month_day(self, day, month, method=None):
        return catalog.month_day(int(day), str(month), method)

    def hijri_method(self, *parts):
        return " ".join(str(p) for p in parts)

    def orthodox_easter(self):
        return catalog.orthodox_easter

    def hindu(self, key):
        return catalog.hindu(str(key))

    def zone(self, text):
        text = str(text)
        return text[1:-1] if text.startswith('"') else text

    def season(self, kind, month, zone):
        return catalog.season(str(kind), str(month), zone)

    def solar_term(self, term, zone):
        return catalog.solar_term(int(term), zone)

    def last_weekday_anchor(self, weekday, month):
        return semantics.annual_anchor(semantics.last_weekday(str(weekday), str(month)))

    # ---------- shifts ----------

    def plus(self, period, endend=None, on=None):
        return catalog.plus(period, 1, endend is not None, on)

    def minus(self, period, endend=None, on=None):
        return catalog.plus(period, -1, endend is not None, on)

    def spot(self, pair, on):
        return catalog.spot(pair and str(pair), on)

    def roll(self, convention, on=None):
        return catalog.roll_shift(str(convention), on)

    def nearest(self, anchor):
        return catalog.nearest_shift(anchor)

    def cutoff(self, at, zone):
        return catalog.cutoff(at, zone)

    def nearest_roll(self, on):
        return catalog.roll_shift("NEAREST", on)

    def postpone(self, convention, on, limit, cumulative):
        return Postpone(str(convention), on, limit, cumulative is not None)

    def observance(self, *entries):
        return catalog.observance(entries)

    def observe_plus(self, *parts):
        return catalog.observe_plus(_weekdays(parts), parts[-1], 1)

    def observe_minus(self, *parts):
        return catalog.observe_plus(_weekdays(parts), parts[-1], -1)

    def observe_next(self, *parts):
        return catalog.observe_anchor(_weekdays(parts), parts[-1], 1)

    def observe_prev(self, *parts):
        return catalog.observe_anchor(_weekdays(parts), parts[-1], -1)

    def observe_nearest(self, *parts):
        return catalog.observe_nearest(_weekdays(parts), parts[-1])

    def observe_roll(self, *parts):
        return catalog.observe_roll(_weekdays(parts[:-2]), str(parts[-2]), parts[-1])

    # ---------- anchors ----------

    def business_day(self, on=None):
        return catalog.business_day(on)

    def holiday(self, on):
        return catalog.holiday(on)

    def nonholiday(self, on):
        return catalog.nonholiday(on)

    # ---------- calendars ----------

    def on(self, spec):
        if isinstance(spec, Token):
            return ("scope", str(spec), None) if spec.type == "SCOPE" else ("named", str(spec))
        return spec

    def calendar(self, *terms):
        return catalog.calendar(terms)

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

    def rule(self, key, event_class, scopes, validity, body, attributes, provenance):
        include, exclude = scopes
        valid_from, valid_to = validity or (None, None)
        return catalog.rule(str(key), str(event_class), include, exclude, valid_from, valid_to, body, attributes or (),
                            provenance or ())

    def body(self, node):
        return node

    def one_of(self, *alternatives):
        return catalog.one_of(alternatives)

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
        return catalog.weekend(str(scope), weekdays, valid_from)

    def calendar_decl(self, key, spec):
        return catalog.calendar_declaration(str(key), spec)

    def attributes(self, *attributes):
        return attributes

    def kind_attribute(self, kind):
        return "kind", kind

    def kind(self, token):
        return str(token)

    def session(self, start, end, tz):
        return catalog.session(start, end, _text(tz))

    def time(self, h, m):
        return catalog.time(int(h), int(m))

    def precision_attribute(self, precision):
        return "precision", precision

    def precision(self, token):
        return str(token)

    def note(self, text):
        return "note", _text(text)

    def provenance(self, *citations):
        return citations

    def citation(self, source, article, url, label):
        return Citation(_text(source), _text(article), _text(url), label)

    def label(self, token):
        return str(token)


def _transform(tree):
    try:
        return RuleTransformer().transform(tree)
    except VisitError as e:
        raise e.orig_exc from None


def schedule(text):
    """one expression of the rule language"""
    return _transform(PARSER.parse(text, start="exprs"))


def parse_rules(text, announcements=None):
    """the rule catalog of a rule file

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
    return RuleCatalog(declarations, announcements, schedule)


# ================= one rule body, for hosts that store rules themselves =================

# tokens that read a business-day calendar: business or fixing days, rolls, BD, holidays, spot, ON another calendar
CALENDAR_WORDS = {"BD", "FD", "HOLIDAY", "NONHOLIDAY", "HOLIDAYS", "SPOT", "TOM", "FIX", "ON", "POSTPONE"}


def parse_body(text):
    """one rule body, such as "3 MON APR", "EASTER + 60D > PRECEDING" or "ONE OF { 1 RAMADAN + 29D AS a, ... }";
    evaluate it with Body.dates(RuleContext(base, market, events, catalog=..., scope=...)), where a catalog needs
    resolve() only for ON

    :raises lark.exceptions.UnexpectedInput: on a syntax error
    :raises ValueError: on an invalid date or the same name twice in ONE OF
    """
    tree = PARSER.parse(text, start="body")
    tokens = list(tree.scan_values(lambda t: isinstance(t, Token)))
    references = frozenset(str(t)[1:] for t in tokens if t.type == "REF")
    calendar = any(t.type == "ROLL" or t.type == "UNIT" and str(t) in "BF" or str(t) in CALENDAR_WORDS for t in tokens)
    node = _transform(tree)
    if isinstance(node, OneOf):
        return catalog.Body(text, node.union, node.alternatives, references, calendar)
    return catalog.Body(text, node, (), references, calendar)
