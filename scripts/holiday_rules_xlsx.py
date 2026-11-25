"""Writes holidays-events/holiday-rules.xlsx: the holiday, central-bank and fixing-calendar rules of holidays-events.

Sheets: Holidays (closing days and early closes), Central banks (all rules of central-bank scopes) and Fixing (the
calendars of SOFR, €STR, SONIA, SARON, TONA, CORRA, PTAX and the BRL NDF, and the LPR). The key is the series key of
market-calendar, {event}:{scope}; the occurrence shape of each event is read from its docs/event-key.md when given.

    uv run --with openpyxl python scripts/holiday_rules_xlsx.py [rules dir] [output] [event-key.md]
"""
import re
import sys
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font

ROOT = Path(__file__).resolve().parent.parent
args = sys.argv[1:]
src = Path(args[0]) if len(args) > 0 else ROOT / "holidays-events"
out = Path(args[1]) if len(args) > 1 else src / "holiday-rules.xlsx"
event_key = Path(args[2]) if len(args) > 2 else ROOT.parent / "market-calendar" / "docs" / "event-key.md"


def declarations(text):
    """yields (comment lines, declaration text) for each rule"""
    lines = text.splitlines()
    i, comments = 0, []
    while i < len(lines):
        line = lines[i]
        if line.startswith("#"):
            body = line[1:].strip()
            if body.startswith("==="):
                comments = []
            else:
                comments.append(body)
            i += 1
            continue
        if not line.strip():
            comments = []
            i += 1
            continue
        if line.startswith("rule "):
            buf, quoted = [], False
            done = False
            while not done:
                for ch in lines[i]:
                    if ch == '"':
                        quoted = not quoted
                    buf.append(ch)
                    if ch == ";" and not quoted:
                        done = True
                        break
                buf.append("\n")
                i += 1
            yield [c for c in comments if c], "".join(buf).strip()
            comments = []
            continue
        comments = []
        i += 1


HEAD = re.compile(r"rule\s+(\S+)\s+for\s+(\S+)\s+in\s+(\S+)((?:\s+valid\s+(?:from\s+\S+)?\s*(?:to\s+\S+)?)?)\s*=\s*(.*?)\s+with\s+(.*);$", re.S)

# occurrence shape per event, from the "Shape per event" table of market-calendar's docs/event-key.md
SHAPES = {}
for line in event_key.read_text(encoding="utf-8").splitlines() if event_key.exists() else []:
    m = re.match(r"\| `(year|hijri_year|date|first_seen)` \| \d+ \| (.*) \|$", line)
    if m:
        SHAPES.update({e: m.group(1) for e in re.findall(r"`([a-z0-9_.]+)`", m.group(2))})

rows = []
for f in sorted(src.glob("*.rules")):
    for comments, decl in declarations(f.read_text(encoding="utf-8")):
        m = HEAD.match(decl)
        if not m:
            sys.exit(f"{f.name}: cannot read {decl[:80]}")
        rule_key, event, scope, valid, expr, attrs = m.groups()
        stype, code = scope.split(":", 1)
        kind = re.search(r"kind\s*=\s*(\w+)", attrs).group(1)
        note = re.search(r'note\s+"([^"]*)"', attrs)
        desc = " ".join(comments)
        if note:
            desc = f"{desc} Note: {note.group(1)}".strip()
        rows.append({
            "Key": f"{event}:{code}", "Occurrence": SHAPES.get(event, ""), "Rule": " ".join(expr.split()),
            "Description": desc, "Scope type": stype, "Kind": kind, "Valid": " ".join(valid.split()),
            "Rule key": rule_key,
        })

COLUMNS = {"Key": 40, "Occurrence": 12, "Rule": 60, "Description": 100, "Scope type": 18, "Kind": 11, "Valid": 30, "Rule key": 34}
wb = Workbook()
wb.remove(wb.active)
sheets = {
    "Holidays": [r for r in rows if r["Kind"] in ("closed", "short_day")],
    "Central banks": [r for r in rows if r["Scope type"] == "central_bank"],
}
for name, selected in sheets.items():
    ws = wb.create_sheet(name)
    ws.append(list(COLUMNS))
    for cell in ws[1]:
        cell.font = Font(bold=True)
    for r in sorted(selected, key=lambda r: (r["Scope type"], r["Key"].split(":")[1], r["Key"], r["Valid"])):
        ws.append([r[c] for c in COLUMNS])
    for idx, (col, width) in enumerate(COLUMNS.items(), 1):
        letter = ws.cell(1, idx).column_letter
        ws.column_dimensions[letter].width = width
        for cell in ws[letter][1:]:
            cell.alignment = Alignment(wrap_text=col in ("Rule", "Description"), vertical="top")
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = ws.dimensions
    print(name, len(selected))

# fixing calendars: examples/rules.txt (RFR publication, NDF) and br.rules (PTAX); scope -> rows
FIXINGS = [
    ("SOFR", "segment:US_BOND", ["segment:US_BOND"]),
    ("€STR", "settlement_system:TARGET2", ["settlement_system:TARGET2"]),
    ("SONIA", "country:GB", ["country:GB"]),
    ("SARON", "settlement_system:SIC", ["settlement_system:SIC"]),
    ("TONA", "settlement_system:BOJNET", ["settlement_system:BOJNET"]),
    ("CORRA", "settlement_system:LYNX", ["settlement_system:LYNX"]),
    ("PTAX (USD/BRL)", "central_bank:BCB", ["central_bank:BCB"]),
    ("BRL NDF valuation", "country:BR & central_bank:FED, preceding business day, postponed at most 14 days",
     ["country:BR", "central_bank:FED"]),
    ("LPR (China)", "event", ["central_bank:PBOC"]),
]
FIX_COLUMNS = {"Fixing": 20, "Calendar": 40, **COLUMNS}
ws = wb.create_sheet("Fixing")
ws.append(list(FIX_COLUMNS))
for cell in ws[1]:
    cell.font = Font(bold=True)
count = 0
for fixing, calendar, scopes in FIXINGS:
    for scope in scopes:
        stype, code = scope.split(":")
        if scope == "country:BR":
            # derived: the PTAX days (br.rules) are the federal holidays plus Carnival Monday and Tuesday and
            # Corpus Christi, so the federal holidays are the PTAX rules without those three
            for r in sorted((r for r in rows if r["Key"].endswith(":BCB") and r["Kind"] == "closed"
                             and r["Key"].split(":")[0] not in ("carnival_monday", "carnival_tuesday", "corpus_christi")),
                            key=lambda r: r["Key"]):
                d = dict(r, Key=r["Key"].replace(":BCB", ":BR"), **{"Scope type": "country"})
                d["Description"] = f"Derived from the PTAX rule {r['Rule key']} (a federal holiday). {r['Description']}"
                ws.append([fixing, calendar] + [d[c] for c in COLUMNS])
                count += 1
            continue
        if not any(r["Scope type"] == stype and r["Key"].endswith(":" + code) for r in rows):
            ws.append([fixing, calendar, "", "", "", f"No rules for {scope} in holidays-events yet.", stype])
            count += 1
        for r in sorted((r for r in rows if r["Scope type"] == stype and r["Key"].endswith(":" + code)
                         and (calendar == "event" or r["Kind"] == "closed")),
                        key=lambda r: (r["Key"], r["Valid"])):
            ws.append([fixing, calendar] + [r[c] for c in COLUMNS])
            count += 1
for idx, (col, width) in enumerate(FIX_COLUMNS.items(), 1):
    letter = ws.cell(1, idx).column_letter
    ws.column_dimensions[letter].width = width
    for cell in ws[letter][1:]:
        cell.alignment = Alignment(wrap_text=col in ("Rule", "Description", "Calendar"), vertical="top")
ws.freeze_panes = "C2"
ws.auto_filter.ref = ws.dimensions
print("Fixing", count)
wb.save(out)
print("declarations", len(rows), "keys", len({r["Key"] for r in rows}))
print("no shape:", sorted({r["Key"] for r in rows if not r["Occurrence"]}))
print("no description:", [r["Key"] for r in rows if not r["Description"]])
