package de.juergens.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public static Rule list(List<Expr> exprs) {
        List<Expr> copy = new ArrayList<>(exprs);
        return context -> copy.stream().map(expr -> expr.at(context)).toList();
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
