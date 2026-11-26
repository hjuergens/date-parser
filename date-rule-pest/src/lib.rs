//! The shared date schedule language parsed with pest (Rust): schedule.pest for the shared examples, rules.pest for
//! rule files. Both grammars are built into one syntax tree (ast.rs, tree.rs), which semantics.rs evaluates against a
//! context and catalog.rs evaluates for rule files.
//!
//! ```
//! use pest_schedule::{parse, Ctx};
//! use chrono::NaiveDate;
//!
//! let rule = parse("2015-06-09 + 3M > IMM").unwrap();
//! assert_eq!(rule.eval(&Ctx::new(None)).unwrap(), vec![NaiveDate::from_ymd_opt(2015, 9, 16).unwrap()]);
//! ```

mod ast;
mod calendars;
mod catalog;
mod semantics;
mod tree;

use std::collections::HashMap;
use std::rc::Rc;

use pest::Parser;

pub use ast::Date;
pub use catalog::Occurrence;
pub use semantics::{Ctx, Error, EventRules, Events, Holidays, Market, MarketRef, NoEvents, RuleMarket, Weekends};

use tree::Node;

mod schedule_grammar {
    #[derive(pest_derive::Parser)]
    #[grammar = "schedule.pest"]
    pub struct ScheduleParser;
}

mod rules_grammar {
    #[derive(pest_derive::Parser)]
    #[grammar = "rules.pest"]
    pub struct RulesParser;
}

/// a parsed schedule: a rule, evaluated later with a context (base date, market, events, fixing calendar)
#[derive(Clone)]
pub struct Schedule(Rc<ast::Exprs>);

impl Schedule {
    /// the dates of the schedule, in the order of its items
    pub fn eval(&self, context: &Ctx) -> Result<Vec<Date>, Error> {
        semantics::evaluate(&self.0, context)
    }

    /// Monday to Friday except the dates of this schedule, evaluated with the first of January of each year
    pub fn market(&self) -> RuleMarket {
        RuleMarket::new(self.0.clone())
    }
}

/// events declared by schedules: name -> its dates
pub fn events(declared: impl IntoIterator<Item = (String, Schedule)>) -> EventRules {
    EventRules(declared.into_iter().map(|(name, s)| (name, s.0)).collect())
}

fn syntax(e: impl std::fmt::Display) -> Error {
    Error::Syntax(e.to_string())
}

/// parses a schedule of the shared example (schedule.pest)
pub fn parse(text: &str) -> Result<Schedule, Error> {
    let mut pairs = schedule_grammar::ScheduleParser::parse(schedule_grammar::Rule::schedule, text).map_err(syntax)?;
    let root = Node::from_pair(pairs.next().unwrap());
    Ok(Schedule(Rc::new(tree::exprs(&root)?)))
}

/// the rule catalog of a rule file (rules.pest)
#[derive(Clone)]
pub struct RuleCatalog(Rc<catalog::Catalog>);

/// parses a rule file; announcements maps a key to the dates an authority published for it
pub fn parse_rules(text: &str, announcements: HashMap<String, Vec<Date>>) -> Result<RuleCatalog, Error> {
    let mut pairs = rules_grammar::RulesParser::parse(rules_grammar::Rule::rule_file, text).map_err(syntax)?;
    let root = Node::from_pair(pairs.next().unwrap());
    Ok(RuleCatalog(catalog::build(&root, announcements)?))
}

/// parses one expression list of the rule language (rules.pest), as it follows "=" in a rule
pub fn parse_rule_schedule(text: &str) -> Result<Schedule, Error> {
    let mut pairs = rules_grammar::RulesParser::parse(rules_grammar::Rule::rule_schedule, text).map_err(syntax)?;
    let root = Node::from_pair(pairs.next().unwrap());
    let exprs = root.children.iter().find(|c| c.rule == "exprs").expect("an expression list");
    Ok(Schedule(Rc::new(tree::exprs(exprs)?)))
}

impl RuleCatalog {
    /// the occurrences of the rules of the scope in the year, sorted by date and rule
    pub fn holidays(&self, scope: &str, year: i64) -> Result<Vec<Occurrence>, Error> {
        self.0.holidays(scope, year)
    }

    /// a context for schedule(): the base date, the calendar of the scope and, for > CUTOFF, the base with its time
    /// of day
    pub fn context(&self, base: Date, scope: Option<&str>, instant: Option<chrono::DateTime<chrono::Utc>>) -> Ctx {
        self.0.context(base, scope, instant)
    }

    /// one expression of the rule language, evaluated with context(base, scope)
    pub fn schedule(&self, text: &str) -> Result<Schedule, Error> {
        parse_rule_schedule(text)
    }

    /// the dates of a rule with a base date, such as a trade date for spot and fixing rules
    pub fn evaluate(&self, key: &str, base: Date, scope: Option<&str>) -> Result<Vec<Date>, Error> {
        self.0.evaluate(key, base, scope)
    }
}

/// the grammars alone, without building or evaluating: for tests that compare them
pub mod grammar {
    use pest::Parser;

    use super::{rules_grammar, schedule_grammar};

    /// whether schedule.pest accepts the text
    pub fn schedule(text: &str) -> bool {
        schedule_grammar::ScheduleParser::parse(schedule_grammar::Rule::schedule, text).is_ok()
    }

    /// whether rules.pest accepts the text as one expression list
    pub fn rule_schedule(text: &str) -> bool {
        rules_grammar::RulesParser::parse(rules_grammar::Rule::rule_schedule, text).is_ok()
    }

    /// the number of declarations of a rule file, or None if rules.pest rejects it
    pub fn declarations(text: &str) -> Option<usize> {
        let mut pairs = rules_grammar::RulesParser::parse(rules_grammar::Rule::rule_file, text).ok()?;
        Some(pairs.next()?.into_inner().filter(|p| p.as_rule() != rules_grammar::Rule::EOI).count())
    }
}
