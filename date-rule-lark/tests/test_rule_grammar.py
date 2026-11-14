"""Checks rules.lark, the grammar of rule files: LALR(1) without conflicts, the declarations of each extension, and
the same verdict as schedule.lark on every case of the three example files."""
import re
from pathlib import Path

import pytest
from lark import Lark
from lark.exceptions import UnexpectedInput

SOURCES = Path(__file__).resolve().parents[1] / "src" / "lark_schedule"
EXAMPLES = Path(__file__).resolve().parents[2] / "examples"
PREFIX = re.compile(r"\[\d{4}-\d{2}-\d{2}(?: [A-Z0-9_]+){0,2}\] (.*)")  # [base], [base market] or [base market fixing]

# Lark raises GrammarError on a conflict, so building the parser is the LALR(1) check
RULES = Lark((SOURCES / "rules.lark").read_text(encoding="utf-8"), parser="lalr", start=["rule_file", "exprs"])
SCHEDULE = Lark((SOURCES / "schedule.lark").read_text(encoding="utf-8"), parser="lalr")

DECLARATIONS = [
    "weekend country:AE = {SAT, SUN} from 2022-01-01;",
    "calendar usd_eur = settlement_system:TARGET2 & settlement_system:FEDWIRE"
    " ignore at T + 1 unless {ARS, BRL, CLP, MXN};",
    'rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON}'
    ' cites "Ley 51 de 1983" art "1" label official;',
    'rule ar.guemes for guemes_day in country:AR valid from 2017-01-01'
    ' = 17 JUN {TUE WED > PREV MON, THU FRI > NEXT MON} cites "Ley 27.399" art "6" label official;',
    "rule us.independence_day for independence_day in country:US = 4 JUL {SAT - 1D, SUN + 1D};",
    "rule us.inauguration_day for inauguration_day in region:US-DC = 20 JAN {SUN + 1D} IF EVERY 4Y FROM 1965;",
    "rule mx.transfer_of_power for transfer_of_power in country:MX valid from 2024-01-01"
    " = 1 OCT IF EVERY 6Y FROM 2024;",
    "rule cl.bridge for national_holiday_bridge in country:CL = 17 SEP IF 18 SEP IS TUE, 20 SEP IF 19 SEP IS THU;",
    "rule za.sunday for public_holiday_substitute in country:ZA"
    " = @za.freedom_day IF SUN + 1D, @za.workers_day IF SUN + 1D;",
    "rule jp.substitute for substitute_holiday in country:JP = @jp.national IF SUN > NEXT NONHOLIDAY ON country:JP,"
    " [1 JAN : 31 DEC] IF BETWEEN HOLIDAYS AND NOT HOLIDAY;",
    "rule gb.christmas_day for christmas_day in country:GB = 25 DEC > FOLLOWING ON country:GB;",
    "rule au.anzac for anzac_day in region:AU-NSW, region:AU-ACT, region:AU-WA except region:AU-VIC"
    " = 25 APR {SAT + 2D, SUN + 1D};",
    'rule nz.matariki for matariki in country:NZ = 2026-07-10, 2027-06-25'
    ' cites "Te Kahui o Matariki Public Holiday Act 2022" art "schedule" label official;',
    "rule ae.eid for eid_al_fitr in country:AE"
    " = [1 SHAWWAL BY SIGHTING : 3 SHAWWAL BY FOLLOW country:SA];",
    'rule il.zahav for short_day in country:IL = T IF FRI'
    ' with kind = short_day, session 09:00 - 14:00 tz "Asia/Jerusalem";',
    'rule jp.equinox for vernal_equinox in country:JP = EQUINOX MAR "Asia/Tokyo", EQUINOX SEP UTC+9;',
    "rule gr.easter for easter_monday in country:GR = ORTHODOX EASTER + 1D;",
    'rule in.diwali for diwali in country:IN = HINDU diwali with precision = day, note "projection";',
    "rule brl.ndf_fixing for ndf_fixing in segment:NDF_BRL = @brl.ndf_settlement - 2B ON country:BR"
    " > PRECEDING ON country:BR > POSTPONE FOLLOWING ON country:BR MAX 14D CUMULATIVE"
    ' cites "ISDA 1998 FX Definitions" art "1.16(f)" label official;',
    "rule fx.spot for spot in pair:EURUSD = T > SPOT EURUSD ON usd_eur, SPOT + 1M ENDEND ON (country:US & country:DE);",
    "rule us.half_day for early_close in country:US"
    " = 4 THU NOV + 1D, 24 DEC IF MON TUE WED THU AND NOT HOLIDAY ON country:US;",
    "rule xx.nearest for test in country:XX"
    " = 1 MAY > NEAREST MON, 1 MAY {SAT SUN > MODPRECEDING}, T > 2ND BD ON usd_eur IN MONTH;",
]


@pytest.mark.parametrize("declaration", DECLARATIONS)
def test_declaration(declaration):
    assert len(RULES.parse(declaration, start="rule_file").children) == 1


def schedules():
    for file in ["schedule.txt", "catalog.txt", "date-holidays.txt"]:
        for line in (EXAMPLES / file).read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith(("#", "market ", "event ")):
                continue
            text = line.rsplit("=>", 1)[0].strip()
            yield (prefix.group(1) if (prefix := PREFIX.fullmatch(text)) else text), line


def accepts(parse):
    try:
        parse()
        return True
    except UnexpectedInput:
        return False


def test_schedules_parse_as_in_schedule_lark():
    """rules.lark accepts an expression exactly when schedule.lark does, except references in lower case
    (@za.freedom_day), which rule files allow and schedule.lark rejects"""
    differ = [line for text, line in schedules()
              if accepts(lambda: RULES.parse(text, start="exprs")) != accepts(lambda: SCHEDULE.parse(text))
              and not re.search(r"@[a-z]", text)]
    assert differ == []
