// Builds dist/server.mjs, one file for npm with no runtime dependencies: the rule-file parser is generated ahead of time
// from the grammars of date-rule-peg (instead of at start-up, as rule-files.mjs does), and esbuild bundles it with the
// server, the MCP SDK, zod and lunar-javascript.
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import peggy from 'peggy';
import { build } from 'esbuild';

const grammar = (file) => {
  const url = new URL(`../date-rule-peg/${file}`, import.meta.url);
  return { source: file, text: readFileSync(url, 'utf8') };
};

mkdirSync(new URL('build/', import.meta.url), { recursive: true });
writeFileSync(new URL('build/rulefile-parser.mjs', import.meta.url), peggy.generate(
  [grammar('schedule.pegjs'), grammar('rulefile.pegjs')],
  { allowedStartRules: ['Schedule', 'ruleFile', 'schedule'], output: 'source', format: 'es' }));

// the same exports as date-rule-peg/rule-files.mjs, on the generated parser
writeFileSync(new URL('build/rule-files.mjs', import.meta.url), `import * as parser from './rulefile-parser.mjs';
import lunar from 'lunar-javascript';
export { parser };
export function parseRules(text, announcements = {}) {
  return parser.parse(text, { startRule: 'ruleFile', Solar: lunar.Solar, announcements });
}
`);

await build({
  entryPoints: [new URL('server.mjs', import.meta.url).pathname],
  outfile: new URL('dist/server.mjs', import.meta.url).pathname,
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node20',
  alias: { 'date-rule-peg/rule-files.mjs': new URL('build/rule-files.mjs', import.meta.url).pathname },
  // the SDK and zod are ES modules that may require() node built-ins
  banner: { js: "import { createRequire } from 'node:module'; const require = createRequire(import.meta.url);" },
  legalComments: 'inline',
  logLevel: 'warning',
});

// npm takes the license file from the package directory
copyFileSync(new URL('../LICENSE.txt', import.meta.url), new URL('LICENSE.txt', import.meta.url));
