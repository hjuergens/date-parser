package de.juergens.dateparser;

import org.joda.time.PeriodType;
import org.joda.time.base.BaseSingleFieldPeriod;

import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

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