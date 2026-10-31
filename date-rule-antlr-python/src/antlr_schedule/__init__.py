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
        # Python target: the rule "range" becomes range_(), it would shadow the builtin
        if ctx.range_():
            return self.visit(ctx.range_())
        items = [self.visit(item) for item in ctx.item()]
        return lambda context: [d for item in items for d in semantics.values(item, context) if d is not None]

    def visitItem(self, ctx):
        expr = self.visit(ctx.expr())
        if not ctx.WEEKDAY():
            return expr
        keep = semantics.only_on({w.getText() for w in ctx.WEEKDAY()})
        return lambda context: keep(expr(context))

    def visitRange(self, ctx):
        first, step, last = self.visit(ctx.first), self.visit(ctx.step), self.visit(ctx.last)
        if not step.is_positive():
            raise ValueError(f"step must be positive: {step}")
        return lambda context: semantics.expand(semantics.single(first, context), step,
                                                semantics.single(last, context), context.market)

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
        anchor = self.visit(ctx.anchor())
        return lambda d, context: anchor(d, 1, context)

    def visitPrev(self, ctx):
        anchor = self.visit(ctx.anchor())
        return lambda d, context: anchor(d, -1, context)

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
        return semantics.annual_anchor(self.visit(ctx.annual()))

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

    def visitMonthDay(self, ctx):
        return semantics.month_day(int(ctx.NUM().getText()), ctx.MONTH().getText())

    def visitNthWeekday(self, ctx):
        return semantics.nth_weekday(int(ctx.NUM().getText()), ctx.WEEKDAY().getText(), ctx.MONTH().getText())

    def visitLastWeekday(self, ctx):
        return semantics.last_weekday(ctx.WEEKDAY().getText(), ctx.MONTH().getText())

    def visitEaster(self, ctx):
        return semantics.easter

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
