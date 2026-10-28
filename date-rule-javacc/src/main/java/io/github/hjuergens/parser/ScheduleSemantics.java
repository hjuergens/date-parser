package io.github.hjuergens.parser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantics of the shared "schedule" example: periods, IMM dates and ranges.
 */
public final class ScheduleSemantics {

    private ScheduleSemantics() {
    }

    /** {@code count} times D (day), W (week), M (month), Q (quarter) or Y (year) */
    public static Period period(int count, String unit) {
        switch (unit) {
            case "D": return Period.ofDays(count);
            case "W": return Period.ofWeeks(count);
            case "M": return Period.ofMonths(count);
            case "Q": return Period.ofMonths(3 * count);
            case "Y": return Period.ofYears(count);
            default: throw new IllegalArgumentException("unknown unit: " + unit);
        }
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

    /** start + k * step for k = 0, 1, ... while not after end */
    public static List<LocalDate> range(LocalDate start, Period step, LocalDate end) {
        if (step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (int k = 0; ; k++) {
            LocalDate date = start.plus(step.multipliedBy(k));
            if (date.isAfter(end)) {
                return dates;
            }
            dates.add(date);
        }
    }
}
