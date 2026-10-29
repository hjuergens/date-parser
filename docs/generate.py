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
         snippet=lambda f: between(f, r"^// periods are applied", r"^period returns"),
         values="Actions with <code>returns […]</code> attributes",
         errors="Throwing error listener plus <code>BailErrorStrategy</code>",
         pitfall="<code>$start</code> is reserved (the rule's first token), so the range labels are <code>first</code>/<code>last</code>."),
    dict(id="combinators", name="Scala combinators", kind="Recursive descent with backtracking", lang="Scala 2.13",
         file="date-rule-combinators/src/main/scala/de/juergens/text/ScheduleParsers.scala",
         snippet=lambda f: between(f, r"def date:", r"def period:") + "\n\n" + between(f, r"def expr:", r"^  }$", True),
         values="<code>^^</code> functions, <code>foldLeft</code> over the shifts",
         errors="<code>NoSuccess</code> result, turned into an exception",
         pitfall="None: the grammar is ordinary Scala code, there is no generator step."),
    dict(id="coco", name="Coco/R", kind="LL(1)", lang="Java",
         file="date-rule-coco/src/main/coco/Schedule.atg",
         snippet=lambda f: between(f, r"Expr<out LocalDate d>", r"Period<out Period p>"),
         values="<code>(. … .)</code> actions with <code>out</code> parameters",
         errors="<code>errors.count</code>, messages written to a stream",
         pitfall="Error recovery keeps running semantic actions on incomplete input, so they must not throw."),
    dict(id="javacc", name="JavaCC", kind="LL(k), here LL(1)", lang="Java",
         file="date-rule-javacc/src/main/javacc/Schedule.jj",
         snippet=lambda f: between(f, r"^LocalDate Expr", r"^Period Period"),
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
         snippet=lambda f: between(f, r"^/\* periods are applied", r"^period ::="),
         values="<code>{: … :}</code> actions, left-recursive rules",
         errors="<code>parse()</code> throws on the first syntax error",
         pitfall="In the JFlex spec, <code>%cupsym</code> must come before <code>%cup</code>, or the EOF action refers to <code>sym</code>."),
    dict(id="bison", name="GNU Bison", kind="LALR(1)", lang="Java skeleton",
         file="date-rule-bison/src/main/resources/schedule.y",
         snippet=lambda f: between(f, r"^/\* periods are applied", r"^period:"),
         values="<code>{ … }</code> actions, left-recursive rules",
         errors="<code>parse()</code> returns <code>false</code>",
         pitfall="In Bison's Java skeleton <code>$$</code> is a plain <code>Object</code>; only <code>$1</code>…<code>$n</code> are typed."),
    dict(id="peg", name="Peggy", kind="PEG · ordered choice", lang="JavaScript",
         file="date-rule-peg/schedule.pegjs",
         snippet=lambda f: between(f, r"^// periods are applied", r"^Period$"),
         values="Action blocks; date arithmetic written by hand",
         errors="<code>SyntaxError</code> with expected tokens",
         pitfall="Peggy provides <code>range()</code> in actions, which silently shadows a helper of the same name."),
]

LINES = {"antlr": "42", "combinators": "52", "coco": "62", "javacc": "74", "sablecc": "38 + 65",
         "cup": "36 + 35", "bison": "102", "peg": "80"}
LINE_NOTES = {"sablecc": "grammar + evaluator", "cup": "parser + lexer", "bison": "incl. hand-written scanner",
              "peg": "incl. date arithmetic"}

# shared test cases, grouped by the comment headers of schedule.txt
groups = []
previous = ""
for line in (ROOT / "examples/schedule.txt").read_text(encoding="utf-8").splitlines():
    if line.strip() and not line.startswith("#"):
        if previous.startswith("#"):
            groups.append((previous[1:].strip(), []))
        arrow = line.rindex("=>")
        groups[-1][1].append((line[:arrow].strip(), line[arrow + 2:].strip()))
    previous = line
groups = [(t, c) for t, c in groups if c]
case_count = sum(len(c) for _, c in groups)


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
    f'      <section>\n      <h3>{html.escape(title)}</h3>\n      <table class="cases">\n        <tbody>\n' + "\n".join(
        f'          <tr><td><code>{html.escape(i) if i else "<em>(empty input)</em>"}</code></td><td>{result(e)}</td></tr>'
        for i, e in items) + "\n        </tbody>\n      </table>\n      </section>"
    for title, items in groups)

page = (Path(__file__).parent / "page_template.html").read_text(encoding="utf-8")
page = (page.replace("{{TABS}}", tabs).replace("{{PANELS}}", panels).replace("{{ROWS}}", rows)
        .replace("{{CASES}}", cases).replace("{{CASE_COUNT}}", str(case_count)).replace("{{REPO}}", REPO))
(ROOT / "docs").mkdir(exist_ok=True)
(ROOT / "docs/index.html").write_text(page, encoding="utf-8")
print("cases:", case_count, "groups:", [t for t, _ in groups])
