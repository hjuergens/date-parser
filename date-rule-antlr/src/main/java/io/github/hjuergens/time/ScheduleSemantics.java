package io.github.hjuergens.time;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantics of the shared "schedule" example: periods and ranges.
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
