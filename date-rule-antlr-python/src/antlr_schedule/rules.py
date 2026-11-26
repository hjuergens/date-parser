"""Rule files (RuleFile.g4) parsed with ANTLR's Python target; evaluated by catalog.py, the same file as in date-rule-lark.

parse_rules(text) returns a catalog.RuleCatalog. The visitor reuses the methods of the schedule visitor (Rules) for the
rules RuleFile.g4 shares with DateSchedule.g4 (same labels) and builds the extensions and declarations with the
functions of catalog.py.
"""
from datetime import date

from antlr4 import CommonTokenStream, InputStream
from antlr4.tree.Tree import TerminalNode

from . import Rules, _Raise, catalog, semantics
from .catalog import Citation, IfClause, Postpone, RuleCatalog
from .generated.RuleFileLexer import RuleFileLexer
from .generated.RuleFileParser import RuleFileParser


def _text(token):
    """the text of a STRING token without its quotes"""
    return token and token.getText()[1:-1]


def _tokens(ctx):
    """the terminal nodes below ctx"""
    if isinstance(ctx, TerminalNode):
        yield ctx
        return
    for child in ctx.getChildren():
        yield from _tokens(child)


def _weekdays(ctx):
    return frozenset(w.getText() for w in ctx.WEEKDAY())


class RuleVisitor(Rules):
    """the schedule visitor with the extensions of RuleFile.g4 and the declarations"""

    # ---------- items and conditions ----------

    def visitSchedule(self, ctx):
        return catalog.exprs([self.visit(item) for item in ctx.item()])

    visitExprs = visitSchedule

    def visitItem(self, ctx):
        picked = ctx.ORDINAL() is not None
        if picked:   # a pick: the item starts with a series or an event
            start = self.visit(ctx.series()) if ctx.series() else semantics.event(ctx.EVENT().getText()[1:])
            expr = catalog.shifted(start, [self.visit(shift) for shift in ctx.shift()])
        else:
            expr = self.visit(ctx.expr())
        combines = [(c.op.text == "&", self.visit(c.operand()), [self.visit(s) for s in c.shift()]) for c in ctx.combine()]
        clause = ctx.ifClause() and self.visit(ctx.ifClause())
        expr = catalog.rule_item(expr, combines, clause)
        if picked:
            expr = semantics.pick(semantics.ordinal(ctx.ORDINAL().getText()), ctx.fromEnd is not None, expr)
        return expr

    def visitIfClause(self, ctx):
        return IfClause(self.visit(ctx.condition()), tuple(self.visit(s) for s in ctx.shift()))

    def visitExpr(self, ctx):
        return catalog.shifted(self.visit(ctx.date()), [self.visit(shift) for shift in ctx.shift()])

    def visitEndpoint(self, ctx):
        return catalog.shifted(self.visit(ctx.singleDate()), [self.visit(shift) for shift in ctx.shift()])

    def visitCondition(self, ctx):
        parts = [self.visit(p) for p in ctx.condAnd()]
        return parts[0] if len(parts) == 1 else catalog.any_condition(parts)

    def visitCondAnd(self, ctx):
        parts = [self.visit(p) for p in ctx.condNot()]
        return parts[0] if len(parts) == 1 else catalog.all_conditions(parts)

    def visitCondNot(self, ctx):
        part = self.visit(ctx.condAtom())
        return catalog.not_condition(part) if ctx.negate else part

    def visitWeekdaySet(self, ctx):
        return _weekdays(ctx)

    def visitOnWeekday(self, ctx):
        return catalog.on_weekday(self.visit(ctx.weekdaySet()))

    def visitOnDay(self, ctx):
        return catalog.on_day(int(ctx.first.text), int(ctx.last.text), ctx.MONTH() and ctx.MONTH().getText())

    def visitSubject(self, ctx):
        signs = [1 if s.text == "+" else -1 for s in ctx.sign]
        shifts = [catalog.plus(self.visit(p), sign) for p, sign in zip(ctx.period(), signs)]
        return catalog.shifted(self.visit(ctx.singleDate()), shifts)

    def visitIsWeekday(self, ctx):
        return catalog.is_weekday(self.visit(ctx.subject()), self.visit(ctx.weekdaySet()))

    def visitIsHoliday(self, ctx):
        return catalog.holiday_condition(self._on(ctx))

    def visitDateIsHoliday(self, ctx):
        return catalog.date_is_holiday(self.visit(ctx.subject()), self._on(ctx))

    def visitBetweenHolidays(self, ctx):
        return catalog.between_holidays(self._on(ctx))

    def visitEvery(self, ctx):
        return catalog.every(self.visit(ctx.period()), int(ctx.NUM().getText()))

    def visitNestedCondition(self, ctx):
        return self.visit(ctx.condition())

    # ---------- dates ----------

    def visitSpotDate(self, ctx):
        return catalog.spot_date(ctx.PAIR() and ctx.PAIR().getText(), self._on(ctx))

    def visitMonthDay(self, ctx):
        method = ctx.hijriMethod()
        return catalog.month_day(int(ctx.NUM().getText()), ctx.MONTH().getText(), method and method.method.text)

    def visitOrthodoxEaster(self, ctx):
        return catalog.orthodox_easter

    def visitHindu(self, ctx):
        return catalog.hindu(ctx.KEY().getText())

    def _zone(self, ctx):
        zone = ctx.zone()
        if zone is None:
            return None
        return _text(zone.STRING()) if zone.STRING() else zone.ZONE().getText()

    def visitSeason(self, ctx):
        return catalog.season(ctx.k.text, ctx.MONTH().getText(), self._zone(ctx))

    def visitSolarTerm(self, ctx):
        return catalog.solar_term(int(ctx.NUM().getText()), self._zone(ctx))

    # ---------- shifts ----------

    def visitPlus(self, ctx):
        return catalog.plus(self.visit(ctx.period()), 1, ctx.endend is not None, self._on(ctx))

    def visitMinus(self, ctx):
        return catalog.plus(self.visit(ctx.period()), -1, ctx.endend is not None, self._on(ctx))

    def visitSpot(self, ctx):
        return catalog.spot(ctx.PAIR() and ctx.PAIR().getText(), self._on(ctx))

    def visitRoll(self, ctx):
        return catalog.roll_shift(ctx.ROLL().getText(), self._on(ctx))

    def visitNearest(self, ctx):
        return catalog.nearest_shift(self.visit(ctx.anchor()))

    def visitNearestRoll(self, ctx):
        return catalog.roll_shift("NEAREST", self._on(ctx))

    def visitCutoff(self, ctx):
        return catalog.cutoff(self.visit(ctx.time()), self._zone(ctx))

    def visitCutoffOn(self, ctx):
        return catalog.cutoff_on(self._on(ctx))

    def visitPostpone(self, ctx):
        return Postpone(ctx.ROLL().getText(), self._on(ctx), self.visit(ctx.period()), ctx.cumulative is not None)

    def visitObservance(self, ctx):
        return catalog.observance([self.visit(observe) for observe in ctx.observe()])

    def visitObservePlus(self, ctx):
        return catalog.observe_plus(_weekdays(ctx), self.visit(ctx.period()), 1 if ctx.sign.text == "+" else -1)

    def visitObserveNext(self, ctx):
        return catalog.observe_anchor(_weekdays(ctx), self.visit(ctx.anchor()), 1)

    def visitObservePrev(self, ctx):
        return catalog.observe_anchor(_weekdays(ctx), self.visit(ctx.anchor()), -1)

    def visitObserveNearest(self, ctx):
        return catalog.observe_nearest(_weekdays(ctx), self.visit(ctx.anchor()))

    def visitObserveRoll(self, ctx):
        return catalog.observe_roll(_weekdays(ctx), ctx.ROLL().getText(), self._on(ctx))

    # ---------- anchors ----------

    def visitBusinessDayAnchor(self, ctx):
        return catalog.business_day(self._on(ctx))

    def visitHolidayAnchor(self, ctx):
        return catalog.holiday(self._on(ctx))

    def visitNonholidayAnchor(self, ctx):
        return catalog.nonholiday(self._on(ctx))

    # ---------- calendars ----------

    def _on(self, ctx):
        """the calendar of the ON clause of ctx, or None"""
        on = ctx.on()
        return on and self.visit(on)

    def visitOn(self, ctx):
        if ctx.SCOPE():
            return "scope", ctx.SCOPE().getText(), None
        if ctx.KEY():
            return "named", ctx.KEY().getText()
        return self.visit(ctx.calendar())

    def visitCalendar(self, ctx):
        return catalog.calendar([self.visit(term) for term in ctx.calTerm()])

    def visitScopeCalendar(self, ctx):
        return "scope", ctx.SCOPE().getText(), ctx.ignore() and self.visit(ctx.ignore())

    def visitNamedCalendar(self, ctx):
        return "named", ctx.KEY().getText()

    def visitNestedCalendar(self, ctx):
        return self.visit(ctx.calendar())

    def visitIgnore(self, ctx):
        return int(ctx.NUM().getText()), frozenset(c.getText() for c in ctx.CCY())

    # ---------- declarations ----------

    def visitRuleFile(self, ctx):
        return [self.visit(d) for d in ctx.declaration()]

    def visitRuleDecl(self, ctx):
        validity = ctx.validity()
        valid_from = validity and validity.from_ and self.visit(validity.from_)
        valid_to = validity and validity.to and self.visit(validity.to)
        attributes = [self.visit(a) for a in ctx.attributes().attribute()] if ctx.attributes() else []
        citations = [self.visit(c) for c in ctx.provenance().citation()] if ctx.provenance() else []
        tokens = list(_tokens(ctx))
        references = {t.getText()[1:] for t in tokens if t.symbol.type == RuleFileParser.EVENT}
        estimated = any(m.method.text != "UMMALQURA" for m in _descendants(ctx, RuleFileParser.HijriMethodContext))
        return catalog.rule(ctx.key.getText(), ctx.eventClass.getText(), self.visit(ctx.scopes(0)),
                            self.visit(ctx.excluded) if ctx.excluded else [], valid_from or None, valid_to or None,
                            self.visit(ctx.body()), attributes, citations, references, estimated)

    def visitExprsBody(self, ctx):
        return self.visit(ctx.exprs())

    def visitOneOf(self, ctx):
        return catalog.one_of([(a.name().getText(), catalog.exprs([self.visit(a.item())])) for a in ctx.alternative()])

    def visitScopes(self, ctx):
        return [s.getText() for s in ctx.SCOPE()]

    def visitIsoDate(self, ctx):
        return date(int(ctx.y.text), int(ctx.m.text), int(ctx.d.text))

    def visitWeekendDecl(self, ctx):
        return catalog.weekend(ctx.SCOPE().getText(), self.visit(ctx.weekdaySet()),
                               ctx.isoDate() and self.visit(ctx.isoDate()))

    def visitCalendarDecl(self, ctx):
        cut = ctx.cutoffAttribute()
        return catalog.calendar_declaration(ctx.KEY().getText(), self.visit(ctx.calendar()), cut and self.visit(cut))

    def visitCutoffAttribute(self, ctx):
        return catalog.cutoff(self.visit(ctx.time()), self._zone(ctx))

    def visitKindAttribute(self, ctx):
        return "kind", ctx.kind.text

    def visitSession(self, ctx):
        return catalog.session(self.visit(ctx.opens), ctx.closes and self.visit(ctx.closes), _text(ctx.STRING()))

    def visitTime(self, ctx):
        return catalog.time(int(ctx.NUM(0).getText()), int(ctx.NUM(1).getText()))

    def visitPrecisionAttribute(self, ctx):
        return "precision", ctx.precision.text

    def visitNote(self, ctx):
        return "note", _text(ctx.STRING())

    def visitCitation(self, ctx):
        return Citation(ctx.source.text[1:-1], ctx.article and ctx.article.text[1:-1], ctx.url and ctx.url.text[1:-1],
                        ctx.label.text)


def _descendants(ctx, kind):
    """the contexts of a kind below ctx"""
    for child in ctx.getChildren():
        if isinstance(child, kind):
            yield child
        if not isinstance(child, TerminalNode):
            yield from _descendants(child, kind)


def _parser(text):
    lexer = RuleFileLexer(InputStream(text))
    parser = RuleFileParser(CommonTokenStream(lexer))
    for recognizer in (lexer, parser):
        recognizer.removeErrorListeners()
        recognizer.addErrorListener(_Raise())
    return parser


def schedule(text):
    """one expression of the rule language"""
    return RuleVisitor().visit(_parser(text).schedule())


def parse_rules(text, announcements=None):
    """the rule catalog of a rule file

    :raises ScheduleSyntaxError: on a syntax error or an unknown character
    :raises ValueError: on an invalid date, overlapping versions, an unknown or a cyclic reference
    """
    return RuleCatalog(RuleVisitor().visit(_parser(text).ruleFile()), announcements, schedule)
