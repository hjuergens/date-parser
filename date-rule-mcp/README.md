# date-rule-mcp

An [MCP](https://modelcontextprotocol.io/) server for the date rule language, on the parser of
[date-rule-peg](../date-rule-peg/README.md) (Peggy, JavaScript). It speaks MCP over stdio, so an AI client can evaluate
schedules and rule files while it writes them.

## Tools

| Tool | Input | Result |
|---|---|---|
| `evaluate_schedule` | `schedule`, `base` (YYYY-MM-DD), optional `rules` (rule-file text) and `scope` | `{ ok, dates }` |
| `check_syntax` | `text`, `kind` (`schedule` or `rule_file`) | `{ ok }`, or `{ ok: false, error, message, line, column }` |
| `rule_dates` | `rules`, `key`, `year`, optional `scope` | `{ ok, scope, dates: [{ date, kind, scenario, projected }] }` |

No rules are bundled: without `rules`, only weekends are non-business days. With them, `ON country:US` and `@us.thanksgiving`
use the declarations passed. A rule file is checked as a whole as well (overlapping versions, unknown or cyclic
references), as when it is loaded.

```
evaluate_schedule  { "schedule": "T > NEAREST ON country:US", "base": "2026-11-26",
                     "rules": "rule us.thanksgiving for thanksgiving in country:US = 4 THU NOV;" }
                   => { "ok": true, "dates": ["2026-11-25"] }
```

## Run

```
cd date-rule-mcp && npm ci
```

Then register it with the client, for Claude Code for example:

```
claude mcp add date-rule -- node /path/to/date-parser/date-rule-mcp/server.mjs
```

`npm test` (or `mise run test:mcp`) starts the server and calls each tool through the SDK's MCP client.

## Status

Working, local only: the package depends on `../date-rule-peg` and is not published.
