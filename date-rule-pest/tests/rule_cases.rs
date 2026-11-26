//! Runs the shared cases of examples/rules.txt: blocks of declarations, each followed by its cases.

use std::collections::HashMap;
use std::fs;

use chrono::{DateTime, FixedOffset, NaiveDate, NaiveDateTime, TimeZone, Utc};
use pest_schedule::{parse_rules, Error};

const RULES: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../examples/rules.txt");

/// (declarations, [(case text, expected)]) in the order of the file
fn blocks() -> Vec<(String, Vec<(String, String)>)> {
    let text = fs::read_to_string(RULES).unwrap();
    let (mut out, mut declarations, mut cases) = (vec![], vec![], vec![]);
    for line in text.lines() {
        if line.trim().is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((case, expected)) = line.rsplit_once("=>") {
            cases.push((case.trim().to_string(), expected.trim().to_string()));
            continue;
        }
        if !cases.is_empty() {
            out.push((declarations.join("\n"), std::mem::take(&mut cases)));
            declarations.clear();
        }
        declarations.push(line);
    }
    if !cases.is_empty() {
        out.push((declarations.join("\n"), cases));
    }
    out
}

/// the instant of a local date and time at a fixed offset (UTC-4) or in an IANA zone ("America/New_York")
fn instant(local: NaiveDateTime, zone: &str) -> DateTime<Utc> {
    match zone.strip_prefix("UTC") {
        Some(hours) => {
            let offset = FixedOffset::east_opt(hours.parse::<i32>().unwrap() * 3600).unwrap();
            offset.from_local_datetime(&local).single().unwrap().with_timezone(&Utc)
        }
        None => {
            let tz = zone.trim_matches('"').parse::<chrono_tz::Tz>().unwrap();
            tz.from_local_datetime(&local).earliest().unwrap().with_timezone(&Utc)
        }
    }
}

/// [base] schedule, [base hh:mm zone] schedule, or scope year
fn evaluate(declarations: &str, text: &str) -> Result<Vec<String>, Error> {
    let book = parse_rules(declarations, HashMap::new())?;
    if let Some((base, schedule)) = text.strip_prefix('[').and_then(|t| t.split_once("] ")) {
        let mut parts = base.splitn(3, ' ');
        let date = NaiveDate::parse_from_str(parts.next().unwrap(), "%Y-%m-%d").unwrap();
        let at = match (parts.next(), parts.next()) {
            (Some(time), Some(zone)) => {
                let local = NaiveDateTime::parse_from_str(&format!("{date} {time}"), "%Y-%m-%d %H:%M").unwrap();
                Some(instant(local, zone))
            }
            _ => None,
        };
        let dates = book.schedule(schedule)?.eval(&book.context(date, None, at))?;
        return Ok(dates.iter().map(|d| d.to_string()).collect());
    }
    let (scope, year) = text.split_once(' ').unwrap();
    let found = book.holidays(scope, year.parse().unwrap())?;
    Ok(found
        .iter()
        .map(|o| match &o.scenario {
            Some(s) => format!("{} {s}", o.date),
            None => o.date.to_string(),
        })
        .collect())
}

#[test]
fn rule_cases() {
    let (mut count, mut failures) = (0, vec![]);
    for (declarations, cases) in blocks() {
        for (text, expected) in cases {
            let actual = match evaluate(&declarations, &text) {
                Ok(dates) if dates.is_empty() => "none".to_string(),
                Ok(dates) => dates.join(", "),
                Err(_) => "error".to_string(),
            };
            count += 1;
            if actual != expected {
                failures.push(format!("{text} => {expected}\n    got {actual}"));
            }
        }
    }
    for f in &failures {
        eprintln!("{f}");
    }
    assert!(failures.is_empty(), "{} of {count} rule cases fail", failures.len());
    eprintln!("rules.txt: {count} cases pass");
}
