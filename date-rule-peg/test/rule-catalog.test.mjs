import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseRules } from '../rule-files.mjs';

// rule files where the result is more than dates: attributes and announcements; the dates are checked by the shared
// cases of examples/rules.txt (rule-cases.test.mjs)
const RULES = `
rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON}
  cites "Ley 51 de 1983" art "1" label official;
weekend country:IL = {FRI, SAT};
rule il.zahav for short_day in country:IL = [1 JAN : 31 DEC] IF THU
  with kind = short_day, session 09:00 - 14:00 tz "Asia/Jerusalem";
rule fed.meeting for central_bank_meeting in country:XX = 15 MAR > PRECEDING with precision = month;
weekend country:AE = {FRI, SAT};
weekend country:AE = {SAT, SUN} from 2022-01-01;
rule ae.eid for eid_al_fitr in country:AE = [1 SHAWWAL : 3 SHAWWAL];
rule sa.eid for eid_al_fitr in country:SA = 1 SHAWWAL BY SIGHTING;
`;

const date = (year, month, day) => ({ year, month, day });

test('an occurrence carries kind, session, precision and citations', () => {
  const catalog = parseRules(RULES);
  const zahav = catalog.holidays('country:IL', 2026)[0];
  assert.equal(zahav.kind, 'short_day');
  assert.deepEqual(zahav.session, ['09:00', '14:00', 'Asia/Jerusalem']);
  assert.equal(catalog.holidays('country:XX', 2026)[0].precision, 'month');
  assert.deepEqual(catalog.holidays('country:CO', 2026)[0].citations,
    [{ source: 'Ley 51 de 1983', article: '1', url: null, label: 'official' }]);
});

test('announcements and projections', () => {
  const catalog = parseRules(RULES, {
    'ae.eid': [date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)], 'sa.eid': [date(2028, 2, 27)] });
  const announced = catalog.holidays('country:AE', 2026);
  assert.deepEqual(announced.map((o) => o.date), [date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)]);
  assert.ok(announced.every((o) => !o.projected));
  const shawwal = catalog.schedule('1 SHAWWAL')(catalog.context(date(2027, 1, 1)))[0];
  const projected = catalog.holidays('country:AE', 2027);
  assert.deepEqual(projected[0].date, shawwal);
  assert.equal(projected.length, 3);
  assert.ok(projected.every((o) => o.projected));
  assert.deepEqual(catalog.holidays('country:SA', 2027).map((o) => [o.date, o.projected]), [[shawwal, true]]);
  assert.deepEqual(catalog.holidays('country:SA', 2028).map((o) => [o.date, o.projected]), [[date(2028, 2, 27), false]]);
});

test('announcements are data, and kind = event closes nothing', () => {
  const catalog = parseRules(`
rule fed.christmas for christmas_day in central_bank:FED = 25 DEC;
rule fed.fomc for fomc_decision in central_bank:FED = 3 THU MAR with kind = event;
rule fed.after for test_day in central_bank:FED = 18 MAR > FOLLOWING;
rule xetr.ex_dividend for ex_dividend in exchange:XETR = @issuer.agm > NEXT BD;
`, { 'fed.fomc': [date(2026, 3, 18)], 'issuer.agm': [date(2026, 5, 7)] });
  const got = (year) => catalog.holidays('central_bank:FED', year)
    .map((o) => `${o.rule} ${o.date.year}-${o.date.month}-${o.date.day} ${o.projected}`);
  assert.ok(got(2026).includes('fed.fomc 2026-3-18 false'));   // the announcement wins
  assert.ok(got(2027).includes('fed.fomc 2027-3-18 true'));    // else the projection, marked
  assert.ok(got(2027).includes('fed.after 2027-3-18 false'));  // the event closes nothing
  // a key without a rule holds announced dates only; a rule can refer to it
  assert.deepEqual(catalog.holidays('exchange:XETR', 2026).map((o) => o.date), [date(2026, 5, 8)]);
  assert.deepEqual(catalog.holidays('exchange:XETR', 2027), []);
});

test('ONE OF dates are projections unless announced', () => {
  const catalog = parseRules(`
rule sa.eid for eid_al_fitr in country:SA = ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 };
rule xsau.eid for eid_al_fitr in exchange:XSAU = ONE OF { 1 SHAWWAL AS uq, 1 SHAWWAL + 1D AS late };
`, { 'xsau.eid': [date(2026, 3, 22)] });
  assert.ok(catalog.holidays('country:SA', 2026).every((o) => o.projected));
  assert.deepEqual(catalog.holidays('exchange:XSAU', 2026).map((o) => [o.date, o.scenario]), [[date(2026, 3, 22), null]]);
});

test('a single session time and the new kinds', () => {
  const catalog = parseRules(`
rule rba.decision for rba_decision in central_bank:RBA = 3 TUE MAR with kind = event, session 14:30 tz "Australia/Sydney";
rule xnse.muhurat for muhurat_session in exchange:XNSE = 1 NOV with kind = special_session;
rule dsmd.ramadan for ramadan_session in exchange:DSMD = 1 MAR with kind = changed_hours, session 09:30 - 13:15;
`);
  const rba = catalog.holidays('central_bank:RBA', 2026);
  assert.equal(rba.length, 1);
  assert.deepEqual(rba[0].session, ['14:30', '14:30', 'Australia/Sydney']);
  assert.equal(catalog.holidays('exchange:XNSE', 2026)[0].kind, 'special_session');
  assert.equal(catalog.holidays('exchange:DSMD', 2026)[0].kind, 'changed_hours');
});
