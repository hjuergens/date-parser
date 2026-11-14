/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * Dates are {year, month, day} objects, periods {months, days, business}; the arithmetic
 * follows java.time: months first (day clamped to the end of the month), then days.
 * parse() returns the rule, a function of the context { base, market, events } that is evaluated later;
 * a market is an object with isBusinessDay(date), the default skips Saturdays and Sundays only;
 * events maps a name to a rule.
 */

{{
  function daysInMonth(year, month) {
    return new Date(Date.UTC(year, month, 0)).getUTCDate();
  }

  function date(year, month, day) {
    if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) {
      throw new RangeError(`invalid date ${year}-${month}-${day}`);
    }
    return { year, month, day };
  }

  // the default market: Monday to Friday, no holidays
  const WEEKENDS = { isBusinessDay: (d) => new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay() % 6 !== 0 };

  // the rule of a schedule; base date, market and events only come with the evaluation
  function rule(dates) {
    return (context = {}) => dates({ base: context.base, market: context.market ?? WEEKENDS, events: context.events ?? {},
      fixing: context.fixing ?? WEEKENDS });
  }

  // thrown by a node with several values that is not bound yet; values() catches it and evaluates the expression once
  // per date of dates(c)
  class Unbound {
    constructor(dates) {
      this.dates = dates;
    }
  }

  // the value of the next node with several values in this evaluation: bound by values() (c.bindings, counted by
  // c.cursor in the order the evaluation reaches them), or Unbound
  function take(c, dates) {
    if (c.cursor && c.cursor[0] < c.bindings.length) return c.bindings[c.cursor[0]++];
    throw new Unbound(dates);
  }

  // @NAME: one occurrence of the event, bound by values()
  function event(name) {
    return (c) => take(c, (c) => occurrences(name, base(c).year, c));
  }

  // [first : step : last]: one element of the series, bound by values(), so the shifts after it apply to each
  function series(first, step, last, startOut, endOut) {
    return (c) => take(c, (c) => {
      const start = single(first, c);
      if (last === null) {   // an open series [first : step : ], only under a pick, which sets how many elements it yields
        if (!c.limit) throw new Error('an open series needs a pick in front of the item');
        return Array.from({ length: c.limit }, (_, k) => plus(start, { months: k * step.months, days: k * step.days, business: k * step.business, fixing: k * (step.fixing ?? 0) }, 1, c.market, c.fixing))
          .filter((d) => !(startOut && compare(d, start) <= 0));
      }
      const end = single(last, c);
      return expand(start, step, end, c.market, c.fixing)
        .filter((d) => !(startOut && compare(d, start) <= 0 || endOut && compare(d, end) >= 0));
    });
  }

  // 5TH ...: the count-th date of the item strictly after T, in the order the item yields them; 2ND LAST ...: counted
  // from the last one. An open series grows until there are enough dates, up to 10000 elements
  function pick(count, fromEnd, item) {
    return (c) => {
      if (count < 1) throw new Error(`count must be positive: ${count}`);
      const t = base(c);
      for (const limit of fromEnd ? [undefined] : [16, 256, 4096, 10000]) {
        const hits = values(item, { ...c, limit }).filter((d) => d !== null && compare(d, t) > 0);
        if (hits.length >= count) return fromEnd ? hits[hits.length - count] : hits[count - 1];
      }
      throw new Error(`no ${count}. date after the base date`);
    };
  }

  // sorted and without duplicates
  function distinct(dates) {
    return [...new Map(dates.map((d) => [`${d.year}-${d.month}-${d.day}`, d])).values()].sort(compare);
  }

  // A & B (include), A \\ B: the dates of A with or without those of the operand (a date, a series or a set of
  // dates), sorted and without duplicates; one value at a time, so the shifts after it apply to each
  function combined(left, include, operand) {
    return (c) => take(c, (c) => {
      const key = (d) => `${d.year}-${d.month}-${d.day}`;
      const leftDates = values(left, c).filter((d) => d !== null);
      const right = operand.flatMap((expr) => values(expr, c)).filter((d) => d !== null);
      if (include) return distinct([...leftDates, ...right]);
      const removed = new Set(right.map(key));
      return distinct(leftDates.filter((d) => !removed.has(key(d))));
    });
  }

  // > {1 FEB, 1 MAY}: the dates of the set in the year of the date, sorted; one value at a time
  function datesInYear(annuals) {
    return (d, c) => take(c, () => distinct(annuals.map((annual) => annual(d.year))));
  }

  // the dates of the event in the year: its rule evaluated with the first of January as base date
  function occurrences(name, year, c) {
    if (!c.events[name]) throw new Error(`no event ${name}`);
    return c.events[name]({ base: { year, month: 1, day: 1 }, market: c.market, events: c.events, fixing: c.fixing })
      .filter((d) => d.year === year).sort(compare);
  }

  // the dates of an expression: one, or one per combination of the values of its nodes with several values (an event's
  // occurrences in the base year, a series' elements, ...), bound one after the other
  function values(expr, c, bindings = []) {
    try {
      return [expr({ ...c, bindings, cursor: [0] })];
    } catch (e) {
      if (!(e instanceof Unbound)) throw e;
      return e.dates(c).flatMap((d) => values(expr, c, [...bindings, d]));
    }
  }

  // the date of an expression that must have exactly one, such as the end of a range
  function single(expr, c) {
    const dates = values(expr, c);
    if (dates.length !== 1) throw new Error(`${dates.length} dates where one is needed`);
    return dates[0];
  }

  // T: the base date of the evaluation
  function base(context) {
    if (!context.base) throw new Error('no base date');
    return context.base;
  }

  function plus(d, period, sign, market, fixing = WEEKENDS) {
    if (period.fixing) return plusBusinessDays(d, sign * period.fixing, fixing);
    if (period.business) return plusBusinessDays(d, sign * period.business, market);
    const total = d.year * 12 + (d.month - 1) + sign * period.months;
    const year = Math.floor(total / 12);
    const month = total - year * 12 + 1;
    const clamped = new Date(Date.UTC(year, month - 1, Math.min(d.day, daysInMonth(year, month))));
    clamped.setUTCDate(clamped.getUTCDate() + sign * period.days);
    return { year: clamped.getUTCFullYear(), month: clamped.getUTCMonth() + 1, day: clamped.getUTCDate() };
  }

  // the third Wednesday of March, June, September or December strictly after d
  function nextImm(d) {
    for (let { year, month } = d; ; month === 12 ? (year++, month = 1) : month++) {
      if (month % 3 !== 0) continue;
      const firstDay = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
      const imm = { year, month, day: 1 + (3 - firstDay + 7) % 7 + 14 };
      if (compare(imm, d) > 0) return imm;
    }
  }

  // count business days of the market later (earlier if negative)
  function plusBusinessDays(d, count, market) {
    const t = new Date(Date.UTC(d.year, d.month - 1, d.day));
    for (let days = 0; days !== count; ) {
      t.setUTCDate(t.getUTCDate() + Math.sign(count));
      const next = { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate() };
      if (market.isBusinessDay(next)) days += Math.sign(count);
    }
    return { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate() };
  }

  // FX spot: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2
  function spot(d, pair, market) {
    return plusBusinessDays(d, spotLag(pair), market);
  }

  function spotLag(pair) {
    return /^(USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD)$/.test(pair) ? 1 : 2;
  }

  // > FIX [PAIR]: the fixing date of a value date, the spot lag of the pair before it in fixing days
  function fix(d, pair, fixing) {
    return plusBusinessDays(d, -spotLag(pair), fixing);
  }

  // the IMM date strictly before d
  function previousImm(d) {
    for (let { year, month } = d; ; month === 1 ? (year--, month = 12) : month--) {
      if (month % 3 !== 0) continue;
      const firstDay = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
      const imm = { year, month, day: 1 + (3 - firstDay + 7) % 7 + 14 };
      if (compare(imm, d) < 0) return imm;
    }
  }

  const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
  const WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  // 0 = Monday, as in WEEKDAYS
  function weekday(d) {
    return (new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay() + 6) % 7;
  }

  function plusDays(d, days) {
    return plus(d, { months: 0, days, business: 0 }, 1, WEEKENDS);
  }

  // annual dates: functions of the year
  // Solar: the Chinese calendar of lunar-javascript, passed as parse option (a generated parser can't import it)
  function monthDay(day, month, Solar) {
    if (!MONTHS.includes(month)) return (year) => lunar(day, month, year, Solar);
    return (year) => date(year, MONTHS.indexOf(month) + 1, day);
  }

  const HIJRI = ['MUHARRAM', 'SAFAR', 'RABIALAWWAL', 'RABIALTHANI', 'JUMADAALULA', 'JUMADAALAKHIRA', 'RAJAB', 'SHABAN',
    'RAMADAN', 'SHAWWAL', 'DHULQADAH', 'DHULHIJJAH'];
  const HEBREW = ['NISAN', 'IYAR', 'SIVAN', 'TAMMUZ', 'AV', 'ELUL', 'TISHRI', 'HESHVAN', 'KISLEV', 'TEVET', 'SHEVAT', 'ADAR', 'ADARII'];
  const CHINESE = ['ZHENGYUE', 'ERYUE', 'SANYUE', 'SIYUE', 'WUYUE', 'LIUYUE', 'QIYUE', 'BAYUE', 'JIUYUE', 'SHIYUE', 'DONGYUE', 'LAYUE'];
  // Intl's names of the Hebrew months; Adar I (in a leap year) counts as ADAR
  const INTL_HEBREW = { Tishri: 'TISHRI', Heshvan: 'HESHVAN', Kislev: 'KISLEV', Tevet: 'TEVET', Shevat: 'SHEVAT', 'Adar I': 'ADAR',
    Adar: 'ADAR', 'Adar II': 'ADARII', Nisan: 'NISAN', Iyar: 'IYAR', Sivan: 'SIVAN', Tamuz: 'TAMMUZ', Av: 'AV', Elul: 'ELUL' };
  const formats = {};

  // the month and day of a date in the calendar of the given month: Hijri (Umm al-Qura) and Hebrew from Intl,
  // Chinese from lunar-javascript (Intl's astronomical Chinese calendar misses some new moons close to midnight)
  function lunarMonthDay(d, like, Solar) {
    if (CHINESE.includes(like)) {
      if (!Solar) throw new Error('Chinese months need the parse option Solar (lunar-javascript)');
      const lunar = Solar.fromYmd(d.year, d.month, d.day).getLunar();
      return { month: CHINESE[lunar.getMonth() - 1], day: lunar.getDay() };   // a leap month is negative: no name
    }
    const calendar = HIJRI.includes(like) ? 'islamic-umalqura' : 'hebrew';
    formats[calendar] ??= new Intl.DateTimeFormat(`en-u-ca-${calendar}`,
      { timeZone: 'UTC', month: calendar === 'hebrew' ? 'long' : 'numeric', day: 'numeric' });
    const parts = Object.fromEntries(formats[calendar].formatToParts(new Date(Date.UTC(d.year, d.month - 1, d.day)))
      .map((part) => [part.type, part.value]));
    const month = calendar === 'hebrew' ? INTL_HEBREW[parts.month] : HIJRI[Number(parts.month) - 1];
    return { month, day: Number(parts.day) };
  }

  // the day of a lunar month in the Gregorian year; an error if it occurs twice or not at all
  function lunar(day, month, year, Solar) {
    const hits = [];
    for (let d = date(year, 1, 1); d.year === year; d = plusDays(d, 1)) {
      const md = lunarMonthDay(d, month, Solar);
      if (md.month === month && md.day === day) hits.push(d);
    }
    if (hits.length !== 1) throw new RangeError(`${day} ${month} occurs ${hits.length} times in ${year}`);
    return hits[0];
  }

  function nthWeekday(n, wd, month) {
    return (year) => {
      const first = date(year, MONTHS.indexOf(month) + 1, 1);
      const d = plusDays(first, (WEEKDAYS.indexOf(wd) - weekday(first) + 7) % 7 + 7 * (n - 1));
      if (n < 1 || d.month !== first.month) throw new RangeError(`no ${n} ${wd} ${month} in ${year}`);
      return d;
    };
  }

  function lastWeekday(wd, month) {
    return (year) => {
      const m = MONTHS.indexOf(month) + 1;
      const last = date(year, m, daysInMonth(year, m));
      return plusDays(last, -((weekday(last) - WEEKDAYS.indexOf(wd) + 7) % 7));
    };
  }

  // Western Easter Sunday (anonymous Gregorian algorithm)
  function easter(year) {
    const a = year % 19, b = Math.floor(year / 100), c = year % 100, d = Math.floor(b / 4), e = b % 4;
    const g = Math.floor((8 * b + 13) / 25), h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
    return date(year, Math.floor((h + l - 7 * m + 114) / 31), (h + l - 7 * m + 114) % 31 + 1);
  }

  // EQUINOX MAR, SOLSTICE JUN, EQUINOX SEP, SOLSTICE DEC: the sun at 0, 90, 180, 270 degrees, in UTC + offset minutes
  function season(kind, month, offset) {
    const index = ['MAR', 'JUN', 'SEP', 'DEC'].indexOf(month);
    return (year) => {
      if (index < 0 || (index % 2 === 0) !== (kind === 'EQUINOX')) throw new RangeError(`no ${kind} in ${month}`);
      return sunAt(90 * index, year, offset);
    };
  }

  // SOLARTERM 1 to 24, counted from Lichun (315 degrees); SOLARTERM 5 is Qingming (15 degrees)
  function solarTerm(term, offset) {
    return (year) => {
      if (term < 1 || term > 24) throw new RangeError(`no solar term ${term}`);
      return sunAt((term + 20) % 24 * 15, year, offset);
    };
  }

  // the terms of VSOP87D (Meeus, Astronomical Algorithms, appendix III) with A >= 100: A (1e-8 rad or AU), B, C
  const EARTH_L = [
    [[175347046, 0, 0], [3341656, 4.6692568, 6283.07585], [34894, 4.6261024, 12566.1517], [3418, 2.8288658, 3.5231183],
      [3497, 2.744118, 5753.3848849], [3136, 3.6276704, 77713.7714681], [2676, 4.4180835, 7860.4193924],
      [2343, 6.1351624, 3930.2096962], [1273, 2.0370966, 529.6909651], [1324, 0.7424636, 11506.7697698],
      [902, 2.0450544, 26.2983198], [1199, 1.1096294, 1577.3435424], [857, 3.5084916, 398.1490034],
      [780, 1.1788265, 5223.6939198], [990, 5.2326813, 5884.9268466], [753, 2.5333905, 5507.5532387],
      [505, 4.5829256, 18849.22755], [492, 4.2050664, 775.5226113], [357, 2.9195412, 0.0673103],
      [284, 1.8986903, 796.2980068], [243, 0.3448114, 5486.7778432], [317, 5.8490195, 11790.6290887],
      [271, 0.3148861, 10977.0788047], [206, 4.8064661, 2544.3144199], [205, 1.8694781, 5573.1428014],
      [202, 2.457678, 6069.7767546], [126, 1.0830263, 20.7753955], [156, 0.8330607, 213.2990954],
      [115, 0.6454491, 0.9803211], [103, 0.6359985, 4694.0029547], [102, 4.2667982, 7.113547],
      [132, 3.4111828, 2942.4634233], [102, 0.9756922, 15720.8387849]],
    [[628331966747, 0, 0], [206059, 2.6782346, 6283.07585], [4303, 2.6351265, 12566.1517], [425, 1.5904698, 3.5231183],
      [109, 2.96618, 1577.3435424], [119, 5.7955749, 26.2983198]],
    [[52919, 0, 0], [8720, 1.0720967, 6283.07585], [309, 0.8672882, 12566.1517]],
    [[289, 5.843842, 6283.07585]],
    [[114, 3.1415927, 0]]];
  const EARTH_R = [
    [[100013989, 0, 0], [1670700, 3.0984635, 6283.07585], [13956, 3.0552461, 12566.1517]],
    [[103019, 1.1074897, 6283.07585]]];

  function vsop(series, tau) {
    let sum = 0;
    for (let n = series.length - 1; n >= 0; n--) {
      sum = sum * tau + series[n].reduce((total, [a, b, c]) => total + a * Math.cos(b + c * tau), 0);
    }
    return sum * 1e-8;
  }

  const radians = (degrees) => degrees * Math.PI / 180;

  // the apparent longitude of the sun in degrees (Meeus, chapter 25, with VSOP87, nutation and aberration)
  function sunLongitude(jde) {
    const tau = (jde - 2451545) / 365250, t = 10 * tau;
    const omega = radians(125.04452 - 1934.136261 * t), sun = radians(280.4665 + 36000.7698 * t);
    const moon = radians(218.3165 + 481267.8813 * t);
    const nutation = -17.20 * Math.sin(omega) - 1.32 * Math.sin(2 * sun) - 0.23 * Math.sin(2 * moon) + 0.21 * Math.sin(2 * omega);
    return vsop(EARTH_L, tau) * 180 / Math.PI + 180 + (nutation - 0.09033 - 20.4898 / vsop(EARTH_R, tau)) / 3600;
  }

  // TD - UT in seconds: Espenak and Meeus' polynomials for 1986 to 2150, their long-term formula outside
  function deltaT(year) {
    const t = year - 2000, u = (year - 1820) / 100, longTerm = -20 + 32 * u * u;
    if (year >= 1986 && year < 2005) {
      return 63.86 + 0.3345 * t - 0.060374 * t ** 2 + 0.0017275 * t ** 3 + 0.000651814 * t ** 4 + 0.00002373599 * t ** 5;
    }
    if (year >= 2005 && year < 2050) return 62.92 + 0.32217 * t + 0.005589 * t * t;
    return year >= 2050 && year < 2150 ? longTerm - 0.5628 * (2150 - year) : longTerm;
  }

  // the day in the year on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes
  function sunAt(degrees, year, offset) {
    let day = 79 + degrees * 365.2422 / 360;  // days after 1 January, from the March equinox on
    if (day > 365.2422) day -= 365.2422;
    let jde = Date.UTC(year, 0, 1) / 86400000 + 2440587.5 + day;
    for (let i = 0; i < 50; i++) {
      const step = 58 * Math.sin(radians(degrees - sunLongitude(jde)));
      jde += step;
      if (Math.abs(step) < 1e-7) break;
    }
    const local = new Date(Math.floor(jde - deltaT(year + day / 365.25) / 86400 + offset / 1440 - 2440587.5) * 86400000);
    return date(local.getUTCFullYear(), local.getUTCMonth() + 1, local.getUTCDate());
  }

  // anchors of > NEXT and > PREV: the first match strictly after (direction 1) or before (-1) d
  function annualAnchor(annual) {
    return (d, direction) => {
      const match = annual(d.year);
      return Math.sign(compare(match, d)) === direction ? match : annual(d.year + direction);
    };
  }

  function eventAnchor(name) {
    return (d, direction, c) => {
      for (const year of [d.year, d.year + direction]) {
        const found = occurrences(name, year, c).filter((o) => Math.sign(compare(o, d)) === direction);
        if (found.length) return direction > 0 ? found[0] : found[found.length - 1];
      }
      throw new Error(`no ${name} ${direction > 0 ? 'after' : 'before'} the date`);
    };
  }

  function weekdayAnchor(wd) {
    return (d, direction) => plusDays(d, direction * ((direction * (WEEKDAYS.indexOf(wd) - weekday(d)) % 7 + 7) % 7 || 7));
  }

  // NEXT {MON, FRI}, NEXT {1 FEB, 1 MAY}: the nearest match of any of the anchors
  function anyOf(anchors) {
    return (d, direction, c) => anchors.map((anchor) => anchor(d, direction, c))
      .reduce((a, b) => (direction * compare(b, a) < 0 ? b : a));
  }

  // 1ST, 2ND, 3RD, 4TH, 11TH, 21ST as a count; the suffix must fit the number
  function ordinal(text) {
    const n = parseInt(text.slice(0, -2), 10);
    const suffix = n % 100 >= 11 && n % 100 <= 13 ? 'TH' : ({ 1: 'ST', 2: 'ND', 3: 'RD' })[n % 10] ?? 'TH';
    if (!text.endsWith(suffix)) throw new Error(`not an ordinal: ${text}`);
    return n;
  }

  // NEXT 3RD FRI: the anchor found n times, each time from the one before
  function times(n, anchor) {
    return (d, direction, c) => {
      if (n < 1) throw new Error(`count must be positive: ${n}`);
      for (let i = 0; i < n; i++) d = anchor(d, direction, c);
      return d;
    };
  }

  // > 3RD WED IN MONTH: the anchor found n times from the day before the period of d starts; > 2ND LAST BD IN MONTH
  // (fromEnd): backwards from the day after it ends; an error if that leaves the period
  function select(d, n, fromEnd, anchor, period, c) {
    if (n < 1) throw new Error(`count must be positive: ${n}`);
    const unit = period === 'HALFYEAR' ? 'H' : period[0];
    const first = roll(d, 'BO' + unit, c.market), last = roll(d, 'EO' + unit, c.market);
    let found = fromEnd ? plusDays(last, 1) : plusDays(first, -1);
    for (let i = 0; i < n; i++) found = anchor(found, fromEnd ? -1 : 1, c);
    if (compare(found, first) < 0 || compare(found, last) > 0) throw new Error(`no ${n}. match in the ${period.toLowerCase()}`);
    return found;
  }

  // FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month); BOW, EOW (Monday to Sunday), BOM, EOM,
  // BOQ, EOQ, BOH, EOH, BOY, EOY
  function roll(d, convention, market) {
    switch (convention) {
      case 'FOLLOWING': return market.isBusinessDay(d) ? d : plusBusinessDays(d, 1, market);
      case 'PRECEDING': return market.isBusinessDay(d) ? d : plusBusinessDays(d, -1, market);
      case 'MODFOLLOWING': {
        const following = roll(d, 'FOLLOWING', market);
        return following.month === d.month ? following : roll(d, 'PRECEDING', market);
      }
      case 'BOW': return plusDays(d, -weekday(d));
      case 'EOW': return plusDays(d, 6 - weekday(d));
      case 'BOM': return { ...d, day: 1 };
      case 'EOM': return { ...d, day: daysInMonth(d.year, d.month) };
      case 'BOQ': return { year: d.year, month: d.month - (d.month - 1) % 3, day: 1 };
      case 'EOQ': return roll({ ...d, month: d.month - (d.month - 1) % 3 + 2, day: 1 }, 'EOM', market);
      case 'BOH': return { year: d.year, month: d.month - (d.month - 1) % 6, day: 1 };
      case 'EOH': return roll({ ...d, month: d.month - (d.month - 1) % 6 + 5, day: 1 }, 'EOM', market);
      case 'BOY': return { year: d.year, month: 1, day: 1 };
      case 'EOY': return { year: d.year, month: 12, day: 31 };
    }
  }

  // the first shift for the weekday of d applies; shifts don't chain
  function observe(d, shifts, market, fixing) {
    const shift = shifts.find((s) => WEEKDAYS.indexOf(s.weekday) === weekday(d));
    return shift ? plus(d, shift.period, shift.sign, market, fixing) : d;
  }

  function compare(a, b) {
    return a.year - b.year || a.month - b.month || a.day - b.day;
  }

  // not "range": Peggy provides range() (the location of the match) in actions
  function expand(first, step, last, market, fixing = WEEKENDS) {
    const dates = [];
    for (let k = 0; ; k++) {
      const d = plus(first, { months: k * step.months, days: k * step.days, business: k * step.business, fixing: k * (step.fixing ?? 0) }, 1, market, fixing);
      if (compare(d, last) > 0) return dates;
      dates.push(d);
    }
  }
}}

Schedule
  = _ head:Item tail:(_ "," _ @Item)* _ { return rule((c) => [head, ...tail].flatMap((item) => values(item, c)).filter((d) => d !== null)); }

// 24 DEC IF MON TUE WED THU: the date only on these weekdays
// 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays
Item
  = PickedItem
  / expr:Combined _ "IF" days:Weekdays {
      return (c) => { const d = expr(c); return days.includes(WEEKDAYS[weekday(d)]) ? d : null; };
    }
  / Combined

// 5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH: the 5th date after T of the item, 2ND LAST from the last one; the item
// starts with a series or an event, where several dates come from
PickedItem
  = count:$([0-9]+ ("ST" / "ND" / "RD" / "TH")) last:(_ "LAST")? _ start:(Series / name:Event { return event(name); })
    shifts:(_ @Shift)* combines:(_ @Combine)* days:(_ "IF" @Weekdays)? {
      const first = (c) => shifts.reduce((d, shift) => shift(d, c), start(c));
      const expr = combines.reduce((left, combine) => combine(left), first);
      const item = days ? (c) => { const d = expr(c); return days.includes(WEEKDAYS[weekday(d)]) ? d : null; } : expr;
      return pick(ordinal(count), last !== null, item);
    }

// A & B includes the dates of B, A \\ B excludes them; the shifts after it apply to the result
Combined
  = first:Expr combines:(_ @Combine)* { return combines.reduce((left, combine) => combine(left), first); }

Combine
  = op:("&" / "\\") _ operand:Operand shifts:(_ @Shift)* {
      return (left) => {
        const node = combined(left, op === '&', operand);
        return (c) => shifts.reduce((d, shift) => shift(d, c), node(c));
      };
    }

// a date, a series or a set of dates
Operand
  = "{" _ head:Expr tail:(_ "," _ @Expr)* _ "}" { return [head, ...tail]; }
  / date:Date { return [date]; }

Weekdays
  = _ "{" _ head:Weekday tail:(_ "," _ @Weekday)* _ "}" { return [head, ...tail]; }
  / (_ @Weekday)+

// [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out. A series is
// a date with several values, like an event: the shifts after it apply to each element
Series
  = open:("[" / "]") _ first:Endpoint _ ":" _
    rest:(step:Period _ ":" _ last:Endpoint? { return [step, last]; } / last:Endpoint { return [null, last]; })
    _ close:("]" / "[") {
      const step = rest[0] ?? { months: 0, days: 1, business: 0 }, last = rest[1];
      if (step.months <= 0 && step.days <= 0 && step.business <= 0 && !(step.fixing > 0)) throw new RangeError('step must be positive');
      if (last === null && close === '[') throw new RangeError('an open series ends with ]');
      return series(first, step, last, open === ']', close === '[');
    }

// an endpoint is one date, so it can't be a series: after the last ":" a "]" closes an open series [SPOT : 1Q : ]
Endpoint
  = start:SingleDate shifts:(_ @Shift)* { return (c) => shifts.reduce((d, shift) => shift(d, c), start(c)); }

// periods are applied from left to right
Expr
  = start:Date shifts:(_ @Shift)* { return (c) => shifts.reduce((d, shift) => shift(d, c), start(c)); }

Shift
  = "+" _ p:Period { return (d, c) => plus(d, p, 1, c.market, c.fixing); }
  / "-" _ p:Period { return (d, c) => plus(d, p, -1, c.market, c.fixing); }
  / ">" _ "IMM" { return nextImm; }
  / ">" _ "SPOT" pair:(_ @Pair)? { return (d, c) => spot(d, pair, c.market); }
  / ">" _ "FIX" pair:(_ @Pair)? { return (d, c) => fix(d, pair, c.fixing); }  // the fixing date of a value date
  / ">" _ convention:$("FOLLOWING" / "PRECEDING" / "MODFOLLOWING" / "BOW" / "EOW" / "BOM" / "EOM" / "BOQ" / "EOQ" / "BOH" / "EOH" / "BOY" / "EOY") {
      return (d, c) => roll(d, convention, c.market);
    }
  / ">" _ "NEXT" _ anchor:Counted { return (d, c) => anchor(d, 1, c); }
  / ">" _ "PREV" _ anchor:Counted { return (d, c) => anchor(d, -1, c); }
  / ">" _ "{" _ head:Annual tail:(_ "," _ @Annual)* _ "}" { return datesInYear([head, ...tail]); }  // > {1 FEB, 1 MAY}
  / ">" _ count:Count _ anchor:Select _ "IN" _ period:$("WEEK" / "MONTH" / "QUARTER" / "HALFYEAR" / "YEAR") {
      return (d, c) => select(d, count.n, count.fromEnd, anchor, period, c);
    }
  / "{" _ head:Observe tail:(_ "," _ @Observe)* _ "}" {
      return (d, c) => observe(d, [head, ...tail], c.market, c.fixing);
    }

Observe
  = weekday:Weekday _ sign:("+" / "-") _ period:Period { return { weekday, sign: sign === '+' ? 1 : -1, period }; }

// NEXT 3RD FRI: the third Friday; the ordinal first, as the number of 3 FRI JAN would take its digits
Counted
  = text:$([0-9]+ ("ST" / "ND" / "RD" / "TH")) _ anchor:Anchor { return times(ordinal(text), anchor); }
  / Anchor

// NEXT {MON, FRI}: the nearest of a set
Anchor
  = "{" _ head:Target tail:(_ "," _ @Target)* _ "}" { return anyOf([head, ...tail]); }
  / Target

// > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH: the n-th match in the period of the date, from its start or its end
Count
  = text:$([0-9]+ ("ST" / "ND" / "RD" / "TH")) last:(_ "LAST")? { return { n: ordinal(text), fromEnd: last !== null }; }
  / "LAST" { return { n: 1, fromEnd: true }; }

// the targets of NEXT; an annual date starting with LAST only in braces, as LAST also starts the count
Select
  = "{" _ head:Target tail:(_ "," _ @Target)* _ "}" { return anyOf([head, ...tail]); }
  / Chosen

Target
  = Chosen
  / annual:LastWeekday { return annualAnchor(annual); }

Chosen
  = wd:Weekday { return weekdayAnchor(wd); }
  / "BD" { return (d, direction, c) => plusBusinessDays(d, direction, c.market); }
  / "FD" { return (d, direction, c) => plusBusinessDays(d, direction, c.fixing); }
  / "IMM" { return (d, direction) => direction > 0 ? nextImm(d) : previousImm(d); }
  / annual:FixedAnnual { return annualAnchor(annual); }
  / name:Event { return eventAnchor(name); }

Date
  = Series
  / SingleDate

// a date that is not a series
SingleDate
  = "TOM" { return (c) => plusBusinessDays(base(c), 1, c.market); }  // before "T": no lexer, so order matters
  / "T" { return base; }  // the base date
  / "SPOT" pair:(_ @Pair)? { return (c) => spot(base(c), pair, c.market); }
  / y:Number _ "-" _ m:Number _ "-" _ d:Number { const value = date(y, m, d); return () => value; }  // ISO
  / d:Number _ "." _ m:Number _ "." _ y:Number { const value = date(y, m, d); return () => value; }  // German
  / m:Number _ "/" _ d:Number _ "/" _ y:Number { const value = date(y, m, d); return () => value; }  // US
  / annual:Annual { return (c) => annual(base(c).year); }
  / name:Event { return event(name); }

// dates that recur every year, evaluated in the year of the base date
Annual
  = FixedAnnual
  / LastWeekday

LastWeekday
  = "LAST" _ wd:Weekday _ month:Month { return lastWeekday(wd, month); }

FixedAnnual
  = day:Number _ month:Month { return monthDay(day, month, options.Solar); }
  / n:Number _ wd:Weekday _ month:Month { return nthWeekday(n, wd, month); }
  / "EASTER" { return easter; }
  / kind:$("EQUINOX" / "SOLSTICE") _ month:Month offset:(_ @Zone)? { return season(kind, month, offset ?? 0); }
  / "SOLARTERM" _ n:Number offset:(_ @Zone)? { return solarTerm(n, offset ?? 0); }

// the time zone of an equinox, solstice or solar term in minutes: UTC unless an offset follows
Zone
  = "UTC" hours:$([+-] [0-9]+) { return parseInt(hours, 10) * 60; }

// Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months; ordered choice, so ADARII before ADAR
Month
  = $("JAN" / "FEB" / "MAR" / "APR" / "MAY" / "JUN" / "JUL" / "AUG" / "SEP" / "OCT" / "NOV" / "DEC"
    / "MUHARRAM" / "SAFAR" / "RABIALAWWAL" / "RABIALTHANI" / "JUMADAALULA" / "JUMADAALAKHIRA" / "RAJAB" / "SHABAN"
    / "RAMADAN" / "SHAWWAL" / "DHULQADAH" / "DHULHIJJAH"
    / "NISAN" / "IYAR" / "SIVAN" / "TAMMUZ" / "AV" / "ELUL" / "TISHRI" / "HESHVAN" / "KISLEV" / "TEVET" / "SHEVAT"
    / "ADARII" / "ADAR"
    / "ZHENGYUE" / "ERYUE" / "SANYUE" / "SIYUE" / "WUYUE" / "LIUYUE" / "QIYUE" / "BAYUE" / "JIUYUE" / "SHIYUE"
    / "DONGYUE" / "LAYUE")

// a named event such as @FOMC; the sigil keeps names apart from keywords and pairs
Event
  = "@" name:$([A-Z] [A-Z0-9_]*) { return name; }

Weekday
  = $("MON" / "TUE" / "WED" / "THU" / "FRI" / "SAT" / "SUN")

// currency pair such as EURUSD; without a lexer nothing reserves the six-letter keywords, so they're excluded here
Pair
  = !("EASTER" / "SHABAN" / "TAMMUZ" / "TISHRI" / "KISLEV" / "SHEVAT" / "ADARII" / "SANYUE" / "LIUYUE" / "JIUYUE" / "SHIYUE")
    pair:$[A-Z]|6| { return pair; }

Period
  = n:Number _ unit:[DWMQHYBF] {
      switch (unit) {
        case 'D': return { months: 0, days: n, business: 0 };
        case 'W': return { months: 0, days: 7 * n, business: 0 };
        case 'M': return { months: n, days: 0, business: 0 };
        case 'Q': return { months: 3 * n, days: 0, business: 0 };
        case 'H': return { months: 6 * n, days: 0, business: 0 };
        case 'Y': return { months: 12 * n, days: 0, business: 0 };
        case 'B': return { months: 0, days: 0, business: n };
        case 'F': return { months: 0, days: 0, business: 0, fixing: n };
      }
    }

Number "number"
  = digits:$[0-9]+ { return parseInt(digits, 10); }

_ "whitespace"
  = [ \t\r\n]*
