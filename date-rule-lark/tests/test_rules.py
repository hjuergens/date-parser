"""Evaluates rule files (rules.py) where the result is more than dates: attributes, announcements, the host API.
The dates are checked by the shared cases of examples/rules.txt (test_rule_cases.py)."""
from datetime import date

import pytest

from lark_schedule import semantics
from lark_schedule.rules import parse_rules

RULES = '''
# Japan: national holidays, the substitute holiday and the day between two holidays (Act No. 178 of 1948, art. 3)
rule jp.new_year for national_holiday in country:JP = 1 JAN;
rule jp.constitution for national_holiday in country:JP = 3 MAY;
rule jp.greenery for national_holiday in country:JP = 4 MAY;
rule jp.children for national_holiday in country:JP = 5 MAY;
rule jp.respect_for_aged for national_holiday in country:JP = 3 MON SEP;
rule jp.autumn_equinox for national_holiday in country:JP = EQUINOX SEP "Asia/Tokyo";
rule jp.substitute for substitute_holiday in country:JP = @national_holiday IF SUN > NEXT NONHOLIDAY
  cites "Act No. 178 of 1948" art "3(2)" label official;
rule jp.citizens for citizens_holiday in country:JP
  = [1 JAN : 31 DEC] IF BETWEEN HOLIDAYS AND NOT HOLIDAY AND NOT SUN
  cites "Act No. 178 of 1948" art "3(3)" label official;

# Great Britain: Christmas and Boxing Day push each other to the next weekdays
rule gb.christmas_day for christmas_day in country:GB = 25 DEC {SAT SUN > FOLLOWING};
rule gb.boxing_day for boxing_day in country:GB = 26 DEC {SAT SUN > FOLLOWING};

rule us.independence_day for independence_day in country:US = 4 JUL {SAT - 1D, SUN + 1D};
rule us.inauguration_day for inauguration_day in region:US-DC = 20 JAN {SUN + 1D} IF EVERY 4Y FROM 1965;
rule mx.transfer_of_power for transfer_of_power in country:MX valid to 2023-12-31 = 1 DEC IF EVERY 6Y FROM 2018;
rule mx.transfer_of_power for transfer_of_power in country:MX valid from 2024-01-01 = 1 OCT IF EVERY 6Y FROM 2024
  cites "Ley Federal del Trabajo" art "74" label secondary;
rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON}
  cites "Ley 51 de 1983" art "1" label official;
rule ar.guemes for guemes_day in country:AR valid from 2017-01-01 = 17 JUN {TUE WED > PREV MON, THU FRI > NEXT MON};
rule cl.bridge for national_holiday_bridge in country:CL = 17 SEP IF 18 SEP IS TUE, 20 SEP IF 19 SEP IS THU;
rule za.freedom_day for freedom_day in country:ZA = 27 APR;
rule za.sunday for public_holiday_substitute in country:ZA = @za.freedom_day IF SUN + 1D;
rule au.anzac for anzac_day in country:AU except region:AU-VIC = 25 APR {SAT + 2D, SUN + 1D};
rule gr.easter_monday for easter_monday in country:GR = ORTHODOX EASTER + 1D;
rule in.diwali for diwali in country:IN = HINDU diwali;

# kinds of day, sessions, precision
weekend country:IL = {FRI, SAT};
rule il.zahav for short_day in country:IL = [1 JAN : 31 DEC] IF THU
  with kind = short_day, session 09:00 - 14:00 tz "Asia/Jerusalem";
rule cn.makeup for makeup_day in country:CN = 2026-02-14 with kind = working_day;
rule fed.meeting for central_bank_meeting in country:XX = 15 MAR > PRECEDING with precision = month;

weekend country:AE = {FRI, SAT};
weekend country:AE = {SAT, SUN} from 2022-01-01;
rule ae.eid for eid_al_fitr in country:AE = [1 SHAWWAL : 3 SHAWWAL];
rule sa.eid for eid_al_fitr in country:SA = 1 SHAWWAL BY SIGHTING;

# FX: two settlement systems, USD on T+1 ignored unless for MXN
rule fedwire.independence_day for independence_day in settlement_system:FEDWIRE = 4 JUL {SAT - 1D, SUN + 1D};
rule target2.labour_day for labour_day in settlement_system:TARGET2 = 1 MAY;
calendar usd_eur = settlement_system:TARGET2 & settlement_system:FEDWIRE ignore at T + 1 unless {MXN};
calendar usd_eur_strict = settlement_system:TARGET2 & settlement_system:FEDWIRE;
rule fx.spot for spot in pair:EURUSD = T > SPOT EURUSD ON usd_eur;
rule br.carnival for carnival in country:BR = [16 FEB : 20 FEB];
'''


@pytest.fixture(scope="module")
def book():
    return parse_rules(RULES, announcements={
        "ae.eid": [date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)], "sa.eid": [date(2028, 2, 27)]})


def test_occurrence_carries_kind_session_precision_and_citations(book):
    zahav = book.holidays("country:IL", 2026)[0]
    assert (zahav.kind, zahav.session) == ("short_day", ("09:00", "14:00", "Asia/Jerusalem"))
    assert book.holidays("country:XX", 2026)[0].precision == "month"
    citation = book.holidays("country:CO", 2026)[0].citations[0]
    assert (citation.source, citation.article, citation.label) == ("Ley 51 de 1983", "1", "official")


def test_announcements_and_projections(book):
    announced = book.holidays("country:AE", 2026)
    assert [o.date for o in announced] == [date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)]
    assert not any(o.projected for o in announced)
    shawwal = semantics.month_day(1, "SHAWWAL")(2027)
    projected = book.holidays("country:AE", 2027)
    assert projected[0].date == shawwal and len(projected) == 3 and all(o.projected for o in projected)
    assert [(o.date, o.projected) for o in book.holidays("country:SA", 2027)] == [(shawwal, True)]
    assert [(o.date, o.projected) for o in book.holidays("country:SA", 2028)] == [(date(2028, 2, 27), False)]


def test_announcements_are_data_and_kind_event():
    book = parse_rules("""
rule fed.christmas for christmas_day in central_bank:FED = 25 DEC;
rule fed.fomc for fomc_decision in central_bank:FED = 3 THU MAR with kind = event;
rule fed.after for test_day in central_bank:FED = 18 MAR > FOLLOWING;
rule xetr.ex_dividend for ex_dividend in exchange:XETR = @issuer.agm > NEXT BD;
""", announcements={"fed.fomc": [date(2026, 3, 18)], "issuer.agm": [date(2026, 5, 7)]})
    got = {y: [(o.rule, o.date, o.projected) for o in book.holidays("central_bank:FED", y)] for y in (2026, 2027)}
    assert ("fed.fomc", date(2026, 3, 18), False) in got[2026]            # the announcement wins
    assert ("fed.fomc", date(2027, 3, 18), True) in got[2027]             # else the projection, marked
    assert ("fed.after", date(2027, 3, 18), False) in got[2027]           # the event on that day closes nothing
    # a key without a rule holds announced dates only; a rule can refer to it
    assert [o.date for o in book.holidays("exchange:XETR", 2026)] == [date(2026, 5, 8)]
    assert book.holidays("exchange:XETR", 2027) == []


def test_one_of_dates_are_projections_unless_announced():
    book = parse_rules("""
rule sa.eid for eid_al_fitr in country:SA = ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 };
rule ae.eid for eid_al_fitr in country:AE
  = ONE OF { [1 RAMADAN + 29D : 1 RAMADAN + 31D] AS ramadan_29, [1 RAMADAN + 29D : 1 RAMADAN + 32D] AS ramadan_30 };
rule xsau.eid for eid_al_fitr in exchange:XSAU = ONE OF { 1 SHAWWAL AS uq, 1 SHAWWAL + 1D AS late };
""", announcements={"xsau.eid": [date(2026, 3, 22)]})
    assert all(o.projected for o in book.holidays("country:SA", 2026))
    assert [(o.date, o.scenario) for o in book.holidays("exchange:XSAU", 2026)] == [(date(2026, 3, 22), None)]


def test_single_session_time_and_new_kinds():
    book = parse_rules("""
rule rba.decision for rba_decision in central_bank:RBA = 3 TUE MAR with kind = event, session 14:30 tz "Australia/Sydney";
rule xnse.muhurat for muhurat_session in exchange:XNSE = 1 NOV with kind = special_session;
rule dsmd.ramadan for ramadan_session in exchange:DSMD = 1 MAR with kind = changed_hours, session 09:30 - 13:15;
""")
    (rba,) = book.holidays("central_bank:RBA", 2026)
    assert rba.session == ("14:30", "14:30", "Australia/Sydney")
    assert book.holidays("exchange:XNSE", 2026)[0].kind == "special_session"
    assert book.holidays("exchange:DSMD", 2026)[0].kind == "changed_hours"


def test_parse_body_for_hosts():
    from lark_schedule.rules import RuleContext, parse_body

    body = parse_body("ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 }")
    assert body.dates(RuleContext(base=date(2026, 1, 1))) == [(date(2026, 3, 19), "ramadan_29"),
                                                               (date(2026, 3, 20), "ramadan_30")]
    assert not body.calendar and not body.references
    rel = parse_body("@fed.fomc_decision + 1B")
    assert rel.references == {"fed.fomc_decision"} and rel.calendar
    assert parse_body("15 MAY {SUN + 1D}").dates(RuleContext(base=date(2022, 1, 1))) == [(date(2022, 5, 16), None)]
    assert parse_body("EASTER + 60D > PRECEDING").calendar
