import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import peggy from 'peggy';
import lunar from 'lunar-javascript';

// runs the shared cases of examples/schedule.txt and examples/catalog.txt (project root)
const { Solar } = lunar;   // Chinese calendar, passed to parse()
const parser = peggy.generate(readFileSync(new URL('../schedule.pegjs', import.meta.url), 'utf8'));

const iso = ({ year, month, day }) =>
  `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
const date = (text) => {
  const [year, month, day] = text.split('-').map(Number);
  return { year, month, day };
};
const weekday = (d) => new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay() % 6 !== 0;

// weekends plus the dates the rule yields for the year before, the year and the year after
function holidayMarket(rule) {
  const years = new Map();
  const holidays = (year) => {
    if (!years.has(year)) years.set(year, new Set(rule({ base: { year, month: 1, day: 1 } }).map(iso)));
    return years.get(year);
  };
  return { isBusinessDay: (d) => weekday(d) && ![d.year - 1, d.year, d.year + 1].some((y) => holidays(y).has(iso(d))) };
}

const markets = new Map();
const events = {};
const cases = [];
const files = ['schedule.txt', 'catalog.txt'];
for (const line of files.flatMap((file) => readFileSync(new URL(`../../examples/${file}`, import.meta.url), 'utf8').split('\n'))) {
  if (line.trim() === '' || line.trim().startsWith('#')) continue;
  if (line.startsWith('market ')) { // market NAME = schedule of the holidays
    const [name, holidays] = line.slice('market '.length).split('=');
    markets.set(name.trim(), holidayMarket(parser.parse(holidays.trim(), { Solar })));
    continue;
  }
  if (line.startsWith('event ')) { // event NAME = schedule of its dates
    const [name, dates] = line.slice('event '.length).split('=');
    events[name.trim()] = parser.parse(dates.trim(), { Solar });
    continue;
  }
  const arrow = line.lastIndexOf('=>');
  const expected = line.slice(arrow + 2).trim();
  let input = line.slice(0, arrow).trim();
  let context = { events };
  const prefix = input.match(/^\[(\S+)(?: (\S+))?\] (.*)$/); // [base] or [base market]
  if (prefix) {
    context = { base: date(prefix[1]), market: prefix[2] && markets.get(prefix[2]), events };
    input = prefix[3];
  }
  cases.push({
    input,
    context,
    expected: expected === 'error' ? null : expected === 'none' ? [] : expected.split(',').map((d) => d.trim()),
  });
}

for (const { input, context, expected } of cases) {
  test(`'${input}' => ${expected ? expected.join(', ') || 'none' : 'error'}`, () => {
    if (expected === null) {
      assert.throws(() => parser.parse(input, { Solar })(context));
    } else {
      assert.deepEqual(parser.parse(input, { Solar })(context).map(iso), expected);
    }
  });
}
