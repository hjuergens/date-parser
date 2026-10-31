package io.github.hjuergens.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.time.temporal.JulianFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.HebrewCalendar;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

/**
 * Semantics of the shared "schedule" example. A parsed schedule is a {@link Rule}: a function of the base date
 * and the market, both only known when the rule is evaluated.
 */
public final class ScheduleSemantics {

    private ScheduleSemantics() {
    }

    /** a market answers questions about days; the meaning of the rules doesn't depend on it */
    public interface Market {

        boolean isBusinessDay(LocalDate date);

        /** the FX spot date: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair) */
        default LocalDate spot(LocalDate trade, String pair) {
            int lag = pair != null && pair.matches("USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD") ? 1 : 2;
            return plusBusinessDays(trade, lag, this);
        }
    }

    /** the default market: Monday to Friday, no holidays */
    public static final Market WEEKENDS = date -> date.getDayOfWeek().getValue() < 6;

    /** Monday to Friday except the given holidays */
    public static Market withHolidays(Set<LocalDate> holidays) {
        return date -> WEEKENDS.isBusinessDay(date) && !holidays.contains(date);
    }

    /**
     * Monday to Friday except the dates the rule yields; the rule is evaluated with the first of January of a year as
     * base date, for the year before and after as well (an observed holiday can fall into the neighbouring year)
     */
    public static Market withHolidayRules(Rule rule) {
        Map<Integer, List<LocalDate>> years = new HashMap<>();
        return date -> {
            if (!WEEKENDS.isBusinessDay(date)) {
                return false;
            }
            for (int year = date.getYear() - 1; year <= date.getYear() + 1; year++) {
                if (years.computeIfAbsent(year, y -> rule.evaluate(new Context(LocalDate.of(y, 1, 1), null)))
                        .contains(date)) {
                    return false;
                }
            }
            return true;
        };
    }

    /** what a rule is evaluated against: the base date (only needed for T) and the market (default: weekends only) */
    public record Context(LocalDate base, Market market) {
        public Context {
            market = market == null ? WEEKENDS : market;
        }
    }

    /** a date, as soon as base date and market are known */
    public interface Expr {
        LocalDate at(Context context);
    }

    /** a parsed schedule */
    public interface Rule {
        List<LocalDate> evaluate(Context context);
    }

    public static Expr literal(LocalDate date) {
        return context -> date;
    }

    /** T: the base date of the evaluation */
    public static final Expr BASE = context -> {
        if (context.base() == null) {
            throw new IllegalStateException("no base date");
        }
        return context.base();
    };

    public static Expr plus(Expr expr, TemporalAmount amount) {
        return context -> plus(expr.at(context), amount, 1, context.market());
    }

    public static Expr minus(Expr expr, TemporalAmount amount) {
        return context -> plus(expr.at(context), amount, -1, context.market());
    }

    public static Expr nextImm(Expr expr) {
        return context -> nextImm(expr.at(context));
    }

    public static Expr spot(Expr expr, String pair) {
        return context -> context.market().spot(expr.at(context), pair);
    }

    /** a date that recurs every year, such as the third Monday of January */
    public interface Annual {
        LocalDate in(int year);
    }

    /** an annual date in the year of the base date */
    public static Expr inYear(Annual annual) {
        return context -> annual.in(BASE.at(context).getYear());
    }

    /** day of month, e.g. 4 JUL, or of a lunar month, e.g. 1 SHAWWAL */
    public static Annual monthDay(int day, String month) {
        if (!MONTHS.contains(month)) {
            return year -> lunar(day, month, year);
        }
        return year -> LocalDate.of(year, month(month), day);
    }

    /** the day of a Hijri (Umm al-Qura), Hebrew or Chinese month in the Gregorian year; an error if it occurs twice or not at all */
    private static LocalDate lunar(int day, String month, int year) {
        Calendar hebrew = new HebrewCalendar();
        List<LocalDate> hits = new ArrayList<>();
        for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
            if (lunarMonth(date, month, hebrew).equals(month) && dayOfMonth(date, month, hebrew) == day) {
                hits.add(date);
            }
        }
        if (hits.size() != 1) {
            throw new java.time.DateTimeException(day + " " + month + " occurs " + hits.size() + " times in " + year);
        }
        return hits.get(0);
    }

    // ICU's Hebrew months start with Tishri; ADAR_1 (5) only exists in a leap year, where ADAR (6) is Adar II
    private static final List<String> ICU_HEBREW = List.of("TISHRI", "HESHVAN", "KISLEV", "TEVET", "SHEVAT", "ADAR", "ADAR",
            "NISAN", "IYAR", "SIVAN", "TAMMUZ", "AV", "ELUL");

    /** the name of the month of the date in the calendar of the given month name; "" for a Chinese leap month */
    private static String lunarMonth(LocalDate date, String like, Calendar hebrew) {
        if (HIJRI.contains(like)) {
            return HIJRI.get(HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) - 1);
        }
        if (CHINESE.contains(like)) {
            int month = chinese(date).getMonth();   // negative for a leap month
            return month > 0 ? CHINESE.get(month - 1) : "";
        }
        hebrew.clear();
        hebrew.set(Calendar.JULIAN_DAY, (int) date.getLong(JulianFields.JULIAN_DAY));
        int month = hebrew.get(Calendar.MONTH);
        boolean leap = (7 * hebrew.get(Calendar.EXTENDED_YEAR) + 1) % 19 < 7;
        return month == 6 && leap ? "ADARII" : ICU_HEBREW.get(month);
    }

    private static int dayOfMonth(LocalDate date, String like, Calendar hebrew) {
        if (HIJRI.contains(like)) {
            return HijrahDate.from(date).get(ChronoField.DAY_OF_MONTH);
        }
        return CHINESE.contains(like) ? chinese(date).getDay() : hebrew.get(Calendar.DAY_OF_MONTH);
    }

    /** the Chinese date from 6tail's lunar: ICU's astronomical Chinese calendar misses some new moons close to midnight */
    private static Lunar chinese(LocalDate date) {
        return Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar();
    }

    /** the n-th weekday of the month, e.g. 3 MON JAN; there is no fifth Monday in every month */
    public static Annual nthWeekday(int n, String weekday, String month) {
        return year -> {
            LocalDate date = LocalDate.of(year, month(month), 1)
                    .with(TemporalAdjusters.nextOrSame(dayOfWeek(weekday))).plusWeeks(n - 1);
            if (n < 1 || date.getMonthValue() != month(month)) {
                throw new java.time.DateTimeException("no " + n + " " + weekday + " " + month + " in " + year);
            }
            return date;
        };
    }

    /** the last weekday of the month, e.g. LAST MON MAY */
    public static Annual lastWeekday(String weekday, String month) {
        return year -> LocalDate.of(year, month(month), 1).with(TemporalAdjusters.lastInMonth(dayOfWeek(weekday)));
    }

    /** Western Easter Sunday (anonymous Gregorian algorithm) */
    public static final Annual EASTER = year -> {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int g = (8 * b + 13) / 25, h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    };

    /** what > NEXT and > PREV look for: the first match strictly after (direction 1) or before (-1) a date */
    public interface Anchor {
        LocalDate from(LocalDate date, int direction, Market market);
    }

    public static Anchor annual(Annual annual) {
        return (date, direction, market) -> {
            LocalDate match = annual.in(date.getYear());
            return Integer.signum(match.compareTo(date)) == direction ? match : annual.in(date.getYear() + direction);
        };
    }

    public static Anchor weekday(String weekday) {
        return (date, direction, market) -> date.with(direction > 0
                ? TemporalAdjusters.next(dayOfWeek(weekday)) : TemporalAdjusters.previous(dayOfWeek(weekday)));
    }

    public static final Anchor BUSINESS_DAY = (date, direction, market) -> plusBusinessDays(date, direction, market);

    public static final Anchor IMM = (date, direction, market) -> direction > 0 ? nextImm(date) : previousImm(date);

    public static Expr next(Expr expr, Anchor anchor) {
        return context -> anchor.from(expr.at(context), 1, context.market());
    }

    public static Expr previous(Expr expr, Anchor anchor) {
        return context -> anchor.from(expr.at(context), -1, context.market());
    }

    /**
     * FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month, then preceding);
     * BOM, EOM, BOY, EOY (first and last day of month and year)
     */
    public static Expr roll(Expr expr, String convention) {
        return context -> roll(expr.at(context), convention, context.market());
    }

    public static LocalDate roll(LocalDate date, String convention, Market market) {
        switch (convention) {
            case "FOLLOWING": return market.isBusinessDay(date) ? date : plusBusinessDays(date, 1, market);
            case "PRECEDING": return market.isBusinessDay(date) ? date : plusBusinessDays(date, -1, market);
            case "MODFOLLOWING": {
                LocalDate following = roll(date, "FOLLOWING", market);
                return following.getMonth() == date.getMonth() ? following : roll(date, "PRECEDING", market);
            }
            case "BOM": return date.withDayOfMonth(1);
            case "EOM": return date.with(TemporalAdjusters.lastDayOfMonth());
            case "BOY": return date.withDayOfYear(1);
            case "EOY": return date.with(TemporalAdjusters.lastDayOfYear());
            default: throw new IllegalArgumentException("unknown convention: " + convention);
        }
    }

    /** one item of an observance such as {SAT - 1D, SUN + 1D} */
    public record Shift(DayOfWeek weekday, TemporalAmount amount, int sign) {
    }

    public static Shift shift(String weekday, TemporalAmount amount, int sign) {
        return new Shift(dayOfWeek(weekday), amount, sign);
    }

    /** the first shift for the weekday of the date applies; shifts don't chain */
    public static Expr observe(Expr expr, List<Shift> shifts) {
        List<Shift> copy = new ArrayList<>(shifts);
        return context -> {
            LocalDate date = expr.at(context);
            for (Shift shift : copy) {
                if (shift.weekday() == date.getDayOfWeek()) {
                    return plus(date, shift.amount(), shift.sign(), context.market());
                }
            }
            return date;
        };
    }

    private static final List<String> MONTHS =
            List.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");
    private static final List<String> WEEKDAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final List<String> HIJRI = List.of("MUHARRAM", "SAFAR", "RABIALAWWAL", "RABIALTHANI", "JUMADAALULA",
            "JUMADAALAKHIRA", "RAJAB", "SHABAN", "RAMADAN", "SHAWWAL", "DHULQADAH", "DHULHIJJAH");
    private static final List<String> HEBREW = List.of("NISAN", "IYAR", "SIVAN", "TAMMUZ", "AV", "ELUL", "TISHRI", "HESHVAN",
            "KISLEV", "TEVET", "SHEVAT", "ADAR", "ADARII");
    private static final List<String> CHINESE = List.of("ZHENGYUE", "ERYUE", "SANYUE", "SIYUE", "WUYUE", "LIUYUE", "QIYUE",
            "BAYUE", "JIUYUE", "SHIYUE", "DONGYUE", "LAYUE");

    private static int month(String name) {
        return MONTHS.indexOf(name) + 1;
    }

    private static DayOfWeek dayOfWeek(String name) {
        return DayOfWeek.of(WEEKDAYS.indexOf(name) + 1);
    }

    /** a list of dates; an item that yields no date (see onlyOn) is left out */
    public static Rule list(List<Expr> exprs) {
        List<Expr> copy = new ArrayList<>(exprs);
        return context -> copy.stream().map(expr -> expr.at(context)).filter(Objects::nonNull).toList();
    }

    /** 24 DEC IF MON TUE WED THU: the date on these weekdays, otherwise none (null) */
    public static Expr onlyOn(Expr expr, List<String> weekdays) {
        Set<DayOfWeek> days = new HashSet<>();
        weekdays.forEach(weekday -> days.add(dayOfWeek(weekday)));
        return context -> {
            LocalDate date = expr.at(context);
            return days.contains(date.getDayOfWeek()) ? date : null;
        };
    }

    /** first + k * step for k = 0, 1, ... while not after last */
    public static Rule range(Expr first, TemporalAmount step, Expr last) {
        if (!isPositive(step)) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        return context -> range(first.at(context), step, last.at(context), context.market());
    }

    /** {@code count} times D (day), W (week), M (month), Q (quarter), Y (year) or B (business day) */
    public static TemporalAmount period(int count, String unit) {
        switch (unit) {
            case "D": return Period.ofDays(count);
            case "W": return Period.ofWeeks(count);
            case "M": return Period.ofMonths(count);
            case "Q": return Period.ofMonths(3 * count);
            case "Y": return Period.ofYears(count);
            case "B": return new BusinessDays(count);
            default: throw new IllegalArgumentException("unknown unit: " + unit);
        }
    }

    /** business days; which days these are depends on the market, so they are only applied by plus(…, market) */
    public record BusinessDays(int count) implements TemporalAmount {

        @Override
        public long get(TemporalUnit unit) {
            throw new UnsupportedTemporalTypeException("business days have no unit: " + unit);
        }

        @Override
        public List<TemporalUnit> getUnits() {
            return List.of();
        }

        @Override
        public Temporal addTo(Temporal temporal) {
            throw new UnsupportedTemporalTypeException("business days need a market");
        }

        @Override
        public Temporal subtractFrom(Temporal temporal) {
            throw new UnsupportedTemporalTypeException("business days need a market");
        }
    }

    public static boolean isPositive(TemporalAmount step) {
        if (step instanceof BusinessDays days) {
            return days.count() > 0;
        }
        Period period = (Period) step;
        return !period.isZero() && !period.isNegative();
    }

    /** date plus amount (minus for sign -1): periods as in java.time, business days of the market */
    public static LocalDate plus(LocalDate date, TemporalAmount amount, int sign, Market market) {
        if (amount instanceof BusinessDays days) {
            return plusBusinessDays(date, sign * days.count(), market);
        }
        return sign > 0 ? date.plus(amount) : date.minus(amount);
    }

    /** date moved by count business days of the market, backwards if count is negative */
    public static LocalDate plusBusinessDays(LocalDate date, int count, Market market) {
        int step = Integer.signum(count);
        for (int days = 0; days != count; ) {
            date = date.plusDays(step);
            if (market.isBusinessDay(date)) {
                days += step;
            }
        }
        return date;
    }

    /** the IMM date (third Wednesday of March, June, September or December) strictly after date */
    public static LocalDate nextImm(LocalDate date) {
        for (LocalDate month = date.withDayOfMonth(1); ; month = month.plusMonths(1)) {
            LocalDate imm = month.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
            if (month.getMonthValue() % 3 == 0 && imm.isAfter(date)) {
                return imm;
            }
        }
    }

    /** the IMM date strictly before date */
    public static LocalDate previousImm(LocalDate date) {
        for (LocalDate month = date.withDayOfMonth(1); ; month = month.minusMonths(1)) {
            LocalDate imm = month.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
            if (month.getMonthValue() % 3 == 0 && imm.isBefore(date)) {
                return imm;
            }
        }
    }

    private static List<LocalDate> range(LocalDate start, TemporalAmount step, LocalDate end, Market market) {
        List<LocalDate> dates = new ArrayList<>();
        for (int k = 0; ; k++) {
            LocalDate date = step instanceof BusinessDays days
                    ? plusBusinessDays(start, k * days.count(), market)
                    : start.plus(((Period) step).multipliedBy(k));
            if (date.isAfter(end)) {
                return dates;
            }
            dates.add(date);
        }
    }
}
