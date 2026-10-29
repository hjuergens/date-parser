package de.juergens.schedule;

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
 */
public final class ScheduleEvaluator {

    private ScheduleEvaluator() {
    }

    /**
     * @throws ParserException on a syntax error
     * @throws LexerException on an unknown character
     * @throws java.time.DateTimeException on an invalid date
     */
    public static List<LocalDate> parse(String text) throws ParserException, LexerException, IOException {
        Start start = new Parser(new Lexer(new PushbackReader(new StringReader(text), 1024))).parse();
        return schedule(start.getPSchedule());
    }

    static List<LocalDate> schedule(PSchedule node) {
        if (node instanceof ARangeSchedule range) {
            return ScheduleSemantics.range(expr(range.getFirst()), period(range.getPeriod()), expr(range.getLast()));
        }
        AListSchedule list = (AListSchedule) node;
        List<LocalDate> dates = new ArrayList<>();
        dates.add(expr(list.getExpr()));
        for (Object more : list.getMoreExpr()) {
            dates.add(expr(((AMoreExpr) more).getExpr()));
        }
        return dates;
    }

    /** periods are applied from left to right */
    static LocalDate expr(PExpr node) {
        AExpr expr = (AExpr) node;
        LocalDate date = date(expr.getDate());
        for (Object shift : expr.getShift()) {
            if (shift instanceof APlusShift plus) {
                date = date.plus(period(plus.getPeriod()));
            } else if (shift instanceof AMinusShift minus) {
                date = date.minus(period(minus.getPeriod()));
            } else if (shift instanceof ASpotShift spot) {
                date = ScheduleSemantics.spot(date, spot.getPair() == null ? null : spot.getPair().getText());
            } else {
                date = ScheduleSemantics.nextImm(date);
            }
        }
        return date;
    }

    static LocalDate date(PDate node) {
        if (node instanceof AIsoDate iso) {
            return LocalDate.of(number(iso.getYear()), number(iso.getMonth()), number(iso.getDay()));
        } else if (node instanceof AGermanDate german) {
            return LocalDate.of(number(german.getYear()), number(german.getMonth()), number(german.getDay()));
        }
        AUsDate us = (AUsDate) node;
        return LocalDate.of(number(us.getYear()), number(us.getMonth()), number(us.getDay()));
    }

    static TemporalAmount period(PPeriod node) {
        APeriod period = (APeriod) node;
        return ScheduleSemantics.period(number(period.getNumber()), period.getUnit().getText());
    }

    static int number(TNumber token) {
        return Integer.parseInt(token.getText());
    }
}
