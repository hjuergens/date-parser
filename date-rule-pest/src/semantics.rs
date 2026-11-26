//! Semantics of the schedule language: periods, business days, IMM and spot dates, series, annual dates, rolling,
//! named events and conditions, evaluated over the syntax tree of ast.rs. Ported from date-rule-lark (semantics.py and
//! catalog.py); the arithmetic follows java.time, like ScheduleSemantics.java in the JVM modules.
//!
//! A parsed schedule is a rule: evaluated later with a context (base date, market, events, fixing calendar).
//! Nodes with several values (an event's occurrences, a series' elements, A & B, > {..}) are bound one at a time by
//! `values`: such a node returns `Error::Unbound(dates)` when its value is not bound yet, and `values` evaluates the
//! expression once per date.

use std::cell::{Cell, RefCell};
use std::collections::{BTreeSet, HashMap};
use std::fmt;
use std::rc::Rc;

use chrono::{DateTime, Datelike, FixedOffset, NaiveDate, NaiveDateTime, Timelike, Utc};

use crate::ast::*;
use crate::calendars;
use crate::catalog::Catalog;

#[derive(Debug, Clone)]
pub enum Error {
    /// the text doesn't follow the grammar
    Syntax(String),
    /// an invalid date, an impossible request, a missing event, ...
    Value(String),
    /// internal: a node with several values that is not bound yet; `values` never lets it out
    Unbound(Vec<Date>),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Error::Syntax(m) => write!(f, "syntax error: {m}"),
            Error::Value(m) => write!(f, "{m}"),
            Error::Unbound(_) => write!(f, "a node with several values outside of values()"),
        }
    }
}

impl std::error::Error for Error {}

pub type R<T> = Result<T, Error>;

pub fn fail<T>(message: impl Into<String>) -> R<T> {
    Err(Error::Value(message.into()))
}

// ================= dates =================

/// a date of the years 1 to 9999, as Python's datetime.date
pub fn date(year: i64, month: i64, day: i64) -> R<Date> {
    if (1..=9999).contains(&year) && (1..=12).contains(&month) && (1..=31).contains(&day) {
        if let Some(d) = NaiveDate::from_ymd_opt(year as i32, month as u32, day as u32) {
            return Ok(d);
        }
    }
    fail(format!("invalid date {year}-{month}-{day}"))
}

/// chrono's day number (0001-01-01 is 1) of 1970-01-01
const EPOCH_FROM_CE: i64 = 719163;

/// days since 1970-01-01
pub fn epoch_day(d: Date) -> i64 {
    d.num_days_from_ce() as i64 - EPOCH_FROM_CE
}

pub fn from_epoch_day(days: i64) -> R<Date> {
    let ce = days + EPOCH_FROM_CE;
    match i32::try_from(ce).ok().and_then(NaiveDate::from_num_days_from_ce_opt) {
        Some(d) if (1..=9999).contains(&d.year()) => Ok(d),
        _ => fail("date out of range"),
    }
}

pub fn add_days(d: Date, days: i64) -> R<Date> {
    from_epoch_day(epoch_day(d).checked_add(days).ok_or_else(|| Error::Value("date out of range".into()))?)
}

/// Monday = 0 ... Sunday = 6
pub fn weekday(d: Date) -> Weekday {
    d.weekday().num_days_from_monday()
}

pub fn month_length(year: i64, month: u32) -> u32 {
    let (y, m) = if month == 12 { (year + 1, 1) } else { (year, month + 1) };
    match (NaiveDate::from_ymd_opt(y as i32, m, 1), NaiveDate::from_ymd_opt(year as i32, month, 1)) {
        (Some(next), Some(first)) => (next - first).num_days() as u32,
        _ => 31,
    }
}

// ================= markets =================

/// a calendar of business days
pub trait Market {
    fn is_business_day(&self, d: Date) -> R<bool>;

    /// a holiday: closed although not a weekend day
    fn is_holiday(&self, d: Date) -> R<bool> {
        Ok(weekday(d) < 5 && !self.is_business_day(d)?)
    }
}

pub type MarketRef = Rc<dyn Market>;

/// Monday to Friday
pub struct Weekends;

impl Market for Weekends {
    fn is_business_day(&self, d: Date) -> R<bool> {
        Ok(weekday(d) < 5)
    }
}

/// Monday to Friday except the holidays
pub struct Holidays(pub BTreeSet<Date>);

impl Market for Holidays {
    fn is_business_day(&self, d: Date) -> R<bool> {
        Ok(weekday(d) < 5 && !self.0.contains(&d))
    }
}

/// every day a business day: the calendar for the nominal dates, which give the order of the passes of a catalog
pub struct Open;

impl Market for Open {
    fn is_business_day(&self, _: Date) -> R<bool> {
        Ok(true)
    }

    fn is_holiday(&self, _: Date) -> R<bool> {
        Ok(false)
    }
}

/// Monday to Friday except the dates the rule yields, evaluated with the first of January of the year before, of the
/// year and of the year after as base date (an observed holiday can fall into the neighbouring year)
pub struct RuleMarket {
    rule: Rc<Exprs>,
    years: RefCell<HashMap<i32, Rc<Vec<Date>>>>,
}

impl RuleMarket {
    pub(crate) fn new(rule: Rc<Exprs>) -> RuleMarket {
        RuleMarket { rule, years: RefCell::new(HashMap::new()) }
    }
}

impl Market for RuleMarket {
    fn is_business_day(&self, d: Date) -> R<bool> {
        if weekday(d) >= 5 {
            return Ok(false);
        }
        for year in d.year() - 1..=d.year() + 1 {
            let cached = self.years.borrow().get(&year).cloned();
            let dates = match cached {
                Some(dates) => dates,
                None => {
                    let dates = Rc::new(evaluate(&self.rule, &Ctx::new(Some(date(year as i64, 1, 1)?)))?);
                    self.years.borrow_mut().insert(year, dates.clone());
                    dates
                }
            };
            if dates.contains(&d) {
                return Ok(false);
            }
        }
        Ok(true)
    }
}

// ================= events =================

/// the named events of a context: @NAME
pub trait Events {
    fn has(&self, name: &str) -> bool;
    /// the dates of the event, evaluated with the context (its base date is the first of January of a year)
    fn dates(&self, name: &str, context: &Ctx) -> R<Vec<Date>>;
}

pub struct NoEvents;

impl Events for NoEvents {
    fn has(&self, _: &str) -> bool {
        false
    }

    fn dates(&self, name: &str, _: &Ctx) -> R<Vec<Date>> {
        fail(format!("no event {name}"))
    }
}

/// events declared as rules: name -> schedule of its dates
#[derive(Default)]
pub struct EventRules(pub(crate) HashMap<String, Rc<Exprs>>);

impl Events for EventRules {
    fn has(&self, name: &str) -> bool {
        self.0.contains_key(name)
    }

    fn dates(&self, name: &str, context: &Ctx) -> R<Vec<Date>> {
        evaluate(&self.0[name], context)
    }
}

// ================= the context =================

/// what a rule is evaluated against: the base date (only needed for T), the market, the named events and the fixing
/// calendar; in a rule file also the catalog and the scope. bindings are the values chosen by values() for the nodes
/// with several values, in the order the evaluation reaches them, counted by cursor
#[derive(Clone)]
pub struct Ctx {
    pub base: Option<Date>,
    pub market: MarketRef,
    pub events: Rc<dyn Events>,
    pub fixing: MarketRef,
    bindings: Rc<Vec<Date>>,
    cursor: Option<Rc<Cell<usize>>>,
    /// how many elements an open series yields; set by a pick, None elsewhere
    limit: Option<usize>,
    pub(crate) catalog: Option<Rc<Catalog>>,
    pub(crate) scope: Option<String>,
    /// the base with its time of day, for > CUTOFF
    pub instant: Option<DateTime<Utc>>,
}

impl Ctx {
    /// a context with Monday to Friday as market and fixing calendar and no events
    pub fn new(base: Option<Date>) -> Ctx {
        Ctx {
            base,
            market: Rc::new(Weekends),
            events: Rc::new(NoEvents),
            fixing: Rc::new(Weekends),
            bindings: Rc::new(vec![]),
            cursor: None,
            limit: None,
            catalog: None,
            scope: None,
            instant: None,
        }
    }

    pub fn with_market(mut self, market: MarketRef) -> Ctx {
        self.market = market;
        self
    }

    pub fn with_events(mut self, events: Rc<dyn Events>) -> Ctx {
        self.events = events;
        self
    }

    pub fn with_fixing(mut self, fixing: MarketRef) -> Ctx {
        self.fixing = fixing;
        self
    }

    pub fn with_instant(mut self, instant: Option<DateTime<Utc>>) -> Ctx {
        self.instant = instant;
        self
    }

    pub(crate) fn in_catalog(mut self, catalog: Rc<Catalog>, scope: Option<String>) -> Ctx {
        self.catalog = Some(catalog);
        self.scope = scope;
        self
    }
}

/// T: the base date of the evaluation
fn base(c: &Ctx) -> R<Date> {
    c.base.ok_or_else(|| Error::Value("no base date".into()))
}

/// the value of the next node with several values in this evaluation: bound by values(), or Unbound(dates)
fn take(c: &Ctx, dates: impl FnOnce() -> R<Vec<Date>>) -> R<Date> {
    if let Some(cursor) = &c.cursor {
        let i = cursor.get();
        cursor.set(i + 1);
        if i < c.bindings.len() {
            return Ok(c.bindings[i]);
        }
    }
    Err(Error::Unbound(dates()?))
}

/// what values() evaluates: an item, the core of an item (without its pick), the core up to its k-th combine, an
/// expression
#[derive(Clone, Copy)]
enum Thunk<'a> {
    Item(&'a Item),
    Core(&'a ItemCore),
    Prefix(&'a ItemCore, usize),
    Expr(&'a Expr),
}

/// the dates of an expression: one, or one per combination of the values of its nodes with several values (an
/// event's occurrences in the base year, a series' elements, ...), bound one after the other; None where a condition
/// drops the date
fn values(t: Thunk, c: &Ctx) -> R<Vec<Option<Date>>> {
    values_with(t, c, &[])
}

fn values_with(t: Thunk, c: &Ctx, bindings: &[Date]) -> R<Vec<Option<Date>>> {
    let bound = Ctx { bindings: Rc::new(bindings.to_vec()), cursor: Some(Rc::new(Cell::new(0))), ..c.clone() };
    match thunk(t, &bound) {
        Ok(d) => Ok(vec![d]),
        Err(Error::Unbound(dates)) => {
            let mut out = vec![];
            let mut next = bindings.to_vec();
            for d in dates {
                next.push(d);
                out.extend(values_with(t, c, &next)?);
                next.pop();
            }
            Ok(out)
        }
        Err(e) => Err(e),
    }
}

fn thunk(t: Thunk, c: &Ctx) -> R<Option<Date>> {
    match t {
        Thunk::Item(Item::Plain(core)) | Thunk::Core(core) => item_core(core, c),
        Thunk::Item(Item::Picked { count, from_end, core }) => pick(*count, *from_end, core, c).map(Some),
        Thunk::Prefix(core, k) => prefix(core, k, c).map(Some),
        Thunk::Expr(e) => expr(e, c).map(Some),
    }
}

fn dates_of(t: Thunk, c: &Ctx) -> R<Vec<Date>> {
    Ok(values(t, c)?.into_iter().flatten().collect())
}

/// the date of an expression that must have exactly one, such as the end of a range
fn single(e: &Expr, c: &Ctx) -> R<Date> {
    let dates = values(Thunk::Expr(e), c)?;
    match dates.as_slice() {
        [Some(d)] => Ok(*d),
        _ => fail(format!("{} dates where one is needed", dates.len())),
    }
}

/// the dates of a list of items, in order
pub fn evaluate(e: &Exprs, c: &Ctx) -> R<Vec<Date>> {
    let mut out = vec![];
    for item in &e.0 {
        out.extend(dates_of(Thunk::Item(item), c)?);
    }
    Ok(out)
}

// ================= items =================

fn item_core(core: &ItemCore, c: &Ctx) -> R<Option<Date>> {
    let d = prefix(core, core.combines.len(), c)?;
    match &core.clause {
        None => Ok(Some(d)),
        Some(clause) if condition(&clause.condition, d, c)? => Ok(Some(apply_shifts(d, &clause.shifts, c)?)),
        Some(_) => Ok(None),
    }
}

/// the expression of the item with its first k combines: A & B (include), A \ B (exclude), the dates of the left side
/// with or without those of the operand, sorted and without duplicates; one value at a time, so the shifts after it
/// apply to each
fn prefix(core: &ItemCore, k: usize, c: &Ctx) -> R<Date> {
    if k == 0 {
        return expr(&core.expr, c);
    }
    let combine = &core.combines[k - 1];
    let d = take(c, || {
        let mut right = BTreeSet::new();
        for e in &combine.operand {
            right.extend(dates_of(Thunk::Expr(e), c)?);
        }
        let left: BTreeSet<Date> = dates_of(Thunk::Prefix(core, k - 1), c)?.into_iter().collect();
        Ok(if combine.include { left.union(&right).copied().collect() } else { left.difference(&right).copied().collect() })
    })?;
    apply_shifts(d, &combine.shifts, c)
}

const PICK_LIMITS: [usize; 4] = [16, 256, 4096, 10000];

/// 5TH ...: the count-th date of the item strictly after T, in the order the item yields them; 2ND LAST ...: counted
/// from the last one. An open series grows until there are enough dates, up to PICK_LIMITS[-1] elements
fn pick(count: i64, from_end: bool, core: &ItemCore, c: &Ctx) -> R<Date> {
    if count < 1 {
        return fail(format!("count must be positive: {count}"));
    }
    let t = base(c)?;
    let limits: Vec<Option<usize>> = if from_end { vec![None] } else { PICK_LIMITS.iter().map(|l| Some(*l)).collect() };
    for limit in limits {
        let hits: Vec<Date> =
            dates_of(Thunk::Core(core), &Ctx { limit, ..c.clone() })?.into_iter().filter(|d| *d > t).collect();
        if hits.len() as i64 >= count {
            let n = count as usize;
            return Ok(if from_end { hits[hits.len() - n] } else { hits[n - 1] });
        }
    }
    fail(format!("no {count}. date after {t}"))
}

fn expr(e: &Expr, c: &Ctx) -> R<Date> {
    let d = date_node(&e.start, c)?;
    apply_shifts(d, &e.shifts, c)
}

fn date_node(n: &DateNode, c: &Ctx) -> R<Date> {
    match n {
        DateNode::Base => base(c),
        DateNode::Tom => plus_business_days(base(c)?, 1, &*c.market),
        DateNode::Spot { pair, on } => spot(&*market(on, c, pair.as_deref())?, base(c)?, pair.as_deref()),
        DateNode::Fixed(d) => Ok(*d),
        // a lunar date that falls twice in the year gives both, one at a time
        DateNode::InYear(Annual::MonthDay(day, month)) if !matches!(month, Month::Gregorian(_)) => {
            take(c, || calendars::lunar_dates(*day, *month, base(c)?.year() as i64))
        }
        DateNode::InYear(a) => annual(a, base(c)?.year() as i64),
        DateNode::Event(name) => take(c, || occurrences(name, base(c)?.year() as i64, c)),
        DateNode::Series(s) => take(c, || series_dates(s, c)),
    }
}

/// [first : step : last]: the elements of the series; [first : step : ] (last None) is open, only under a pick, which
/// sets how many elements it yields
fn series_dates(s: &Series, c: &Ctx) -> R<Vec<Date>> {
    if let Some(last) = &s.last {
        let firsts = dates_of(Thunk::Expr(&s.first), c)?;
        let lasts = dates_of(Thunk::Expr(last), c)?;
        if firsts.len() == lasts.len() && firsts.len() > 1 {
            // one range per occurrence: [@E - 3D : @E + 1D]
            let mut all = BTreeSet::new();
            for (a, b) in firsts.iter().zip(&lasts) {
                all.extend(series(*a, s.step, *b, s.start_out, s.end_out, c)?);
            }
            return Ok(all.into_iter().collect());
        }
        return series(single(&s.first, c)?, s.step, single(last, c)?, s.start_out, s.end_out, c);
    }
    let Some(limit) = c.limit else {
        return fail("an open series needs a pick in front of the item");
    };
    let start = single(&s.first, c)?;
    let mut out = vec![];
    for k in 0..limit as i64 {
        let d = plus(start, s.step.times(k), 1, &*c.market, &*c.fixing)?;
        if !(s.start_out && d <= start) {
            out.push(d);
        }
    }
    Ok(out)
}

/// first + k * step while not after last; start_out and end_out leave out an endpoint
fn series(first: Date, step: Period, last: Date, start_out: bool, end_out: bool, c: &Ctx) -> R<Vec<Date>> {
    let mut out = vec![];
    for k in 0.. {
        let d = plus(first, step.times(k), 1, &*c.market, &*c.fixing)?;
        if d > last {
            break;
        }
        if !(start_out && d <= first || end_out && d >= last) {
            out.push(d);
        }
    }
    Ok(out)
}

/// the dates of the event in the year: its rule evaluated with the first of January as base date
fn occurrences(name: &str, year: i64, c: &Ctx) -> R<Vec<Date>> {
    if !c.events.has(name) {
        return fail(format!("no event {name}"));
    }
    let first = Ctx::new(Some(date(year, 1, 1)?))
        .with_market(c.market.clone())
        .with_events(c.events.clone())
        .with_fixing(c.fixing.clone());
    let mut dates: Vec<Date> =
        c.events.dates(name, &first)?.into_iter().filter(|d| d.year() as i64 == year).collect();
    dates.sort();
    Ok(dates)
}

// ================= shifts =================

/// the shifts from left to right; a postponement is limited from the date before it (CUMULATIVE: before the first
/// postponement)
pub fn apply_shifts(mut d: Date, shifts: &[Shift], c: &Ctx) -> R<Date> {
    let mut origin: Option<Date> = None;
    for s in shifts {
        if let Shift::Postpone { roll: convention, on, limit, cumulative } = s {
            let first = *origin.get_or_insert(d);
            let from = if *cumulative { first } else { d };
            let target = roll(d, *convention, &*market(on, c, None)?)?;
            d = if target > d {
                target.min(plus(from, *limit, 1, &*c.market, &*c.fixing)?)
            } else {
                target.max(plus(from, *limit, -1, &*c.market, &*c.fixing)?)
            };
        } else {
            d = shift(s, d, c)?;
        }
    }
    Ok(d)
}

fn shift(s: &Shift, d: Date, c: &Ctx) -> R<Date> {
    match s {
        Shift::Plus { period, sign, endend, on } => {
            let m = market(on, c, None)?;
            let moved = plus(d, *period, *sign, &*m, &*c.fixing)?;
            if *endend && d == last_business_day(d, &*m)? {
                return last_business_day(moved, &*m);
            }
            Ok(moved)
        }
        Shift::Imm => next_imm(d),
        Shift::Spot { pair, on } => spot(&*market(on, c, pair.as_deref())?, d, pair.as_deref()),
        Shift::Fix(pair) => plus_business_days(d, -spot_lag(pair.as_deref()), &*c.fixing),
        Shift::Roll(convention, on) => roll(d, *convention, &*market(on, c, None)?),
        Shift::Find { count, anchor, direction } => {
            if *count < 1 {
                return fail(format!("count must be positive: {count}"));
            }
            let mut d = d;
            for _ in 0..*count {
                d = find(anchor, d, *direction, c)?;
            }
            Ok(d)
        }
        Shift::Nearest(anchor) => nearest(anchor, d, c),
        Shift::Cutoff { hours, minutes, zone } => cutoff(*hours, *minutes, zone, c),
        Shift::Select { count, from_end, anchor, period } => select(anchor, *count, *from_end, *period, d, c),
        // > {1 FEB, 1 MAY}: the dates of the set in the year of the date, sorted; one value at a time
        Shift::DatesInYear(annuals) => take(c, || {
            let mut set = BTreeSet::new();
            for a in annuals {
                set.insert(annual(a, d.year() as i64)?);
            }
            Ok(set.into_iter().collect())
        }),
        Shift::Postpone { .. } => unreachable!("applied by apply_shifts"),
        // {SAT - 1D, TUE WED > PREV MON}: the first entry for the weekday of the date moves it; entries don't chain
        Shift::Observance(entries) => {
            for (days, action) in entries {
                if days.contains(weekday(d)) {
                    return match action {
                        Move::Plus(p, sign) => plus(d, *p, *sign, &*c.market, &*c.fixing),
                        Move::Find(anchor, direction) => find(anchor, d, *direction, c),
                        Move::Nearest(anchor) => nearest(anchor, d, c),
                        Move::Roll(convention, on) => roll(d, *convention, &*market(on, c, None)?),
                    };
                }
            }
            Ok(d)
        }
    }
}

/// the calendar of ON ...; without ON the market of the context (in a rule: its own scope)
pub fn market(on: &Option<Cal>, c: &Ctx, pair: Option<&str>) -> R<MarketRef> {
    match on {
        None => Ok(c.market.clone()),
        Some(cal) => match &c.catalog {
            Some(catalog) => catalog.resolve(cal, c, pair),
            None => fail("ON needs a rule catalog"),
        },
    }
}

/// months first (day clamped to the end of the month), then days; business days of the market and fixing days of the
/// fixing calendar on their own
pub fn plus(d: Date, p: Period, sign: i64, market: &dyn Market, fixing: &dyn Market) -> R<Date> {
    if p.fixing != 0 {
        return plus_business_days(d, sign * p.fixing, fixing);
    }
    if p.business != 0 {
        return plus_business_days(d, sign * p.business, market);
    }
    let total = d.year() as i64 * 12 + d.month0() as i64 + sign * p.months;
    let (year, month) = (total.div_euclid(12), total.rem_euclid(12) as u32 + 1);
    if !(1..=9999).contains(&year) {
        return fail("date out of range");
    }
    let clamped = date(year, month as i64, d.day().min(month_length(year, month)) as i64)?;
    add_days(clamped, sign * p.days)
}

/// count business days of the market later (earlier if negative)
pub fn plus_business_days(mut d: Date, mut count: i64, market: &dyn Market) -> R<Date> {
    let step = if count > 0 { 1 } else { -1 };
    while count != 0 {
        d = add_days(d, step)?;
        if market.is_business_day(d)? {
            count -= step;
        }
    }
    Ok(d)
}

/// the business days from trade to spot: 1 for USD against CAD, KZT, PHP, RUB, TRY or UZS, 0 against AZN, KGS, MDL,
/// MNT or UAH, otherwise 2 (also without pair; a cross takes T+2)
pub fn spot_lag(pair: Option<&str>) -> i64 {
    let Some(pair) = pair else { return 2 };
    let other = if let Some(rest) = pair.strip_prefix("USD") {
        rest
    } else if let Some(first) = pair.strip_suffix("USD") {
        first
    } else {
        return 2;
    };
    if ["CAD", "KZT", "PHP", "RUB", "TRY", "UZS"].contains(&other) {
        1
    } else if ["AZN", "KGS", "MDL", "MNT", "UAH"].contains(&other) {
        0
    } else {
        2
    }
}

/// FX spot: T+1, T+0 or T+2 by the pair, see spot_lag
pub fn spot(market: &dyn Market, trade: Date, pair: Option<&str>) -> R<Date> {
    plus_business_days(trade, spot_lag(pair), market)
}

fn imm_of(year: i64, month: u32) -> R<Date> {
    let first = date(year, month as i64, 1)?;
    add_days(first, ((2 - weekday(first) as i64).rem_euclid(7)) + 14)
}

/// the IMM date (third Wednesday of March, June, September or December) strictly after d
pub fn next_imm(d: Date) -> R<Date> {
    let (mut year, mut month) = (d.year() as i64, d.month());
    loop {
        if month % 3 == 0 {
            let imm = imm_of(year, month)?;
            if imm > d {
                return Ok(imm);
            }
        }
        (year, month) = if month == 12 { (year + 1, 1) } else { (year, month + 1) };
    }
}

/// the IMM date strictly before d
pub fn previous_imm(d: Date) -> R<Date> {
    let (mut year, mut month) = (d.year() as i64, d.month());
    loop {
        if month % 3 == 0 {
            let imm = imm_of(year, month)?;
            if imm < d {
                return Ok(imm);
            }
        }
        (year, month) = if month == 1 { (year - 1, 12) } else { (year, month - 1) };
    }
}

/// FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month, then preceding), MODPRECEDING, NEAREST;
/// BOW, EOW, BOM, EOM, BOQ, EOQ, BOH, EOH, BOY, EOY (first and last day of week (Monday to Sunday), month, quarter,
/// half year and year)
pub fn roll(d: Date, convention: Roll, m: &dyn Market) -> R<Date> {
    let year = d.year() as i64;
    match convention {
        Roll::Following => if m.is_business_day(d)? { Ok(d) } else { plus_business_days(d, 1, m) },
        Roll::Preceding => if m.is_business_day(d)? { Ok(d) } else { plus_business_days(d, -1, m) },
        Roll::ModFollowing => {
            let following = roll(d, Roll::Following, m)?;
            if following.month() == d.month() { Ok(following) } else { roll(d, Roll::Preceding, m) }
        }
        Roll::Nearest => {
            if m.is_business_day(d)? {
                Ok(d)
            } else {
                roll(d, if matches!(weekday(d), 0 | 6) { Roll::Following } else { Roll::Preceding }, m)
            }
        }
        Roll::ModPreceding => {
            let preceding = roll(d, Roll::Preceding, m)?;
            if preceding.month() == d.month() { Ok(preceding) } else { roll(d, Roll::Following, m) }
        }
        Roll::Bow => Ok(period_of(d, PeriodKind::Week)?.0),
        Roll::Eow => Ok(period_of(d, PeriodKind::Week)?.1),
        Roll::Bom => Ok(period_of(d, PeriodKind::Month)?.0),
        Roll::Eom => Ok(period_of(d, PeriodKind::Month)?.1),
        Roll::Boq => Ok(period_of(d, PeriodKind::Quarter)?.0),
        Roll::Eoq => Ok(period_of(d, PeriodKind::Quarter)?.1),
        Roll::Boh => Ok(period_of(d, PeriodKind::HalfYear)?.0),
        Roll::Eoh => Ok(period_of(d, PeriodKind::HalfYear)?.1),
        Roll::Boy => date(year, 1, 1),
        Roll::Eoy => date(year, 12, 31),
    }
}

/// the first and last day of the WEEK (Monday to Sunday), MONTH, QUARTER, HALFYEAR or YEAR of d
fn period_of(d: Date, kind: PeriodKind) -> R<(Date, Date)> {
    let months = match kind {
        PeriodKind::Week => {
            let first = add_days(d, -(weekday(d) as i64))?;
            return Ok((first, add_days(first, 6)?));
        }
        PeriodKind::Month => 1,
        PeriodKind::Quarter => 3,
        PeriodKind::HalfYear => 6,
        PeriodKind::Year => 12,
    };
    let year = d.year() as i64;
    let first_month = (d.month() - 1) / months * months + 1;
    let last_month = first_month + months - 1;
    Ok((date(year, first_month as i64, 1)?, date(year, last_month as i64, month_length(year, last_month) as i64)?))
}

/// the last business day of the month of d
fn last_business_day(d: Date, m: &dyn Market) -> R<Date> {
    roll(roll(d, Roll::Eom, m)?, Roll::Preceding, m)
}

// ================= anchors =================

/// the first match of the anchor strictly after (direction 1) or before (-1) the date
fn find(anchor: &Anchor, d: Date, direction: i64, c: &Ctx) -> R<Date> {
    match anchor {
        Anchor::Weekday(w) => {
            let days = ((*w as i64 - weekday(d) as i64) * direction).rem_euclid(7);
            add_days(d, direction * if days == 0 { 7 } else { days })
        }
        Anchor::BusinessDay(on) => plus_business_days(d, direction, &*market(on, c, None)?),
        Anchor::FixingDay => plus_business_days(d, direction, &*c.fixing),
        Anchor::Imm => if direction > 0 { next_imm(d) } else { previous_imm(d) },
        Anchor::Annual(a) => {
            let found = annual(a, d.year() as i64)?;
            if (direction > 0 && found > d) || (direction < 0 && found < d) {
                Ok(found)
            } else {
                annual(a, d.year() as i64 + direction)
            }
        }
        Anchor::Event(name) => {
            for year in [d.year() as i64, d.year() as i64 + direction] {
                let found: Vec<Date> = occurrences(name, year, c)?
                    .into_iter()
                    .filter(|o| if direction > 0 { *o > d } else { *o < d })
                    .collect();
                if let Some(o) = if direction > 0 { found.first() } else { found.last() } {
                    return Ok(*o);
                }
            }
            fail(format!("no {name} {} {d}", if direction > 0 { "after" } else { "before" }))
        }
        Anchor::Holiday(on) => {
            let m = market(on, c, None)?;
            next_day(d, direction, |x| m.is_holiday(x))
        }
        Anchor::NonHoliday(on) => {
            let m = market(on, c, None)?;
            next_day(d, direction, |x| Ok(!m.is_holiday(x)?))
        }
        // NEXT {MON, FRI}, NEXT {1 FEB, 1 MAY}: the nearest match of any of the anchors
        Anchor::AnyOf(anchors) => {
            let mut best: Option<Date> = None;
            for a in anchors {
                let found = find(a, d, direction, c)?;
                best = Some(match best {
                    None => found,
                    Some(b) if direction > 0 => b.min(found),
                    Some(b) => b.max(found),
                });
            }
            Ok(best.expect("at least one anchor"))
        }
    }
}

/// the first day strictly after (before) d that passes the test
pub fn next_day(mut d: Date, direction: i64, test: impl Fn(Date) -> R<bool>) -> R<Date> {
    for _ in 0..3700 {
        d = add_days(d, direction)?;
        if test(d)? {
            return Ok(d);
        }
    }
    fail(format!("no such day within 3700 days of {d}"))
}

/// the local date and time of an instant at a fixed offset (minutes east of UTC) or in an IANA zone
fn local_time(instant: DateTime<Utc>, zone: &Zone) -> R<NaiveDateTime> {
    match zone {
        Zone::Offset(minutes) => match FixedOffset::east_opt(*minutes as i32 * 60) {
            Some(offset) => Ok(instant.with_timezone(&offset).naive_local()),
            None => fail(format!("no time zone UTC{:+}", minutes / 60)),
        },
        Zone::Iana(name) => match name.parse::<chrono_tz::Tz>() {
            Ok(tz) => Ok(instant.with_timezone(&tz).naive_local()),
            Err(_) => fail(format!("no time zone {name}")),
        },
    }
}

/// > CUTOFF hh:mm zone: the date of the base instant in the zone, the next one from the cutoff time on
fn cutoff(hours: u32, minutes: u32, zone: &Zone, c: &Ctx) -> R<Date> {
    let Some(instant) = c.instant else { return fail("CUTOFF needs a base with a time of day") };
    let local = local_time(instant, zone)?;
    if (local.hour(), local.minute()) >= (hours, minutes) {
        add_days(local.date(), 1)
    } else {
        Ok(local.date())
    }
}

/// the date itself if it matches, else the nearer of the next and the previous match; ties go forward
fn nearest(anchor: &Anchor, d: Date, c: &Ctx) -> R<Date> {
    if find(anchor, add_days(d, -1)?, 1, c)? == d {
        return Ok(d);
    }
    let after = find(anchor, d, 1, c)?;
    let before = find(anchor, d, -1, c)?;
    Ok(if after - d <= d - before { after } else { before })
}

/// > 3RD WED IN MONTH: the anchor found count times from the day before the period starts; > 2ND LAST BD IN MONTH:
/// count times backwards from the day after it ends; an error if that leaves the period
fn select(anchor: &Anchor, count: i64, from_end: bool, kind: PeriodKind, d: Date, c: &Ctx) -> R<Date> {
    if count < 1 {
        return fail(format!("count must be positive: {count}"));
    }
    let (first, last) = period_of(d, kind)?;
    let mut found = if from_end { add_days(last, 1)? } else { add_days(first, -1)? };
    for _ in 0..count {
        found = find(anchor, found, if from_end { -1 } else { 1 }, c)?;
    }
    if found < first || found > last {
        return fail(format!("no {count}. match in the {kind:?} of {d}"));
    }
    Ok(found)
}

// ================= conditions =================

fn condition(cond: &Condition, d: Date, c: &Ctx) -> R<bool> {
    Ok(match cond {
        Condition::Any(parts) => {
            for p in parts {
                if condition(p, d, c)? {
                    return Ok(true);
                }
            }
            false
        }
        Condition::All(parts) => {
            for p in parts {
                if !condition(p, d, c)? {
                    return Ok(false);
                }
            }
            true
        }
        Condition::Not(part) => !condition(part, d, c)?,
        Condition::OnWeekday(days) => days.contains(weekday(d)),
        Condition::OnDay { first, last, month } => {
            *first <= d.day() && d.day() <= *last && month.map_or(true, |m| d.month() == m)
        }
        Condition::IsWeekday(subject, days) => days.contains(weekday(single(subject, c)?)),
        Condition::Holiday(on) => market(on, c, None)?.is_holiday(d)?,
        Condition::DateIsHoliday(subject, on) => market(on, c, None)?.is_holiday(single(subject, c)?)?,
        Condition::BetweenHolidays(on) => {
            let m = market(on, c, None)?;
            m.is_holiday(add_days(d, -1)?)? && m.is_holiday(add_days(d, 1)?)?
        }
        Condition::Every { years, first } => {
            let year = d.year() as i64;
            year >= *first && (year - first) % years == 0
        }
    })
}

// ================= annual dates =================

/// an annual date in the year; a lunar date must occur exactly once in it
pub fn annual(a: &Annual, year: i64) -> R<Date> {
    match a {
        Annual::MonthDay(day, Month::Gregorian(m)) => date(year, *m as i64, *day as i64),
        Annual::MonthDay(day, month) => calendars::lunar(*day, *month, year),
        Annual::NthWeekday(n, wd, month) => {
            let Month::Gregorian(m) = month else { return fail("not a Gregorian month") };
            let first = date(year, *m as i64, 1)?;
            if !(1..=5).contains(n) {
                return fail(format!("no {n}. weekday {wd} of month {m} in {year}"));
            }
            let d = add_days(first, (*wd as i64 - weekday(first) as i64).rem_euclid(7) + 7 * (n - 1))?;
            if d.month() != *m {
                return fail(format!("no {n}. weekday {wd} of month {m} in {year}"));
            }
            Ok(d)
        }
        Annual::LastWeekday(wd, month) => {
            let Month::Gregorian(m) = month else { return fail("not a Gregorian month") };
            let last = date(year, *m as i64, month_length(year, *m) as i64)?;
            add_days(last, -((weekday(last) as i64 - *wd as i64).rem_euclid(7)))
        }
        Annual::Easter => calendars::easter(year),
        Annual::OrthodoxEaster => calendars::orthodox_easter(year),
        Annual::Sun(angle, zone) => calendars::sun_at(*angle, year, zone),
        Annual::Hindu(key) => fail(format!("HINDU {key}: no Hindu calendar in this implementation")),
    }
}
