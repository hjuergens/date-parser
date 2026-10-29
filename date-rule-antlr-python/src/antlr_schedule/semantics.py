"""Semantics of the shared "schedule" example: periods, business days, IMM and spot dates, ranges.

The arithmetic follows java.time, like ScheduleSemantics.java in the JVM modules.
"""
import calendar
import itertools
import re
from dataclasses import dataclass
from datetime import date, timedelta


@dataclass(frozen=True)
class Period:
    """months and days as in java.time.Period, or business days (unit B)"""
    months: int = 0
    days: int = 0
    business: int = 0

    def times(self, k):
        return Period(k * self.months, k * self.days, k * self.business)


def period(count, unit):
    """count times D (day), W (week), M (month), Q (quarter), Y (year) or B (business day)"""
    return {"D": Period(days=count), "W": Period(days=7 * count), "M": Period(months=count),
            "Q": Period(months=3 * count), "Y": Period(months=12 * count), "B": Period(business=count)}[unit]


def plus(d, p, sign=1):
    """months first (day clamped to the end of the month), then days; business days on their own"""
    if p.business:
        return plus_business_days(d, sign * p.business)
    year, month = divmod(d.year * 12 + d.month - 1 + sign * p.months, 12)
    month += 1
    clamped = date(year, month, min(d.day, calendar.monthrange(year, month)[1]))
    return clamped + timedelta(days=sign * p.days)


def plus_business_days(d, count):
    """count business days later (earlier if negative): only Saturdays and Sundays are skipped"""
    step = 1 if count > 0 else -1
    while count:
        d += timedelta(days=step)
        if d.weekday() < 5:
            count -= step
    return d


def next_imm(d):
    """the IMM date (third Wednesday of March, June, September or December) strictly after d"""
    year, month = d.year, d.month
    while True:
        if month % 3 == 0:
            first = date(year, month, 1)
            imm = first + timedelta(days=(2 - first.weekday()) % 7 + 14)
            if imm > d:
                return imm
        year, month = (year + 1, 1) if month == 12 else (year, month + 1)


def spot(d, pair=None):
    """FX spot: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair)"""
    t_plus_one = pair and re.fullmatch(r"USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD", pair)
    return plus_business_days(d, 1 if t_plus_one else 2)


def expand(first, step, last):
    """first + k * step for k = 0, 1, ... while not after last"""
    if step.months <= 0 and step.days <= 0 and step.business <= 0:
        raise ValueError(f"step must be positive: {step}")
    dates = []
    for k in itertools.count():
        d = plus(first, step.times(k))
        if d > last:
            return dates
        dates.append(d)
