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

/**
 * Semantics of the shared "schedule" example: periods, business days, IMM and spot dates, ranges.
 */
public final class ScheduleSemantics {

    private ScheduleSemantics() {
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

    /** business days, Monday to Friday: only weekends are skipped, there are no holiday calendars */
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
            return plusBusinessDays(LocalDate.from(temporal), count);
        }

        @Override
        public Temporal subtractFrom(Temporal temporal) {
            return plusBusinessDays(LocalDate.from(temporal), -count);
        }
    }

    /** date moved by count business days, backwards if count is negative */
    public static LocalDate plusBusinessDays(LocalDate date, int count) {
        int step = Integer.signum(count);
        for (int days = 0; days != count; ) {
            date = date.plusDays(step);
            if (date.getDayOfWeek().getValue() < 6) {
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

    /** the FX spot date: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair) */
    public static LocalDate spot(LocalDate trade, String pair) {
        int lag = pair != null && pair.matches("USD(CAD|TRY|PHP|RUB)|(CAD|TRY|PHP|RUB)USD") ? 1 : 2;
        return plusBusinessDays(trade, lag);
    }

    public static boolean isPositive(TemporalAmount step) {
        if (step instanceof BusinessDays days) {
            return days.count() > 0;
        }
        Period period = (Period) step;
        return !period.isZero() && !period.isNegative();
    }

    /** start + k * step for k = 0, 1, ... while not after end */
    public static List<LocalDate> range(LocalDate start, TemporalAmount step, LocalDate end) {
        if (!isPositive(step)) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (int k = 0; ; k++) {
            LocalDate date = step instanceof BusinessDays days
                    ? plusBusinessDays(start, k * days.count())
                    : start.plus(((Period) step).multipliedBy(k));
            if (date.isAfter(end)) {
                return dates;
            }
            dates.add(date);
        }
    }
}
