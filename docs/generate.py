"""Generates docs/index.html from the grammars and examples/schedule.txt of the repository.

Run from anywhere after changing a grammar or the shared test cases:  python3 docs/generate.py
"""
import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = "https://github.com/hjuergens/date-parser/blob/master/"


def between(path, start, end, include_end=False):
    """Lines from the first line matching start up to (excluding) the first later line matching end."""
    lines = (ROOT / path).read_text(encoding="utf-8").splitlines()
    i = next(n for n, l in enumerate(lines) if re.search(start, l))
    j = next(n for n in range(i + 1, len(lines)) if re.search(end, lines[n]))
    block = lines[i:j + 1 if include_end else j]
    while block and not block[-1].strip():
        block.pop()
    text = "\n".join(block).expandtabs(4)
    indent = min(len(l) - len(l.lstrip()) for l in text.splitlines() if l.strip())
    return "\n".join(l[indent:] for l in text.splitlines())


def code(text):
    """Escape and mark comments for subtle coloring."""
    out = []
    for line in html.escape(text).splitlines():
        line = re.sub(r"(//.*|/\*.*?\*/|\(\* .*? \*\))$", r'<span class="c">\1</span>', line)
        out.append(line)
    return "\n".join(out)


TOOLS = [
    dict(id="antlr", name="ANTLR 4", kind="ALL(*) · adaptive LL", lang="Java",
         file="date-rule-antlr/src/main/antlr4/io/github/hjuergens/time/DateSchedule.g4",
         snippet=lambda f: between(f, r"^// periods are applied", r"^// \{SAT"),
         values="Actions with <code>returns […]</code> attributes",
         errors="Throwing error listener plus <code>BailErrorStrategy</code>",
         pitfall="<code>$start</code> is reserved (the rule's first token), so the range labels are <code>first</code>/<code>last</code>."),
    dict(id="antlr-python", name="ANTLR 4 · Python", kind="ALL(*) · adaptive LL", lang="Python",
         file="date-rule-antlr-python/src/antlr_schedule/__init__.py",
         snippet=lambda f: between(f, r"# shifts are applied", r"def visitNext"),
         values="Visitor over the parse tree; the grammar has no actions",
         errors="Error listener on lexer and parser that raises",
         pitfall="Java actions can't be generated into Python, so the grammar is copied without them. The Python target also renames the rule <code>range</code> to <code>range_()</code>."),
    dict(id="combinators", name="Scala combinators", kind="Recursive descent with backtracking", lang="Scala 2.13",
         file="date-rule-combinators/src/main/scala/de/juergens/text/ScheduleParsers.scala",
         snippet=lambda f: between(f, r"def date:", r"def period:") + "\n\n" + between(f, r"def expr:", r"^  }$", True),
         values="<code>^^</code> functions, <code>foldLeft</code> over the shifts",
         errors="<code>NoSuccess</code> result, turned into an exception",
         pitfall="None: the grammar is ordinary Scala code, there is no generator step."),
    dict(id="coco", name="Coco/R", kind="LL(1)", lang="Java",
         file="date-rule-coco/src/main/coco/Schedule.atg",
         snippet=lambda f: between(f, r"Expr<out ScheduleSemantics.Expr d>", r"Spot<out String code>"),
         values="<code>(. … .)</code> actions with <code>out</code> parameters",
         errors="<code>errors.count</code>, messages written to a stream",
         pitfall="Error recovery keeps running semantic actions on incomplete input, so they must not throw."),
    dict(id="javacc", name="JavaCC", kind="LL(k), here LL(1)", lang="Java",
         file="date-rule-javacc/src/main/javacc/Schedule.jj",
         snippet=lambda f: between(f, r"^ScheduleSemantics.Expr Expr", r"^String Spot"),
         values="Java code inside the productions; each declares its locals",
         errors="<code>ParseException</code>, <code>TokenMgrException</code>",
         pitfall="ParserGeneratorCC (used by the Maven plugin) rejects the classic <code>STATIC</code> option."),
    dict(id="sablecc", name="SableCC", kind="LALR(1) with typed AST", lang="Java",
         file="date-rule-sablecc/src/main/sablecc/schedule.grammar",
         snippet=lambda f: between(f, r"expr = date", r"period = ", True),
         values="Separate evaluator walking the generated typed tree",
         errors="<code>ParserException</code>, <code>LexerException</code>",
         pitfall="Elements that occur twice in one alternative need names: <code>[year]:number</code>, <code>[s1]:minus</code>."),
    dict(id="cup", name="JFlex + CUP", kind="LALR(1)", lang="Java",
         file="date-rule-cup-jflex/src/main/cup/schedule.cup",
         snippet=lambda f: between(f, r"^/\* periods are applied", r"^shifts ::="),
         values="<code>{: … :}</code> actions, left-recursive rules",
         errors="<code>parse()</code> throws on the first syntax error",
         pitfall="In the JFlex spec, <code>%cupsym</code> must come before <code>%cup</code>, or the EOF action refers to <code>sym</code>."),
    dict(id="bison", name="GNU Bison", kind="LALR(1)", lang="Java skeleton",
         file="date-rule-bison/src/main/resources/schedule.y",
         snippet=lambda f: between(f, r"^/\* periods are applied", r"^shifts:"),
         values="<code>{ … }</code> actions, left-recursive rules",
         errors="<code>parse()</code> returns <code>false</code>",
         pitfall="In Bison's Java skeleton <code>$$</code> is a plain <code>Object</code>; only <code>$1</code>…<code>$n</code> are typed."),
    dict(id="peg", name="Peggy", kind="PEG · ordered choice", lang="JavaScript",
         file="date-rule-peg/schedule.pegjs",
         snippet=lambda f: between(f, r"^// periods are applied", r"^Observe$"),
         values="Action blocks; date arithmetic written by hand",
         errors="<code>SyntaxError</code> with expected tokens",
         pitfall="Peggy provides <code>range()</code> in actions, which silently shadows a helper of the same name."),
    dict(id="lark", name="Lark", kind="Earley and LALR(1), one grammar", lang="Python",
         file="date-rule-lark/src/lark_schedule/schedule.lark",
         snippet=lambda f: between(f, r"^// shifts are applied", r"^NUM:"),
         values="<code>Transformer</code>, one method per rule alias",
         errors="<code>UnexpectedInput</code>; <code>VisitError</code> wraps exceptions of the transformer",
         pitfall="In LALR mode the contextual lexer only tries tokens the parser can accept next, so <code>&gt;SPOTUSDCAD</code> parses; ANTLR's lexer reads <code>SPOTUS</code> as a pair and rejects it."),
]

LINES = {"antlr": "99", "combinators": "215", "coco": "132", "javacc": "143", "sablecc": "88 + 131",
         "cup": "75 + 59", "bison": "198", "peg": "275", "lark": "47 + 89", "antlr-python": "67 + 104"}
LINE_NOTES = {"sablecc": "grammar + evaluator", "cup": "parser + lexer", "bison": "incl. hand-written scanner",
              "peg": "incl. date arithmetic", "lark": "grammar + transformer", "antlr-python": "grammar + visitor"}

# shared test cases, grouped by the comment headers of schedule.txt
groups = []   # (title, market declarations, cases)
previous = ""
for line in (ROOT / "examples/schedule.txt").read_text(encoding="utf-8").splitlines():
    if line.strip() and not line.startswith("#"):
        if previous.startswith("#"):
            groups.append((previous[1:].strip(), [], []))
        if line.startswith(("market ", "event ")):
            groups[-1][1].append(line)
        else:
            arrow = line.rindex("=>")
            groups[-1][2].append((line[:arrow].strip(), line[arrow + 2:].strip()))
    previous = line
groups = [g for g in groups if g[2]]
case_count = sum(len(c) for _, _, c in groups)


def case_input(text):
    """the input, with the test context prefix [base market] set apart"""
    if not text:
        return "<em>(empty input)</em>"
    match = re.fullmatch(r"(\[[^]]*\]) (.*)", text)
    if match:
        return f'<span class="ctx">{html.escape(match[1])}</span> {html.escape(match[2])}'
    return html.escape(text)


def result(expected):
    if expected == "error":
        return '<span class="err">error</span>'
    if expected == "none":
        return '<span class="none">empty list</span>'
    return ", ".join(f"<span class=\"date\">{html.escape(d.strip())}</span>" for d in expected.split(","))


tabs = "\n".join(
    f'      <button role="tab" id="tab-{t["id"]}" aria-controls="panel-{t["id"]}" aria-selected="{"true" if i == 0 else "false"}" tabindex="{0 if i == 0 else -1}">{t["name"]}</button>'
    for i, t in enumerate(TOOLS))

panels = "\n".join(f'''    <div role="tabpanel" id="panel-{t["id"]}" aria-labelledby="tab-{t["id"]}"{"" if i == 0 else " hidden"}>
      <div class="panel-head">
        <span class="kind">{t["kind"]}</span><span class="lang">{t["lang"]}</span>
        <a href="{REPO}{t["file"]}">{html.escape(Path(t["file"]).name)} ↗</a>
      </div>
      <pre><code>{code(t["snippet"](t["file"]))}</code></pre>
      <p class="pitfall"><strong>Pitfall.</strong> {t["pitfall"]}</p>
    </div>''' for i, t in enumerate(TOOLS))

rows = "\n".join(f'''        <tr>
          <th scope="row"><a href="{REPO}{t["file"]}">{t["name"]}</a></th>
          <td>{t["kind"]}</td>
          <td class="num">{LINES[t["id"]]}{f'<small>{LINE_NOTES[t["id"]]}</small>' if t["id"] in LINE_NOTES else ""}</td>
          <td>{t["values"]}</td>
          <td>{t["errors"]}</td>
        </tr>''' for t in TOOLS)

cases = "\n".join(
    f'      <section>\n      <h3>{html.escape(title)}</h3>\n'
    + "".join(f'      <p class="market"><code>{html.escape(m)}</code></p>\n' for m in markets)
    + '      <table class="cases">\n        <tbody>\n' + "\n".join(
        f'          <tr><td><code>{case_input(i)}</code></td><td>{result(e)}</td></tr>'
        for i, e in items) + "\n        </tbody>\n      </table>\n      </section>"
    for title, markets, items in groups)

page = (Path(__file__).parent / "page_template.html").read_text(encoding="utf-8")
page = (page.replace("{{TABS}}", tabs).replace("{{PANELS}}", panels).replace("{{ROWS}}", rows)
        .replace("{{CASES}}", cases).replace("{{CASE_COUNT}}", str(case_count))
        .replace("{{TOOL_COUNT}}", str(len(TOOLS))).replace("{{LANG_COUNT}}", str(len({t["lang"].split()[0] for t in TOOLS}))).replace("{{REPO}}", REPO))
(ROOT / "docs").mkdir(exist_ok=True)
(ROOT / "docs/index.html").write_text(page, encoding="utf-8")
print("cases:", case_count, "groups:", [t for t, _, _ in groups])
