// Generates examples/date-holidays.txt: test cases from the holiday rules of date-holidays
// (https://github.com/commenthol/date-holidays, code ISC, data CC BY 3.0).
//
// For every country, state and region, each rule that the schedule language can express is translated into a
// schedule, and the expected dates come from date-holidays itself, so all ten parsers are checked against a second
// independent implementation. A case is only written for a year in which date-holidays yields the rule, which takes
// care of "since 2005", "in even years" and similar conditions without modelling them. Run with:  npm run generate
import { readFileSync, writeFileSync } from 'node:fs';
import Holidays from 'date-holidays';

// the package exports neither its version nor its data, so both are read from the installed files
const PACKAGE = new URL('node_modules/date-holidays/', import.meta.url);
const VERSION = JSON.parse(readFileSync(new URL('package.json', PACKAGE), 'utf8')).version;
const DATA = JSON.parse(readFileSync(new URL('data/holidays.json', PACKAGE), 'utf8'));
const OUT = new URL('../../examples/date-holidays.txt', import.meta.url);
const YEARS = [2024, 2025, 2026, 2027];

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
const MONTH_NAMES = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october',
  'november', 'december'];
const WEEKDAYS = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];  // JS getDay order
const WD = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const HIJRI = {
  'muharram': 'MUHARRAM', 'safar': 'SAFAR', 'rabi al-awwal': 'RABIALAWWAL', 'rabi al-thani': 'RABIALTHANI',
  'jumada al-awwal': 'JUMADAALULA', 'jumada al-thani': 'JUMADAALAKHIRA', 'rajab': 'RAJAB', 'shaban': 'SHABAN',
  'ramadan': 'RAMADAN', 'shawwal': 'SHAWWAL', 'dhu al-qidah': 'DHULQADAH', 'dhu al-hijjah': 'DHULHIJJAH',
};
const HEBREW = {
  'nisan': 'NISAN', 'iyyar': 'IYAR', 'sivan': 'SIVAN', 'tamuz': 'TAMMUZ', 'av': 'AV', 'elul': 'ELUL', 'tishrei': 'TISHRI',
  'cheshvan': 'HESHVAN', 'kislev': 'KISLEV', 'tevet': 'TEVET', 'shvat': 'SHEVAT',
};
const CHINESE = ['ZHENGYUE', 'ERYUE', 'SANYUE', 'SIYUE', 'WUYUE', 'LIUYUE', 'QIYUE', 'BAYUE', 'JIUYUE', 'SHIYUE', 'DONGYUE',
  'LAYUE'];

class Skip extends Error {}

const days = (n) => (n > 0 ? ` + ${n}D` : n < 0 ? ` - ${-n}D` : '');
const ordinal = (s) => parseInt(s, 10);

// the date part of a rule: "12-25", "easter -2", "3rd monday in June", "monday before 06-01", "1 Shawwal", ...
function base(text) {
  let m;
  if ((m = text.match(/^(\d\d)-(\d\d)$/))) return `${+m[2]} ${MONTHS[+m[1] - 1]}`;
  if ((m = text.match(/^(\d{4})-(\d\d)-(\d\d)$/))) return `${m[1]}-${m[2]}-${m[3]}`;
  if ((m = text.match(/^easter(?: ([+-]?\d+))?$/))) return `EASTER${days(+(m[1] ?? 0))}`;
  if (/^orthodox\b/.test(text)) throw new Skip('Orthodox (Julian) Easter');
  if ((m = text.match(/^(january|february|march|april|may|june|july|august|september|october|november|december)$/))) {
    return `1 ${MONTHS[MONTH_NAMES.indexOf(m[1])]}`;
  }
  if ((m = text.match(/^(\d+) (.+)$/)) && HIJRI[m[2]]) return `${+m[1]} ${HIJRI[m[2]]}`;
  if ((m = text.match(/^(\d+) (.+)$/)) && HEBREW[m[2]]) return `${+m[1]} ${HEBREW[m[2]]}`;
  if ((m = text.match(/^(\d+) adar$/))) throw new Skip('Adar: which Adar in a leap year differs');
  if ((m = text.match(/^chinese (?:\d+-\d+-)?(\d\d)-([01])-(\d\d)$/))) {
    if (m[2] === '1') throw new Skip('a Chinese leap month');
    const day = +m[3];
    return day === 0 ? `1 ${CHINESE[+m[1] - 1]} - 1D` : `${day} ${CHINESE[+m[1] - 1]}`;  // day 0: the day before
  }
  // 3rd monday in June, monday before June
  if ((m = text.match(/^(?:(\d+)(?:st|nd|rd|th) )?(\w+day) (in|before) (\w+)$/)) && MONTH_NAMES.includes(m[4])) {
    const n = m[1] ? ordinal(m[1]) : 1, wd = WD[WEEKDAYS.indexOf(m[2])], month = MONTHS[MONTH_NAMES.indexOf(m[4])];
    // date-holidays counts on past the end of the month ("5th monday in October" can be in November)
    if (m[3] === 'in') return n <= 4 ? `${n} ${wd} ${month}` : `1 ${month} - 1D > NEXT ${wd}${days(7 * (n - 1))}`;
    return `1 ${month} > PREV ${wd}${days(-7 * (n - 1))}`;
  }
  // monday after 02-01 (the same day or later), 2nd sunday before 05-01 (strictly before), friday after <rule>
  if ((m = text.match(/^(?:(\d+)(?:st|nd|rd|th) )?(\w+day) (after|before) (.+)$/)) && WEEKDAYS.includes(m[2])) {
    const n = m[1] ? ordinal(m[1]) : 1, wd = WD[WEEKDAYS.indexOf(m[2])], inner = base(m[4]);
    if (/[,(]/.test(inner)) throw new Skip('a weekday relative to a span');
    return m[3] === 'after' ? `${inner} - 1D > NEXT ${wd}${days(7 * (n - 1))}` : `${inner} > PREV ${wd}${days(-7 * (n - 1))}`;
  }
  if (/equinox|solstice/.test(text)) throw new Skip('equinox or solstice');
  if (/solarterm/.test(text)) throw new Skip('a Chinese solar term');
  if (/bengali/.test(text)) throw new Skip('the Bengali calendar');
  if (/farvardin|ordibehesht|khordad|\btir\b|mordad|shahrivar|mehr|aban|azar|\bdey\b|bahman|esfand/.test(text)) {
    throw new Skip('the Persian calendar');
  }
  throw new Skip('a date form the translator doesn\'t know');
}

function skip(reason) {
  throw new Skip(reason);
}

// "if saturday, sunday then next monday if ..." -> [{ from: [6, 0], to: 1, dir: 1 }, ...]
function clauses(text) {
  const out = [];
  for (const [, from, dir, to] of text.matchAll(/if ([\w, ]+?) then (next|previous) (\w+day)/g)) {
    out.push({ from: from.split(/, ?| and /).map((w) => WEEKDAYS.indexOf(w.trim())), to: WEEKDAYS.indexOf(to), dir: dir === 'next' ? 1 : -1 });
  }
  if (out.some((c) => c.to < 0 || c.from.some((w) => w < 0))) throw new Skip('a condition on something else than the weekday');
  return out;
}

// days from weekday w to the next (dir 1) or previous (-1) weekday t
const step = (w, t, dir) => dir * (((dir * (t - w)) % 7 + 7) % 7 || 7);

// the schedule for a rule string, or Skip
function translate(rule) {
  let text = rule.toLowerCase().trim();
  if (/\bis (\w+ )?holiday\b/.test(text)) throw new Skip('depends on other holidays');
  if (/\d\d:\d\d|\bp\d*t|\bp\d+d\d|\bpt/.test(text) || /\bp\d+dt/.test(text)) throw new Skip('a time of day or a duration in hours');
  // date-holidays reads "04-28 since 2022 and if sunday then next monday" as a move, not as both days
  if (/ (since|prior to|in|every) .* and if /.test(text)) throw new Skip('a year condition before "and if"');
  // conditions on the year: date-holidays applies them, and cases are only written for the years it yields
  text = text.replace(/ (since|prior to) \d{4}(-\d\d-\d\d)?/g, '').replace(/ in (even|odd|leap|non-leap) years/g, '')
    .replace(/ every \d+ years since \d{4}/g, '').replace(/ in \d{4}(-\d{4})?/g, '').trim();
  let span = 1, m;
  if ((m = text.match(/ p(\d+)d$/))) {
    span = +m[1];
    text = text.slice(0, m.index);
  }
  let substitutes = false, both = false, weekdays = null;
  if (text.startsWith('substitutes ')) {
    substitutes = true;
    text = text.slice('substitutes '.length);
  }
  if ((m = text.match(/ (not )?on ((?:\w+day(?:, ?| and )?)+)$/))) {
    const listed = m[2].split(/, ?| and /).map((w) => WEEKDAYS.indexOf(w.trim()));
    weekdays = m[1] ? [0, 1, 2, 3, 4, 5, 6].filter((w) => !listed.includes(w)) : listed;
    text = text.slice(0, m.index);
  }
  let conditions = [];
  if ((m = text.match(/ (and )?(if .*)$/))) {
    both = Boolean(m[1]);
    if (/ and if /.test(m[2])) throw new Skip('observed on both days for some weekdays only');
    conditions = clauses(m[2]);
    text = text.slice(0, m.index);
  }
  const date = base(text.trim());
  const range = (d) => (span === 1 ? d : `(${d} : 1D : ${d}${days(span - 1)})`);
  if (span > 1 && (conditions.length || weekdays)) throw new Skip('a span with conditions');
  const only = (d, list) => `${d} IF ${list.map((w) => WD[w]).join(' ')}`;
  if (weekdays) {
    if (conditions.length) throw new Skip('weekday conditions of two kinds');
    return only(date, weekdays);
  }
  if (!conditions.length) return range(date);
  if (substitutes || both) {
    // D + k IF t: the weekday of D + k is t exactly when the weekday of D is t - k
    // sorted by the shift, so that the dates come out in order: at most one item applies besides the date
    const items = conditions.flatMap((c) => c.from.map((w) => [step(w, c.to, c.dir), only(`${date}${days(step(w, c.to, c.dir))}`, [c.to])]));
    return [...items, ...(both ? [[0, date]] : [])].sort((a, b) => a[0] - b[0]).map(([, item]) => item).join(', ');
  }
  // observance: the first shift for the weekday applies
  const shifts = conditions.flatMap((c) => c.from.map((w) => `${WD[w]} ${step(w, c.to, c.dir) > 0 ? '+' : '-'} ${Math.abs(step(w, c.to, c.dir))}D`));
  return `${date} {${shifts.join(', ')}}`;
}

// dates of an entry: the day of its start (lunar holidays start at 18:00 the evening before, but their date is the day)
function dates(h) {
  const first = h.date.slice(0, 10);
  const n = Math.round((h.end - h.start) / 86400000);
  return Array.from({ length: Math.max(n, 1) }, (_, i) => {
    const d = new Date(`${first}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() + i);
    return d.toISOString().slice(0, 10);
  });
}

// years in which a rule was disabled or moved somewhere: those years are left out
const changed = new Map();
(function walk(node) {
  for (const [rule, spec] of Object.entries(node?.days ?? {})) {
    for (const d of [...(spec?.disable ?? []), ...(spec?.enable ?? [])]) {
      const year = +String(d).slice(0, 4);
      if (!changed.has(rule)) changed.set(rule, new Set());
      changed.get(rule).add(year);
    }
  }
  for (const sub of ['states', 'regions']) for (const child of Object.values(node?.[sub] ?? {})) walk(child);
  if (node?.holidays) for (const c of Object.values(node.holidays)) walk(c);
})(DATA);

// every country, state and region: rule -> year -> dates
const found = new Map();
const originals = new Set();    // rule|place year with an entry that is not a substitute day
const substitutes = new Set();  // rule|place year with a substitute day
const places = [];
const hd = new Holidays();
for (const country of Object.keys(hd.getCountries())) {
  places.push([country]);
  for (const state of Object.keys(hd.getStates(country) ?? {})) {
    places.push([country, state]);
    for (const region of Object.keys(hd.getRegions(country, state) ?? {})) places.push([country, state, region]);
  }
}
for (const place of places) {
  const h = new Holidays(...place);
  for (const year of [YEARS[0] - 1, ...YEARS, YEARS.at(-1) + 1]) {
    for (const entry of h.getHolidays(year) ?? []) {
      if (!found.has(entry.rule)) found.set(entry.rule, new Map());
      const byYear = found.get(entry.rule);
      const key = `${place.join('-')} ${year}`;
      if (!byYear.has(key)) byYear.set(key, new Set());
      for (const d of dates(entry)) byYear.get(key).add(d);
      (entry.substitute ? substitutes : originals).add(`${entry.rule}|${key}`);
    }
  }
}

// the calendar and day of the first lunar month name in a schedule such as "10 TISHRI" or "(30 RAMADAN : 1D : …)"
function lunarMonth(schedule) {
  const m = schedule.match(/(\d+) ([A-Z]+)/);
  if (!m) return null;
  if (Object.values(HIJRI).includes(m[2])) return { calendar: 'islamic-umalqura', day: +m[1], month: Object.values(HIJRI).indexOf(m[2]) + 1 };
  if (Object.values(HEBREW).includes(m[2])) return { calendar: 'hebrew', day: +m[1], name: m[2] };
  if (CHINESE.includes(m[2])) return { calendar: 'chinese' };
  return null;
}

// whether the Gregorian date is that day of that month in the calendar the language uses (Umm al-Qura; Hebrew)
const HEBREW_INTL = { NISAN: 'Nisan', IYAR: 'Iyar', SIVAN: 'Sivan', TAMMUZ: 'Tamuz', AV: 'Av', ELUL: 'Elul', TISHRI: 'Tishri',
  HESHVAN: 'Heshvan', KISLEV: 'Kislev', TEVET: 'Tevet', SHEVAT: 'Shevat' };
function lunarMatches(iso, lunar) {
  const fmt = new Intl.DateTimeFormat('en-u-ca-' + lunar.calendar, { day: 'numeric', month: lunar.calendar === 'hebrew' ? 'long' : 'numeric', timeZone: 'UTC' });
  const parts = Object.fromEntries(fmt.formatToParts(new Date(`${iso}T12:00:00Z`)).map((p) => [p.type, p.value]));
  if (+parts.day !== lunar.day) return false;
  return lunar.calendar === 'hebrew' ? parts.month === HEBREW_INTL[lunar.name] : +parts.month === lunar.month;
}

const lunarSkipped = new Set();
const cases = new Map();      // schedule -> year -> expected
const conflicts = new Set();
const skipped = new Map();    // reason -> number of rules
for (const [rule, byPlace] of found) {
  let schedule;
  try {
    schedule = translate(rule);
  } catch (e) {
    if (!(e instanceof Skip)) throw e;
    skipped.set(e.message, (skipped.get(e.message) ?? 0) + 1);
    continue;
  }
  const shifted = /\bif\b/.test(rule.toLowerCase());
  for (const [key, set] of byPlace) {
    const year = +key.slice(key.lastIndexOf(' ') + 1);
    if (!YEARS.includes(year) || changed.get(rule)?.has(year)) continue;
    const place = key.slice(0, key.lastIndexOf(' '));
    // a date moved across the turn of the year belongs to the other year in date-holidays
    const near = [year - 1, year, year + 1].flatMap((y) => [...(byPlace.get(`${place} ${y}`) ?? [])])
      .some((d) => /-(12-(2[5-9]|3[01])|01-0[1-7])$/.test(d));
    if (shifted && near) continue;
    // date-holidays sometimes drops the date itself and keeps only the substitute of an "observe both" rule
    if (/\band if\b/.test(rule.toLowerCase())
        && (!originals.has(`${rule}|${key}`) || substitutes.has(`${rule}|${key}`) && set.size < 2)) continue;
    let expected = [...set].sort().join(', ');
    const lunar = lunarMonth(schedule);
    if (lunar) {
      if (lunar.calendar !== 'chinese' && ![...set].some((d) => lunarMatches(d, lunar))) {
        lunarSkipped.add(rule);
        continue;
      }
      // a lunar date that falls twice in the Gregorian year is an error in the language
      if (!/[(,]/.test(schedule) && set.size === 2) expected = 'error';
    }
    if (/^\d{4}-/.test(schedule) && !expected.startsWith(schedule.slice(0, 4))) continue;
    if (!cases.has(schedule)) cases.set(schedule, new Map());
    const known = cases.get(schedule).get(year);
    if (known !== undefined && known !== expected) conflicts.add(`${schedule} ${year}`);
    cases.get(schedule).set(year, expected);
  }
}
for (const c of conflicts) cases.get(c.slice(0, c.lastIndexOf(' '))).delete(+c.slice(c.lastIndexOf(' ') + 1));

const lines = [];
for (const [schedule, byYear] of [...cases].sort(([a], [b]) => a.localeCompare(b))) {
  for (const [year, expected] of [...byYear].sort(([a], [b]) => a - b)) lines.push(`[${year}-01-01] ${schedule} => ${expected}`);
}
const header = [
  `# Generated by scripts/date-holidays/generate.mjs from the holiday rules of date-holidays ${VERSION}; don't edit.`,
  '# Holiday data (c) date-holidays contributors, https://github.com/commenthol/date-holidays, licensed under CC BY 3.0',
  '# (https://creativecommons.org/licenses/by/3.0/); the rules are translated into the schedule language.',
  `# Every country, state and region, ${YEARS[0]} to ${YEARS.at(-1)}, with the first of January as base date; a case is only`,
  '# written for a year in which date-holidays yields the rule, so conditions such as "since 2005" need no syntax here.',
  '# Rules disabled or moved in a year, and weekday shifts near the turn of the year, are left out for that year.',
  '',
];
const footer = ['', `# ${conflicts.size} schedule-years left out because places disagree, and the years of ${lunarSkipped.size} rules`,
  '# whose Hijri or Hebrew date differs from the calendar of the language (Umm al-Qura; Hebrew).',
  '# Rules not translated, by reason:',
  ...[...skipped].sort((a, b) => b[1] - a[1]).map(([reason, n]) => `#   ${n} ${reason}`)];
writeFileSync(OUT, [...header, ...lines, ...footer].join('\n') + '\n');
console.log(`${lines.length} cases from ${cases.size} schedules, ${places.length} places; ${[...skipped.values()].reduce((a, b) => a + b, 0)} rules skipped`);
