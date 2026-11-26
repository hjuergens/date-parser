# date-rule-mcp

An [MCP](https://modelcontextprotocol.io/) server for the date rule language, on the parser of
[date-rule-peg](https://github.com/hjuergens/date-parser/tree/main/date-rule-peg) (Peggy, JavaScript). It speaks MCP over stdio, so an AI client can evaluate
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

From npm, for Claude Code for example:

```
claude mcp add date-rule -- npx -y date-rule-mcp
```

Other clients take the same command (`npx -y date-rule-mcp`) in their MCP server configuration. Node 20 or later.

From the repository: `cd date-rule-mcp && npm ci`, then `node /path/to/date-parser/date-rule-mcp/server.mjs` as the
command.

## Build

`npm run build` writes `dist/server.mjs`, the file published to npm: the rule-file parser generated ahead of time from
the grammars of date-rule-peg, bundled with esbuild together with the MCP SDK, zod and lunar-javascript, so the package
has no runtime dependencies. `npm pack` and `npm publish` build it first. Building needs Node 22 (Peggy).

`npm test` (or `mise run test:mcp`) builds, then starts both the source and the bundle and calls each tool through the
SDK's MCP client.
