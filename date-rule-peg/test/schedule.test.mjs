import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import peggy from 'peggy';

// runs the shared cases of examples/schedule.txt (project root)
const parser = peggy.generate(readFileSync(new URL('../schedule.pegjs', import.meta.url), 'utf8'));

const iso = ({ year, month, day }) =>
  `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;

const cases = readFileSync(new URL('../../examples/schedule.txt', import.meta.url), 'utf8')
  .split('\n')
  .filter((line) => line.trim() !== '' && !line.trim().startsWith('#'))
  .map((line) => {
    const arrow = line.lastIndexOf('=>');
    const expected = line.slice(arrow + 2).trim();
    return {
      input: line.slice(0, arrow).trim(),
      expected: expected === 'error' ? null : expected === 'none' ? [] : expected.split(',').map((d) => d.trim()),
    };
  });

for (const { input, expected } of cases) {
  test(`'${input}' => ${expected ? expected.join(', ') || 'none' : 'error'}`, () => {
    if (expected === null) {
      assert.throws(() => parser.parse(input));
    } else {
      assert.deepEqual(parser.parse(input).map(iso), expected);
    }
  });
}
