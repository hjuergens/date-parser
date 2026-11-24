//! Lunar months (Umm al-Qura, Hebrew, Chinese), Easter and the sun's longitude (equinoxes, solstices, solar terms).
//!
//! The Umm al-Qura and Chinese months come from the tables of date-rule-flex-bison/src/lunar_tables.h (generated there
//! from hijridate and lunardate; build.rs turns them into Rust); the Hebrew calendar is computed (Reingold and
//! Dershowitz, Calendrical Calculations), the sun with VSOP87 (Meeus, Astronomical Algorithms), as in the C module.

use chrono::{DateTime, Datelike};

use crate::ast::{Date, Month, Zone};
use crate::semantics::{add_days, date, epoch_day, fail, from_epoch_day, R};

include!(concat!(env!("OUT_DIR"), "/lunar_tables.rs"));

fn floor_div(a: i64, b: i64) -> i64 {
    a.div_euclid(b)
}

// ================= Hebrew calendar: months from NISAN = 1, ADAR = 12 (Adar I in a leap year), ADARII = 13 =================

fn hebrew_leap(year: i64) -> bool {
    (7 * year + 1).rem_euclid(19) < 7
}

fn hebrew_elapsed(year: i64) -> i64 {
    let months = floor_div(235 * year - 234, 19);
    let parts = 12084 + 13753 * months;
    let day = 29 * months + floor_div(parts, 25920);
    if (3 * (day + 1)).rem_euclid(7) < 3 { day + 1 } else { day }
}

/// R.D. day (0001-01-01 is 1) of 1 TISHRI of the year
fn hebrew_new_year(year: i64) -> i64 {
    let (before, now, after) = (hebrew_elapsed(year - 1), hebrew_elapsed(year), hebrew_elapsed(year + 1));
    let correction = if after - now == 356 { 2 } else if now - before == 382 { 1 } else { 0 };
    -1373427 + now + correction
}

fn hebrew_month_length(year: i64, month: u32) -> i64 {
    let days = hebrew_new_year(year + 1) - hebrew_new_year(year);
    match month {
        2 | 4 | 6 | 10 | 13 => 29,
        12 if !hebrew_leap(year) => 29,
        8 if days % 10 != 5 => 29, // Heshvan is long in a year of 355 or 385 days
        9 if days % 10 == 3 => 29, // Kislev is short in a year of 353 or 383 days
        _ => 30,
    }
}

/// days since 1970-01-01 of a Hebrew date; the year begins with TISHRI = 7
fn hebrew_day(year: i64, month: u32, day: i64) -> i64 {
    let mut rd = hebrew_new_year(year) + day - 1;
    let last = if hebrew_leap(year) { 13 } else { 12 };
    if month < 7 {
        rd += (7..=last).map(|m| hebrew_month_length(year, m)).sum::<i64>();
        rd += (1..month).map(|m| hebrew_month_length(year, m)).sum::<i64>();
    } else {
        rd += (7..month).map(|m| hebrew_month_length(year, m)).sum::<i64>();
    }
    rd - 719163
}

// ================= lunar months in a Gregorian year =================

/// the year of the table that a day (days since 1970-01-01) falls into, or the first year - 1
fn table_year(months: &[[[i64; 2]; 12]], first: i64, day: i64) -> i64 {
    let mut year = first - 1;
    for (i, m) in months.iter().enumerate() {
        if m[0][0] <= day {
            year = first + i as i64;
        } else {
            break;
        }
    }
    year
}

/// the days of a Hijri, Hebrew or Chinese month in the Gregorian year: one, or two when the lunar year is shorter than
/// the Gregorian one (1 RAMADAN in 2030: 5 January and 26 December); an error if there is none
pub fn lunar_dates(day: u32, month: Month, year: i64) -> R<Vec<Date>> {
    let day = day as i64;
    let mut candidates = vec![];
    match month {
        Month::Hijri(m) => {
            let end = HIJRI_MONTHS[HIJRI_MONTHS.len() - 1][11];
            if !(1..=9999).contains(&year) {
                return fail(format!("no Hijri dates in {year}"));
            }
            let (jan1, dec31) = (epoch_day(date(year, 1, 1)?), epoch_day(date(year, 12, 31)?));
            if jan1 < HIJRI_MONTHS[0][0][0] || dec31 >= end[0] + end[1] {
                return fail(format!("no Hijri dates in {year}"));
            }
            let first = table_year(HIJRI_MONTHS, HIJRI_FIRST_YEAR, jan1);
            let last = table_year(HIJRI_MONTHS, HIJRI_FIRST_YEAR, dec31);
            for y in first..=last {
                let [start, length] = HIJRI_MONTHS[(y - HIJRI_FIRST_YEAR) as usize][m as usize - 1];
                if (1..=length).contains(&day) {
                    candidates.push(start + day - 1);
                }
            }
        }
        Month::Hebrew(m) => {
            for y in year + 3760..=year + 3761 {
                if y >= 1 && (m < 13 || hebrew_leap(y)) && (1..=hebrew_month_length(y, m)).contains(&day) {
                    candidates.push(hebrew_day(y, m, day));
                }
            }
        }
        Month::Chinese(m) => {
            for y in year - 1..=year {
                if !(CHINESE_FIRST_YEAR..=CHINESE_LAST_YEAR).contains(&y) {
                    continue;
                }
                let [start, length] = CHINESE_MONTHS[(y - CHINESE_FIRST_YEAR) as usize][m as usize - 1];
                if (1..=length).contains(&day) {
                    candidates.push(start + day - 1);
                }
            }
        }
        Month::Gregorian(m) => return date(year, m as i64, day).map(|d| vec![d]),
    }
    let mut hits: Vec<Date> =
        candidates.into_iter().filter_map(|c| from_epoch_day(c).ok()).filter(|d| d.year() as i64 == year).collect();
    if hits.is_empty() {
        return fail(format!("{day} {month:?} occurs 0 times in {year}"));
    }
    hits.sort();
    Ok(hits)
}

/// the day of a lunar month in the Gregorian year, where one date is needed (an anchor, a condition); an error if it
/// occurs twice or not at all
pub fn lunar(day: u32, month: Month, year: i64) -> R<Date> {
    let hits = lunar_dates(day, month, year)?;
    if hits.len() != 1 {
        return fail(format!("{day} {month:?} occurs {} times in {year}", hits.len()));
    }
    Ok(hits[0])
}

// ================= Easter =================

/// Western Easter Sunday (anonymous Gregorian algorithm)
pub fn easter(year: i64) -> R<Date> {
    let (a, b, c) = (year % 19, year / 100, year % 100);
    let (d, e) = (b / 4, b % 4);
    let g = (8 * b + 13) / 25;
    let h = (19 * a + b - d - g + 15) % 30;
    let (i, k) = (c / 4, c % 4);
    let l = (32 + 2 * e + 2 * i - h - k) % 7;
    let m = (a + 11 * h + 22 * l) / 451;
    let n = h + l - 7 * m + 114;
    date(year, n / 31, n % 31 + 1)
}

/// Orthodox Easter Sunday: the Julian computus (Meeus), converted to the Gregorian calendar
pub fn orthodox_easter(year: i64) -> R<Date> {
    let (a, b, c) = (year % 4, year % 7, year % 19);
    let d = (19 * c + 15) % 30;
    let e = (2 * a + 4 * b - d + 34) % 7;
    let (month, day) = ((d + e + 114) / 31, (d + e + 114) % 31);
    let k = (14 - month) / 12;
    let (y, m) = (year + 4800 - k, month + 12 * k - 3);
    let jdn = day + 1 + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
    from_epoch_day(jdn - 2440588)
}

// ================= the sun =================

// the terms of VSOP87D (Meeus, Astronomical Algorithms, appendix III) with A >= 100: A (1e-8 rad or AU), B, C
const L0: &[[f64; 3]] = &[
    [175347046.0, 0.0, 0.0], [3341656.0, 4.6692568, 6283.07585], [34894.0, 4.6261024, 12566.1517],
    [3418.0, 2.8288658, 3.5231183], [3497.0, 2.744118, 5753.3848849], [3136.0, 3.6276704, 77713.7714681],
    [2676.0, 4.4180835, 7860.4193924], [2343.0, 6.1351624, 3930.2096962], [1273.0, 2.0370966, 529.6909651],
    [1324.0, 0.7424636, 11506.7697698], [902.0, 2.0450544, 26.2983198], [1199.0, 1.1096294, 1577.3435424],
    [857.0, 3.5084916, 398.1490034], [780.0, 1.1788265, 5223.6939198], [990.0, 5.2326813, 5884.9268466],
    [753.0, 2.5333905, 5507.5532387], [505.0, 4.5829256, 18849.22755], [492.0, 4.2050664, 775.5226113],
    [357.0, 2.9195412, 0.0673103], [284.0, 1.8986903, 796.2980068], [243.0, 0.3448114, 5486.7778432],
    [317.0, 5.8490195, 11790.6290887], [271.0, 0.3148861, 10977.0788047], [206.0, 4.8064661, 2544.3144199],
    [205.0, 1.8694781, 5573.1428014], [202.0, 2.457678, 6069.7767546], [126.0, 1.0830263, 20.7753955],
    [156.0, 0.8330607, 213.2990954], [115.0, 0.6454491, 0.9803211], [103.0, 0.6359985, 4694.0029547],
    [102.0, 4.2667982, 7.113547], [132.0, 3.4111828, 2942.4634233], [102.0, 0.9756922, 15720.8387849],
];
const L1: &[[f64; 3]] = &[
    [628331966747.0, 0.0, 0.0], [206059.0, 2.6782346, 6283.07585], [4303.0, 2.6351265, 12566.1517],
    [425.0, 1.5904698, 3.5231183], [109.0, 2.96618, 1577.3435424], [119.0, 5.7955749, 26.2983198],
];
const L2: &[[f64; 3]] = &[[52919.0, 0.0, 0.0], [8720.0, 1.0720967, 6283.07585], [309.0, 0.8672882, 12566.1517]];
const L3: &[[f64; 3]] = &[[289.0, 5.843842, 6283.07585]];
const L4: &[[f64; 3]] = &[[114.0, 3.1415927, 0.0]];
const R0: &[[f64; 3]] = &[[100013989.0, 0.0, 0.0], [1670700.0, 3.0984635, 6283.07585], [13956.0, 3.0552461, 12566.1517]];
const R1: &[[f64; 3]] = &[[103019.0, 1.1074897, 6283.07585]];

fn vsop(series: &[&[[f64; 3]]], tau: f64) -> f64 {
    let mut total = 0.0;
    for terms in series.iter().rev() {
        total = total * tau + terms.iter().map(|[a, b, c]| a * (b + c * tau).cos()).sum::<f64>();
    }
    total * 1e-8
}

/// the apparent longitude of the sun in degrees (Meeus, chapter 25, with VSOP87, nutation and aberration)
fn sun_longitude(jde: f64) -> f64 {
    let tau = (jde - 2451545.0) / 365250.0;
    let t = 10.0 * tau;
    let omega = (125.04452 - 1934.136261 * t).to_radians();
    let sun = (280.4665 + 36000.7698 * t).to_radians();
    let moon = (218.3165 + 481267.8813 * t).to_radians();
    let nutation =
        -17.20 * omega.sin() - 1.32 * (2.0 * sun).sin() - 0.23 * (2.0 * moon).sin() + 0.21 * (2.0 * omega).sin();
    vsop(&[L0, L1, L2, L3, L4], tau).to_degrees() + 180.0
        + (nutation - 0.09033 - 20.4898 / vsop(&[R0, R1], tau)) / 3600.0
}

/// TD - UT in seconds: Espenak and Meeus' polynomials for 1986 to 2150, their long-term formula outside
fn delta_t(year: f64) -> f64 {
    let (t, u) = (year - 2000.0, (year - 1820.0) / 100.0);
    let long_term = -20.0 + 32.0 * u * u;
    if (1986.0..2005.0).contains(&year) {
        return 63.86 + 0.3345 * t - 0.060374 * t.powi(2) + 0.0017275 * t.powi(3) + 0.000651814 * t.powi(4)
            + 0.00002373599 * t.powi(5);
    }
    if (2005.0..2050.0).contains(&year) {
        return 62.92 + 0.32217 * t + 0.005589 * t * t;
    }
    if (2050.0..2150.0).contains(&year) { long_term - 0.5628 * (2150.0 - year) } else { long_term }
}

/// the instant (days since 1970-01-01, UT) at which the sun's apparent longitude reaches the degrees in the year
fn sun_universal(degrees: i32, year: i64) -> R<f64> {
    let mut day = 79.0 + degrees as f64 * 365.2422 / 360.0; // days after 1 January, from the March equinox on
    if day > 365.2422 {
        day -= 365.2422;
    }
    let mut jde = epoch_day(date(year, 1, 1)?) as f64 + 2440587.5 + day;
    for _ in 0..50 {
        let step = 58.0 * (degrees as f64 - sun_longitude(jde)).to_radians().sin();
        jde += step;
        if step.abs() < 1e-7 {
            break;
        }
    }
    Ok(jde - delta_t(year as f64 + day / 365.25) / 86400.0 - 2440587.5)
}

/// the day in the year on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes or in an
/// IANA time zone
pub fn sun_at(degrees: i32, year: i64, zone: &Zone) -> R<Date> {
    let instant = sun_universal(degrees, year)?;
    match zone {
        Zone::Offset(minutes) => add_days(date(1970, 1, 1)?, (instant + *minutes as f64 / 1440.0).floor() as i64),
        Zone::Iana(name) => {
            let Ok(tz) = name.parse::<chrono_tz::Tz>() else { return fail(format!("no time zone {name}")) };
            let micros = (instant * 86400.0 * 1e6).round() as i64;
            let Some(utc) = DateTime::from_timestamp_micros(micros) else { return fail("instant out of range") };
            Ok(utc.with_timezone(&tz).date_naive())
        }
    }
}
