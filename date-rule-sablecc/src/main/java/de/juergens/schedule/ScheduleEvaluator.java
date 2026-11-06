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
        ASchedule list = (ASchedule) node;
        List<ScheduleSemantics.Expr> items = new ArrayList<>();
        items.add(item(list.getItem()));
        for (Object more : list.getMoreItem()) {
            items.add(item(((AMoreItem) more).getItem()));
        }
        return ScheduleSemantics.list(items).evaluate(context);
    }

    /** 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays */
    static ScheduleSemantics.Expr item(PItem node) {
        if (node instanceof APlainItem plain) {
            return item(expr(plain.getExpr()), plain.getCombine(), plain.getCondition());
        }
        // 5TH [SPOT : 1Q : ] > EOQ > ...: the item of a pick starts with a series or an event
        APick pick;
        ScheduleSemantics.Expr value;
        if (node instanceof APickedSeriesItem picked) {
            pick = (APick) picked.getPick();
            value = item(shifts(series(picked.getSeries()), picked.getShift()), picked.getCombine(), picked.getCondition());
        } else {
            APickedEventItem picked = (APickedEventItem) node;
            pick = (APick) picked.getPick();
            value = item(shifts(ScheduleSemantics.event(picked.getEvent().getText().substring(1)), picked.getShift()),
                    picked.getCombine(), picked.getCondition());
        }
        return ScheduleSemantics.pick(count(pick.getOrdinal()), pick.getLast() != null, value);
    }

    static ScheduleSemantics.Expr item(ScheduleSemantics.Expr start, List<?> combines, PCondition condition) {
        ScheduleSemantics.Expr value = start;
        // A & B includes the dates of B, A \\ B excludes them; the shifts after it apply to the result
        for (Object combine : combines) {
            if (combine instanceof AIncludeCombine include) {
                value = shifts(ScheduleSemantics.combined(value, true, operand(include.getOperand())), include.getShift());
            } else {
                AExcludeCombine exclude = (AExcludeCombine) combine;
                value = shifts(ScheduleSemantics.combined(value, false, operand(exclude.getOperand())), exclude.getShift());
            }
        }
        if (condition == null) {
            return value;
        }
        List<String> weekdays = new ArrayList<>();
        if (condition instanceof APlainCondition plain) {
            for (Object weekday : plain.getWeekday()) {
                weekdays.add(((TWeekday) weekday).getText());
            }
        } else {
            ASetCondition set = (ASetCondition) condition;
            weekdays.add(set.getWeekday().getText());
            for (Object more : set.getMoreWeekday()) {
                weekdays.add(((AMoreWeekday) more).getWeekday().getText());
            }
        }
        return ScheduleSemantics.onlyOn(value, weekdays);
    }

    /** a date, a series or a set of dates */
    static List<ScheduleSemantics.Expr> operand(POperand node) {
        List<ScheduleSemantics.Expr> exprs = new ArrayList<>();
        if (node instanceof ASingleOperand single) {
            exprs.add(date(single.getDate()));
        } else {
            ASetOperand set = (ASetOperand) node;
            exprs.add(expr(set.getExpr()));
            for (Object more : set.getMoreExpr()) {
                exprs.add(expr(((AMoreExpr) more).getExpr()));
            }
        }
        return exprs;
    }

    /** [a : step : b]: a date with several values, the shifts after it apply to each element */
    static ScheduleSemantics.Expr series(PSeries node) {
        ASeries series = (ASeries) node;
        TemporalAmount step = series.getStep() == null ? null : period(((AStep) series.getStep()).getPeriod());
        if (step == null && series.getLast() == null) {
            throw new IllegalArgumentException("a series needs an end or a step");
        }
        return ScheduleSemantics.series(endpoint(series.getFirst()), step,
                series.getLast() == null ? null : endpoint(series.getLast()),
                series.getStartBracket() instanceof AOpenStartBracket, series.getEndBracket() instanceof AOpenEndBracket);
    }

    /** an endpoint is one date, never a series */
    static ScheduleSemantics.Expr endpoint(PEndpoint node) {
        AEndpoint endpoint = (AEndpoint) node;
        return shifts(singleDate(endpoint.getSingleDate()), endpoint.getShift());
    }

    /** periods are applied from left to right */
    static ScheduleSemantics.Expr expr(PExpr node) {
        AExpr expr = (AExpr) node;
        return shifts(date(expr.getDate()), expr.getShift());
    }

    /** each shift applies to everything on its left */
    static ScheduleSemantics.Expr shifts(ScheduleSemantics.Expr date, List<?> nodes) {
        for (Object shift : nodes) {
            if (shift instanceof APlusShift plus) {
                date = ScheduleSemantics.plus(date, period(plus.getPeriod()));
            } else if (shift instanceof AMinusShift minus) {
                date = ScheduleSemantics.minus(date, period(minus.getPeriod()));
            } else if (shift instanceof ASpotShift spot) {
                date = ScheduleSemantics.spot(date, spot.getPair() == null ? null : spot.getPair().getText());
            } else if (shift instanceof ARollShift roll) {
                date = ScheduleSemantics.roll(date, roll.getRoll().getText());
            } else if (shift instanceof ANextShift next) {
                date = ScheduleSemantics.next(date, count(next.getOrdinal()), anchor(next.getAnchor()));
            } else if (shift instanceof APrevShift prev) {
                date = ScheduleSemantics.previous(date, count(prev.getOrdinal()), anchor(prev.getAnchor()));
            } else if (shift instanceof ASelectShift select) {
                PCount count = select.getCount();
                boolean fromEnd = !(count instanceof AFromStartCount);
                int n = count instanceof AFromStartCount start ? count(start.getOrdinal())
                        : count instanceof AFromEndCount end ? count(end.getOrdinal()) : 1;
                date = ScheduleSemantics.select(date, n, fromEnd, select(select.getSelect()), select.getPeriodWord().getText());
            } else if (shift instanceof ADatesInYearShift datesInYear) {
                List<ScheduleSemantics.Annual> annuals = new ArrayList<>();
                annuals.add(annual(datesInYear.getAnnual()));
                for (Object more : datesInYear.getMoreAnnual()) {
                    annuals.add(annual(((AMoreAnnual) more).getAnnual()));
                }
                date = ScheduleSemantics.datesInYear(date, annuals);
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

    /** NEXT {MON, FRI}: the nearest of a set */
    static ScheduleSemantics.Anchor anchor(PAnchor node) {
        if (node instanceof ASingleAnchor single) {
            return target(single.getTarget());
        }
        ASetAnchor set = (ASetAnchor) node;
        List<ScheduleSemantics.Anchor> anchors = new ArrayList<>();
        anchors.add(target(set.getTarget()));
        for (Object more : set.getMoreTarget()) {
            anchors.add(target(((AMoreTarget) more).getTarget()));
        }
        return ScheduleSemantics.anyOf(anchors);
    }

    /** the targets of NEXT; an annual date starting with LAST only in braces */
    static ScheduleSemantics.Anchor select(PSelect node) {
        if (node instanceof ASingleSelect single) {
            return chosen(single.getChosen());
        }
        ASetSelect set = (ASetSelect) node;
        List<ScheduleSemantics.Anchor> anchors = new ArrayList<>();
        anchors.add(target(set.getTarget()));
        for (Object more : set.getMoreTarget()) {
            anchors.add(target(((AMoreTarget) more).getTarget()));
        }
        return ScheduleSemantics.anyOf(anchors);
    }

    static ScheduleSemantics.Anchor target(PTarget node) {
        if (node instanceof AChosenTarget chosen) {
            return chosen(chosen.getChosen());
        }
        ALastTarget last = (ALastTarget) node;
        return ScheduleSemantics.annual(ScheduleSemantics.lastWeekday(last.getWeekday().getText(), last.getMonth().getText()));
    }

    static ScheduleSemantics.Anchor chosen(PChosen node) {
        if (node instanceof AWeekdayChosen weekday) {
            return ScheduleSemantics.weekday(weekday.getWeekday().getText());
        } else if (node instanceof ABdChosen) {
            return ScheduleSemantics.BUSINESS_DAY;
        } else if (node instanceof AImmChosen) {
            return ScheduleSemantics.IMM;
        } else if (node instanceof AEventChosen event) {
            return ScheduleSemantics.eventAnchor(event.getEvent().getText().substring(1));
        }
        return ScheduleSemantics.annual(fixedAnnual(((AAnnualChosen) node).getFixedAnnual()));
    }

    /** dates that recur every year */
    static ScheduleSemantics.Annual annual(PAnnual node) {
        if (node instanceof ALastAnnual last) {
            return ScheduleSemantics.lastWeekday(last.getWeekday().getText(), last.getMonth().getText());
        }
        return fixedAnnual(((AFixedAnnual) node).getFixedAnnual());
    }

    static ScheduleSemantics.Annual fixedAnnual(PFixedAnnual node) {
        if (node instanceof AMonthDayFixedAnnual monthDay) {
            return ScheduleSemantics.monthDay(number(monthDay.getNumber()), monthDay.getMonth().getText());
        } else if (node instanceof ANthWeekdayFixedAnnual nth) {
            return ScheduleSemantics.nthWeekday(number(nth.getNumber()), nth.getWeekday().getText(), nth.getMonth().getText());
        } else if (node instanceof ASeasonFixedAnnual season) {
            return ScheduleSemantics.season(season.getSeason().getText(), season.getMonth().getText(), zone(season.getZone()));
        } else if (node instanceof ASolarTermFixedAnnual term) {
            return ScheduleSemantics.solarTerm(number(term.getNumber()), zone(term.getZone()));
        }
        return ScheduleSemantics.EASTER;
    }

    /** UTC unless an offset follows */
    static int zone(TZone zone) {
        return zone == null ? 0 : ScheduleSemantics.zone(zone.getText());
    }

    static ScheduleSemantics.Expr date(PDate node) {
        return node instanceof ASeriesDate series ? series(series.getSeries()) : singleDate(((ASingleDate) node).getSingleDate());
    }

    /** a date that is not a series */
    static ScheduleSemantics.Expr singleDate(PSingleDate node) {
        if (node instanceof ABaseSingleDate) {
            return ScheduleSemantics.BASE;
        } else if (node instanceof ATomSingleDate) {
            return ScheduleSemantics.plus(ScheduleSemantics.BASE, ScheduleSemantics.period(1, "B"));
        } else if (node instanceof ASpotSingleDate spot) {
            return ScheduleSemantics.spot(ScheduleSemantics.BASE, spot.getPair() == null ? null : spot.getPair().getText());
        } else if (node instanceof AAnnualSingleDate annual) {
            return ScheduleSemantics.inYear(annual(annual.getAnnual()));
        } else if (node instanceof AEventSingleDate event) {
            return ScheduleSemantics.event(event.getEvent().getText().substring(1));
        } else if (node instanceof AIsoSingleDate iso) {
            return ScheduleSemantics.literal(LocalDate.of(number(iso.getYear()), number(iso.getMonth()), number(iso.getDay())));
        } else if (node instanceof AGermanSingleDate german) {
            return ScheduleSemantics.literal(LocalDate.of(number(german.getYear()), number(german.getMonth()), number(german.getDay())));
        }
        AUsSingleDate us = (AUsSingleDate) node;
        return ScheduleSemantics.literal(LocalDate.of(number(us.getYear()), number(us.getMonth()), number(us.getDay())));
    }

    static TemporalAmount period(PPeriod node) {
        APeriod period = (APeriod) node;
        return ScheduleSemantics.period(number(period.getNumber()), period.getUnit().getText());
    }

    /** NEXT FRI is NEXT 1ST FRI */
    static int count(TOrdinal token) {
        return token == null ? 1 : ScheduleSemantics.ordinal(token.getText());
    }

    static int number(TNumber token) {
        return Integer.parseInt(token.getText());
    }
}
