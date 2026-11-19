// Rule files: one parser generated from schedule.pegjs and rulefile.pegjs together, so the rule-file grammar uses the
// helpers and rules of the schedule grammar; parseRules(text) returns a RuleCatalog (see rulefile.pegjs).
import { readFileSync } from 'node:fs';
import peggy from 'peggy';
import lunar from 'lunar-javascript';

const grammar = (file) => ({ source: file, text: readFileSync(new URL(file, import.meta.url), 'utf8') });

export const parser = peggy.generate([grammar('schedule.pegjs'), grammar('rulefile.pegjs')],
  { allowedStartRules: ['Schedule', 'ruleFile', 'schedule'] });

/**
 * the rule catalog of a rule file; announcements map a key to the dates an authority published ({ year, month, day })
 * @throws peg$SyntaxError on a syntax error; Error or RangeError on overlapping versions, an unknown or a cyclic
 *         reference, an invalid date
 */
export function parseRules(text, announcements = {}) {
  return parser.parse(text, { startRule: 'ruleFile', Solar: lunar.Solar, announcements });
}
