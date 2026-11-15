"""Evaluates rule files (rules.py): declarations, the extensions of the schedule language and the set-level passes."""
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


def dates(book, scope, year, rule=None):
    return [o.date for o in book.holidays(scope, year) if rule is None or o.rule == rule]


@pytest.mark.parametrize("scope, year, rule, expected", [
    ("country:JP", 2026, "jp.citizens", [date(2026, 9, 22)]),        # between Respect for the Aged Day and the equinox
    ("country:JP", 2026, "jp.substitute", [date(2026, 5, 6)]),       # 3 May is a Sunday, 4 and 5 May are holidays
    ("country:JP", 2026, "jp.autumn_equinox", [date(2026, 9, 23)]),
    ("country:GB", 2021, None, [date(2021, 12, 27), date(2021, 12, 28)]),   # Christmas on Saturday
    ("country:GB", 2022, "gb.christmas_day", [date(2022, 12, 27)]),         # Christmas on Sunday, Boxing Day stays
    ("country:GB", 2022, "gb.boxing_day", [date(2022, 12, 26)]),
    ("region:US-DC", 2013, "us.inauguration_day", [date(2013, 1, 21)]),
    ("region:US-DC", 2025, "us.inauguration_day", [date(2025, 1, 20)]),
    ("region:US-DC", 2026, "us.inauguration_day", []),
    ("region:US-DC", 2026, "us.independence_day", [date(2026, 7, 3)]),     # a national rule in the region
    ("country:US", 2025, "us.inauguration_day", []),
    ("country:MX", 2018, None, [date(2018, 12, 1)]),
    ("country:MX", 2024, None, [date(2024, 10, 1)]),
    ("country:MX", 2025, None, []),
    ("country:CO", 2026, None, [date(2026, 5, 18)]),
    ("country:AR", 2026, None, [date(2026, 6, 15)]),
    ("country:AR", 2016, None, []),
    ("country:CL", 2018, None, [date(2018, 9, 17)]),
    ("country:CL", 2024, None, [date(2024, 9, 20)]),
    ("country:ZA", 2025, "za.sunday", [date(2025, 4, 28)]),
    ("country:ZA", 2026, "za.sunday", []),
    ("region:AU-NSW", 2026, None, [date(2026, 4, 27)]),
    ("region:AU-VIC", 2026, None, []),
    ("country:GR", 2026, None, [date(2026, 4, 13)]),
])
def test_holidays(book, scope, year, rule, expected):
    assert dates(book, scope, year, rule) == expected


def test_occurrence_carries_kind_session_precision_and_citations(book):
    zahav = book.holidays("country:IL", 2026)[0]
    assert (zahav.kind, zahav.session) == ("short_day", ("09:00", "14:00", "Asia/Jerusalem"))
    assert book.holidays("country:XX", 2026)[0].precision == "month"
    citation = book.holidays("country:CO", 2026)[0].citations[0]
    assert (citation.source, citation.article, citation.label) == ("Ley 51 de 1983", "1", "official")


def test_weekend_regimes_and_kinds(book):
    il, ae, cn = (book.calendar(s) for s in ("country:IL", "country:AE", "country:CN"))
    assert not il.is_business_day(date(2026, 11, 13)) and il.is_business_day(date(2026, 11, 15))    # Fri, Sun
    assert il.is_business_day(date(2026, 11, 12))           # a short day is not closed
    assert not ae.is_business_day(date(2021, 12, 31)) and ae.is_business_day(date(2022, 1, 7))      # Fridays
    assert cn.is_business_day(date(2026, 2, 14))            # a make-up working Saturday


def test_announcements_and_projections(book):
    announced = book.holidays("country:AE", 2026)
    assert [o.date for o in announced] == [date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)]
    assert not any(o.projected for o in announced)
    shawwal = semantics.month_day(1, "SHAWWAL")(2027)
    projected = book.holidays("country:AE", 2027)
    assert projected[0].date == shawwal and len(projected) == 3 and all(o.projected for o in projected)
    assert [(o.date, o.projected) for o in book.holidays("country:SA", 2027)] == [(shawwal, True)]
    assert [(o.date, o.projected) for o in book.holidays("country:SA", 2028)] == [(date(2028, 2, 27), False)]


def test_spot_on_combined_calendars(book):
    trade = date(2026, 7, 2)        # T+1 is 3 July, the observed Independence Day
    assert book.evaluate("fx.spot", trade) == [date(2026, 7, 6)]                  # USD ignored on T+1
    strict = book.schedule("T > SPOT EURUSD ON usd_eur_strict")
    assert strict(book.context(trade)) == [date(2026, 7, 7)]
    assert book.schedule("T > SPOT USDMXN ON usd_eur")(book.context(trade)) == [date(2026, 7, 7)]   # unless MXN


@pytest.mark.parametrize("text, base, expected", [
    ("T + 1M ENDEND ON country:US", date(2026, 2, 27), date(2026, 3, 31)),   # the last business day of February
    ("T + 1M ENDEND ON country:US", date(2026, 2, 10), date(2026, 3, 10)),
    ("T > NEAREST MON", date(2026, 11, 5), date(2026, 11, 2)),
    ("T > MODPRECEDING", date(2026, 11, 1), date(2026, 11, 2)),
    ("T > POSTPONE FOLLOWING ON country:BR MAX 3D", date(2026, 2, 16), date(2026, 2, 19)),
    ("T > POSTPONE FOLLOWING ON country:BR MAX 30D", date(2026, 2, 16), date(2026, 2, 23)),
    ("T > POSTPONE FOLLOWING ON country:BR MAX 2D > POSTPONE FOLLOWING ON country:BR MAX 2D",
     date(2026, 2, 16), date(2026, 2, 20)),
    ("T > POSTPONE FOLLOWING ON country:BR MAX 2D > POSTPONE FOLLOWING ON country:BR MAX 2D CUMULATIVE",
     date(2026, 2, 16), date(2026, 2, 18)),
    ("T > NEXT HOLIDAY ON country:BR", date(2026, 1, 1), date(2026, 2, 16)),
    ("T > 2ND BD ON country:BR IN MONTH", date(2026, 2, 1), date(2026, 2, 3)),
    ("EQUINOX MAR \"Asia/Tokyo\", EQUINOX MAR UTC+9", date(2026, 1, 1), date(2026, 3, 20)),
])
def test_schedule_extensions(book, text, base, expected):
    assert set(book.schedule(text)(book.context(base))) == {expected}


def test_hindu_is_not_computed(book):
    with pytest.raises(ValueError, match="HINDU"):
        book.holidays("country:IN", 2026)


@pytest.mark.parametrize("text, message", [
    ("rule a for x in country:XX = @b; rule b for y in country:XX = @a;", "cyclic"),
    ("rule a for x in country:XX = @missing;", "no rule"),
    ("rule a for x in country:XX valid from 2020-01-01 = 1 JAN; rule a for x in country:XX = 2 JAN;", "overlap"),
    ("rule a for x in country:XX = 1 JAN BY SIGHTING;", "Hijri"),
    ("rule a for x in country:XX = 1 JAN IF EVERY 6M FROM 2000;", "whole years"),
])
def test_load_errors(text, message):
    with pytest.raises(ValueError, match=message):
        parse_rules(text)


def test_announced_by_with_and_without_projection_and_kind_event():
    from datetime import date

    from lark_schedule.rules import parse_rules

    book = parse_rules("""
rule fed.christmas for christmas_day in central_bank:FED = 25 DEC;
rule fed.fomc for fomc_decision in central_bank:FED = ANNOUNCED BY fed_fomc_calendar ELSE 3 THU MAR with kind = event;
rule fed.minutes for fomc_minutes in central_bank:FED = ANNOUNCED BY fed_fomc_calendar with kind = event;
rule fed.after for test_day in central_bank:FED = 18 MAR > FOLLOWING;
""", announcements={"fed.fomc": [date(2026, 3, 18)], "fed.minutes": [date(2026, 4, 8)]})
    got = {y: [(o.rule, o.date, o.projected) for o in book.holidays("central_bank:FED", y)] for y in (2026, 2027)}
    assert ("fed.fomc", date(2026, 3, 18), False) in got[2026]            # the announcement wins
    assert ("fed.fomc", date(2027, 3, 18), True) in got[2027]             # else the projection, marked
    assert ("fed.minutes", date(2026, 4, 8), False) in got[2026]
    assert not [o for o in got[2027] if o[0] == "fed.minutes"]            # no ELSE: nothing until announced
    assert ("fed.after", date(2027, 3, 18), False) in got[2027]           # the event on that day closes nothing
