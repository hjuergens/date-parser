"""Semantics of the shared "schedule" example: periods, business days, IMM and spot dates, ranges,
annual dates (holiday rules, also in the Hijri, Hebrew and Chinese calendars), rolling and business day conventions.

A parsed schedule is a rule: a function of the Context (base date and market), evaluated later.
The arithmetic follows java.time, like ScheduleSemantics.java in the JVM modules.
"""
import calendar
import itertools
import re
from dataclasses import dataclass
from datetime import date, timedelta

from hijridate import Gregorian, Hijri
from lunardate import LunarDate
from pyluach.dates import HebrewDate


class Market:
    """Monday to Friday except the holidays; a market only answers questions about days"""

    def __init__(self, holidays=()):
        self.holidays = frozenset(holidays)

    def is_business_day(self, d):
        return d.weekday() < 5 and d not in self.holidays

    def spot(self, trade, pair=None):
        """FX spot: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair)"""
        t_plus_one = pair and re.fullmatch(r"USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD", pair)
        return plus_business_days(trade, 1 if t_plus_one else 2, self)


WEEKENDS = Market()


class RuleMarket(Market):
    """Monday to Friday except the dates the rule yields, evaluated with the first of January of the year before,
    of the year and of the year after as base date (an observed holiday can fall into the neighbouring year)"""

    def __init__(self, rule):
        super().__init__()
        self.rule = rule
        self.years = {}

    def is_business_day(self, d):
        if d.weekday() >= 5:
            return False
        for year in (d.year - 1, d.year, d.year + 1):
            if year not in self.years:
                self.years[year] = self.rule(Context(date(year, 1, 1)))
            if d in self.years[year]:
                return False
        return True


@dataclass(frozen=True)
class Context:
    """what a rule is evaluated against: the base date (only needed for T) and the market"""
    base: date | None = None
    market: Market = WEEKENDS


def base(context):
    """T: the base date of the evaluation"""
    if context.base is None:
        raise ValueError("no base date")
    return context.base


@dataclass(frozen=True)
class Period:
    """months and days as in java.time.Period, or business days (unit B)"""
    months: int = 0
    days: int = 0
    business: int = 0

    def times(self, k):
        return Period(k * self.months, k * self.days, k * self.business)

    def is_positive(self):
        return self.months > 0 or self.days > 0 or self.business > 0


def period(count, unit):
    """count times D (day), W (week), M (month), Q (quarter), Y (year) or B (business day)"""
    return {"D": Period(days=count), "W": Period(days=7 * count), "M": Period(months=count),
            "Q": Period(months=3 * count), "Y": Period(months=12 * count), "B": Period(business=count)}[unit]


def plus(d, p, sign, market):
    """months first (day clamped to the end of the month), then days; business days of the market on their own"""
    if p.business:
        return plus_business_days(d, sign * p.business, market)
    year, month = divmod(d.year * 12 + d.month - 1 + sign * p.months, 12)
    month += 1
    clamped = date(year, month, min(d.day, calendar.monthrange(year, month)[1]))
    return clamped + timedelta(days=sign * p.days)


def plus_business_days(d, count, market):
    """count business days of the market later (earlier if negative)"""
    step = 1 if count > 0 else -1
    while count:
        d += timedelta(days=step)
        if market.is_business_day(d):
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


def expand(first, step, last, market):
    """first + k * step for k = 0, 1, ... while not after last"""
    dates = []
    for k in itertools.count():
        d = plus(first, step.times(k), 1, market)
        if d > last:
            return dates
        dates.append(d)


def previous_imm(d):
    """the IMM date strictly before d"""
    year, month = d.year, d.month
    while True:
        if month % 3 == 0:
            first = date(year, month, 1)
            imm = first + timedelta(days=(2 - first.weekday()) % 7 + 14)
            if imm < d:
                return imm
        year, month = (year - 1, 12) if month == 1 else (year, month - 1)


MONTHS = "JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split()
WEEKDAYS = "MON TUE WED THU FRI SAT SUN".split()
HIJRI = ("MUHARRAM SAFAR RABIALAWWAL RABIALTHANI JUMADAALULA JUMADAALAKHIRA RAJAB SHABAN RAMADAN SHAWWAL "
         "DHULQADAH DHULHIJJAH").split()                                       # Umm al-Qura
HEBREW = "NISAN IYAR SIVAN TAMMUZ AV ELUL TISHRI HESHVAN KISLEV TEVET SHEVAT ADAR ADARII".split()  # ADAR: Adar I in a leap year
CHINESE = "ZHENGYUE ERYUE SANYUE SIYUE WUYUE LIUYUE QIYUE BAYUE JIUYUE SHIYUE DONGYUE LAYUE".split()  # leap months excluded


# annual dates: functions of the year
def month_day(day, month):
    """day of month, e.g. 4 JUL, or of a lunar month, e.g. 1 SHAWWAL"""
    if month not in MONTHS:
        return lambda year: lunar(day, month, year)
    return lambda year: date(year, MONTHS.index(month) + 1, day)


def lunar(day, month, year):
    """the day of a Hijri, Hebrew or Chinese month in the Gregorian year; an error if it occurs twice or not at all"""
    if month in HIJRI:
        first, last = (Gregorian(year, m, d).to_hijri().year for m, d in ((1, 1), (12, 31)))
        candidates = [lambda y=y: Hijri(y, HIJRI.index(month) + 1, day).to_gregorian() for y in range(first, last + 1)]
    elif month in HEBREW:
        candidates = [lambda y=y: HebrewDate(y, HEBREW.index(month) + 1, day).to_pydate() for y in (year + 3760, year + 3761)]
    else:
        candidates = [lambda y=y: LunarDate(y, CHINESE.index(month) + 1, day).to_solar_date() for y in (year - 1, year)]
    hits = []
    for candidate in candidates:
        try:
            d = candidate()
        except (ValueError, OverflowError):
            continue
        if d.year == year:
            hits.append(d)
    if len(hits) != 1:
        raise ValueError(f"{day} {month} occurs {len(hits)} times in {year}")
    return hits[0]


def only_on(weekdays):
    """IF MON TUE ...: the date on these weekdays, otherwise none"""
    return lambda d: d if WEEKDAYS[d.weekday()] in weekdays else None


def nth_weekday(n, weekday, month):
    """the n-th weekday of the month, e.g. 3 MON JAN; there is no fifth Monday in every month"""
    def in_year(year):
        first = date(year, MONTHS.index(month) + 1, 1)
        d = first + timedelta(days=(WEEKDAYS.index(weekday) - first.weekday()) % 7 + 7 * (n - 1))
        if n < 1 or d.month != first.month:
            raise ValueError(f"no {n} {weekday} {month} in {year}")
        return d
    return in_year


def last_weekday(weekday, month):
    """the last weekday of the month, e.g. LAST MON MAY"""
    def in_year(year):
        m = MONTHS.index(month) + 1
        last = date(year, m, calendar.monthrange(year, m)[1])
        return last - timedelta(days=(last.weekday() - WEEKDAYS.index(weekday)) % 7)
    return in_year


def easter(year):
    """Western Easter Sunday (anonymous Gregorian algorithm)"""
    a, b, c = year % 19, year // 100, year % 100
    d, e = divmod(b, 4)
    g = (8 * b + 13) // 25
    h = (19 * a + b - d - g + 15) % 30
    i, k = divmod(c, 4)
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    month, day = divmod(h + l - 7 * m + 114, 31)
    return date(year, month, day + 1)


def in_year(annual):
    """an annual date in the year of the base date"""
    return lambda context: annual(base(context).year)


# anchors of > NEXT and > PREV: the first match strictly after (direction 1) or before (-1) a date
def annual_anchor(annual):
    def find(d, direction, market):
        match = annual(d.year)
        return match if (match > d if direction > 0 else match < d) else annual(d.year + direction)
    return find


def weekday_anchor(weekday):
    def find(d, direction, market):
        days = (WEEKDAYS.index(weekday) - d.weekday()) * direction % 7 or 7
        return d + timedelta(days=direction * days)
    return find


def business_day_anchor(d, direction, market):
    return plus_business_days(d, direction, market)


def imm_anchor(d, direction, market):
    return next_imm(d) if direction > 0 else previous_imm(d)


def roll(d, convention, market):
    """FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month, then preceding);
    BOM, EOM, BOY, EOY (first and last day of month and year)"""
    if convention == "FOLLOWING":
        return d if market.is_business_day(d) else plus_business_days(d, 1, market)
    if convention == "PRECEDING":
        return d if market.is_business_day(d) else plus_business_days(d, -1, market)
    if convention == "MODFOLLOWING":
        following = roll(d, "FOLLOWING", market)
        return following if following.month == d.month else roll(d, "PRECEDING", market)
    if convention == "BOM":
        return d.replace(day=1)
    if convention == "EOM":
        return d.replace(day=calendar.monthrange(d.year, d.month)[1])
    if convention == "BOY":
        return date(d.year, 1, 1)
    if convention == "EOY":
        return date(d.year, 12, 31)
    raise ValueError(f"unknown convention: {convention}")


def observe(d, shifts, market):
    """shifts: (weekday, period, sign) such as {SAT - 1D, SUN + 1D}; the first for the weekday of d applies,
    shifts don't chain"""
    for weekday, p, sign in shifts:
        if WEEKDAYS.index(weekday) == d.weekday():
            return plus(d, p, sign, market)
    return d
