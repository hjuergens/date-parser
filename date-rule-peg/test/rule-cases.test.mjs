import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parseRules } from '../rule-files.mjs';

// runs the shared cases of examples/rules.txt (project root): blocks of declarations, each followed by its cases;
// declarations after cases start a new block
const iso = ({ year, month, day }) =>
  `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;

const cases = [];
let declarations = [], afterCases = false;
for (const line of readFileSync(new URL('../../examples/rules.txt', import.meta.url), 'utf8').split('\n')) {
  if (line.trim() === '' || line.startsWith('#')) continue;
  const arrow = line.lastIndexOf('=>');
  if (arrow >= 0) {
    cases.push({ text: line.slice(0, arrow).trim(), expected: line.slice(arrow + 2).trim(), declarations: declarations.join('\n') });
    afterCases = true;
    continue;
  }
  if (afterCases) {
    declarations = [];
    afterCases = false;
  }
  declarations.push(line);
}

// the instant of a local date and time (ms since the epoch as if at UTC) at a fixed offset (UTC-4) or in an IANA zone:
// the offset of the zone at a guess, twice
function instantOf(local, zone) {
  if (zone.startsWith('UTC')) return new Date(local - parseInt(zone.slice(3), 10) * 3600000);
  const format = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: 'numeric', day: 'numeric',
    hour: 'numeric', minute: 'numeric', second: 'numeric', hourCycle: 'h23' });
  const offset = (t) => {
    const p = Object.fromEntries(format.formatToParts(new Date(t)).map((part) => [part.type, Number(part.value)]));
    return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - t;
  };
  let t = local - offset(local);
  t = local - offset(t);
  return new Date(t);
}

function evaluate(declarations, text) {
  const catalog = parseRules(declarations);
  // [base] schedule or [base hh:mm zone] schedule
  const schedule = text.match(/^\[(\d{4})-(\d{2})-(\d{2})(?: (\d{2}):(\d{2}) ("[^"]+"|UTC[+-]\d+))?\] (.*)$/);
  if (schedule) {
    const [year, month, day] = [Number(schedule[1]), Number(schedule[2]), Number(schedule[3])];
    const local = Date.UTC(year, month - 1, day, Number(schedule[4]), Number(schedule[5]));
    const instant = schedule[4] ? instantOf(local, schedule[6].replaceAll('"', '')) : null;
    return catalog.schedule(schedule[7])(catalog.context({ year, month, day }, null, instant)).map(iso);
  }
  const [, scope, year] = text.match(/^(\S+) (\d{4})$/);   // scope year
  return catalog.holidays(scope, Number(year)).map((o) => iso(o.date) + (o.scenario ? ` ${o.scenario}` : ''));
}

function result(declarations, text) {
  try {
    return evaluate(declarations, text).join(', ') || 'none';
  } catch {
    return 'error';
  }
}

cases.forEach(({ text, expected, declarations }, index) => {
  test(`${index}: '${text}' => ${expected}`, () => {
    assert.equal(result(declarations, text), expected);
  });
});
