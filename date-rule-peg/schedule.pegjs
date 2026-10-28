/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * Dates are {year, month, day} objects, periods {months, days}; the arithmetic
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
    const total = d.year * 12 + (d.month - 1) + sign * period.months;
    const year = Math.floor(total / 12);
    const month = total - year * 12 + 1;
    const clamped = new Date(Date.UTC(year, month - 1, Math.min(d.day, daysInMonth(year, month))));
    clamped.setUTCDate(clamped.getUTCDate() + sign * period.days);
    return { year: clamped.getUTCFullYear(), month: clamped.getUTCMonth() + 1, day: clamped.getUTCDate() };
  }

  function compare(a, b) {
    return a.year - b.year || a.month - b.month || a.day - b.day;
  }

  // not "range": Peggy provides range() (the location of the match) in actions
  function expand(first, step, last) {
    if (step.months <= 0 && step.days <= 0) {
      throw new RangeError('step must be positive');
    }
    const dates = [];
    for (let k = 0; ; k++) {
      const d = plus(first, { months: k * step.months, days: k * step.days });
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
  = start:Date shifts:(_ @[+-] _ @Period)* {
      return shifts.reduce((d, [sign, p]) => plus(d, p, sign === '+' ? 1 : -1), start);
    }

Date
  = y:Number _ "-" _ m:Number _ "-" _ d:Number { return date(y, m, d); }  // ISO
  / d:Number _ "." _ m:Number _ "." _ y:Number { return date(y, m, d); }  // German
  / m:Number _ "/" _ d:Number _ "/" _ y:Number { return date(y, m, d); }  // US

Period
  = n:Number _ unit:[DWMQY] {
      switch (unit) {
        case 'D': return { months: 0, days: n };
        case 'W': return { months: 0, days: 7 * n };
        case 'M': return { months: n, days: 0 };
        case 'Q': return { months: 3 * n, days: 0 };
        case 'Y': return { months: 12 * n, days: 0 };
      }
    }

Number "number"
  = digits:$[0-9]+ { return parseInt(digits, 10); }

_ "whitespace"
  = [ \t\r\n]*
