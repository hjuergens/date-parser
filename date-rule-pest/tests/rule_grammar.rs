//! Checks rules.pest, the grammar of rule files: the declarations of each extension, and the same verdict as
//! schedule.pest on every case of the three example files (as test_rule_grammar.py of date-rule-lark).

use std::fs;

use pest_schedule::grammar;

const EXAMPLES: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../examples/");

const DECLARATIONS: &[&str] = &[
    r#"weekend country:AE = {SAT, SUN} from 2022-01-01;"#,
    r#"calendar usd_eur = settlement_system:TARGET2 & settlement_system:FEDWIRE ignore at T + 1 unless {ARS, BRL, CLP, MXN};"#,
    r#"rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON} cites "Ley 51 de 1983" art "1" label official;"#,
    r#"rule ar.guemes for guemes_day in country:AR valid from 2017-01-01 = 17 JUN {TUE WED > PREV MON, THU FRI > NEXT MON} cites "Ley 27.399" art "6" label official;"#,
    r#"rule us.independence_day for independence_day in country:US = 4 JUL {SAT - 1D, SUN + 1D};"#,
    r#"rule us.inauguration_day for inauguration_day in region:US-DC = 20 JAN {SUN + 1D} IF EVERY 4Y FROM 1965;"#,
    r#"rule mx.transfer_of_power for transfer_of_power in country:MX valid from 2024-01-01 = 1 OCT IF EVERY 6Y FROM 2024;"#,
    r#"rule cl.bridge for national_holiday_bridge in country:CL = 17 SEP IF 18 SEP IS TUE, 20 SEP IF 19 SEP IS THU;"#,
    r#"rule za.sunday for public_holiday_substitute in country:ZA = @za.freedom_day IF SUN + 1D, @za.workers_day IF SUN + 1D;"#,
    r#"rule jp.substitute for substitute_holiday in country:JP = @jp.national IF SUN > NEXT NONHOLIDAY ON country:JP, [1 JAN : 31 DEC] IF BETWEEN HOLIDAYS AND NOT HOLIDAY;"#,
    r#"rule gb.christmas_day for christmas_day in country:GB = 25 DEC > FOLLOWING ON country:GB;"#,
    r#"rule au.anzac for anzac_day in region:AU-NSW, region:AU-ACT, region:AU-WA except region:AU-VIC = 25 APR {SAT + 2D, SUN + 1D};"#,
    r#"rule nz.matariki for matariki in country:NZ = 2026-07-10, 2027-06-25 cites "Te Kahui o Matariki Public Holiday Act 2022" art "schedule" label official;"#,
    r#"rule ae.eid for eid_al_fitr in country:AE = [1 SHAWWAL BY SIGHTING : 3 SHAWWAL BY FOLLOW country:SA];"#,
    r#"rule il.zahav for short_day in country:IL = T IF FRI with kind = short_day, session 09:00 - 14:00 tz "Asia/Jerusalem";"#,
    r#"rule jp.equinox for vernal_equinox in country:JP = EQUINOX MAR "Asia/Tokyo", EQUINOX SEP UTC+9;"#,
    r#"rule gr.easter for easter_monday in country:GR = ORTHODOX EASTER + 1D;"#,
    r#"rule in.diwali for diwali in country:IN = HINDU diwali with precision = day, note "projection";"#,
    r#"rule brl.ndf_fixing for ndf_fixing in segment:NDF_BRL = @brl.ndf_settlement - 2B ON country:BR > PRECEDING ON country:BR > POSTPONE FOLLOWING ON country:BR MAX 14D CUMULATIVE cites "ISDA 1998 FX Definitions" art "1.16(f)" label official;"#,
    r#"rule fx.spot for spot in pair:EURUSD = T > SPOT EURUSD ON usd_eur, SPOT + 1M ENDEND ON (country:US & country:DE);"#,
    r#"rule us.half_day for early_close in country:US = 4 THU NOV + 1D, 24 DEC IF MON TUE WED THU AND NOT HOLIDAY ON country:US;"#,
    r#"rule xx.nearest for test in country:XX = 1 MAY > NEAREST MON, 1 MAY {SAT SUN > MODPRECEDING}, T > 2ND BD ON usd_eur IN MONTH;"#,
];

#[test]
fn each_declaration_parses_as_one() {
    let failing: Vec<&&str> = DECLARATIONS.iter().filter(|d| grammar::declarations(d) != Some(1)).collect();
    assert!(failing.is_empty(), "rejected: {failing:#?}");
}

/// the schedule of a case line, without its [base market fixing] prefix
fn schedule_of(line: &str) -> &str {
    let text = line.rsplit_once("=>").map_or(line, |(t, _)| t).trim();
    if let Some((inside, rest)) = text.strip_prefix('[').and_then(|t| t.split_once("] ")) {
        let parts: Vec<&str> = inside.split(' ').collect();
        let name = |s: &&str| s.chars().all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || c == '_');
        if parts.len() <= 3 && parts[0].len() == 10 && parts[1..].iter().all(name) {
            return rest;
        }
    }
    text
}

/// rules.pest accepts an expression exactly when schedule.pest does
#[test]
fn schedules_parse_as_in_schedule_pest() {
    let mut differ = vec![];
    for file in ["schedule.txt", "catalog.txt", "date-holidays.txt"] {
        for line in fs::read_to_string(format!("{EXAMPLES}{file}")).unwrap().lines() {
            if line.trim().is_empty() || ["#", "market ", "event "].iter().any(|p| line.starts_with(p)) {
                continue;
            }
            let text = schedule_of(line);
            if grammar::rule_schedule(text) != grammar::schedule(text) {
                differ.push(format!("{file}: {line}"));
            }
        }
    }
    assert!(differ.is_empty(), "{} differ:\n{}", differ.len(), differ.join("\n"));
}
