"""The shared "schedule" example parsed with ANTLR 4's Python target.

A visitor builds the rule: a function of semantics.Context (base date and market) that is evaluated later.
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
    """builds the rule top-down: each visit returns a function of the context (shifts: of date and market)"""

    def visitSchedule(self, ctx):
        # Python target: the rule "range" becomes range_(), it would shadow the builtin
        if ctx.range_():
            return self.visit(ctx.range_())
        exprs = [self.visit(expr) for expr in ctx.expr()]
        return lambda context: [expr(context) for expr in exprs]

    def visitRange(self, ctx):
        first, step, last = self.visit(ctx.first), self.visit(ctx.step), self.visit(ctx.last)
        if not step.is_positive():
            raise ValueError(f"step must be positive: {step}")
        return lambda context: semantics.expand(first(context), step, last(context), context.market)

    # shifts are applied from left to right
    def visitExpr(self, ctx):
        start, shifts = self.visit(ctx.date()), [self.visit(shift) for shift in ctx.shift()]

        def at(context):
            d = start(context)
            for shift in shifts:
                d = shift(d, context.market)
            return d
        return at

    def visitPlus(self, ctx):
        period = self.visit(ctx.period())
        return lambda d, market: semantics.plus(d, period, 1, market)

    def visitMinus(self, ctx):
        period = self.visit(ctx.period())
        return lambda d, market: semantics.plus(d, period, -1, market)

    def visitImm(self, ctx):
        return lambda d, market: semantics.next_imm(d)

    def visitSpot(self, ctx):
        pair = ctx.PAIR() and ctx.PAIR().getText()
        return lambda d, market: market.spot(d, pair)

    def visitBase(self, ctx):
        return semantics.base

    def visitIso(self, ctx):
        value = date(int(ctx.y.text), int(ctx.m.text), int(ctx.d.text))
        return lambda context: value

    visitGerman = visitUs = visitIso

    def visitPeriod(self, ctx):
        return semantics.period(int(ctx.NUM().getText()), ctx.UNIT().getText())


def parse(text):
    """the rule of a schedule; evaluate it with rule(semantics.Context(base, market))

    :raises ScheduleSyntaxError: on a syntax error or an unknown character
    :raises ValueError: on an invalid date or a range without positive step
    """
    lexer = DateScheduleLexer(InputStream(text))
    parser = DateScheduleParser(CommonTokenStream(lexer))
    for recognizer in (lexer, parser):
        recognizer.removeErrorListeners()
        recognizer.addErrorListener(_Raise())
    return Rules().visit(parser.schedule())
