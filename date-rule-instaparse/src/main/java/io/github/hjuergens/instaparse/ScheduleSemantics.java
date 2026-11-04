package io.github.hjuergens.instaparse;

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
import java.util.Collections;
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
 * Semantics of the shared "schedule" example. A parsed schedule is a {@link Rule}: a function of the base date,
 * the market and the named events, all only known when the rule is evaluated.
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

    /**
     * what a rule is evaluated against: the base date (only needed for T), the market (default: weekends only) and
     * the named events; occurrence is the date of the event an expression is being evaluated for
     */
    public record Context(LocalDate base, Market market, Map<String, Rule> events, LocalDate occurrence) {
        public Context {
            market = market == null ? WEEKENDS : market;
            events = events == null ? Map.of() : events;
        }

        public Context(LocalDate base, Market market) {
            this(base, market, null, null);
        }

        public Context(LocalDate base, Market market, Map<String, Rule> events) {
            this(base, market, events, null);
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

    /** thrown by an event date that is not bound to one occurrence yet; values() catches it */
    private static final class Unbound extends RuntimeException {
        private final String name;

        private Unbound(String name) {
            super("unbound event " + name, null, false, false);
            this.name = name;
        }
    }

    /** @NAME: one occurrence of the event, bound by values() */
    public static Expr event(String name) {
        return context -> {
            if (context.occurrence() == null) {
                throw new Unbound(name);
            }
            return context.occurrence();
        };
    }

    /** the dates of the event in the year: its rule evaluated with the first of January as base date */
    private static List<LocalDate> occurrences(String name, int year, Context context) {
        Rule rule = context.events().get(name);
        if (rule == null) {
            throw new IllegalArgumentException("no event " + name);
        }
        return rule.evaluate(new Context(LocalDate.of(year, 1, 1), context.market(), context.events())).stream()
                .filter(date -> date.getYear() == year).sorted().toList();
    }

    /** the dates of an expression: one, or one per occurrence in the base year if it starts with an event */
    private static List<LocalDate> values(Expr expr, Context context) {
        try {
            return Collections.singletonList(expr.at(context));
        } catch (Unbound unbound) {
            return occurrences(unbound.name, BASE.at(context).getYear(), context).stream()
                    .map(date -> expr.at(new Context(context.base(), context.market(), context.events(), date)))
                    .toList();
        }
    }

    /** the date of an expression that must have exactly one, such as the end of a range */
    private static LocalDate single(Expr expr, Context context) {
        List<LocalDate> dates = values(expr, context);
        if (dates.size() != 1) {
            throw new IllegalArgumentException(dates.size() + " dates where one is needed");
        }
        return dates.get(0);
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

    /** EQUINOX MAR, SOLSTICE JUN, EQUINOX SEP, SOLSTICE DEC: the sun at 0, 90, 180, 270 degrees, in UTC + offset minutes */
    public static Annual season(String kind, String month, int offset) {
        int index = List.of("MAR", "JUN", "SEP", "DEC").indexOf(month);
        return year -> {
            if (index < 0 || (index % 2 == 0) != kind.equals("EQUINOX")) {
                throw new java.time.DateTimeException("no " + kind + " in " + month);
            }
            return sunAt(90 * index, year, offset);
        };
    }

    /** SOLARTERM 1 to 24, counted from Lichun (315 degrees); SOLARTERM 5 is Qingming (15 degrees) */
    public static Annual solarTerm(int term, int offset) {
        return year -> {
            if (term < 1 || term > 24) {
                throw new java.time.DateTimeException("no solar term " + term);
            }
            return sunAt((term + 20) % 24 * 15, year, offset);
        };
    }

    /** UTC+9, UTC-4: the offset in minutes */
    public static int zone(String text) {
        return Integer.parseInt(text.substring(3)) * 60;
    }

    // the terms of VSOP87D (Meeus, Astronomical Algorithms, appendix III) with A >= 100: A (1e-8 rad or AU), B, C
    private static final double[][][] EARTH_L = {
        {{175347046, 0, 0}, {3341656, 4.6692568, 6283.07585}, {34894, 4.6261024, 12566.1517}, {3418, 2.8288658, 3.5231183},
         {3497, 2.744118, 5753.3848849}, {3136, 3.6276704, 77713.7714681}, {2676, 4.4180835, 7860.4193924},
         {2343, 6.1351624, 3930.2096962}, {1273, 2.0370966, 529.6909651}, {1324, 0.7424636, 11506.7697698},
         {902, 2.0450544, 26.2983198}, {1199, 1.1096294, 1577.3435424}, {857, 3.5084916, 398.1490034},
         {780, 1.1788265, 5223.6939198}, {990, 5.2326813, 5884.9268466}, {753, 2.5333905, 5507.5532387},
         {505, 4.5829256, 18849.22755}, {492, 4.2050664, 775.5226113}, {357, 2.9195412, 0.0673103},
         {284, 1.8986903, 796.2980068}, {243, 0.3448114, 5486.7778432}, {317, 5.8490195, 11790.6290887},
         {271, 0.3148861, 10977.0788047}, {206, 4.8064661, 2544.3144199}, {205, 1.8694781, 5573.1428014},
         {202, 2.457678, 6069.7767546}, {126, 1.0830263, 20.7753955}, {156, 0.8330607, 213.2990954},
         {115, 0.6454491, 0.9803211}, {103, 0.6359985, 4694.0029547}, {102, 4.2667982, 7.113547},
         {132, 3.4111828, 2942.4634233}, {102, 0.9756922, 15720.8387849}},
        {{628331966747.0, 0, 0}, {206059, 2.6782346, 6283.07585}, {4303, 2.6351265, 12566.1517}, {425, 1.5904698, 3.5231183},
         {109, 2.96618, 1577.3435424}, {119, 5.7955749, 26.2983198}},
        {{52919, 0, 0}, {8720, 1.0720967, 6283.07585}, {309, 0.8672882, 12566.1517}},
        {{289, 5.843842, 6283.07585}},
        {{114, 3.1415927, 0}}};
    private static final double[][][] EARTH_R = {
        {{100013989, 0, 0}, {1670700, 3.0984635, 6283.07585}, {13956, 3.0552461, 12566.1517}},
        {{103019, 1.1074897, 6283.07585}}};

    private static double vsop(double[][][] series, double tau) {
        double sum = 0;
        for (int n = series.length - 1; n >= 0; n--) {
            double term = 0;
            for (double[] t : series[n]) {
                term += t[0] * Math.cos(t[1] + t[2] * tau);
            }
            sum = sum * tau + term;
        }
        return sum * 1e-8;
    }

    /** the apparent longitude of the sun in degrees (Meeus, chapter 25, with VSOP87, nutation and aberration) */
    private static double sunLongitude(double jde) {
        double tau = (jde - 2451545) / 365250, t = 10 * tau;
        double omega = Math.toRadians(125.04452 - 1934.136261 * t), sun = Math.toRadians(280.4665 + 36000.7698 * t);
        double moon = Math.toRadians(218.3165 + 481267.8813 * t);
        double nutation = -17.20 * Math.sin(omega) - 1.32 * Math.sin(2 * sun) - 0.23 * Math.sin(2 * moon) + 0.21 * Math.sin(2 * omega);
        return Math.toDegrees(vsop(EARTH_L, tau)) + 180 + (nutation - 0.09033 - 20.4898 / vsop(EARTH_R, tau)) / 3600;
    }

    /** TD - UT in seconds: Espenak and Meeus' polynomials for 1986 to 2150, their long-term formula outside */
    private static double deltaT(double year) {
        double t = year - 2000, u = (year - 1820) / 100, longTerm = -20 + 32 * u * u;
        if (year >= 1986 && year < 2005) {
            return 63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t + 0.000651814 * t * t * t * t
                    + 0.00002373599 * t * t * t * t * t;
        }
        if (year >= 2005 && year < 2050) {
            return 62.92 + 0.32217 * t + 0.005589 * t * t;
        }
        return year >= 2050 && year < 2150 ? longTerm - 0.5628 * (2150 - year) : longTerm;
    }

    /** the day in the year on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes */
    private static LocalDate sunAt(int degrees, int year, int offset) {
        double day = 79 + degrees * 365.2422 / 360;  // days after 1 January, from the March equinox on
        if (day > 365.2422) {
            day -= 365.2422;
        }
        double jde = LocalDate.of(year, 1, 1).toEpochDay() + 2440587.5 + day;
        for (int i = 0; i < 50; i++) {
            double step = 58 * Math.sin(Math.toRadians(degrees - sunLongitude(jde)));
            jde += step;
            if (Math.abs(step) < 1e-7) {
                break;
            }
        }
        double local = jde - deltaT(year + day / 365.25) / 86400 + offset / 1440.0;
        return LocalDate.ofEpochDay((long) Math.floor(local - 2440587.5));
    }

    /** what > NEXT and > PREV look for: the first match strictly after (direction 1) or before (-1) a date */
    public interface Anchor {
        LocalDate from(LocalDate date, int direction, Context context);
    }

    public static Anchor annual(Annual annual) {
        return (date, direction, context) -> {
            LocalDate match = annual.in(date.getYear());
            return Integer.signum(match.compareTo(date)) == direction ? match : annual.in(date.getYear() + direction);
        };
    }

    public static Anchor weekday(String weekday) {
        return (date, direction, context) -> date.with(direction > 0
                ? TemporalAdjusters.next(dayOfWeek(weekday)) : TemporalAdjusters.previous(dayOfWeek(weekday)));
    }

    /** NEXT {MON, FRI}, NEXT {1 FEB, 1 MAY}: the nearest match of any of the anchors */
    public static Anchor anyOf(List<Anchor> anchors) {
        return (date, direction, context) -> {
            LocalDate nearest = null;
            for (Anchor anchor : anchors) {
                LocalDate found = anchor.from(date, direction, context);
                if (nearest == null || Integer.signum(nearest.compareTo(found)) == direction) {
                    nearest = found;
                }
            }
            return nearest;
        };
    }

    /** 1ST, 2ND, 3RD, 4TH, 11TH, 21ST as a count; the suffix must fit the number */
    public static int ordinal(String text) {
        int n = Integer.parseInt(text.substring(0, text.length() - 2));
        String suffix = n % 100 >= 11 && n % 100 <= 13 ? "TH"
                : n % 10 == 1 ? "ST" : n % 10 == 2 ? "ND" : n % 10 == 3 ? "RD" : "TH";
        if (!text.endsWith(suffix)) {
            throw new IllegalArgumentException("not an ordinal: " + text);
        }
        return n;
    }

    public static final Anchor BUSINESS_DAY = (date, direction, context) ->
            plusBusinessDays(date, direction, context.market());

    public static final Anchor IMM = (date, direction, context) -> direction > 0 ? nextImm(date) : previousImm(date);

    /** the occurrence of the event strictly after (before) the date, in its year or the next (previous) */
    public static Anchor eventAnchor(String name) {
        return (date, direction, context) -> {
            for (int year : new int[] {date.getYear(), date.getYear() + direction}) {
                List<LocalDate> found = occurrences(name, year, context).stream()
                        .filter(occurrence -> Integer.signum(occurrence.compareTo(date)) == direction).toList();
                if (!found.isEmpty()) {
                    return direction > 0 ? found.get(0) : found.get(found.size() - 1);
                }
            }
            throw new IllegalArgumentException("no " + name + (direction > 0 ? " after " : " before ") + date);
        };
    }

    public static Expr next(Expr expr, Anchor anchor) {
        return context -> anchor.from(expr.at(context), 1, context);
    }

    public static Expr previous(Expr expr, Anchor anchor) {
        return context -> anchor.from(expr.at(context), -1, context);
    }

    public static Expr next(Expr expr, int count, Anchor anchor) {
        return next(expr, times(count, anchor));
    }

    public static Expr previous(Expr expr, int count, Anchor anchor) {
        return previous(expr, times(count, anchor));
    }

    /** NEXT 3RD FRI: the anchor found {@code count} times, each time from the one before */
    public static Anchor times(int count, Anchor anchor) {
        return (date, direction, context) -> {
            if (count < 1) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
            for (int i = 0; i < count; i++) {
                date = anchor.from(date, direction, context);
            }
            return date;
        };
    }

    /**
     * > 3RD WED IN MONTH: the anchor found {@code count} times from the day before the period of the date starts;
     * > 2ND LAST BD IN MONTH ({@code fromEnd}): backwards from the day after it ends; an error if that leaves the period
     */
    public static Expr select(Expr expr, int count, boolean fromEnd, Anchor anchor, String period) {
        return context -> {
            if (count < 1) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
            LocalDate date = expr.at(context);
            String unit = period.equals("HALFYEAR") ? "H" : period.substring(0, 1);   // WEEK, MONTH, QUARTER, YEAR
            LocalDate first = roll(date, "BO" + unit, context.market());
            LocalDate last = roll(date, "EO" + unit, context.market());
            LocalDate found = fromEnd ? last.plusDays(1) : first.minusDays(1);
            for (int i = 0; i < count; i++) {
                found = anchor.from(found, fromEnd ? -1 : 1, context);
            }
            if (found.isBefore(first) || found.isAfter(last)) {
                throw new IllegalArgumentException("no " + count + ". match in the " + period.toLowerCase() + " of " + date);
            }
            return found;
        };
    }

    /**
     * FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month, then preceding);
     * BOW, EOW, BOM, EOM, BOQ, EOQ, BOH, EOH, BOY, EOY (first and last day of week (Monday to Sunday), month, quarter,
     * half year and year)
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
            case "BOW": return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "EOW": return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case "BOM": return date.withDayOfMonth(1);
            case "EOM": return date.with(TemporalAdjusters.lastDayOfMonth());
            case "BOQ": return date.withDayOfMonth(1).withMonth((date.getMonthValue() - 1) / 3 * 3 + 1);
            case "EOQ": return roll(roll(date, "BOQ", market).plusMonths(2), "EOM", market);
            case "BOH": return date.withDayOfMonth(1).withMonth((date.getMonthValue() - 1) / 6 * 6 + 1);
            case "EOH": return roll(roll(date, "BOH", market).plusMonths(5), "EOM", market);
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

    /** a list of dates; an item that yields no date (see onlyOn) is left out, an event yields one per occurrence */
    public static Rule list(List<Expr> exprs) {
        List<Expr> copy = new ArrayList<>(exprs);
        return context -> copy.stream().flatMap(expr -> values(expr, context).stream()).filter(Objects::nonNull)
                .toList();
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
        return context -> range(single(first, context), step, single(last, context), context.market());
    }

    /** {@code count} times D (day), W (week), M (month), Q (quarter), H (half year), Y (year) or B (business day) */
    public static TemporalAmount period(int count, String unit) {
        switch (unit) {
            case "D": return Period.ofDays(count);
            case "W": return Period.ofWeeks(count);
            case "M": return Period.ofMonths(count);
            case "Q": return Period.ofMonths(3 * count);
            case "H": return Period.ofMonths(6 * count);
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
