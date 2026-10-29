import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import peggy from 'peggy';

// runs the shared cases of examples/schedule.txt (project root)
const parser = peggy.generate(readFileSync(new URL('../schedule.pegjs', import.meta.url), 'utf8'));

const iso = ({ year, month, day }) =>
  `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
const date = (text) => {
  const [year, month, day] = text.split('-').map(Number);
  return { year, month, day };
};
const weekday = (d) => new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay() % 6 !== 0;

const markets = new Map();
const cases = [];
for (const line of readFileSync(new URL('../../examples/schedule.txt', import.meta.url), 'utf8').split('\n')) {
  if (line.trim() === '' || line.trim().startsWith('#')) continue;
  if (line.startsWith('market ')) { // market NAME = holiday, holiday, ...
    const [name, holidays] = line.slice('market '.length).split('=');
    const days = new Set(holidays.split(',').map((d) => d.trim()));
    markets.set(name.trim(), { isBusinessDay: (d) => weekday(d) && !days.has(iso(d)) });
    continue;
  }
  const arrow = line.lastIndexOf('=>');
  const expected = line.slice(arrow + 2).trim();
  let input = line.slice(0, arrow).trim();
  let context = {};
  const prefix = input.match(/^\[(\S+)(?: (\S+))?\] (.*)$/); // [base] or [base market]
  if (prefix) {
    context = { base: date(prefix[1]), market: prefix[2] && markets.get(prefix[2]) };
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
      assert.throws(() => parser.parse(input)(context));
    } else {
      assert.deepEqual(parser.parse(input)(context).map(iso), expected);
    }
  });
}
