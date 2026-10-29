package io.github.hjuergens.schedule;

import java.io.StringReader;

/**
 * Entry point of the shared "schedule" example, see {@code schedule.cup} and {@code schedule.jflex}.
 */
public final class Schedules {

    private Schedules() {
    }

    /**
     * The rule of a schedule; evaluate it with {@code rule.evaluate(new ScheduleSemantics.Context(base, market))}.
     *
     * @throws Exception from CUP on a syntax error ("Can't recover from previous error(s)"),
     *                   {@link IllegalArgumentException} from the lexer on an unknown character,
     *                   {@link java.time.DateTimeException} on an invalid date
     */
    @SuppressWarnings("deprecation") // the SymbolFactory constructors need factory-made symbols from the lexer
    public static ScheduleSemantics.Rule parse(String text) throws Exception {
        ScheduleParser parser = new ScheduleParser(new ScheduleLexer(new StringReader(text)));
        return (ScheduleSemantics.Rule) parser.parse().value;
    }
}
