// The three tools of the MCP server, as plain functions over the rule-file parser of date-rule-peg: evaluateSchedule,
// checkSyntax and ruleDates. Dates go in and out as ISO strings (YYYY-MM-DD); the rules are rule-file text the client
// passes, no rules are bundled.
import { parser, parseRules } from 'date-rule-peg/rule-files.mjs';

const iso = ({ year, month, day }) =>
  `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;

function dateOf(text) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text ?? '');
  if (!m) throw new RangeError(`not a date (YYYY-MM-DD): ${text}`);
  return { year: Number(m[1]), month: Number(m[2]), day: Number(m[3]) };
}

/** a syntax error with its position, or any other error with its message */
function failure(e) {
  if (e?.name === 'SyntaxError' && e.location) {
    const { line, column } = e.location.start;
    return { ok: false, error: 'syntax', message: e.message, line, column };
  }
  return { ok: false, error: 'value', message: e?.message ?? String(e) };
}

/** the dates of a schedule with a base date; with rules, ON and @references see their scopes and calendars */
export function evaluateSchedule({ schedule, base, rules = '', scope = null }) {
  try {
    const catalog = parseRules(rules);
    const dates = catalog.schedule(schedule)(catalog.context(dateOf(base), scope));
    return { ok: true, dates: dates.map(iso) };
  } catch (e) {
    return failure(e);
  }
}

/** whether the text parses as a schedule or as a rule file; a rule file is also checked as a whole (versions,
 *  references), which the parser alone doesn't do */
export function checkSyntax({ text, kind = 'schedule' }) {
  try {
    if (kind === 'rule_file') {
      const catalog = parseRules(text);
      return { ok: true, rules: catalog.rules.length };
    }
    parser.parse(text, { startRule: 'schedule' });
    return { ok: true };
  } catch (e) {
    return failure(e);
  }
}

/** the dates of one rule in a year, with kind and scenario; the scope defaults to the rule's first scope */
export function ruleDates({ rules, key, year, scope = null }) {
  try {
    const catalog = parseRules(rules);
    const rule = catalog.rules.find((r) => r.key === key);
    if (!rule) return { ok: false, error: 'value', message: `no rule ${key}` };
    const occurrences = catalog.holidays(scope ?? rule.include[0], year).filter((o) => o.rule === key);
    return {
      ok: true,
      scope: scope ?? rule.include[0],
      dates: occurrences.map((o) => ({ date: iso(o.date), kind: o.kind, scenario: o.scenario, projected: o.projected })),
    };
  } catch (e) {
    return failure(e);
  }
}
