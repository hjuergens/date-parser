"""The shared "schedule" example parsed with ANTLR 4's Python target; a visitor computes the dates."""
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


class Dates(DateScheduleVisitor):
    """computes the dates top-down: each visit returns the value of its node"""

    def visitSchedule(self, ctx):
        # Python target: the rule "range" becomes range_(), it would shadow the builtin
        if ctx.range_():
            return self.visit(ctx.range_())
        return [self.visit(expr) for expr in ctx.expr()]

    def visitRange(self, ctx):
        return semantics.expand(self.visit(ctx.first), self.visit(ctx.step), self.visit(ctx.last))

    # shifts are applied from left to right
    def visitExpr(self, ctx):
        d = self.visit(ctx.date())
        for shift in ctx.shift():
            d = self.visit(shift)(d)
        return d

    def visitPlus(self, ctx):
        return lambda d: semantics.plus(d, self.visit(ctx.period()))

    def visitMinus(self, ctx):
        return lambda d: semantics.plus(d, self.visit(ctx.period()), -1)

    def visitImm(self, ctx):
        return semantics.next_imm

    def visitSpot(self, ctx):
        return lambda d: semantics.spot(d, ctx.PAIR() and ctx.PAIR().getText())

    def visitIso(self, ctx):
        return date(int(ctx.y.text), int(ctx.m.text), int(ctx.d.text))

    visitGerman = visitUs = visitIso

    def visitPeriod(self, ctx):
        return semantics.period(int(ctx.NUM().getText()), ctx.UNIT().getText())


def parse(text):
    """the dates of a schedule

    :raises ScheduleSyntaxError: on a syntax error or an unknown character
    :raises ValueError: on an invalid date or a range without positive step
    """
    lexer = DateScheduleLexer(InputStream(text))
    parser = DateScheduleParser(CommonTokenStream(lexer))
    for recognizer in (lexer, parser):
        recognizer.removeErrorListeners()
        recognizer.addErrorListener(_Raise())
    return Dates().visit(parser.schedule())
