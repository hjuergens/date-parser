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

function evaluate(declarations, text) {
  const catalog = parseRules(declarations);
  const schedule = text.match(/^\[(\d{4})-(\d{2})-(\d{2})\] (.*)$/);   // [base] schedule
  if (schedule) {
    const base = { year: Number(schedule[1]), month: Number(schedule[2]), day: Number(schedule[3]) };
    return catalog.schedule(schedule[4])(catalog.context(base)).map(iso);
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
