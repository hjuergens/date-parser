//! Builds the syntax tree of ast.rs from the parse trees of schedule.pest and rules.pest.
//!
//! pest generates one `Rule` enum per grammar; both grammars name their nodes alike, so the parse tree is first copied
//! into `Node`s that carry the rule name as text, and one builder reads the nodes of either grammar.
//! The builder checks what the grammar can't: valid dates, ordinals that fit their number, positive steps.

use pest::iterators::Pair;
use pest::RuleType;

use crate::ast::*;
use crate::semantics::{date, Error, R};

/// a node of the parse tree: the name of its rule, its text and its children
#[derive(Debug)]
pub struct Node {
    pub rule: String,
    pub text: String,
    pub children: Vec<Node>,
}

impl Node {
    pub fn from_pair<T: RuleType>(pair: Pair<T>) -> Node {
        Node {
            rule: format!("{:?}", pair.as_rule()),
            text: pair.as_str().to_string(),
            children: pair.into_inner().map(Node::from_pair).collect(),
        }
    }

    fn is(&self, rule: &str) -> bool {
        self.rule == rule
    }

    fn child(&self, rule: &str) -> Option<&Node> {
        self.children.iter().find(|c| c.is(rule))
    }

    fn all(&self, rule: &str) -> impl Iterator<Item = &Node> {
        let rule = rule.to_string();
        self.children.iter().filter(move |c| c.rule == rule)
    }

    /// every node of the subtree with this rule
    pub fn find_all<'a>(&'a self, rule: &str, out: &mut Vec<&'a Node>) {
        if self.is(rule) {
            out.push(self);
        }
        for child in &self.children {
            child.find_all(rule, out);
        }
    }
}

fn error<T>(message: String) -> R<T> {
    Err(Error::Value(message))
}

fn number(node: &Node) -> R<i64> {
    node.text.parse::<i64>().or_else(|_| error(format!("number too large: {}", node.text)))
}

fn numbers(node: &Node) -> R<Vec<i64>> {
    node.all("num").map(number).collect()
}

pub fn exprs(node: &Node) -> R<Exprs> {
    exprs_of_items(&node.children.iter().filter(|c| c.is("item") || c.is("picked_item")).collect::<Vec<_>>())
}

pub fn exprs_of_items(items: &[&Node]) -> R<Exprs> {
    items.iter().map(|i| item(i)).collect::<R<_>>().map(Exprs)
}

fn item(node: &Node) -> R<Item> {
    if node.is("picked_item") {
        let count = ordinal(&node.children[0].text)?;
        let from_end = node.child("last_word").is_some();
        let start = node.children.iter().find(|c| c.is("series") || c.is("event")).expect("a series or an event");
        let expr = Expr { start: date_node(start)?, shifts: shifts(node)? };
        return Ok(Item::Picked { count, from_end, core: core(expr, node)? });
    }
    Ok(Item::Plain(core(expression(node.child("expr").expect("an expression"))?, node)?))
}

/// the combines and the IF clause of an item
fn core(expr: Expr, node: &Node) -> R<ItemCore> {
    let combines = node.all("combine").map(combine).collect::<R<_>>()?;
    let clause = match node.child("if_clause") {
        Some(clause) => Some(IfClause { condition: condition(&clause.children[0])?, shifts: shifts(clause)? }),
        None => None,
    };
    Ok(ItemCore { expr, combines, clause })
}

fn combine(node: &Node) -> R<Combine> {
    let include = node.children[0].is("op_include");
    let operand = &node.children[1];
    let operand = if operand.is("operand_set") {
        operand.children.iter().map(expression).collect::<R<_>>()?
    } else {
        vec![Expr { start: date_node(operand)?, shifts: vec![] }]
    };
    Ok(Combine { include, operand, shifts: shifts(node)? })
}

/// an expr or an endpoint: a date and its shifts
fn expression(node: &Node) -> R<Expr> {
    Ok(Expr { start: date_node(&node.children[0])?, shifts: shifts(node)? })
}

fn shifts(node: &Node) -> R<Vec<Shift>> {
    node.children.iter().filter(|c| c.rule.starts_with("shift_")).map(shift).collect()
}

fn on(node: &Node) -> R<Option<Cal>> {
    node.child("on").map(|on| calendar_term(&on.children[0])).transpose()
}

fn pair_of(node: &Node) -> Option<String> {
    node.child("pair").map(|p| p.text.clone())
}

fn shift(node: &Node) -> R<Shift> {
    Ok(match node.rule.as_str() {
        "shift_plus" | "shift_minus" => Shift::Plus {
            period: period(node.child("period").unwrap())?,
            sign: if node.is("shift_plus") { 1 } else { -1 },
            endend: node.child("endend").is_some(),
            on: on(node)?,
        },
        "shift_imm" => Shift::Imm,
        "shift_spot" => Shift::Spot { pair: pair_of(node), on: on(node)? },
        "shift_fix" => Shift::Fix(pair_of(node)),
        "shift_roll" => Shift::Roll(roll(&node.child("roll_word").unwrap().text), on(node)?),
        "shift_next" | "shift_prev" => Shift::Find {
            count: node.child("ordinal").map(|o| ordinal(&o.text)).transpose()?.unwrap_or(1),
            anchor: anchor(node.children.last().unwrap())?,
            direction: if node.is("shift_next") { 1 } else { -1 },
        },
        "shift_nearest" => Shift::Nearest(anchor(&node.children[0])?),
        "shift_nearest_roll" => Shift::Roll(Roll::Nearest, on(node)?),
        "shift_postpone" => Shift::Postpone {
            roll: roll(&node.child("roll_word").unwrap().text),
            on: on(node)?,
            limit: period(node.child("period").unwrap())?,
            cumulative: node.child("cumulative").is_some(),
        },
        "shift_select" => {
            let count = &node.children[0];
            let (n, from_end) = if count.is("count_last") {
                (1, true)
            } else {
                (ordinal(&count.children[0].text)?, count.child("last_word").is_some())
            };
            let kind = match node.children[2].text.trim_end() {
                "WEEK" => PeriodKind::Week,
                "MONTH" => PeriodKind::Month,
                "QUARTER" => PeriodKind::Quarter,
                "HALFYEAR" => PeriodKind::HalfYear,
                _ => PeriodKind::Year,
            };
            Shift::Select { count: n, from_end, anchor: anchor(&node.children[1])?, period: kind }
        }
        "shift_dates_in_year" => Shift::DatesInYear(node.children.iter().map(annual).collect::<R<_>>()?),
        "shift_observance" => Shift::Observance(node.children.iter().map(observe).collect::<R<_>>()?),
        other => unreachable!("shift {other}"),
    })
}

fn observe(node: &Node) -> R<(Weekdays, Move)> {
    let days = Weekdays(node.all("weekday").fold(0, |set, w| set | 1 << weekday(&w.text)));
    let action = node.children.last().unwrap();
    let action = match action.rule.as_str() {
        "move_plus" => Move::Plus(period(&action.children[0])?, 1),
        "move_minus" => Move::Plus(period(&action.children[0])?, -1),
        "move_next" => Move::Find(anchor(&action.children[0])?, 1),
        "move_prev" => Move::Find(anchor(&action.children[0])?, -1),
        "move_nearest" => Move::Nearest(anchor(&action.children[0])?),
        _ => Move::Roll(roll(&action.child("roll_word").unwrap().text), on(action)?),
    };
    Ok((days, action))
}

fn anchor(node: &Node) -> R<Anchor> {
    Ok(match node.rule.as_str() {
        "any_of" => Anchor::AnyOf(node.children.iter().map(anchor).collect::<R<_>>()?),
        "weekday" => Anchor::Weekday(weekday(&node.text)),
        "anchor_bd" => Anchor::BusinessDay(on(node)?),
        "anchor_fd" => Anchor::FixingDay,
        "anchor_imm" => Anchor::Imm,
        "anchor_holiday" => Anchor::Holiday(on(node)?),
        "anchor_nonholiday" => Anchor::NonHoliday(on(node)?),
        "event" => Anchor::Event(node.text[1..].to_string()),
        _ => Anchor::Annual(annual(node)?),
    })
}

fn date_node(node: &Node) -> R<DateNode> {
    Ok(match node.rule.as_str() {
        "series" => DateNode::Series(Box::new(series(node)?)),
        "tom" => DateNode::Tom,
        "base" => DateNode::Base,
        "spot_date" => DateNode::Spot { pair: pair_of(node), on: on(node)? },
        "iso" | "german" | "us" => {
            let n = numbers(node)?;
            let (y, m, d) = match node.rule.as_str() {
                "iso" => (n[0], n[1], n[2]),
                "german" => (n[2], n[1], n[0]),
                _ => (n[2], n[0], n[1]),
            };
            DateNode::Fixed(date(y, m, d)?)
        }
        "event" => DateNode::Event(node.text[1..].to_string()),
        _ => DateNode::InYear(annual(node)?),
    })
}

fn series(node: &Node) -> R<Series> {
    let start_out = node.children[0].is("start_out");
    let end_out = node.children.last().unwrap().is("end_out");
    let endpoints = node.all("endpoint").map(expression).collect::<R<Vec<_>>>()?;
    let step = match node.child("period") {
        Some(p) => period(p)?,
        None => Period { days: 1, ..Period::default() },
    };
    let mut endpoints = endpoints.into_iter();
    let first = endpoints.next().unwrap();
    let last = endpoints.next();
    if last.is_none() && end_out {
        return error("an open series ends with ]".into());
    }
    if !step.is_positive() {
        return error(format!("step must be positive: {step:?}"));
    }
    Ok(Series { first, step, last, start_out, end_out })
}

pub fn annual(node: &Node) -> R<Annual> {
    Ok(match node.rule.as_str() {
        "month_day" => {
            let m = month(&node.child("month").unwrap().text);
            if node.child("hijri_method").is_some() && !matches!(m, Month::Hijri(_)) {
                return error(format!("BY only for Hijri months, not {}", node.child("month").unwrap().text));
            }
            Annual::MonthDay(clamp_day(number(&node.children[0])?), m)
        }
        "nth_weekday" => Annual::NthWeekday(
            number(&node.children[0])?,
            weekday(&node.child("weekday").unwrap().text),
            month(&node.child("month").unwrap().text),
        ),
        "last_weekday" => {
            Annual::LastWeekday(weekday(&node.child("weekday").unwrap().text), month(&node.child("month").unwrap().text))
        }
        "easter" => Annual::Easter,
        "orthodox_easter" => Annual::OrthodoxEaster,
        "season" => {
            let equinox = node.children[0].text.starts_with("EQUINOX");
            let index = match month(&node.child("month").unwrap().text) {
                Month::Gregorian(3) => 0,
                Month::Gregorian(6) => 1,
                Month::Gregorian(9) => 2,
                Month::Gregorian(12) => 3,
                _ => -1,
            };
            if index < 0 || (index % 2 == 0) != equinox {
                return error(format!("no {}", node.text));
            }
            Annual::Sun(90 * index, zone(node)?)
        }
        "solar_term" => {
            let term = number(&node.children[0])?;
            if !(1..=24).contains(&term) {
                return error(format!("no solar term {term}"));
            }
            Annual::Sun(((term + 20) % 24 * 15) as i32, zone(node)?)
        }
        "hindu" => Annual::Hindu(node.children[0].text.clone()),
        other => unreachable!("annual {other}"),
    })
}

/// a day of the month that no month has stays invalid; large numbers only need to stay out of range
fn clamp_day(day: i64) -> u32 {
    day.clamp(0, 99) as u32
}

fn zone(node: &Node) -> R<Zone> {
    if let Some(offset) = node.child("zone_offset") {
        let hours = offset.text[3..].parse::<i64>().or_else(|_| error(format!("no time zone {}", offset.text)))?;
        return Ok(Zone::Offset(hours * 60));
    }
    Ok(match node.child("string") {
        Some(s) => Zone::Iana(s.children[0].text.clone()),
        None => Zone::Offset(0),
    })
}

fn condition(node: &Node) -> R<Condition> {
    Ok(match node.rule.as_str() {
        "condition" | "cond_and" => {
            let parts = node.children.iter().map(condition).collect::<R<Vec<_>>>()?;
            if parts.len() == 1 {
                parts.into_iter().next().unwrap()
            } else if node.is("condition") {
                Condition::Any(parts)
            } else {
                Condition::All(parts)
            }
        }
        "cond_not" => {
            let atom = condition(node.children.last().unwrap())?;
            if node.child("not_word").is_some() {
                Condition::Not(Box::new(atom))
            } else {
                atom
            }
        }
        "cond_weekday" => Condition::OnWeekday(weekdays(&node.children[0])),
        "cond_day" => {
            let n = numbers(node)?;
            let m = node.child("month").map(|m| month(&m.text));
            if !(1 <= n[0] && n[0] <= n[1] && n[1] <= 31) {
                return error(format!("days must run from 1 to 31 upwards: {}..{}", n[0], n[1]));
            }
            let month = match m {
                None => None,
                Some(Month::Gregorian(m)) => Some(m),
                Some(_) => return error("IF DAY takes a Gregorian month".into()),
            };
            Condition::OnDay { first: n[0] as u32, last: n[1] as u32, month }
        }
        "cond_between" => Condition::BetweenHolidays(on(node)?),
        "cond_every" => {
            let p = period(node.child("period").unwrap())?;
            if p.days != 0 || p.business != 0 || p.fixing != 0 || p.months % 12 != 0 || p.months <= 0 {
                return error("EVERY needs whole years: EVERY 4Y FROM 1965".into());
            }
            Condition::Every { years: p.months / 12, first: number(node.child("num").unwrap())? }
        }
        "cond_holiday" => Condition::Holiday(on(node)?),
        "cond_subject" => {
            let subject = node.child("subject").unwrap();
            let mut shifts = vec![];
            for s in &subject.children[1..] {
                let sign = if s.is("move_plus") { 1 } else { -1 };
                shifts.push(Shift::Plus { period: period(&s.children[0])?, sign, endend: false, on: None });
            }
            let subject = Expr { start: date_node(&subject.children[0])?, shifts };
            let test = &node.children[1];
            if test.is("is_holiday") {
                Condition::DateIsHoliday(subject, on(test)?)
            } else {
                Condition::IsWeekday(subject, weekdays(test))
            }
        }
        other => unreachable!("condition {other}"),
    })
}

fn weekdays(node: &Node) -> Weekdays {
    Weekdays(node.all("weekday").fold(0, |set, w| set | 1 << weekday(&w.text)))
}

pub fn weekday_set(node: &Node) -> Weekdays {
    weekdays(node)
}

/// a term of ON or of a calendar declaration
pub fn calendar_term(node: &Node) -> R<Cal> {
    Ok(match node.rule.as_str() {
        "on_scope" => Cal::Scope(node.text.clone(), None),
        "cal_named" => Cal::Named(node.text.clone()),
        "cal_scope" => {
            let ignore = match node.child("ignore") {
                Some(i) => Some(Ignore {
                    days: number(i.child("num").unwrap())?.clamp(0, 100_000) as u32,
                    currencies: i.all("ccy").map(|c| c.text.clone()).collect(),
                }),
                None => None,
            };
            Cal::Scope(node.children[0].text.clone(), ignore)
        }
        "calendar" => {
            let mut terms = node.children.iter().map(calendar_term).collect::<R<Vec<_>>>()?;
            if terms.len() == 1 {
                terms.pop().unwrap()
            } else {
                Cal::Combined(terms)
            }
        }
        other => unreachable!("calendar {other}"),
    })
}

pub fn period(node: &Node) -> R<Period> {
    let n = number(&node.children[0])?;
    Ok(match node.children[1].text.as_str() {
        "D" => Period { days: n, ..Period::default() },
        "W" => Period { days: 7 * n, ..Period::default() },
        "M" => Period { months: n, ..Period::default() },
        "Q" => Period { months: 3 * n, ..Period::default() },
        "H" => Period { months: 6 * n, ..Period::default() },
        "Y" => Period { months: 12 * n, ..Period::default() },
        "B" => Period { business: n, ..Period::default() },
        _ => Period { fixing: n, ..Period::default() },
    })
}

/// 1ST, 2ND, 3RD, 4TH, 11TH, 21ST as a count; the suffix must fit the number
fn ordinal(text: &str) -> R<i64> {
    let (digits, suffix) = text.split_at(text.len() - 2);
    let n: i64 = digits.parse().or_else(|_| error(format!("not an ordinal: {text}")))?;
    let fits = if (11..=13).contains(&(n % 100)) {
        "TH"
    } else {
        match n % 10 {
            1 => "ST",
            2 => "ND",
            3 => "RD",
            _ => "TH",
        }
    };
    if suffix != fits {
        return error(format!("not an ordinal: {text}"));
    }
    Ok(n)
}

pub fn weekday(text: &str) -> Weekday {
    ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"].iter().position(|w| *w == text).unwrap() as Weekday
}

const GREGORIAN: [&str; 12] = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];
const HIJRI: [&str; 12] = [
    "MUHARRAM", "SAFAR", "RABIALAWWAL", "RABIALTHANI", "JUMADAALULA", "JUMADAALAKHIRA", "RAJAB", "SHABAN", "RAMADAN",
    "SHAWWAL", "DHULQADAH", "DHULHIJJAH",
];
const HEBREW: [&str; 13] =
    ["NISAN", "IYAR", "SIVAN", "TAMMUZ", "AV", "ELUL", "TISHRI", "HESHVAN", "KISLEV", "TEVET", "SHEVAT", "ADAR", "ADARII"];
const CHINESE: [&str; 12] = [
    "ZHENGYUE", "ERYUE", "SANYUE", "SIYUE", "WUYUE", "LIUYUE", "QIYUE", "BAYUE", "JIUYUE", "SHIYUE", "DONGYUE", "LAYUE",
];

fn index(names: &[&str], text: &str) -> Option<u32> {
    names.iter().position(|n| *n == text).map(|i| i as u32 + 1)
}

pub fn month(text: &str) -> Month {
    if let Some(m) = index(&GREGORIAN, text) {
        Month::Gregorian(m)
    } else if let Some(m) = index(&HIJRI, text) {
        Month::Hijri(m)
    } else if let Some(m) = index(&HEBREW, text) {
        Month::Hebrew(m)
    } else {
        Month::Chinese(index(&CHINESE, text).expect("a month"))
    }
}

fn roll(text: &str) -> Roll {
    match text {
        "FOLLOWING" => Roll::Following,
        "PRECEDING" => Roll::Preceding,
        "MODFOLLOWING" => Roll::ModFollowing,
        "MODPRECEDING" => Roll::ModPreceding,
        "BOW" => Roll::Bow,
        "EOW" => Roll::Eow,
        "BOM" => Roll::Bom,
        "EOM" => Roll::Eom,
        "BOQ" => Roll::Boq,
        "EOQ" => Roll::Eoq,
        "BOH" => Roll::Boh,
        "EOH" => Roll::Eoh,
        "BOY" => Roll::Boy,
        _ => Roll::Eoy,
    }
}
