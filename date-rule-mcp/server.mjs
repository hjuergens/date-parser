#!/usr/bin/env node
// MCP server (stdio) for the date rule language: evaluate_schedule, check_syntax and rule_dates (tools.mjs).
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import { checkSyntax, evaluateSchedule, ruleDates } from './tools.mjs';

const server = new McpServer({ name: 'date-rule-mcp', version: '0.0.1' });

const reply = (result) => ({
  content: [{ type: 'text', text: JSON.stringify(result) }],
  isError: !result.ok,
});

const rulesField = z.string().describe('rule-file text: rule, calendar and weekend declarations, each ending with ";"');

server.registerTool('evaluate_schedule', {
  title: 'Evaluate a schedule',
  description: 'The dates of a schedule of the date rule language, e.g. "T + 3M > IMM", "T > SPOT EURUSD", '
    + '"[T : 1M : T + 1Y] > MODFOLLOWING ON country:US", evaluated with a base date T. Without rules only weekends are '
    + 'non-business days; with rules, ON <scope> and @<rule key> use them.',
  inputSchema: {
    schedule: z.string().describe('the schedule text'),
    base: z.string().describe('the base date T, YYYY-MM-DD'),
    rules: rulesField.optional(),
    scope: z.string().optional().describe('the default calendar, e.g. country:US (from the rules)'),
  },
}, async (args) => reply(evaluateSchedule(args)));

server.registerTool('check_syntax', {
  title: 'Check syntax',
  description: 'Whether a text parses as a schedule or as a rule file; on an error the message with line and column. '
    + 'A rule file is also checked as a whole: overlapping versions, unknown or cyclic references.',
  inputSchema: {
    text: z.string().describe('the text to check'),
    kind: z.enum(['schedule', 'rule_file']).default('schedule'),
  },
}, async (args) => reply(checkSyntax(args)));

server.registerTool('rule_dates', {
  title: 'Dates of a rule',
  description: 'The dates of one rule of a rule file in a year, with its kind (closed, short_day, event, ...) and the '
    + 'scenario of ONE OF alternatives.',
  inputSchema: {
    rules: rulesField,
    key: z.string().describe('the rule key, e.g. us.thanksgiving'),
    year: z.number().int().describe('the year'),
    scope: z.string().optional().describe('the scope to evaluate in; defaults to the rule\'s first scope'),
  },
}, async (args) => reply(ruleDates(args)));

await server.connect(new StdioServerTransport());
