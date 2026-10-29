"""The shared "schedule" example parsed with Lark; the same grammar runs as Earley and as LALR(1) parser."""
from datetime import date

from lark import Lark, Transformer, v_args
from lark.exceptions import VisitError

from . import semantics

PARSERS = {name: Lark.open("schedule.lark", rel_to=__file__, parser=name) for name in ("lalr", "earley")}


@v_args(inline=True)
class Dates(Transformer):
    """computes the dates bottom-up from the parse tree"""

    def exprs(self, *dates):
        return list(dates)

    def range(self, first, step, last):
        return semantics.expand(first, step, last)

    def expr(self, start, *shifts):
        for shift in shifts:
            start = shift(start)
        return start

    def plus(self, period):
        return lambda d: semantics.plus(d, period)

    def minus(self, period):
        return lambda d: semantics.plus(d, period, -1)

    def imm(self):
        return semantics.next_imm

    def spot(self, pair):
        return lambda d: semantics.spot(d, pair and str(pair))

    def iso(self, y, m, d):
        return date(int(y), int(m), int(d))

    def german(self, d, m, y):
        return date(int(y), int(m), int(d))

    def us(self, m, d, y):
        return date(int(y), int(m), int(d))

    def period(self, n, unit):
        return semantics.period(int(n), str(unit))


def parse(text, parser="lalr"):
    """the dates of a schedule

    :raises lark.exceptions.UnexpectedInput: on a syntax error
    :raises ValueError: on an invalid date or a range without positive step
    """
    tree = PARSERS[parser].parse(text)
    try:
        return Dates().transform(tree)
    except VisitError as e:
        raise e.orig_exc from None
