"""The shared "schedule" example parsed with ANTLR 4's Python target.

A visitor builds the rule: a function of semantics.Context (base date, market and events) that is evaluated later.
"""
from datetime import date

from antlr4 import CommonTokenStream, InputStream
from antlr4.error.ErrorListener import ErrorListener

from . import semantics
from .generated.DateScheduleLexer import DateScheduleLexer
from .generated.DateScheduleParser import DateScheduleParser
from .generated.DateScheduleVisitor import DateScheduleVisitor


class ScheduleSyntaxError(Exception):
    """a syntax error or an unknown character"""


class _Raise(ErrorListener):
    """ends the parse at the first error (the default listener only prints and recovers)"""

    def syntaxError(self, recognizer, offendingSymbol, line, column, msg, e):
        raise ScheduleSyntaxError(f"column {column}: {msg}")


class Rules(DateScheduleVisitor):
    """builds the rule top-down: each visit returns a function of the context (shifts: of date and context)"""

    def visitSchedule(self, ctx):
        items = [self.visit(item) for item in ctx.item()]
        return lambda context: [d for item in items for d in semantics.values(item, context) if d is not None]

    def visitItem(self, ctx):
        if ctx.ORDINAL():   # a pick: the item starts with a series or an event
            start = self.visit(ctx.series()) if ctx.series() else semantics.event(ctx.EVENT().getText()[1:])
            expr = self._shifted(start, [self.visit(shift) for shift in ctx.shift()])
        else:
            expr = self.visit(ctx.expr())
        for combine in ctx.combine():
            operand = self.visit(combine.operand())
            node = semantics.combined(expr, combine.op.text == "&", operand)
            shifts = [self.visit(shift) for shift in combine.shift()]
            expr = self._shifted(node, shifts)
        if ctx.WEEKDAY():
            keep, unfiltered = semantics.only_on({w.getText() for w in ctx.WEEKDAY()}), expr
            expr = lambda context: keep(unfiltered(context))
        if ctx.ORDINAL():
            expr = semantics.pick(semantics.ordinal(ctx.ORDINAL().getText()), ctx.fromEnd is not None, expr)
        return expr

    @staticmethod
    def _shifted(start, shifts):
        def at(context):
            d = start(context)
            for shift in shifts:
                d = shift(d, context)
            return d
        return at

    def visitSingleOperand(self, ctx):
        return [self.visit(ctx.date())]

    def visitSetOperand(self, ctx):
        return [self.visit(expr) for expr in ctx.expr()]

    def visitDatesInYear(self, ctx):
        return semantics.dates_in_year([self.visit(annual) for annual in ctx.annual()])

    def visitSeriesDate(self, ctx):
        return self.visit(ctx.series())

    def visitSeries(self, ctx):
        step = self.visit(ctx.step) if ctx.step else semantics.period(1, "D")
        if not step.is_positive():
            raise ValueError(f"step must be positive: {step}")
        if ctx.last is None and ctx.right.text == "[":
            raise ValueError("an open series ends with ]")
        return semantics.series_date(self.visit(ctx.first), step, ctx.last and self.visit(ctx.last),
                                     ctx.left.text == "]", ctx.right.text == "[")

    def visitSingle(self, ctx):
        return self.visit(ctx.singleDate())

    def visitEndpoint(self, ctx):
        return self._shifted(self.visit(ctx.singleDate()), [self.visit(shift) for shift in ctx.shift()])

    # shifts are applied from left to right
    def visitExpr(self, ctx):
        start, shifts = self.visit(ctx.date()), [self.visit(shift) for shift in ctx.shift()]

        def at(context):
            d = start(context)
            for shift in shifts:
                d = shift(d, context)
            return d
        return at

    def visitPlus(self, ctx):
        period = self.visit(ctx.period())
        return lambda d, context: semantics.plus(d, period, 1, context.market)

    def visitMinus(self, ctx):
        period = self.visit(ctx.period())
        return lambda d, context: semantics.plus(d, period, -1, context.market)

    def visitImm(self, ctx):
        return lambda d, context: semantics.next_imm(d)

    def visitSpot(self, ctx):
        pair = ctx.PAIR() and ctx.PAIR().getText()
        return lambda d, context: context.market.spot(d, pair)

    def visitRoll(self, ctx):
        convention = ctx.ROLL().getText()
        return lambda d, context: semantics.roll(d, convention, context.market)

    def visitNext(self, ctx):
        return semantics.repeat(self.visit(ctx.anchor()), 1, semantics.ordinal(ctx.ORDINAL() and ctx.ORDINAL().getText()))

    def visitPrev(self, ctx):
        return semantics.repeat(self.visit(ctx.anchor()), -1, semantics.ordinal(ctx.ORDINAL() and ctx.ORDINAL().getText()))

    def visitSelectIn(self, ctx):
        count = ctx.count()
        n = semantics.ordinal(count.ORDINAL().getText()) if count.ORDINAL() else 1
        return semantics.select(self.visit(ctx.select()), n, count.last is not None, ctx.PERIOD().getText())

    def visitSingleSelect(self, ctx):
        return self.visit(ctx.chosen())

    def visitAnySelect(self, ctx):
        return semantics.any_of([self.visit(target) for target in ctx.target()])

    def visitChosenTarget(self, ctx):
        return self.visit(ctx.chosen())

    def visitLastWeekdayAnchor(self, ctx):
        return semantics.annual_anchor(semantics.last_weekday(ctx.WEEKDAY().getText(), ctx.MONTH().getText()))

    def visitSingleAnchor(self, ctx):
        return self.visit(ctx.target())

    def visitAnyOf(self, ctx):
        return semantics.any_of([self.visit(target) for target in ctx.target()])

    def visitObservance(self, ctx):
        shifts = [self.visit(observe) for observe in ctx.observe()]
        return lambda d, context: semantics.observe(d, shifts, context.market)

    def visitObserve(self, ctx):
        return ctx.WEEKDAY().getText(), self.visit(ctx.period()), 1 if ctx.sign.text == "+" else -1

    def visitWeekdayAnchor(self, ctx):
        return semantics.weekday_anchor(ctx.WEEKDAY().getText())

    def visitBusinessDayAnchor(self, ctx):
        return semantics.business_day_anchor

    def visitImmAnchor(self, ctx):
        return semantics.imm_anchor

    def visitAnnualAnchor(self, ctx):
        return semantics.annual_anchor(self.visit(ctx.fixedAnnual()))

    def visitEventAnchor(self, ctx):
        return semantics.event_anchor(ctx.EVENT().getText()[1:])

    def visitBase(self, ctx):
        return semantics.base

    def visitTom(self, ctx):
        return lambda context: semantics.plus_business_days(semantics.base(context), 1, context.market)

    def visitSpotDate(self, ctx):
        pair = ctx.PAIR() and ctx.PAIR().getText()
        return lambda context: context.market.spot(semantics.base(context), pair)

    def visitInYear(self, ctx):
        return semantics.in_year(self.visit(ctx.annual()))

    def visitEvent(self, ctx):
        return semantics.event(ctx.EVENT().getText()[1:])

    def visitFixed(self, ctx):
        return self.visit(ctx.fixedAnnual())

    def visitMonthDay(self, ctx):
        return semantics.month_day(int(ctx.NUM().getText()), ctx.MONTH().getText())

    def visitNthWeekday(self, ctx):
        return semantics.nth_weekday(int(ctx.NUM().getText()), ctx.WEEKDAY().getText(), ctx.MONTH().getText())

    def visitLastWeekday(self, ctx):
        return semantics.last_weekday(ctx.WEEKDAY().getText(), ctx.MONTH().getText())

    def visitEaster(self, ctx):
        return semantics.easter

    def visitSeason(self, ctx):
        return semantics.season(ctx.k.text, ctx.MONTH().getText(), semantics.zone(ctx.ZONE() and ctx.ZONE().getText()))

    def visitSolarTerm(self, ctx):
        return semantics.solar_term(int(ctx.NUM().getText()), semantics.zone(ctx.ZONE() and ctx.ZONE().getText()))

    def visitIso(self, ctx):
        value = date(int(ctx.y.text), int(ctx.m.text), int(ctx.d.text))
        return lambda context: value

    visitGerman = visitUs = visitIso

    def visitPeriod(self, ctx):
        return semantics.period(int(ctx.NUM().getText()), ctx.UNIT().getText())


def parse(text):
    """the rule of a schedule; evaluate it with rule(semantics.Context(base, market, events))

    :raises ScheduleSyntaxError: on a syntax error or an unknown character
    :raises ValueError: on an invalid date or a range without positive step
    """
    lexer = DateScheduleLexer(InputStream(text))
    parser = DateScheduleParser(CommonTokenStream(lexer))
    for recognizer in (lexer, parser):
        recognizer.removeErrorListeners()
        recognizer.addErrorListener(_Raise())
    return Rules().visit(parser.schedule())
