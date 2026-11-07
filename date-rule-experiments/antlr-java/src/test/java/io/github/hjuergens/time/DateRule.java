package io.github.hjuergens.time;

import org.joda.time.PeriodType;

public class DateRule {
    private int cardinal;
    private PeriodType unit;

    public DateRule(int cardinal, PeriodType unit) {
        this.cardinal = cardinal;
        this.unit = unit;
    }

    public void play() {

    }
}