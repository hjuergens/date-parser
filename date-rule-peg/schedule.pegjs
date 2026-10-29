/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * Dates are {year, month, day} objects, periods {months, days, business}; the arithmetic
 * follows java.time: months first (day clamped to the end of the month), then days.
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

  function plus(d, period, sign = 1) {
    if (period.business) return plusBusinessDays(d, sign * period.business);
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

  // count business days later (earlier if negative); only Saturdays and Sundays are skipped
  function plusBusinessDays(d, count) {
    const t = new Date(Date.UTC(d.year, d.month - 1, d.day));
    for (let days = 0; days !== count; ) {
      t.setUTCDate(t.getUTCDate() + Math.sign(count));
      if (t.getUTCDay() % 6 !== 0) days += Math.sign(count);
    }
    return { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate() };
  }

  // FX spot: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2
  function spot(d, pair) {
    return plusBusinessDays(d, /^(USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD)$/.test(pair) ? 1 : 2);
  }

  function compare(a, b) {
    return a.year - b.year || a.month - b.month || a.day - b.day;
  }

  // not "range": Peggy provides range() (the location of the match) in actions
  function expand(first, step, last) {
    if (step.months <= 0 && step.days <= 0 && step.business <= 0) {
      throw new RangeError('step must be positive');
    }
    const dates = [];
    for (let k = 0; ; k++) {
      const d = plus(first, { months: k * step.months, days: k * step.days, business: k * step.business });
      if (compare(d, last) > 0) return dates;
      dates.push(d);
    }
  }
}}

Schedule
  = _ r:Range _ { return r; }
  / _ head:Expr tail:(_ "," _ @Expr)* _ { return [head, ...tail]; }

Range
  = "(" _ first:Expr _ ":" _ step:Period _ ":" _ last:Expr _ ")" { return expand(first, step, last); }

// periods are applied from left to right
Expr
  = start:Date shifts:(_ @Shift)* { return shifts.reduce((d, shift) => shift(d), start); }

Shift
  = "+" _ p:Period { return d => plus(d, p); }
  / "-" _ p:Period { return d => plus(d, p, -1); }
  / ">" _ "IMM" { return nextImm; }
  / ">" _ "SPOT" pair:(_ @$[A-Z]|6|)? { return d => spot(d, pair); }

Date
  = y:Number _ "-" _ m:Number _ "-" _ d:Number { return date(y, m, d); }  // ISO
  / d:Number _ "." _ m:Number _ "." _ y:Number { return date(y, m, d); }  // German
  / m:Number _ "/" _ d:Number _ "/" _ y:Number { return date(y, m, d); }  // US

Period
  = n:Number _ unit:[DWMQYB] {
      switch (unit) {
        case 'D': return { months: 0, days: n, business: 0 };
        case 'W': return { months: 0, days: 7 * n, business: 0 };
        case 'M': return { months: n, days: 0, business: 0 };
        case 'Q': return { months: 3 * n, days: 0, business: 0 };
        case 'Y': return { months: 12 * n, days: 0, business: 0 };
        case 'B': return { months: 0, days: 0, business: n };
      }
    }

Number "number"
  = digits:$[0-9]+ { return parseInt(digits, 10); }

_ "whitespace"
  = [ \t\r\n]*
