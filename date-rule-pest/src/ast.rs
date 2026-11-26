//! The syntax tree that both grammars (schedule.pest, rules.pest) are built into; semantics.rs evaluates it.

use chrono::NaiveDate;

pub type Date = NaiveDate;

/// months and days as in java.time.Period, or business days (unit B) or fixing days (unit F)
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Period {
    pub months: i64,
    pub days: i64,
    pub business: i64,
    pub fixing: i64,
}

impl Period {
    pub fn times(&self, k: i64) -> Period {
        Period { months: k * self.months, days: k * self.days, business: k * self.business, fixing: k * self.fixing }
    }

    pub fn is_positive(&self) -> bool {
        self.months > 0 || self.days > 0 || self.business > 0 || self.fixing > 0
    }
}

/// Monday = 0 ... Sunday = 6
pub type Weekday = u32;

/// a set of weekdays, bit 0 = Monday
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Weekdays(pub u8);

impl Weekdays {
    pub fn contains(&self, weekday: Weekday) -> bool {
        self.0 & (1 << weekday) != 0
    }
}

/// a month of the Gregorian calendar (1 to 12) or of a lunar calendar
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Month {
    Gregorian(u32),
    /// Umm al-Qura, MUHARRAM = 1
    Hijri(u32),
    /// NISAN = 1, ADAR = 12 (Adar I in a leap year), ADARII = 13
    Hebrew(u32),
    /// ZHENGYUE = 1; leap months have no name
    Chinese(u32),
}

/// the time zone of an equinox, solstice or solar term
#[derive(Clone, Debug, PartialEq)]
pub enum Zone {
    /// minutes east of UTC
    Offset(i64),
    /// an IANA zone such as "Asia/Tokyo"
    Iana(String),
}

/// a date that recurs every year, a function of the year
#[derive(Clone, Debug)]
pub enum Annual {
    MonthDay(u32, Month),
    NthWeekday(i64, Weekday, Month),
    LastWeekday(Weekday, Month),
    Easter,
    OrthodoxEaster,
    /// the sun at the angle (degrees): equinoxes, solstices and solar terms
    Sun(i32, Zone),
    Hindu(String),
}

/// a calendar of ON and of calendar declarations
#[derive(Clone, Debug)]
pub enum Cal {
    Scope(String, Option<Ignore>),
    Named(String),
    Combined(Vec<Cal>),
}

/// ignore at T + days unless {currencies}
#[derive(Clone, Debug)]
pub struct Ignore {
    pub days: u32,
    pub currencies: Vec<String>,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Roll {
    Following,
    Preceding,
    ModFollowing,
    ModPreceding,
    /// > NEAREST ON cal (rule files): a non-business Sunday or Monday follows, any other day precedes (FpML)
    Nearest,
    Bow,
    Eow,
    Bom,
    Eom,
    Boq,
    Eoq,
    Boh,
    Eoh,
    Boy,
    Eoy,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum PeriodKind {
    Week,
    Month,
    Quarter,
    HalfYear,
    Year,
}

/// the targets of NEXT, PREV, NEAREST and selections: found strictly after (direction 1) or before (-1) a date
#[derive(Clone, Debug)]
pub enum Anchor {
    Weekday(Weekday),
    BusinessDay(Option<Cal>),
    FixingDay,
    Imm,
    Annual(Annual),
    Event(String),
    Holiday(Option<Cal>),
    NonHoliday(Option<Cal>),
    AnyOf(Vec<Anchor>),
}

/// what an observance entry does with a date on one of its weekdays
#[derive(Clone, Debug)]
pub enum Move {
    Plus(Period, i64),
    Find(Anchor, i64),
    Nearest(Anchor),
    Roll(Roll, Option<Cal>),
}

#[derive(Clone, Debug)]
pub enum Shift {
    Plus { period: Period, sign: i64, endend: bool, on: Option<Cal> },
    Imm,
    Spot { pair: Option<String>, on: Option<Cal> },
    Fix(Option<String>),
    Roll(Roll, Option<Cal>),
    /// NEXT (direction 1) and PREV (-1): the anchor found count times
    Find { count: i64, anchor: Anchor, direction: i64 },
    Nearest(Anchor),
    /// > CUTOFF hh:mm zone: the date of the base instant in the zone, the next one from the cutoff time on
    Cutoff { hours: u32, minutes: u32, zone: Zone },
    Select { count: i64, from_end: bool, anchor: Anchor, period: PeriodKind },
    DatesInYear(Vec<Annual>),
    Postpone { roll: Roll, on: Option<Cal>, limit: Period, cumulative: bool },
    Observance(Vec<(Weekdays, Move)>),
}

/// [first : step : last]; an open series (last None) only under a pick
#[derive(Clone, Debug)]
pub struct Series {
    pub first: Expr,
    pub step: Period,
    pub last: Option<Expr>,
    pub start_out: bool,
    pub end_out: bool,
}

#[derive(Clone, Debug)]
pub enum DateNode {
    Base,
    Tom,
    Spot { pair: Option<String>, on: Option<Cal> },
    Fixed(Date),
    InYear(Annual),
    Event(String),
    Series(Box<Series>),
}

/// a date, then the shifts from left to right
#[derive(Clone, Debug)]
pub struct Expr {
    pub start: DateNode,
    pub shifts: Vec<Shift>,
}

/// A & B (include), A \ B (exclude) with an operand of one or more expressions, then shifts
#[derive(Clone, Debug)]
pub struct Combine {
    pub include: bool,
    pub operand: Vec<Expr>,
    pub shifts: Vec<Shift>,
}

#[derive(Clone, Debug)]
pub enum Condition {
    Any(Vec<Condition>),
    All(Vec<Condition>),
    Not(Box<Condition>),
    OnWeekday(Weekdays),
    OnDay { first: u32, last: u32, month: Option<u32> },
    IsWeekday(Expr, Weekdays),
    Holiday(Option<Cal>),
    DateIsHoliday(Expr, Option<Cal>),
    BetweenHolidays(Option<Cal>),
    Every { years: i64, first: i64 },
}

/// IF condition, then the shifts for the dates it keeps
#[derive(Clone, Debug)]
pub struct IfClause {
    pub condition: Condition,
    pub shifts: Vec<Shift>,
}

/// an expression with its combines and condition
#[derive(Clone, Debug)]
pub struct ItemCore {
    pub expr: Expr,
    pub combines: Vec<Combine>,
    pub clause: Option<IfClause>,
}

#[derive(Clone, Debug)]
pub enum Item {
    Plain(ItemCore),
    /// 5TH [SPOT : 1Q : ] > EOQ: the count-th date after T of the item, from the end with LAST
    Picked { count: i64, from_end: bool, core: ItemCore },
}

/// a list of items: all their dates, in order
#[derive(Clone, Debug)]
pub struct Exprs(pub Vec<Item>);
