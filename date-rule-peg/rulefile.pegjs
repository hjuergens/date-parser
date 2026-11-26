/*
 * Rule files: the schedule language of schedule.pegjs inside declarations with scope, validity, kind and provenance,
 * the same language as date-rule-antlr-java's RuleFile.g4 and date-rule-lark's rules.lark (shared cases:
 * examples/rules.txt). Generated together with schedule.pegjs (peggy.generate([schedule, rulefile])), so this grammar
 * uses the helpers of its initializer (plus, roll, take, values, ...) and some of its rules (Number, Period, Month,
 * Weekday, Event). The rules here carry the lowercase names of RuleFile.g4; Peggy's names are case-sensitive, so they
 * don't clash with Schedule, Item, Shift, ... of schedule.pegjs.
 *
 * parse(text, { startRule: 'ruleFile', Solar, announcements }) returns a RuleCatalog, the JavaScript port of catalog.py
 * (date-rule-lark): catalog.holidays(scope, year), catalog.schedule(text)(catalog.context(base, scope)). Announcements
 * map a key to the dates an authority published ({ year, month, day }); rules only project.
 */

{{
  // ================= markets that answer more than business days =================

  const CLOSED = new Set(['closed', 'non_working']);   // kinds that close a day; working_day opens one
  const MAX_PASSES = 8;
  const ESTIMATED = /\bBY\s+(SIGHTING|DIYANET|FOLLOW)\b/;
  const REFERENCE = /@[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*/g;

  const isoOf = (d) => `${String(d.year).padStart(4, '0')}-${String(d.month).padStart(2, '0')}-${String(d.day).padStart(2, '0')}`;
  const dayNumber = (d) => d.year * 10000 + d.month * 100 + d.day;

  // a holiday of the market: closed although not a weekend day
  function isHoliday(market, d) {
    if (market.isHoliday) return market.isHoliday(d);
    return weekday(d) < 5 && !market.isBusinessDay(d);
  }

  // every day a business day: the calendar for the nominal dates, which give the order of the passes
  const OPEN = { isBusinessDay: () => true, isHoliday: () => false };

  // the weekend regime of a scope and the dates of its rules, from the evaluations of the year of a date and of the
  // years before and after. During the evaluation of a year (home) skip leaves out the rule being evaluated, the other
  // years are read shallow, and a shallow calendar reads its home year only, so no evaluation starts a chain of years
  class ScopeCalendar {
    constructor(catalog, scope, skip = null, home = null, shallow = false) {
      Object.assign(this, { catalog, scope, skip, home, shallow });
    }

    days(d) {
      let closed = false, opened = false;
      const key = isoOf(d);
      for (const year of [d.year - 1, d.year, d.year + 1]) {
        if (this.shallow && year !== this.home) continue;
        for (const [rule, result] of this.catalog.state(this.scope, year, this.home !== null && year !== this.home)) {
          if (this.skip && this.skip.rule === rule && this.skip.year === year || !result.keys.has(key)) continue;
          if (CLOSED.has(rule.kind)) closed = true;
          else if (rule.kind === 'working_day') opened = true;
        }
      }
      return { closed, opened };
    }

    isHoliday(d) {
      return this.days(d).closed;
    }

    isBusinessDay(d) {
      const { closed, opened } = this.days(d);
      if (opened) return true;
      return !this.catalog.weekend(this.scope, d).has(WEEKDAYS[weekday(d)]) && !closed;
    }
  }

  // A & B: a business day in every term; a term with ignore does not count on the ignore-th business day after T of the
  // other terms, unless the currency pair of the evaluation contains one of its currencies
  class CombinedCalendar {
    constructor(terms, t, pair) {   // terms: { market, ignore } with ignore null or { days, currencies }
      this.terms = terms;
      this.ignored = new Set();
      terms.forEach(({ ignore }, i) => {
        if (ignore && t && !(pair && ignore.currencies.some((ccy) => pair.includes(ccy)))) {
          const others = terms.filter((_, j) => j !== i).map((term) => term.market);
          let day = t;
          for (let k = 0; k < ignore.days; k++) day = nextDay(day, (x) => others.every((m) => m.isBusinessDay(x)));
          this.ignored.add(`${i} ${isoOf(day)}`);
        }
      });
    }

    isBusinessDay(d) {
      return this.terms.every(({ market }, i) => this.ignored.has(`${i} ${isoOf(d)}`) || market.isBusinessDay(d));
    }

    isHoliday(d) {
      return this.terms.some(({ market }) => isHoliday(market, d));
    }
  }

  // the first day strictly after (before) d that passes the test
  function nextDay(d, test, direction = 1, limit = 3700) {
    for (let i = 0; i < limit; i++) {
      d = plusDays(d, direction);
      if (test(d)) return d;
    }
    throw new Error(`no such day within ${limit} days`);
  }

  // ================= the context of a rule: catalog and scope ride along in the context object =================

  // the calendar of ON ...; without ON the market of the context (in a rule: its own scope)
  function market(on, c, pair = null) {
    if (!on) return c.market;
    if (!c.catalog) throw new Error('ON needs a rule catalog');
    return c.catalog.resolve(on, c, pair ?? c.pair ?? null);
  }

  // @key: the dates of a rule (by key), of the rules of an event class or of a key with announced dates only, in the
  // year of the base date; the events of a context, a Proxy, as occurrences() of schedule.pegjs reads events[name]
  function references(catalog, scope, home = null, shallow = false) {
    return new Proxy({}, {
      get(_, name) {
        if (typeof name !== 'string' || !catalog.referenced(name).length && !catalog.announcements.has(name)) return undefined;
        return (c) => {
          const year = base(c).year;
          if (shallow && year !== home) return [];
          return catalog.referenceDates(name, scope, year, home !== null && year !== home);
        };
      },
    });
  }

  // ================= dates of the rule language =================

  // the conventions of the schedule language, MODPRECEDING (preceding unless that leaves the month) and NEAREST (a
  // non-business Sunday or Monday follows, any other non-business day precedes)
  function rollConvention(d, convention, m) {
    if (convention === 'NEAREST') {
      if (m.isBusinessDay(d)) return d;
      return roll(d, weekday(d) === 6 || weekday(d) === 0 ? 'FOLLOWING' : 'PRECEDING', m);
    }
    if (convention === 'MODPRECEDING') {
      const preceding = roll(d, 'PRECEDING', m);
      return preceding.month === d.month ? preceding : roll(d, 'FOLLOWING', m);
    }
    return roll(d, convention, m);
  }

  // Orthodox Easter Sunday: the Julian computus (Meeus), converted to the Gregorian calendar
  function orthodoxEaster(year) {
    const a = year % 4, b = year % 7, c = year % 19;
    const d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
    const month = Math.floor((d + e + 114) / 31), day = (d + e + 114) % 31;
    const k = Math.floor((14 - month) / 12), y = year + 4800 - k, m = month + 12 * k - 3;
    const jdn = day + 1 + Math.floor((153 * m + 2) / 5) + 365 * y + Math.floor(y / 4) - 32083;
    const t = new Date((jdn - 2440588) * 86400000);
    return date(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate());
  }

  const zoneFormats = {};

  // the local day (IANA time zone) on which the sun's apparent longitude reaches the degrees: sunAt of schedule.pegjs
  // up to the instant, then the date there
  function sunInZone(degrees, year, zone) {
    let day = 79 + degrees * 365.2422 / 360;
    if (day > 365.2422) day -= 365.2422;
    let jde = Date.UTC(year, 0, 1) / 86400000 + 2440587.5 + day;
    for (let i = 0; i < 50; i++) {
      const step = 58 * Math.sin(radians(degrees - sunLongitude(jde)));
      jde += step;
      if (Math.abs(step) < 1e-7) break;
    }
    const instant = new Date(Math.round((jde - deltaT(year + day / 365.25) / 86400 - 2440587.5) * 86400000));
    zoneFormats[zone] ??= new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: 'numeric', day: 'numeric' });
    const parts = Object.fromEntries(zoneFormats[zone].formatToParts(instant).map((part) => [part.type, part.value]));
    return date(Number(parts.year), Number(parts.month), Number(parts.day));
  }

  // an astronomical annual date in UTC, a fixed offset (UTC+9) or an IANA time zone ("Asia/Tokyo")
  function zonedSeason(kind, month, zone) {
    const index = ['MAR', 'JUN', 'SEP', 'DEC'].indexOf(month);
    if (index < 0 || (index % 2 === 0) !== (kind === 'EQUINOX')) throw new RangeError(`no ${kind} in ${month}`);
    if (zone && !zone.startsWith('UTC')) {
      new Intl.DateTimeFormat('en', { timeZone: zone });   // a RangeError for an unknown zone
      return (year) => sunInZone(90 * index, year, zone);
    }
    return season(kind, month, zone ? parseInt(zone.slice(3), 10) * 60 : 0);
  }

  function zonedSolarTerm(term, zone) {
    if (term < 1 || term > 24) throw new RangeError(`no solar term ${term}`);
    if (zone && !zone.startsWith('UTC')) {
      new Intl.DateTimeFormat('en', { timeZone: zone });
      return (year) => sunInZone((term + 20) % 24 * 15, year, zone);
    }
    return solarTerm(term, zone ? parseInt(zone.slice(3), 10) * 60 : 0);
  }

  // the local date and time of day (hh:mm) of an instant (a Date) at a fixed offset (UTC+9) or in an IANA time zone
  function localAt(instant, zone) {
    if (zone.startsWith('UTC')) {
      const t = new Date(instant.getTime() + parseInt(zone.slice(3), 10) * 3600000);
      return { date: date(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()),
        time: `${String(t.getUTCHours()).padStart(2, '0')}:${String(t.getUTCMinutes()).padStart(2, '0')}` };
    }
    const format = zoneFormats[`${zone} time`] ??= new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric',
      month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    const parts = Object.fromEntries(format.formatToParts(instant).map((part) => [part.type, part.value]));
    return { date: date(Number(parts.year), Number(parts.month), Number(parts.day)),
      time: `${parts.hour}:${parts.minute}` };
  }

  // > CUTOFF 17:00 "America/New_York": the date of the base instant (c.instant) in the zone, the next one from the
  // cutoff on
  function cutoff(at, zone) {
    const [hours, minutes] = at.split(':').map(Number);
    if (hours > 23 || minutes > 59) throw new RangeError(`not a time of day: ${at}`);
    if (!zone.startsWith('UTC')) new Intl.DateTimeFormat('en', { timeZone: zone });   // a RangeError if unknown
    return (d, c) => {
      if (!c.instant) throw new Error('CUTOFF needs a base with a time of day');
      const local = localAt(c.instant, zone);
      return local.time >= at ? plusDays(local.date, 1) : local.date;
    };
  }

  // 1 SHAWWAL BY SIGHTING: the method only for Hijri months; all are computed with Umm al-Qura
  function hijriMonthDay(day, month, method, Solar) {
    if (method && !HIJRI.includes(month)) throw new RangeError(`BY ${method} only for Hijri months, not ${month}`);
    return monthDay(day, month, Solar);
  }

  function hindu(key) {
    return () => { throw new Error(`HINDU ${key}: no Hindu calendar in this implementation`); };
  }

  // an annual date in the year of the base date; a lunar date that falls twice gives both, one at a time
  function inYear(annual) {
    return annual.every ? (c) => take(c, (c) => annual.every(base(c).year)) : (c) => annual(base(c).year);
  }

  // ================= building the rules: the grammar calls these =================

  // a date expression: the start, then the shifts from left to right
  function shifted(start, shifts) {
    return (c) => applyShifts(start(c), shifts, c);
  }

  // an item with its IF clause (or null): the date if it meets the condition, then the shifts of the clause
  function ruleItem(expr, clause) {
    if (!clause) return expr;
    return (c) => {
      const d = expr(c);
      if (d === null || !clause.condition(d, c)) return null;
      return applyShifts(d, clause.shifts, c);
    };
  }

  // a list of items: all their dates, in order
  function exprsOf(items) {
    return (c) => items.flatMap((item) => values(item, c)).filter((d) => d !== null);
  }

  // conditions: functions of (date, context)
  const anyCondition = (parts) => (d, c) => parts.some((p) => p(d, c));
  const allConditions = (parts) => (d, c) => parts.every((p) => p(d, c));
  const notCondition = (part) => (d, c) => !part(d, c);
  const onWeekday = (weekdays) => (d) => weekdays.includes(WEEKDAYS[weekday(d)]);

  function onDay(first, last, month) {
    if (!(first >= 1 && first <= last && last <= 31)) throw new RangeError(`days must run from 1 to 31 upwards: ${first}..${last}`);
    if (month && !MONTHS.includes(month)) throw new RangeError(`IF DAY takes a Gregorian month, not ${month}`);
    return (d) => d.day >= first && d.day <= last && (!month || d.month === MONTHS.indexOf(month) + 1);
  }

  const isWeekday = (subject, weekdays) => (d, c) => weekdays.includes(WEEKDAYS[weekday(single(subject, c))]);
  const holidayCondition = (on) => (d, c) => isHoliday(market(on, c), d);
  const dateIsHoliday = (subject, on) => (d, c) => isHoliday(market(on, c), single(subject, c));

  function betweenHolidays(on) {
    return (d, c) => {
      const m = market(on, c);
      return isHoliday(m, plusDays(d, -1)) && isHoliday(m, plusDays(d, 1));
    };
  }

  function every(period, first) {
    if (period.days || period.business || period.fixing || period.months <= 0 || period.months % 12) {
      throw new RangeError('EVERY needs whole years: EVERY 4Y FROM 1965');
    }
    const years = period.months / 12;
    return (d) => d.year >= first && (d.year - first) % years === 0;
  }

  // shifts: functions of (date, context); a postponement carries its parts as properties
  function plusShift(period, sign, endend = false, on = null) {
    return (d, c) => {
      const m = market(on, c);
      const moved = plus(d, period, sign, m, c.fixing);
      return endend && compare(d, lastBusinessDay(d, m)) === 0 ? lastBusinessDay(moved, m) : moved;
    };
  }

  const spotShift = (pair, on) => (d, c) => spot(d, pair, market(on, c, pair));
  const rollShift = (convention, on) => (d, c) => rollConvention(d, convention, market(on, c));
  const nearestShift = (anchor) => (d, c) => nearest(anchor, d, c);

  // > POSTPONE FOLLOWING ... MAX 14D [CUMULATIVE]: rolled, but at most the period from the date (with CUMULATIVE from
  // the date before the first postponement of the expression)
  function postpone(convention, on, limit, cumulative) {
    const apply = (d, c, origin) => {
      const target = rollConvention(d, convention, market(on, c));
      if (compare(target, d) > 0) {
        const bound = plus(origin, limit, 1, c.market);
        return compare(target, bound) < 0 ? target : bound;
      }
      const bound = plus(origin, limit, -1, c.market);
      return compare(target, bound) > 0 ? target : bound;
    };
    const shift = (d, c) => apply(d, c, d);
    return Object.assign(shift, { postpone: apply, cumulative });
  }

  // {SAT - 1D, TUE WED > PREV MON}: entries [weekdays, move]; the first entry for the weekday of the date moves it
  function observance(entries) {
    return (d, c) => {
      const entry = entries.find(([weekdays]) => weekdays.includes(WEEKDAYS[weekday(d)]));
      return entry ? entry[1](d, c) : d;
    };
  }

  // anchors: functions of (date, direction, context)
  const businessDay = (on) => (d, direction, c) => plusBusinessDays(d, direction, market(on, c));
  const holiday = (on) => (d, direction, c) => { const m = market(on, c); return nextDay(d, (x) => isHoliday(m, x), direction); };
  const nonholiday = (on) => (d, direction, c) => { const m = market(on, c); return nextDay(d, (x) => !isHoliday(m, x), direction); };

  // declarations; a calendar is { scope, ignore }, { named } or { combined: [terms] }, ignore { days, currencies }
  const calendarOf = (terms) => terms.length === 1 ? terms[0] : { combined: terms };

  // ONE OF {item AS name, ...}: alternatives [name, exprs] of which exactly one will happen
  function oneOf(alternatives) {
    const names = alternatives.map(([name]) => name);
    if (new Set(names).size !== names.length) throw new Error(`ONE OF: names twice in ${names}`);
    return { alternatives, union: (c) => alternatives.flatMap(([, expr]) => expr(c)) };
  }

  function ruleDeclaration(key, eventClass, include, exclude, validFrom, validTo, body, attributes, citations, refs, estimated) {
    const alternatives = body.alternatives ?? [];
    const named = Object.fromEntries(attributes);
    return {
      key, eventClass, include, exclude, validFrom, validTo, expr: body.union ?? body, attributes: named, citations,
      references: refs, estimated: estimated || alternatives.length > 0, alternatives,
      kind: named.kind ?? 'closed',
      valid(d) {
        return (!validFrom || compare(d, validFrom) >= 0) && (!validTo || compare(d, validTo) <= 0);
      },
      inYear(year) {
        return this.valid(date(year, 12, 31)) || this.valid(date(year, 1, 1)) || !!validFrom && validFrom.year === year;
      },
    };
  }

  // the moves from left to right; a postponement is limited from the date before it (CUMULATIVE: before the first)
  function applyShifts(d, shifts, c) {
    let origin = null;
    for (const shift of shifts) {
      if (shift.postpone) {
        origin ??= d;
        d = shift.postpone(d, c, shift.cumulative ? origin : d);
      } else {
        d = shift(d, c);
      }
    }
    return d;
  }

  // the date itself if it matches, else the nearer of the next and the previous match; ties go forward
  function nearest(anchor, d, c) {
    const same = anchor(plusDays(d, -1), 1, c);
    if (same.year === d.year && same.month === d.month && same.day === d.day) return d;
    const after = anchor(d, 1, c), before = anchor(d, -1, c);
    const days = (a, b) => (Date.UTC(a.year, a.month - 1, a.day) - Date.UTC(b.year, b.month - 1, b.day)) / 86400000;
    return days(after, d) <= days(d, before) ? after : before;
  }

  // the last business day of the month of d
  function lastBusinessDay(d, m) {
    return roll(roll(d, 'EOM', m), 'PRECEDING', m);
  }

  // country:AU contains country:AU and region:AU-NSW
  function contains(outer, inner) {
    return outer === inner || !!inner && inner.split(':')[1].startsWith(outer.split(':')[1] + '-');
  }

  // ================= the rule catalog =================

  class RuleCatalog {
    // declarations: rules, { weekend } and { calendar }; announcements: key -> the dates an authority published
    constructor(declarations, announcements = {}, parse = null) {
      this.parse = parse;
      this.rules = declarations.filter((d) => d.expr);
      this.weekends = new Map();
      this.calendars = new Map();
      for (const d of declarations) {
        if (d.weekend) {
          if (!this.weekends.has(d.weekend)) this.weekends.set(d.weekend, []);
          this.weekends.get(d.weekend).push(d);
        } else if (d.calendar) {
          if (this.calendars.has(d.calendar)) throw new Error(`calendar ${d.calendar} declared twice`);
          this.calendars.set(d.calendar, d.spec);
        }
      }
      for (const regimes of this.weekends.values()) regimes.sort((a, b) => dayNumber(a.from) - dayNumber(b.from));
      this.announcements = new Map(Object.entries(announcements ?? {}).map(([key, dates]) => [key, [...dates].sort(compare)]));
      this.states = new Map();
      this.shallowStates = new Map();
      this.running = new Map();
      this.checkVersions();
      this.checkReferences();
    }

    // ---------- checks at load ----------

    checkVersions() {
      const min = date(1, 1, 1), max = date(9999, 12, 31);
      for (const a of this.rules) {
        for (const b of this.rules) {
          if (a !== b && a.key === b.key && compare(a.validFrom ?? min, b.validTo ?? max) <= 0
              && compare(b.validFrom ?? min, a.validTo ?? max) <= 0) {
            throw new Error(`rule ${a.key}: versions overlap in time`);
          }
        }
      }
    }

    referenced(name) {
      return this.rules.filter((r) => r.key === name || r.eventClass === name);
    }

    checkReferences() {
      const graph = new Map();
      for (const rule of this.rules) {
        if (!graph.has(rule.key)) graph.set(rule.key, new Set());
        for (const name of rule.references) {
          const found = this.referenced(name);
          if (!found.length && !this.announcements.has(name)) {
            throw new Error(`rule ${rule.key}: no rule, event class or announced key ${name}`);
          }
          found.forEach((r) => graph.get(rule.key).add(r.key));
        }
      }
      const done = new Set(), path = [];
      const visit = (key) => {
        if (path.includes(key)) throw new Error(`cyclic references: ${[...path.slice(path.indexOf(key)), key].join(' -> ')}`);
        if (done.has(key)) return;
        path.push(key);
        for (const target of graph.get(key) ?? []) visit(target);
        path.pop();
        done.add(key);
      };
      for (const key of graph.keys()) visit(key);
    }

    // ---------- scopes and calendars ----------

    applies(rule, scope) {
      return rule.include.some((s) => contains(s, scope)) && !rule.exclude.some((s) => contains(s, scope));
    }

    // the weekend days of the scope on d: its own regime, else that of the scope containing it, else SAT SUN
    weekend(scope, d) {
      for (const candidate of [...this.weekends.keys()].sort((a, b) => b.length - a.length)) {
        if (!contains(candidate, scope)) continue;
        const regimes = this.weekends.get(candidate).filter((r) => compare(r.from, d) <= 0);
        if (regimes.length) return regimes[regimes.length - 1].weekdays;
      }
      return new Set(['SAT', 'SUN']);
    }

    // the calendar of a scope, for contexts of the schedule language
    calendar(scope) {
      return new ScopeCalendar(this, scope);
    }

    resolve(spec, c, pair = null) {
      if (spec.named) {
        if (!this.calendars.has(spec.named)) throw new Error(`no calendar ${spec.named}`);
        return this.resolve(this.calendars.get(spec.named), c, pair);
      }
      if (spec.scope && !spec.ignore) return spec.scope === c.scope ? c.market : this.calendar(spec.scope);
      const terms = spec.combined ?? [spec];
      return new CombinedCalendar(terms.map((t) => t.scope
        ? { market: this.resolve({ scope: t.scope, ignore: null }, c), ignore: t.ignore }
        : { market: this.resolve(t, c, pair), ignore: null }), c.base, pair);
    }

    announcement(key, year) {
      return (this.announcements.get(key) ?? []).filter((d) => d.year === year);
    }

    // ---------- evaluation ----------

    ruleContext(rule, scope, year, shallow, m = null) {
      return { base: date(year, 1, 1), market: m ?? new ScopeCalendar(this, scope, { rule, year }, year, shallow),
        events: references(this, scope, year, shallow), fixing: WEEKENDS, catalog: this, scope, rule: rule.key };
    }

    // { dates, keys, projected } of the rule evaluated in the year; an announcement for the year always wins
    evaluateIn(rule, scope, year, shallow, m = null) {
      const published = this.announcement(rule.key, year);
      const result = (dates, projected) => ({ dates, keys: new Set(dates.map(isoOf)), projected });
      if (published.length) return result(published.filter((d) => rule.valid(d)), false);
      const dates = distinct(rule.expr(this.ruleContext(rule, scope, year, shallow, m)).filter((d) => rule.valid(d)));
      return result(dates, rule.estimated || this.announcements.has(rule.key));
    }

    // rule -> { dates, keys, projected } of the rules of the scope evaluated with the first of January of the year
    state(scope, year, shallow = false) {
      const key = `${scope} ${year}`;
      const cache = shallow ? this.shallowStates : this.states;
      if (this.states.has(key)) return this.states.get(key);
      if (cache.has(key)) return cache.get(key);
      if (this.running.has(key)) return this.running.get(key);
      const rules = this.rules.filter((r) => this.applies(r, scope) && r.inYear(year));
      const state = new Map(rules.map((r) => [r, { dates: [], keys: new Set(), projected: r.estimated || this.announcements.has(r.key) }]));
      this.running.set(key, state);
      try {
        const nominal = rules.map((r) => this.nominal(r, scope, year));
        const order = rules.map((_, i) => i).sort((a, b) => nominal[a] - nominal[b] || a - b);
        let settled = false;
        for (let pass = 0; pass < MAX_PASSES && !settled; pass++) {
          settled = true;
          for (const i of order) {
            const result = this.evaluateIn(rules[i], scope, year, shallow);
            const before = state.get(rules[i]);
            if (result.dates.map(isoOf).join() !== before.dates.map(isoOf).join() || result.projected !== before.projected) {
              state.set(rules[i], result);
              settled = false;
            }
          }
        }
        if (!settled) throw new Error(`the rules of ${scope} in ${year} do not settle in ${MAX_PASSES} passes`);
      } finally {
        this.running.delete(key);
      }
      cache.set(key, state);
      return state;
    }

    // the first date of the rule with every day open, which orders the passes; Infinity if it needs more
    nominal(rule, scope, year) {
      try {
        const { dates } = this.evaluateIn(rule, scope, year, true, OPEN);
        return dates.length ? dayNumber(dates[0]) : Infinity;
      } catch {
        return Infinity;
      }
    }

    // the occurrences of the rules of the scope in the year (also those evaluated with a neighbouring year), sorted by
    // date and rule key; the alternatives of ONE OF in the order written
    holidays(scope, year) {
      const found = new Map();
      for (const y of [year - 1, year, year + 1]) {
        for (const [rule, { dates, projected }] of this.state(scope, y)) {
          for (const [d, scenario] of this.scenarios(rule, scope, y, dates)) {
            if (d.year !== year) continue;
            const occurrence = { date: d, rule: rule.key, eventClass: rule.eventClass, kind: rule.kind,
              precision: rule.attributes.precision ?? 'day', session: rule.attributes.session ?? null,
              citations: rule.citations, projected, scenario };
            const key = `${isoOf(d)} ${rule.key} ${scenario} ${projected}`;
            if (!found.has(key)) found.set(key, occurrence);
          }
        }
      }
      return [...found.values()].sort((a, b) => compare(a.date, b.date) || (a.rule < b.rule ? -1 : a.rule > b.rule ? 1 : 0));
    }

    // [date, scenario] for the dates of the rule: per alternative of ONE OF, else without a scenario
    scenarios(rule, scope, year, dates) {
      if (!rule.alternatives.length || this.announcement(rule.key, year).length) return dates.map((d) => [d, null]);
      const c = this.ruleContext(rule, scope, year, false);
      return rule.alternatives.flatMap(([name, expr]) => distinct(expr(c)).filter((d) => rule.valid(d)).map((d) => [d, name]));
    }

    referenceDates(name, scope, year, shallow = false) {
      const rules = this.referenced(name);
      const dates = rules.length ? [] : [...this.announcement(name, year)];
      for (const rule of rules) {
        const inScope = scope && this.applies(rule, scope) ? scope : rule.include[0];
        const result = this.state(inScope, year, shallow).get(rule);
        if (result) dates.push(...result.dates);
      }
      return distinct(dates);
    }

    // a context for catalog.schedule(...): the base date, the calendar of the scope (weekends only without one) and,
    // for > CUTOFF, the base with its time of day (a Date)
    context(t, scope = null, instant = null) {
      return { base: t, market: scope ? this.calendar(scope) : WEEKENDS, events: references(this, scope),
        fixing: WEEKENDS, catalog: this, scope, instant };
    }

    // one expression of the rule language, evaluated with catalog.context(base, scope)
    schedule(text) {
      return this.parse(text);
    }
  }
}}

// ================= start rules =================

ruleFile
  = ws declarations:(@declaration ws)* {
      return new RuleCatalog(declarations, options.announcements,
        (text) => peg$parse(text, { ...options, startRule: 'schedule' }));
    }

schedule
  = ws @exprs ws

// ================= declarations =================

declaration
  = ruleDecl
  / weekendDecl
  / calendarDecl

// a parser can backtrack after an @reference, so the references and Hijri methods are read from the declaration's
// text, without strings and comments
ruleDecl
  = "rule" wordEnd ws key:name ws "for" wordEnd ws eventClass:name ws "in" wordEnd ws include:scopes
    exclude:(ws "except" wordEnd ws @scopes)? validity:(ws @validity)? ws "=" ws body:body
    attributes:(ws @attributes)? citations:(ws @provenance)? ws ";" {
      const plain = text().replace(/"[^"]*"|#[^\n]*/g, '');
      const refs = new Set([...plain.matchAll(REFERENCE)].map((m) => m[0].slice(1)));
      return ruleDeclaration(key, eventClass, include, exclude ?? [], validity?.[0] ?? null, validity?.[1] ?? null, body,
        attributes ?? [], citations ?? [], refs, ESTIMATED.test(plain));
    }

weekendDecl
  = "weekend" wordEnd ws s:scope ws "=" ws weekdays:weekdaySet from:(ws "from" wordEnd ws @isoDate)? ws ";" {
      return { weekend: s, weekdays: new Set(weekdays), from: from ?? date(1, 1, 1) };
    }

calendarDecl
  = "calendar" wordEnd ws key:name ws "=" ws spec:calendar ws ";" { return { calendar: key, spec }; }

// exactly one of the alternatives will happen: ONE OF {item AS name, ...}
body
  = "ONE" wordEnd ws "OF" wordEnd ws "{" ws head:alternative tail:(ws "," ws @alternative)* ws "}" { return oneOf([head, ...tail]); }
  / exprs

alternative
  = i:item ws "AS" wordEnd ws n:name { return [n, exprsOf([i])]; }

// a key or an event class; the words of kinds, precisions and labels are names as well
name
  = $([a-z] [a-z0-9_]* ("." [a-z] [a-z0-9_]*)*)

// a scope: country:JP, region:AU-NSW, settlement_system:TARGET2
scope
  = $([a-z] [a-z_]* ":" [A-Z0-9] [A-Z0-9_]* ("-" [A-Z0-9] [A-Z0-9_]*)*)

scopes
  = head:scope tail:(ws "," ws @scope)* { return [head, ...tail]; }

validity
  = "valid" wordEnd from:(ws "from" wordEnd ws @isoDate)? to:(ws "to" wordEnd ws @isoDate)? { return [from, to]; }

isoDate
  = y:Number ws "-" ws m:Number ws "-" ws d:Number { return date(y, m, d); }

attributes
  = "with" wordEnd ws head:attribute tail:(ws "," ws @attribute)* { return [head, ...tail]; }

attribute
  = "kind" wordEnd ws "=" ws kind:$("closed" / "non_working" / "short_day" / "working_day" / "paid_no_rest" / "tourist"
      / "event" / "special_session" / "changed_hours") wordEnd { return ['kind', kind]; }
  / "session" wordEnd ws opens:time closes:(ws "-" ws @time)? tz:(ws "tz" wordEnd ws @string)? {
      return ['session', [opens, closes ?? opens, tz]];
    }
  / "precision" wordEnd ws "=" ws precision:$("day" / "month") wordEnd { return ['precision', precision]; }
  / "note" wordEnd ws s:string { return ['note', s]; }

time
  = h:Number ws ":" ws m:Number { return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`; }

provenance
  = "cites" wordEnd ws head:citation tail:(ws "," ws @citation)* { return [head, ...tail]; }

citation
  = source:string article:(ws "art" wordEnd ws @string)? url:(ws "url" wordEnd ws @string)? ws "label" wordEnd ws
    label:$("official" / "benchmark" / "practice" / "secondary") wordEnd {
      return { source, article, url, label };
    }

// the text without the quotes
string
  = '"' @$[^"]* '"'

// ================= calendars =================

// a business day in every term of "&"
calendar
  = head:calTerm tail:(ws "&" ws @calTerm)* { return calendarOf([head, ...tail]); }

// USD on T+1 does not count, unless for MXN; the scope before the name, which would take its first word
calTerm
  = s:scope ignore:(ws @ignore)? { return { scope: s, ignore }; }
  / n:name { return { named: n }; }
  / "(" ws @calendar ws ")"

ignore
  = "ignore" wordEnd ws "at" wordEnd ws "T" wordEnd ws "+" ws days:Number
    currencies:(ws "unless" wordEnd ws "{" ws head:ccy tail:(ws "," ws @ccy)* ws "}" { return [head, ...tail]; })? {
      return { days, currencies: currencies ?? [] };
    }

// in an expression one term only, as "&" there is include: ON country:BR, ON usd_eur, ON (a & b)
on
  = "ON" wordEnd ws spec:(s:scope { return { scope: s, ignore: null }; } / n:name { return { named: n }; } / "(" ws @calendar ws ")") {
      return spec;
    }

// ================= expressions =================

exprs
  = head:item tail:(ws "," ws @item)* { return exprsOf([head, ...tail]); }

item
  = pickedItem
  / plainItem

// 5TH [SPOT : 1Q : ] > EOQ > ...: the item starts with a series or an event, where several dates come from
pickedItem
  = n:ordinal last:(ws "LAST" wordEnd)? ws start:(series / name:Event { return event(name); }) shifts:(ws @shift)* rest:itemRest {
      return pick(n, last !== null, rest(shifted(start, shifts)));
    }

plainItem
  = e:expr rest:itemRest { return rest(e); }

// the combines and the IF of an item; the condition applies to every date, the shifts after it to the dates it keeps
itemRest
  = combines:(ws @combine)* clause:(ws @ifClause)? {
      return (first) => ruleItem(combines.reduce((left, combine) => combine(left), first), clause);
    }

ifClause
  = "IF" wordEnd ws c:condition shifts:(ws @shift)* { return { condition: c, shifts }; }

// A & B includes the dates of B, A \ B excludes them; the shifts after it apply to the result
combine
  = op:("&" / "\\") ws o:operand shifts:(ws @shift)* {
      return (left) => shifted(combined(left, op === '&', o), shifts);
    }

// a date, a series or a set of dates
operand
  = "{" ws head:expr tail:(ws "," ws @expr)* ws "}" { return [head, ...tail]; }
  / d:date { return [d]; }

// [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out
series
  = open:("[" / "]") ws first:endpoint ws ":" ws
    rest:(step:period ws ":" ws last:endpoint? { return [step, last]; } / last:endpoint { return [null, last]; })
    ws close:("]" / "[") {
      const step = rest[0] ?? { months: 0, days: 1, business: 0 }, last = rest[1];
      if (step.months <= 0 && step.days <= 0 && step.business <= 0 && !(step.fixing > 0)) throw new RangeError('step must be positive');
      if (last === null && close === '[') throw new RangeError('an open series ends with ]');
      return series(first, step, last, open === ']', close === '[');
    }

// an endpoint is one date, so it can't be a series
endpoint
  = d:singleDate shifts:(ws @shift)* { return shifted(d, shifts); }

// shifts are applied from left to right
expr
  = d:date shifts:(ws @shift)* { return shifted(d, shifts); }

date
  = series
  / singleDate

shift
  = "+" ws p:period endend:(ws "ENDEND" wordEnd)? on:(ws @on)? { return plusShift(p, 1, endend !== null, on); }
  / "-" ws p:period endend:(ws "ENDEND" wordEnd)? on:(ws @on)? { return plusShift(p, -1, endend !== null, on); }
  / ">" ws @(
      "IMM" wordEnd { return nextImm; }
    / "SPOT" wordEnd pair:(ws @pair)? on:(ws @on)? { return spotShift(pair, on); }
    / "FIX" wordEnd pair:(ws @pair)? { return (d, c) => fix(d, pair, c.fixing); }
    / "NEXT" wordEnd ws n:(@ordinal ws)? a:anchor { return (d, c) => times(n ?? 1, a)(d, 1, c); }
    / "PREV" wordEnd ws n:(@ordinal ws)? a:anchor { return (d, c) => times(n ?? 1, a)(d, -1, c); }
    / "NEAREST" wordEnd ws a:anchor { return nearestShift(a); }   // ties go forward
    / "NEAREST" wordEnd ws on:on { return rollShift('NEAREST', on); }   // the FpML convention
    / "CUTOFF" wordEnd ws at:time ws z:zone { return cutoff(at, z); }   // the trade date of a base with a time of day
    / "POSTPONE" wordEnd ws r:rollWord on:(ws @on)? ws "MAX" wordEnd ws p:period cumulative:(ws "CUMULATIVE" wordEnd)? {
        return postpone(r, on, p, cumulative !== null);
      }
    / r:rollWord on:(ws @on)? { return rollShift(r, on); }
      // > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH
    / count:count ws a:select ws "IN" wordEnd ws p:$("WEEK" / "MONTH" / "QUARTER" / "HALFYEAR" / "YEAR") wordEnd {
        return (d, c) => select(d, count.n, count.fromEnd, a, p, c);
      }
      // > {1 FEB, 1 MAY}: the dates of the set in the year of the date
    / "{" ws head:annual tail:(ws "," ws @annual)* ws "}" { return datesInYear([head, ...tail]); })
    // {SAT - 1D, TUE WED > PREV MON}: the first entry for the weekday of the date moves it
  / "{" ws head:observe tail:(ws "," ws @observe)* ws "}" { return observance([head, ...tail]); }

observe
  = weekdays:(@weekday ws)+ move:(
      "+" ws p:period { return (d, c) => plus(d, p, 1, c.market, c.fixing); }
    / "-" ws p:period { return (d, c) => plus(d, p, -1, c.market, c.fixing); }
    / ">" ws @(
        "NEXT" wordEnd ws a:anchor { return (d, c) => a(d, 1, c); }
      / "PREV" wordEnd ws a:anchor { return (d, c) => a(d, -1, c); }
      / "NEAREST" wordEnd ws a:anchor { return nearestShift(a); }
      / r:rollWord on:(ws @on)? { return rollShift(r, on); })) {
      return [weekdays, move];
    }

rollWord
  = @$("MODFOLLOWING" / "MODPRECEDING" / "FOLLOWING" / "PRECEDING" / "BOW" / "EOW" / "BOM" / "EOM" / "BOQ" / "EOQ"
      / "BOH" / "EOH" / "BOY" / "EOY") wordEnd

// the count of a selection: from the start, or from the end with LAST
count
  = n:ordinal last:(ws "LAST" wordEnd)? { return { n, fromEnd: last !== null }; }
  / "LAST" wordEnd { return { n: 1, fromEnd: true }; }

// the targets of a selection; an annual date starting with LAST only in braces, as LAST also starts the count
select
  = "{" ws head:target tail:(ws "," ws @target)* ws "}" { return anyOf([head, ...tail]); }
  / chosen

// NEXT {MON, FRI}: the nearest of a set
anchor
  = "{" ws head:target tail:(ws "," ws @target)* ws "}" { return anyOf([head, ...tail]); }
  / target

target
  = chosen
  / "LAST" wordEnd ws w:weekday ws m:month { return annualAnchor(lastWeekday(w, m)); }

chosen
  = w:weekday { return weekdayAnchor(w); }
  / "BD" wordEnd on:(ws @on)? { return businessDay(on); }
  / "FD" wordEnd { return (d, direction, c) => plusBusinessDays(d, direction, c.fixing); }
  / "IMM" wordEnd { return (d, direction) => direction > 0 ? nextImm(d) : previousImm(d); }
  / a:fixedAnnual { return annualAnchor(a); }
  / n:Event { return eventAnchor(n); }
  / "HOLIDAY" wordEnd on:(ws @on)? { return holiday(on); }
  / "NONHOLIDAY" wordEnd on:(ws @on)? { return nonholiday(on); }

// a date that is not a series; the dates that start with a number before the annual dates, which also do
singleDate
  = "TOM" wordEnd { return (c) => plusBusinessDays(base(c), 1, c.market); }
  / "T" wordEnd { return base; }
  / "SPOT" wordEnd pair:(ws @pair)? on:(ws @on)? { return (c) => spot(base(c), pair, market(on, c, pair)); }
  / y:Number ws "-" ws m:Number ws "-" ws d:Number { const value = date(y, m, d); return () => value; }   // ISO
  / d:Number ws "." ws m:Number ws "." ws y:Number { const value = date(y, m, d); return () => value; }   // German
  / m:Number ws "/" ws d:Number ws "/" ws y:Number { const value = date(y, m, d); return () => value; }   // US
  / a:annual { return inYear(a); }
  / n:Event { return event(n); }

// dates that recur every year
annual
  = fixedAnnual
  / "LAST" wordEnd ws w:weekday ws m:month { return lastWeekday(w, m); }

fixedAnnual
  = d:Number ws m:month method:(ws @hijriMethod)? { return hijriMonthDay(d, m, method, options.Solar); }
  / n:Number ws w:weekday ws m:month { return nthWeekday(n, w, m); }
  / "EASTER" wordEnd { return easter; }
  / "ORTHODOX" wordEnd ws "EASTER" wordEnd { return orthodoxEaster; }
  / kind:$("EQUINOX" / "SOLSTICE") wordEnd ws m:month z:(ws @zone)? { return zonedSeason(kind, m, z); }
  / "SOLARTERM" wordEnd ws n:Number z:(ws @zone)? { return zonedSolarTerm(n, z); }
  / "HINDU" wordEnd ws key:name { return hindu(key); }

// 1 SHAWWAL BY SIGHTING
hijriMethod
  = "BY" wordEnd ws @(@$("UMMALQURA" / "SIGHTING" / "DIYANET") wordEnd / "FOLLOW" wordEnd ws scope { return 'FOLLOW'; })

// UTC+9 or an IANA time zone such as "Asia/Tokyo"
zone
  = $("UTC" [+-] [0-9]+)
  / string

// 1ST, 2ND, 3RD, 4TH; ordinal() of schedule.pegjs checks the suffix
ordinal
  = text:$([0-9]+ ("ST" / "ND" / "RD" / "TH")) wordEnd { return ordinal(text); }

// with a word boundary, unlike Period, so 1 DEC is no period
period
  = @Period wordEnd

month
  = @Month wordEnd

weekday
  = @Weekday wordEnd

// without a lexer nothing reserves the six-letter keywords, so they're excluded here
pair
  = !("EASTER" / "ENDEND" / "FOLLOW" / "SHABAN" / "TAMMUZ" / "TISHRI" / "KISLEV" / "SHEVAT" / "ADARII" / "SANYUE"
      / "LIUYUE" / "JIUYUE" / "SHIYUE") @$[A-Z]|6| ![A-Z]

// a currency, only inside "unless {...}"
ccy
  = @$[A-Z]|3| ![A-Z]

// ================= conditions =================

condition
  = head:condAnd tail:(ws "OR" wordEnd ws @condAnd)* { return tail.length ? anyCondition([head, ...tail]) : head; }

condAnd
  = head:condNot tail:(ws "AND" wordEnd ws @condNot)* { return tail.length ? allConditions([head, ...tail]) : head; }

condNot
  = negate:("NOT" wordEnd ws)? atom:condAtom { return negate ? notCondition(atom) : atom; }

condAtom
  = "DAY" wordEnd ws first:Number ws ".." ws last:Number m:(ws @month)? { return onDay(first, last, m); }   // DAY 8..14 APR
  / "BETWEEN" wordEnd ws "HOLIDAYS" wordEnd on:(ws @on)? { return betweenHolidays(on); }   // JP art. 3(3)
  / "EVERY" wordEnd ws p:period ws "FROM" wordEnd ws n:Number { return every(p, n); }   // EVERY 4Y FROM 1965
  / "HOLIDAY" wordEnd on:(ws @on)? { return holidayCondition(on); }
  / "(" ws @condition ws ")"
  / s:subject ws "IS" wordEnd ws test:(
      "HOLIDAY" wordEnd on:(ws @on)? { return dateIsHoliday(s, on); }
    / w:weekdaySet { return isWeekday(s, w); }) { return test; }
  / w:weekdaySet { return onWeekday(w); }   // the weekday of the date itself

// 18 SEP, EASTER + 1D
subject
  = d:singleDate shifts:(ws @(sign:("+" / "-") ws p:period { return plusShift(p, sign === '+' ? 1 : -1); }))* {
      return shifted(d, shifts);
    }

weekdaySet
  = "{" ws head:weekday tail:(ws "," ws @weekday)* ws "}" { return [head, ...tail]; }
  / head:weekday tail:(ws @weekday)* { return [head, ...tail]; }

// ================= lexical =================

// a word ends here: no letter, digit or "_" follows (T is not the start of TOM, OR not the start of ORTHODOX)
wordEnd
  = ![A-Za-z0-9_]

// whitespace and comments (# to the end of the line)
ws
  = ([ \t\r\n] / "#" [^\n]*)*
