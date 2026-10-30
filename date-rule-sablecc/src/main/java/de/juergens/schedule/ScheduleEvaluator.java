package de.juergens.schedule;

import de.juergens.schedule.ScheduleSemantics.Context;
import de.juergens.schedule.ScheduleSemantics.Rule;
import de.juergens.schedule.lexer.Lexer;
import de.juergens.schedule.lexer.LexerException;
import de.juergens.schedule.node.*;
import de.juergens.schedule.parser.Parser;
import de.juergens.schedule.parser.ParserException;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the dates of the shared "schedule" example from the syntax tree built by SableCC.
 * The tree already is the rule: evaluating it later only needs the context (base date and market).
 */
public final class ScheduleEvaluator {

    private ScheduleEvaluator() {
    }

    /**
     * The rule of a schedule; evaluate it with {@code rule.evaluate(new ScheduleSemantics.Context(base, market))}.
     * Invalid dates and ranges without positive step surface only then.
     *
     * @throws ParserException on a syntax error
     * @throws LexerException on an unknown character
     */
    public static Rule parse(String text) throws ParserException, LexerException, IOException {
        Start start = new Parser(new Lexer(new PushbackReader(new StringReader(text), 1024))).parse();
        return context -> schedule(start.getPSchedule(), context);
    }

    /**
     * @throws java.time.DateTimeException on an invalid date
     * @throws IllegalArgumentException on a range without positive step
     */
    static List<LocalDate> schedule(PSchedule node, Context context) {
        if (node instanceof ARangeSchedule range) {
            return ScheduleSemantics.range(expr(range.getFirst()), period(range.getPeriod()), expr(range.getLast()))
                    .evaluate(context);
        }
        AListSchedule list = (AListSchedule) node;
        List<LocalDate> dates = new ArrayList<>();
        dates.add(expr(list.getExpr()).at(context));
        for (Object more : list.getMoreExpr()) {
            dates.add(expr(((AMoreExpr) more).getExpr()).at(context));
        }
        return dates;
    }

    /** periods are applied from left to right */
    static ScheduleSemantics.Expr expr(PExpr node) {
        AExpr expr = (AExpr) node;
        ScheduleSemantics.Expr date = date(expr.getDate());
        for (Object shift : expr.getShift()) {
            if (shift instanceof APlusShift plus) {
                date = ScheduleSemantics.plus(date, period(plus.getPeriod()));
            } else if (shift instanceof AMinusShift minus) {
                date = ScheduleSemantics.minus(date, period(minus.getPeriod()));
            } else if (shift instanceof ASpotShift spot) {
                date = ScheduleSemantics.spot(date, spot.getPair() == null ? null : spot.getPair().getText());
            } else if (shift instanceof ARollShift roll) {
                date = ScheduleSemantics.roll(date, roll.getRoll().getText());
            } else if (shift instanceof ANextShift next) {
                date = ScheduleSemantics.next(date, anchor(next.getAnchor()));
            } else if (shift instanceof APrevShift prev) {
                date = ScheduleSemantics.previous(date, anchor(prev.getAnchor()));
            } else if (shift instanceof AObservanceShift observance) {
                List<ScheduleSemantics.Shift> shifts = new ArrayList<>();
                shifts.add(observe(observance.getObserve()));
                for (Object more : observance.getMoreObserve()) {
                    shifts.add(observe(((AMoreObserve) more).getObserve()));
                }
                date = ScheduleSemantics.observe(date, shifts);
            } else {
                date = ScheduleSemantics.nextImm(date);
            }
        }
        return date;
    }

    static ScheduleSemantics.Shift observe(PObserve node) {
        if (node instanceof APlusObserve plus) {
            return ScheduleSemantics.shift(plus.getWeekday().getText(), period(plus.getPeriod()), 1);
        }
        AMinusObserve minus = (AMinusObserve) node;
        return ScheduleSemantics.shift(minus.getWeekday().getText(), period(minus.getPeriod()), -1);
    }

    static ScheduleSemantics.Anchor anchor(PAnchor node) {
        if (node instanceof AWeekdayAnchor weekday) {
            return ScheduleSemantics.weekday(weekday.getWeekday().getText());
        } else if (node instanceof ABdAnchor) {
            return ScheduleSemantics.BUSINESS_DAY;
        } else if (node instanceof AImmAnchor) {
            return ScheduleSemantics.IMM;
        }
        return ScheduleSemantics.annual(annual(((AAnnualAnchor) node).getAnnual()));
    }

    /** dates that recur every year */
    static ScheduleSemantics.Annual annual(PAnnual node) {
        if (node instanceof AMonthDayAnnual monthDay) {
            return ScheduleSemantics.monthDay(number(monthDay.getNumber()), monthDay.getMonth().getText());
        } else if (node instanceof ANthWeekdayAnnual nth) {
            return ScheduleSemantics.nthWeekday(number(nth.getNumber()), nth.getWeekday().getText(), nth.getMonth().getText());
        } else if (node instanceof ALastAnnual last) {
            return ScheduleSemantics.lastWeekday(last.getWeekday().getText(), last.getMonth().getText());
        }
        return ScheduleSemantics.EASTER;
    }

    static ScheduleSemantics.Expr date(PDate node) {
        if (node instanceof ABaseDate) {
            return ScheduleSemantics.BASE;
        } else if (node instanceof ATomDate) {
            return ScheduleSemantics.plus(ScheduleSemantics.BASE, ScheduleSemantics.period(1, "B"));
        } else if (node instanceof ASpotDate spot) {
            return ScheduleSemantics.spot(ScheduleSemantics.BASE, spot.getPair() == null ? null : spot.getPair().getText());
        } else if (node instanceof AAnnualDate annual) {
            return ScheduleSemantics.inYear(annual(annual.getAnnual()));
        } else if (node instanceof AIsoDate iso) {
            return ScheduleSemantics.literal(LocalDate.of(number(iso.getYear()), number(iso.getMonth()), number(iso.getDay())));
        } else if (node instanceof AGermanDate german) {
            return ScheduleSemantics.literal(LocalDate.of(number(german.getYear()), number(german.getMonth()), number(german.getDay())));
        }
        AUsDate us = (AUsDate) node;
        return ScheduleSemantics.literal(LocalDate.of(number(us.getYear()), number(us.getMonth()), number(us.getDay())));
    }

    static TemporalAmount period(PPeriod node) {
        APeriod period = (APeriod) node;
        return ScheduleSemantics.period(number(period.getNumber()), period.getUnit().getText());
    }

    static int number(TNumber token) {
        return Integer.parseInt(token.getText());
    }
}
