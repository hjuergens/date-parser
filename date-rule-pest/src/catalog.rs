//! The evaluator of rule files (rules.pest), ported from catalog.py of date-rule-lark.
//!
//! `holidays(scope, year)` evaluates the rules of a scope for a year in two phases: each rule on its own, then, for the
//! rules that read the holidays of their own scope (a business day, a roll or a holiday condition without ON, or ON the
//! own scope), passes in the order of the nominal dates until nothing changes; a rule sees the dates of the other
//! rules, never its own.
//!
//! Not computed here: HINDU (no Hindu calendar) is an error at evaluation; the Hijri methods other than UMMALQURA are
//! projected with Umm al-Qura and marked as projections.
//!
//! Rules only project. Announcements are data, not grammar: the dates an authority published for a key and year
//! replace the dates of the rule with that key for that year, and in the other years its dates are projections. A key
//! without a rule holds only announced dates, and other rules can refer to it with @key.

use std::cell::RefCell;
use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
use std::rc::{Rc, Weak};

use chrono::{DateTime, Datelike, NaiveDate, Utc};

use crate::ast::*;
use crate::semantics::{
    date, evaluate, fail, next_day, weekday, Ctx, Error, Events, Market, MarketRef, Open, Weekends, R,
};
use crate::tree::{self, Node};

const MAX_PASSES: usize = 8;
const SAT_SUN: Weekdays = Weekdays(0b110_0000);

/// a rule declaration
pub struct RuleDecl {
    pub key: String,
    pub event_class: String,
    pub include: Vec<String>,
    pub exclude: Vec<String>,
    pub valid_from: Option<Date>,
    pub valid_to: Option<Date>,
    /// the expression list, or the union of the alternatives of ONE OF
    pub body: Exprs,
    /// (scenario name, expressions) of ONE OF
    pub alternatives: Vec<(String, Exprs)>,
    pub kind: String,
    pub precision: String,
    pub session: Option<String>,
    /// the @keys it reads
    pub references: BTreeSet<String>,
    /// its dates are projections: a Hijri method other than UMMALQURA, or ONE OF
    pub estimated: bool,
}

impl RuleDecl {
    fn valid(&self, d: Date) -> bool {
        self.valid_from.map_or(true, |f| d >= f) && self.valid_to.map_or(true, |t| d <= t)
    }

    fn in_year(&self, year: i64) -> bool {
        let (Ok(first), Ok(last)) = (date(year, 1, 1), date(year, 12, 31)) else { return false };
        self.valid(last) || self.valid(first) || self.valid_from.is_some_and(|f| f.year() as i64 == year)
    }

    fn closes(&self) -> bool {
        self.kind == "closed" || self.kind == "non_working"
    }
}

/// a date with the rule that produced it; projected when it comes from a projection (a Hijri date by sighting, a rule
/// with announced dates in a year without them), scenario the ONE OF alternative it belongs to
#[derive(Clone, Debug, PartialEq)]
pub struct Occurrence {
    pub date: Date,
    pub rule: String,
    pub event_class: String,
    pub kind: String,
    pub precision: String,
    pub session: Option<String>,
    pub projected: bool,
    pub scenario: Option<String>,
}

/// rule index -> (dates, projected) of the rules of a scope evaluated with the first of January of a year
type State = Rc<RefCell<Vec<(usize, Vec<Date>, bool)>>>;
type Key = (String, i64);

pub struct Catalog {
    me: Weak<Catalog>,
    rules: Vec<RuleDecl>,
    weekends: Vec<(String, Vec<(Date, Weekdays)>)>,
    calendars: HashMap<String, Cal>,
    /// calendar key -> the > CUTOFF of its cutoff attribute
    cutoffs: HashMap<String, Shift>,
    announcements: HashMap<String, Vec<Date>>,
    states: RefCell<HashMap<Key, State>>,
    shallow: RefCell<HashMap<Key, State>>,
    running: RefCell<HashMap<Key, State>>,
}

/// country:AU contains country:AU and region:AU-NSW
fn contains(outer: &str, inner: &str) -> bool {
    let tail = |s: &str| s.split_once(':').map_or(String::new(), |(_, t)| t.to_string());
    outer == inner || tail(inner).starts_with(&(tail(outer) + "-"))
}

impl Catalog {
    fn rc(&self) -> Rc<Catalog> {
        self.me.upgrade().expect("a live catalog")
    }

    fn rule_context(&self, rule: usize, scope: &str, year: i64, shallow: bool, market: Option<MarketRef>) -> R<Ctx> {
        let market = market.unwrap_or_else(|| {
            Rc::new(ScopeCalendar { catalog: self.rc(), scope: scope.into(), skip: Some((rule, year)), home: Some(year), shallow })
        });
        let events = References { catalog: self.rc(), scope: Some(scope.into()), home: Some(year), shallow };
        Ok(Ctx::new(Some(date(year, 1, 1)?))
            .with_market(market)
            .with_events(Rc::new(events))
            .in_catalog(self.rc(), Some(scope.into())))
    }

    // ---------- checks at load ----------

    fn check_versions(&self) -> R<()> {
        for (i, a) in self.rules.iter().enumerate() {
            for (j, b) in self.rules.iter().enumerate() {
                if i != j
                    && a.key == b.key
                    && a.valid_from.unwrap_or(NaiveDate::MIN) <= b.valid_to.unwrap_or(NaiveDate::MAX)
                    && b.valid_from.unwrap_or(NaiveDate::MIN) <= a.valid_to.unwrap_or(NaiveDate::MAX)
                {
                    return fail(format!("rule {}: versions overlap in time", a.key));
                }
            }
        }
        Ok(())
    }

    fn referenced(&self, name: &str) -> Vec<usize> {
        (0..self.rules.len()).filter(|&i| self.rules[i].key == name || self.rules[i].event_class == name).collect()
    }

    fn check_references(&self) -> R<()> {
        let mut graph: BTreeMap<&str, BTreeSet<&str>> = BTreeMap::new();
        for rule in &self.rules {
            let mut targets = BTreeSet::new();
            for name in &rule.references {
                let found = self.referenced(name);
                if found.is_empty() && !self.announcements.contains_key(name) {
                    return fail(format!("rule {}: no rule, event class or announced key {name}", rule.key));
                }
                targets.extend(found.iter().map(|&i| self.rules[i].key.as_str()));
            }
            graph.entry(&rule.key).or_default().extend(targets);
        }
        fn visit<'a>(
            key: &'a str,
            graph: &BTreeMap<&'a str, BTreeSet<&'a str>>,
            path: &mut Vec<&'a str>,
            done: &mut HashSet<&'a str>,
        ) -> R<()> {
            if let Some(at) = path.iter().position(|k| *k == key) {
                return fail(format!("cyclic references: {} -> {key}", path[at..].join(" -> ")));
            }
            if done.contains(key) {
                return Ok(());
            }
            path.push(key);
            for target in graph.get(key).into_iter().flatten() {
                visit(target, graph, path, done)?;
            }
            path.pop();
            done.insert(key);
            Ok(())
        }
        let (mut path, mut done) = (vec![], HashSet::new());
        for key in graph.keys() {
            visit(key, &graph, &mut path, &mut done)?;
        }
        Ok(())
    }

    // ---------- scopes and calendars ----------

    fn applies(&self, rule: usize, scope: &str) -> bool {
        let rule = &self.rules[rule];
        rule.include.iter().any(|s| contains(s, scope)) && !rule.exclude.iter().any(|s| contains(s, scope))
    }

    /// the weekend days of the scope on d: its own regime, else that of the scope containing it, else SAT SUN
    fn weekend(&self, scope: &str, d: Date) -> Weekdays {
        let mut candidates: Vec<&(String, Vec<(Date, Weekdays)>)> = self.weekends.iter().collect();
        candidates.sort_by_key(|(s, _)| std::cmp::Reverse(s.len()));
        for (candidate, regimes) in candidates {
            if contains(candidate, scope) {
                if let Some((_, days)) = regimes.iter().filter(|(start, _)| *start <= d).last() {
                    return *days;
                }
            }
        }
        SAT_SUN
    }

    /// the > CUTOFF of the cutoff attribute of a calendar declaration
    pub(crate) fn cutoff(&self, key: &str) -> Option<&Shift> {
        self.cutoffs.get(key)
    }

    pub(crate) fn resolve(&self, cal: &Cal, c: &Ctx, pair: Option<&str>) -> R<MarketRef> {
        match cal {
            Cal::Named(name) => match self.calendars.get(name) {
                Some(spec) => self.resolve(spec, c, pair),
                None => fail(format!("no calendar {name}")),
            },
            Cal::Scope(scope, None) => {
                if c.scope.as_deref() == Some(scope.as_str()) {
                    Ok(c.market.clone())
                } else {
                    Ok(Rc::new(ScopeCalendar::of(self.rc(), scope)))
                }
            }
            _ => {
                let terms: Vec<&Cal> = match cal {
                    Cal::Combined(terms) => terms.iter().collect(),
                    other => vec![other],
                };
                let mut resolved = vec![];
                for term in terms {
                    resolved.push(match term {
                        Cal::Scope(scope, ignore) => (self.resolve(&Cal::Scope(scope.clone(), None), c, None)?, ignore.clone()),
                        other => (self.resolve(other, c, pair)?, None),
                    });
                }
                Ok(Rc::new(CombinedCalendar::new(resolved, c.base, pair)?))
            }
        }
    }

    fn announcement(&self, key: &str, year: i64) -> Vec<Date> {
        self.announcements.get(key).map_or(vec![], |dates| dates.iter().filter(|d| d.year() as i64 == year).copied().collect())
    }

    // ---------- evaluation ----------

    /// (dates, projected) of the rule evaluated in the year; an announcement for the year always wins
    fn evaluate_rule(&self, i: usize, scope: &str, year: i64, shallow: bool, market: Option<MarketRef>) -> R<(Vec<Date>, bool)> {
        let rule = &self.rules[i];
        let published = self.announcement(&rule.key, year);
        if !published.is_empty() {
            return Ok((published.into_iter().filter(|d| rule.valid(*d)).collect(), false));
        }
        let context = self.rule_context(i, scope, year, shallow, market)?;
        let dates: BTreeSet<Date> = evaluate(&rule.body, &context)?.into_iter().filter(|d| rule.valid(*d)).collect();
        Ok((dates.into_iter().collect(), rule.estimated || self.announcements.contains_key(&rule.key)))
    }

    fn state(&self, scope: &str, year: i64, shallow: bool) -> R<State> {
        let key = (scope.to_string(), year);
        if let Some(state) = self.states.borrow().get(&key) {
            return Ok(state.clone());
        }
        if shallow {
            if let Some(state) = self.shallow.borrow().get(&key) {
                return Ok(state.clone());
            }
        }
        if let Some(state) = self.running.borrow().get(&key) {
            return Ok(state.clone());
        }
        let rules: Vec<usize> =
            (0..self.rules.len()).filter(|&i| self.applies(i, scope) && self.rules[i].in_year(year)).collect();
        let state: State = Rc::new(RefCell::new(
            rules
                .iter()
                .map(|&i| (i, vec![], self.rules[i].estimated || self.announcements.contains_key(&self.rules[i].key)))
                .collect(),
        ));
        self.running.borrow_mut().insert(key.clone(), state.clone());
        let result = self.passes(&rules, &state, scope, year, shallow);
        self.running.borrow_mut().remove(&key);
        result?;
        if shallow { &self.shallow } else { &self.states }.borrow_mut().insert(key, state.clone());
        Ok(state)
    }

    fn passes(&self, rules: &[usize], state: &State, scope: &str, year: i64, shallow: bool) -> R<()> {
        let mut order: Vec<(Date, usize)> =
            rules.iter().enumerate().map(|(pos, &i)| (self.nominal(i, scope, year), pos)).collect();
        order.sort();
        for _ in 0..MAX_PASSES {
            let mut changed = false;
            for &(_, pos) in &order {
                let (dates, projected) = self.evaluate_rule(rules[pos], scope, year, shallow, None)?;
                let mut state = state.borrow_mut();
                if state[pos].1 != dates || state[pos].2 != projected {
                    state[pos] = (rules[pos], dates, projected);
                    changed = true;
                }
            }
            if !changed {
                return Ok(());
            }
        }
        fail(format!("the rules of {scope} in {year} do not settle in {MAX_PASSES} passes"))
    }

    /// the first date of the rule with every day open, which orders the passes; the latest date if it needs more
    fn nominal(&self, i: usize, scope: &str, year: i64) -> Date {
        match self.evaluate_rule(i, scope, year, true, Some(Rc::new(Open))) {
            Ok((dates, _)) => dates.first().copied().unwrap_or(NaiveDate::MAX),
            Err(_) => NaiveDate::MAX,
        }
    }

    /// (date, scenario) for the dates of the rule: per alternative of ONE OF, else with no scenario; announced dates
    /// have none
    fn scenarios(&self, i: usize, scope: &str, year: i64, dates: &[Date]) -> R<Vec<(Date, Option<String>)>> {
        let rule = &self.rules[i];
        if rule.alternatives.is_empty() || !self.announcement(&rule.key, year).is_empty() {
            return Ok(dates.iter().map(|d| (*d, None)).collect());
        }
        let context = self.rule_context(i, scope, year, false, None)?;
        let mut out = vec![];
        for (name, exprs) in &rule.alternatives {
            let found: BTreeSet<Date> = evaluate(exprs, &context)?.into_iter().filter(|d| rule.valid(*d)).collect();
            out.extend(found.into_iter().map(|d| (d, Some(name.clone()))));
        }
        Ok(out)
    }

    fn reference_dates(&self, name: &str, scope: Option<&str>, year: i64, shallow: bool) -> R<Vec<Date>> {
        let rules = self.referenced(name);
        let mut dates: BTreeSet<Date> =
            if rules.is_empty() { self.announcement(name, year).into_iter().collect() } else { BTreeSet::new() };
        for i in rules {
            let Some(scope) = scope else { return fail(format!("@{name} needs a scope")) };
            let in_scope = if self.applies(i, scope) { scope.to_string() } else { self.rules[i].include[0].clone() };
            let state = self.state(&in_scope, year, shallow)?;
            let state = state.borrow();
            if let Some((_, found, _)) = state.iter().find(|(r, _, _)| *r == i) {
                dates.extend(found.iter().copied());
            }
        }
        Ok(dates.into_iter().collect())
    }

    /// the occurrences of the rules of the scope in the year (also those evaluated with a neighbouring year)
    pub fn holidays(&self, scope: &str, year: i64) -> R<Vec<Occurrence>> {
        let mut found: Vec<Occurrence> = vec![];
        for y in year - 1..=year + 1 {
            let state = self.state(scope, y, false)?.borrow().clone();
            for (i, dates, projected) in state {
                let rule = &self.rules[i];
                for (d, scenario) in self.scenarios(i, scope, y, &dates)? {
                    let occurrence = Occurrence {
                        date: d,
                        rule: rule.key.clone(),
                        event_class: rule.event_class.clone(),
                        kind: rule.kind.clone(),
                        precision: rule.precision.clone(),
                        session: rule.session.clone(),
                        projected,
                        scenario,
                    };
                    if d.year() as i64 == year && !found.contains(&occurrence) {
                        found.push(occurrence);
                    }
                }
            }
        }
        found.sort_by(|a, b| (a.date, &a.rule).cmp(&(b.date, &b.rule)));
        Ok(found)
    }

    /// a context for schedule(): the base date, the calendar of the scope and, for > CUTOFF, the base with its time
    /// of day
    pub fn context(&self, base: Date, scope: Option<&str>, instant: Option<DateTime<Utc>>) -> Ctx {
        let market: MarketRef = match scope {
            Some(s) => Rc::new(ScopeCalendar::of(self.rc(), s)),
            None => Rc::new(Weekends),
        };
        let events = References { catalog: self.rc(), scope: scope.map(String::from), home: None, shallow: false };
        Ctx::new(Some(base))
            .with_market(market)
            .with_events(Rc::new(events))
            .with_instant(instant)
            .in_catalog(self.rc(), scope.map(String::from))
    }

    /// the dates of a rule with a base date, such as a trade date for spot and fixing rules
    pub fn evaluate(&self, key: &str, base: Date, scope: Option<&str>) -> R<Vec<Date>> {
        let versions: Vec<&RuleDecl> = self.rules.iter().filter(|r| r.key == key).collect();
        let Some(rule) = versions.iter().find(|r| r.valid(base)).or(versions.first()) else {
            return fail(format!("no rule {key}"));
        };
        let scope = scope.unwrap_or(&rule.include[0]);
        let mut dates = evaluate(&rule.body, &self.context(base, Some(scope), None))?;
        dates.sort();
        Ok(dates)
    }
}

/// the weekend regime of a scope and the dates of its rules, from the evaluations of the year of a date and of the
/// years before and after (an observed holiday can fall into the neighbouring year). During the evaluation of a year
/// (home) skip leaves out the rule being evaluated, the other years are read shallow (evaluated with their own year
/// only), and a shallow calendar reads its home year only, so no evaluation starts a chain of years
struct ScopeCalendar {
    catalog: Rc<Catalog>,
    scope: String,
    skip: Option<(usize, i64)>,
    home: Option<i64>,
    shallow: bool,
}

impl ScopeCalendar {
    fn of(catalog: Rc<Catalog>, scope: &str) -> ScopeCalendar {
        ScopeCalendar { catalog, scope: scope.into(), skip: None, home: None, shallow: false }
    }

    /// (closed, opened): whether a rule closes the day and whether one opens it
    fn days(&self, d: Date) -> R<(bool, bool)> {
        let (mut closed, mut opened) = (false, false);
        for year in d.year() as i64 - 1..=d.year() as i64 + 1 {
            if self.shallow && Some(year) != self.home {
                continue;
            }
            let state = self.catalog.state(&self.scope, year, self.home.is_some() && Some(year) != self.home)?;
            for (rule, dates, _) in state.borrow().iter() {
                if self.skip == Some((*rule, year)) || !dates.contains(&d) {
                    continue;
                }
                let rule = &self.catalog.rules[*rule];
                if rule.closes() {
                    closed = true;
                } else if rule.kind == "working_day" {
                    opened = true;
                }
            }
        }
        Ok((closed, opened))
    }
}

impl Market for ScopeCalendar {
    fn is_business_day(&self, d: Date) -> R<bool> {
        let (closed, opened) = self.days(d)?;
        Ok(opened || !self.catalog.weekend(&self.scope, d).contains(weekday(d)) && !closed)
    }

    fn is_holiday(&self, d: Date) -> R<bool> {
        Ok(self.days(d)?.0)
    }
}

/// A & B: a business day in every term; a term with ignore does not count on the first business days after T of the
/// other terms, unless the currency pair of the evaluation contains one of its currencies
struct CombinedCalendar {
    terms: Vec<MarketRef>,
    ignored: HashSet<(usize, Date)>,
}

impl CombinedCalendar {
    fn new(terms: Vec<(MarketRef, Option<Ignore>)>, base: Option<Date>, pair: Option<&str>) -> R<CombinedCalendar> {
        let mut ignored = HashSet::new();
        for (i, (_, ignore)) in terms.iter().enumerate() {
            let (Some(ignore), Some(base)) = (ignore, base) else { continue };
            if pair.is_some_and(|p| ignore.currencies.iter().any(|c| p.contains(c.as_str()))) {
                continue;
            }
            let others: Vec<&MarketRef> = terms.iter().enumerate().filter(|(j, _)| *j != i).map(|(_, (m, _))| m).collect();
            let mut day = base;
            for _ in 0..ignore.days {
                day = next_day(day, 1, |x| {
                    for m in &others {
                        if !m.is_business_day(x)? {
                            return Ok(false);
                        }
                    }
                    Ok(true)
                })?;
            }
            ignored.insert((i, day));
        }
        Ok(CombinedCalendar { terms: terms.into_iter().map(|(m, _)| m).collect(), ignored })
    }
}

impl Market for CombinedCalendar {
    fn is_business_day(&self, d: Date) -> R<bool> {
        for (i, m) in self.terms.iter().enumerate() {
            if !self.ignored.contains(&(i, d)) && !m.is_business_day(d)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    fn is_holiday(&self, d: Date) -> R<bool> {
        for m in &self.terms {
            if m.is_holiday(d)? {
                return Ok(true);
            }
        }
        Ok(false)
    }
}

/// @key: the dates of a rule (by key), of the rules of an event class or of a key with announced dates only, in the
/// year of the base date; during the evaluation of a year (home) other years are read shallow, or not at all from a
/// shallow evaluation
struct References {
    catalog: Rc<Catalog>,
    scope: Option<String>,
    home: Option<i64>,
    shallow: bool,
}

impl Events for References {
    fn has(&self, name: &str) -> bool {
        !self.catalog.referenced(name).is_empty() || self.catalog.announcements.contains_key(name)
    }

    fn dates(&self, name: &str, context: &Ctx) -> R<Vec<Date>> {
        let year = context.base.ok_or_else(|| Error::Value("no base date".into()))?.year() as i64;
        if self.shallow && Some(year) != self.home {
            return Ok(vec![]);
        }
        self.catalog.reference_dates(name, self.scope.as_deref(), year, self.home.is_some() && Some(year) != self.home)
    }
}

// ================= building the catalog from the parse tree of rules.pest =================

fn child<'a>(node: &'a Node, rule: &str) -> Option<&'a Node> {
    node.children.iter().find(|c| c.rule == rule)
}

fn iso_date(node: &Node) -> R<Date> {
    let n: Vec<i64> = node.children.iter().map(|c| c.text.parse::<i64>().unwrap_or(0)).collect();
    date(n[0], n[1], n[2])
}

fn rule_declaration(node: &Node) -> R<RuleDecl> {
    let names: Vec<&Node> = node.children.iter().filter(|c| c.rule == "name").collect();
    let scopes = |n: Option<&Node>| -> Vec<String> {
        n.map_or(vec![], |n| n.children.iter().filter(|c| c.rule == "scope").map(|c| c.text.clone()).collect())
    };
    let validity = child(node, "validity");
    let bound = |rule: &str| -> R<Option<Date>> {
        validity.and_then(|v| child(v, rule)).map(|b| iso_date(&b.children[0])).transpose()
    };
    let (body, alternatives) = if let Some(one_of) = child(node, "one_of") {
        let mut alternatives: Vec<(String, Exprs)> = vec![];
        for alternative in &one_of.children {
            let name = alternative.children[1].text.clone();
            if alternatives.iter().any(|(n, _)| *n == name) {
                return fail(format!("ONE OF: the name {name} twice"));
            }
            alternatives.push((name, tree::exprs_of_items(&[&alternative.children[0]])?));
        }
        let union = Exprs(alternatives.iter().flat_map(|(_, e)| e.0.iter().cloned()).collect());
        (union, alternatives)
    } else {
        (tree::exprs(child(node, "exprs").expect("an expression list"))?, vec![])
    };
    let mut kind = "closed".to_string();
    let mut precision = "day".to_string();
    let mut session = None;
    if let Some(attributes) = child(node, "attributes") {
        for attribute in &attributes.children {
            match attribute.rule.as_str() {
                "kind_attribute" => kind = attribute.children[0].text.clone(),
                "precision_attribute" => precision = attribute.children[0].text.clone(),
                "session" => session = Some(attribute.text["session".len()..].trim().to_string()),
                _ => {}
            }
        }
    }
    let mut events = vec![];
    node.find_all("event", &mut events);
    let mut methods = vec![];
    node.find_all("hijri_method", &mut methods);
    let estimated = methods.iter().any(|m| m.children[0].text != "UMMALQURA") || !alternatives.is_empty();
    Ok(RuleDecl {
        key: names[0].text.clone(),
        event_class: names[1].text.clone(),
        include: scopes(child(node, "scopes")),
        exclude: scopes(child(node, "except_scopes")),
        valid_from: bound("valid_from")?,
        valid_to: bound("valid_to")?,
        body,
        alternatives,
        kind,
        precision,
        session,
        references: events.iter().map(|e| e.text[1..].to_string()).collect(),
        estimated,
    })
}

/// the catalog of the declarations of a rule file (the parse tree of rule_file); announcements maps a key to the dates
/// an authority published for it
pub fn build(file: &Node, announcements: HashMap<String, Vec<Date>>) -> R<Rc<Catalog>> {
    let mut rules = vec![];
    let mut weekends: Vec<(String, Vec<(Date, Weekdays)>)> = vec![];
    let mut calendars = HashMap::new();
    let mut cutoffs = HashMap::new();
    for declaration in &file.children {
        match declaration.rule.as_str() {
            "rule_decl" => rules.push(rule_declaration(declaration)?),
            "weekend_decl" => {
                let scope = declaration.children[0].text.clone();
                let days = tree::weekday_set(&declaration.children[1]);
                let from = match child(declaration, "valid_from") {
                    Some(f) => iso_date(&f.children[0])?,
                    None => NaiveDate::MIN,
                };
                match weekends.iter_mut().find(|(s, _)| *s == scope) {
                    Some((_, regimes)) => regimes.push((from, days)),
                    None => weekends.push((scope, vec![(from, days)])),
                }
            }
            "calendar_decl" => {
                let key = declaration.children[0].text.clone();
                if calendars.contains_key(&key) {
                    return fail(format!("calendar {key} declared twice"));
                }
                if let Some(attribute) = child(declaration, "cutoff_attribute") {
                    cutoffs.insert(key.clone(), tree::cutoff(attribute)?);
                }
                calendars.insert(key, tree::calendar_term(&declaration.children[1])?);
            }
            _ => {}
        }
    }
    for (_, regimes) in weekends.iter_mut() {
        regimes.sort_by_key(|(start, _)| *start);
    }
    let announcements = announcements
        .into_iter()
        .map(|(k, mut v)| {
            v.sort();
            (k, v)
        })
        .collect();
    let catalog = Rc::new_cyclic(|me| Catalog {
        me: me.clone(),
        rules,
        weekends,
        calendars,
        cutoffs,
        announcements,
        states: RefCell::new(HashMap::new()),
        shallow: RefCell::new(HashMap::new()),
        running: RefCell::new(HashMap::new()),
    });
    catalog.check_versions()?;
    catalog.check_references()?;
    Ok(catalog)
}
