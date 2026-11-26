// Starts the server over stdio with the MCP client of the SDK and calls each tool once.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const RULES = 'rule us.thanksgiving for thanksgiving in country:US = 4 THU NOV;\n'
  + 'rule us.memorial_day for memorial_day in country:US = LAST MON MAY;';

test('the three tools over stdio', async () => {
  const client = new Client({ name: 'test', version: '0.0.1' });
  await client.connect(new StdioClientTransport({ command: process.execPath,
    args: [new URL('../server.mjs', import.meta.url).pathname] }));
  try {
    const { tools } = await client.listTools();
    assert.deepEqual(tools.map((t) => t.name).sort(), ['check_syntax', 'evaluate_schedule', 'rule_dates']);

    const call = async (name, args) => JSON.parse((await client.callTool({ name, arguments: args })).content[0].text);

    assert.deepEqual(await call('evaluate_schedule', { schedule: 'T + 3M > IMM', base: '2015-06-09' }),
      { ok: true, dates: ['2015-09-16'] });
    assert.deepEqual(await call('evaluate_schedule', { schedule: 'T > NEAREST ON country:US', base: '2026-11-26', rules: RULES }),
      { ok: true, dates: ['2026-11-25'] });
    assert.deepEqual(await call('check_syntax', { text: '2015-06-09 + 3X' }),
      { ok: false, error: 'syntax', message: 'Expected [DWMQHYBF] but "X" found.', line: 1, column: 15 });
    assert.deepEqual(await call('check_syntax', { text: RULES, kind: 'rule_file' }), { ok: true, rules: 2 });
    assert.deepEqual(await call('rule_dates', { rules: RULES, key: 'us.memorial_day', year: 2027 }),
      { ok: true, scope: 'country:US', dates: [{ date: '2027-05-31', kind: 'closed', scenario: null, projected: false }] });
    assert.equal((await call('rule_dates', { rules: RULES, key: 'us.nope', year: 2027 })).ok, false);
  } finally {
    await client.close();
  }
});
