"""The shared "schedule" example parsed with Lark; the same grammar runs as Earley and as LALR(1) parser.

parse() returns the rule: a function of semantics.Context (base date and market) that is evaluated later.
"""
from datetime import date

from lark import Lark, Transformer, v_args
from lark.exceptions import VisitError

from . import semantics

PARSERS = {name: Lark.open("schedule.lark", rel_to=__file__, parser=name) for name in ("lalr", "earley")}


@v_args(inline=True)
class Rules(Transformer):
    """builds the rule bottom-up from the parse tree: every node becomes a function of the context"""

    def exprs(self, *exprs):
        return lambda context: [expr(context) for expr in exprs]

    def range(self, first, step, last):
        if not step.is_positive():
            raise ValueError(f"step must be positive: {step}")
        return lambda context: semantics.expand(first(context), step, last(context), context.market)

    def expr(self, start, *shifts):
        def at(context):
            d = start(context)
            for shift in shifts:
                d = shift(d, context.market)
            return d
        return at

    def plus(self, period):
        return lambda d, market: semantics.plus(d, period, 1, market)

    def minus(self, period):
        return lambda d, market: semantics.plus(d, period, -1, market)

    def imm(self):
        return lambda d, market: semantics.next_imm(d)

    def spot(self, pair):
        return lambda d, market: market.spot(d, pair and str(pair))

    def base(self):
        return semantics.base

    def iso(self, y, m, d):
        value = date(int(y), int(m), int(d))
        return lambda context: value

    def german(self, d, m, y):
        return self.iso(y, m, d)

    def us(self, m, d, y):
        return self.iso(y, m, d)

    def period(self, n, unit):
        return semantics.period(int(n), str(unit))


def parse(text, parser="lalr"):
    """the rule of a schedule; evaluate it with rule(semantics.Context(base, market))

    :raises lark.exceptions.UnexpectedInput: on a syntax error
    :raises ValueError: on an invalid date or a range without positive step
    """
    tree = PARSERS[parser].parse(text)
    try:
        return Rules().transform(tree)
    except VisitError as e:
        raise e.orig_exc from None
