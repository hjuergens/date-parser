//! Runs the shared cases of examples/schedule.txt, examples/catalog.txt and examples/date-holidays.txt.

use std::collections::HashMap;
use std::fs;
use std::rc::Rc;

use chrono::NaiveDate;
use pest_schedule::{events, parse, Ctx, MarketRef, Schedule, Weekends};

const EXAMPLES: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../examples/");

/// [base], [base market] or [base market fixing] in front of the schedule
fn prefix(text: &str) -> Option<(NaiveDate, Option<&str>, Option<&str>, &str)> {
    let rest = text.strip_prefix('[')?;
    let (inside, schedule) = rest.split_once("] ")?;
    let parts: Vec<&str> = inside.split(' ').collect();
    let name = |s: &str| !s.is_empty() && s.chars().all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || c == '_');
    if parts.len() > 3 || parts[0].len() != 10 || !parts[1..].iter().all(|p| name(p)) {
        return None;
    }
    let base = NaiveDate::parse_from_str(parts[0], "%Y-%m-%d").ok()?;
    Some((base, parts.get(1).copied(), parts.get(2).copied(), schedule))
}

fn run(file: &str) -> (usize, Vec<String>) {
    let text = fs::read_to_string(format!("{EXAMPLES}{file}")).unwrap();
    let lines: Vec<&str> = text.lines().filter(|l| !l.trim().is_empty() && !l.starts_with('#')).collect();
    // event NAME = schedule of its dates; all events of the file are known to every case
    let declared: Vec<(String, Schedule)> = lines
        .iter()
        .filter_map(|l| l.strip_prefix("event "))
        .map(|l| {
            let (name, dates) = l.split_once('=').unwrap();
            (name.trim().to_string(), parse(dates.trim()).unwrap())
        })
        .collect();
    let events = Rc::new(events(declared));
    let mut markets: HashMap<String, MarketRef> = HashMap::new();
    let (mut count, mut failures) = (0, vec![]);
    for line in lines {
        if line.starts_with("event ") {
            continue;
        }
        if let Some(rest) = line.strip_prefix("market ") {
            // market NAME = schedule of the holidays
            let (name, holidays) = rest.split_once('=').unwrap();
            markets.insert(name.trim().to_string(), Rc::new(parse(holidays.trim()).unwrap().market()));
            continue;
        }
        let (text, expected) = line.rsplit_once("=>").unwrap();
        let (text, expected) = (text.trim(), expected.trim());
        let mut context = Ctx::new(None).with_events(events.clone());
        let mut schedule = text;
        if let Some((base, market, fixing, rest)) = prefix(text) {
            let weekends: MarketRef = Rc::new(Weekends);
            context = Ctx::new(Some(base))
                .with_market(market.map_or(weekends.clone(), |m| markets[m].clone()))
                .with_events(events.clone())
                .with_fixing(fixing.map_or(weekends, |m| markets[m].clone()));
            schedule = rest;
        }
        let result = parse(schedule).and_then(|s| s.eval(&context));
        let actual = match result {
            Err(_) => "error".to_string(),
            Ok(dates) if dates.is_empty() => "none".to_string(),
            Ok(dates) => dates.iter().map(|d| d.to_string()).collect::<Vec<_>>().join(", "),
        };
        let expected: String = expected.split(',').map(str::trim).collect::<Vec<_>>().join(", ");
        count += 1;
        if actual != expected {
            failures.push(format!("{file}: {line}\n    got {actual}"));
        }
    }
    (count, failures)
}

fn check(file: &str) {
    let (count, failures) = run(file);
    for f in failures.iter().take(40) {
        eprintln!("{f}");
    }
    assert!(failures.is_empty(), "{file}: {} of {count} cases fail", failures.len());
    eprintln!("{file}: {count} cases pass");
}

#[test]
fn schedule_cases() {
    check("schedule.txt");
}

#[test]
fn catalog_cases() {
    check("catalog.txt");
}

#[test]
fn date_holidays_cases() {
    check("date-holidays.txt");
}
