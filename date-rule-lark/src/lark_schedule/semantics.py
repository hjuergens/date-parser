"""Semantics of the shared "schedule" example: periods, business days, IMM and spot dates, ranges,
annual dates (holiday rules, also in the Hijri, Hebrew and Chinese calendars), rolling and business day conventions,
named events.

A parsed schedule is a rule: a function of the Context (base date, market and events), evaluated later.
The arithmetic follows java.time, like ScheduleSemantics.java in the JVM modules.
"""
import calendar
import itertools
import math
import re
from dataclasses import dataclass, field, replace
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
    """what a rule is evaluated against: the base date (only needed for T), the market and the named events
    (name -> rule); occurrence is the date of the event an expression is being evaluated for"""
    base: date | None = None
    market: Market = WEEKENDS
    events: dict = field(default_factory=dict)
    occurrence: date | None = None


class Unbound(Exception):
    """raised by an event date that is not bound to one occurrence yet; values() catches it"""


def event(name):
    """@NAME: one occurrence of the event, bound by values()"""
    def at(context):
        if context.occurrence is None:
            raise Unbound(name)
        return context.occurrence
    return at


def occurrences(name, year, context):
    """the dates of the event in the year: its rule evaluated with the first of January as base date"""
    if name not in context.events:
        raise ValueError(f"no event {name}")
    first = Context(date(year, 1, 1), context.market, context.events)
    return sorted(d for d in context.events[name](first) if d.year == year)


def values(expr, context):
    """the dates of an expression: one, or one per occurrence in the base year if it starts with an event"""
    try:
        return [expr(context)]
    except Unbound as unbound:
        name = unbound.args[0]
        return [expr(replace(context, occurrence=d)) for d in occurrences(name, base(context).year, context)]


def single(expr, context):
    """the date of an expression that must have exactly one, such as the end of a range"""
    dates = values(expr, context)
    if len(dates) != 1:
        raise ValueError(f"{len(dates)} dates where one is needed")
    return dates[0]


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
    """count times D (day), W (week), M (month), Q (quarter), H (half year), Y (year) or B (business day)"""
    return {"D": Period(days=count), "W": Period(days=7 * count), "M": Period(months=count),
            "Q": Period(months=3 * count), "H": Period(months=6 * count), "Y": Period(months=12 * count),
            "B": Period(business=count)}[unit]


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


def season(kind, month, offset=0):
    """EQUINOX MAR, SOLSTICE JUN, EQUINOX SEP, SOLSTICE DEC: the sun at 0, 90, 180, 270 degrees, in UTC + offset minutes"""
    def in_year(year):
        index = ["MAR", "JUN", "SEP", "DEC"].index(month) if month in ["MAR", "JUN", "SEP", "DEC"] else -1
        if index < 0 or (index % 2 == 0) != (kind == "EQUINOX"):
            raise ValueError(f"no {kind} in {month}")
        return sun_at(90 * index, year, offset)
    return in_year


def solar_term(term, offset=0):
    """SOLARTERM 1 to 24, counted from Lichun (315 degrees); SOLARTERM 5 is Qingming (15 degrees)"""
    def in_year(year):
        if not 1 <= term <= 24:
            raise ValueError(f"no solar term {term}")
        return sun_at((term + 20) % 24 * 15, year, offset)
    return in_year


def zone(text):
    """UTC+9, UTC-4: the offset in minutes; none is UTC"""
    return int(text[3:]) * 60 if text else 0


# the terms of VSOP87D (Meeus, Astronomical Algorithms, appendix III) with A >= 100: A (1e-8 rad or AU), B, C
EARTH_L = [
    [(175347046, 0, 0), (3341656, 4.6692568, 6283.07585), (34894, 4.6261024, 12566.1517), (3418, 2.8288658, 3.5231183),
     (3497, 2.744118, 5753.3848849), (3136, 3.6276704, 77713.7714681), (2676, 4.4180835, 7860.4193924),
     (2343, 6.1351624, 3930.2096962), (1273, 2.0370966, 529.6909651), (1324, 0.7424636, 11506.7697698),
     (902, 2.0450544, 26.2983198), (1199, 1.1096294, 1577.3435424), (857, 3.5084916, 398.1490034),
     (780, 1.1788265, 5223.6939198), (990, 5.2326813, 5884.9268466), (753, 2.5333905, 5507.5532387),
     (505, 4.5829256, 18849.22755), (492, 4.2050664, 775.5226113), (357, 2.9195412, 0.0673103),
     (284, 1.8986903, 796.2980068), (243, 0.3448114, 5486.7778432), (317, 5.8490195, 11790.6290887),
     (271, 0.3148861, 10977.0788047), (206, 4.8064661, 2544.3144199), (205, 1.8694781, 5573.1428014),
     (202, 2.457678, 6069.7767546), (126, 1.0830263, 20.7753955), (156, 0.8330607, 213.2990954),
     (115, 0.6454491, 0.9803211), (103, 0.6359985, 4694.0029547), (102, 4.2667982, 7.113547),
     (132, 3.4111828, 2942.4634233), (102, 0.9756922, 15720.8387849)],
    [(628331966747, 0, 0), (206059, 2.6782346, 6283.07585), (4303, 2.6351265, 12566.1517), (425, 1.5904698, 3.5231183),
     (109, 2.96618, 1577.3435424), (119, 5.7955749, 26.2983198)],
    [(52919, 0, 0), (8720, 1.0720967, 6283.07585), (309, 0.8672882, 12566.1517)],
    [(289, 5.843842, 6283.07585)],
    [(114, 3.1415927, 0)]]
EARTH_R = [
    [(100013989, 0, 0), (1670700, 3.0984635, 6283.07585), (13956, 3.0552461, 12566.1517)],
    [(103019, 1.1074897, 6283.07585)]]


def vsop(series, tau):
    total = 0
    for terms in reversed(series):
        total = total * tau + sum(a * math.cos(b + c * tau) for a, b, c in terms)
    return total * 1e-8


def sun_longitude(jde):
    """the apparent longitude of the sun in degrees (Meeus, chapter 25, with VSOP87, nutation and aberration)"""
    tau = (jde - 2451545) / 365250
    t = 10 * tau
    omega, sun = math.radians(125.04452 - 1934.136261 * t), math.radians(280.4665 + 36000.7698 * t)
    moon = math.radians(218.3165 + 481267.8813 * t)
    nutation = -17.20 * math.sin(omega) - 1.32 * math.sin(2 * sun) - 0.23 * math.sin(2 * moon) + 0.21 * math.sin(2 * omega)
    return math.degrees(vsop(EARTH_L, tau)) + 180 + (nutation - 0.09033 - 20.4898 / vsop(EARTH_R, tau)) / 3600


def delta_t(year):
    """TD - UT in seconds: Espenak and Meeus' polynomials for 1986 to 2150, their long-term formula outside"""
    t, u = year - 2000, (year - 1820) / 100
    long_term = -20 + 32 * u * u
    if 1986 <= year < 2005:
        return 63.86 + 0.3345 * t - 0.060374 * t ** 2 + 0.0017275 * t ** 3 + 0.000651814 * t ** 4 + 0.00002373599 * t ** 5
    if 2005 <= year < 2050:
        return 62.92 + 0.32217 * t + 0.005589 * t * t
    return long_term - 0.5628 * (2150 - year) if 2050 <= year < 2150 else long_term


def sun_at(degrees, year, offset):
    """the day in the year on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes"""
    day = 79 + degrees * 365.2422 / 360  # days after 1 January, from the March equinox on
    if day > 365.2422:
        day -= 365.2422
    jde = date(year, 1, 1).toordinal() - date(1970, 1, 1).toordinal() + 2440587.5 + day
    for _ in range(50):
        step = 58 * math.sin(math.radians(degrees - sun_longitude(jde)))
        jde += step
        if abs(step) < 1e-7:
            break
    local = jde - delta_t(year + day / 365.25) / 86400 + offset / 1440
    return date(1970, 1, 1) + timedelta(days=math.floor(local - 2440587.5))


def in_year(annual):
    """an annual date in the year of the base date"""
    return lambda context: annual(base(context).year)


# anchors of > NEXT and > PREV: the first match strictly after (direction 1) or before (-1) a date
def annual_anchor(annual):
    def find(d, direction, context):
        match = annual(d.year)
        return match if (match > d if direction > 0 else match < d) else annual(d.year + direction)
    return find


def weekday_anchor(weekday):
    def find(d, direction, context):
        days = (WEEKDAYS.index(weekday) - d.weekday()) * direction % 7 or 7
        return d + timedelta(days=direction * days)
    return find


def any_of(anchors):
    """NEXT {MON, FRI}, NEXT {1 FEB, 1 MAY}: the nearest match of any of the anchors"""
    def find(d, direction, context):
        found = [anchor(d, direction, context) for anchor in anchors]
        return min(found) if direction > 0 else max(found)
    return find


def ordinal(text):
    """1ST, 2ND, 3RD, 4TH, 11TH, 21ST as a count; None (no ordinal) is 1"""
    if text is None:
        return 1
    n = int(text[:-2])
    suffix = "TH" if n % 100 in (11, 12, 13) else {1: "ST", 2: "ND", 3: "RD"}.get(n % 10, "TH")
    if text[-2:] != suffix:
        raise ValueError(f"not an ordinal: {text}")
    return n


def repeat(anchor, direction, count):
    """NEXT 3RD FRI: the anchor found count times, each time from the one before"""
    def shift(d, context):
        if count < 1:
            raise ValueError(f"count must be positive: {count}")
        for _ in range(count):
            d = anchor(d, direction, context)
        return d
    return shift


def business_day_anchor(d, direction, context):
    return plus_business_days(d, direction, context.market)


def imm_anchor(d, direction, context):
    return next_imm(d) if direction > 0 else previous_imm(d)


def event_anchor(name):
    """the occurrence of the event strictly after (before) the date, in its year or the next (previous)"""
    def find(d, direction, context):
        for year in (d.year, d.year + direction):
            found = [o for o in occurrences(name, year, context) if (o > d if direction > 0 else o < d)]
            if found:
                return found[0] if direction > 0 else found[-1]
        raise ValueError(f"no {name} {'after' if direction > 0 else 'before'} {d}")
    return find


def roll(d, convention, market):
    """FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month, then preceding);
    BOM, EOM, BOQ, EOQ, BOH, EOH, BOY, EOY (first and last day of month, quarter, half year and year)"""
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
    if convention in ("BOQ", "EOQ", "BOH", "EOH"):
        months = 3 if convention[2] == "Q" else 6
        first = date(d.year, (d.month - 1) // months * months + 1, 1)
        return first if convention[0] == "B" else roll(first.replace(month=first.month + months - 1), "EOM", market)
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
