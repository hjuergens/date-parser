package io.github.hjuergens.schedule;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

/**
 * Entry point of the shared "schedule" example, see {@code schedule.cup} and {@code schedule.jflex}.
 */
public final class Schedules {

    private Schedules() {
    }

    /**
     * @throws Exception from CUP on a syntax error ("Can't recover from previous error(s)"),
     *                   {@link IllegalArgumentException} from the lexer on an unknown character,
     *                   {@link java.time.DateTimeException} on an invalid date
     */
    @SuppressWarnings({"unchecked", "deprecation"}) // the SymbolFactory constructors need factory-made symbols from the lexer
    public static List<LocalDate> parse(String text) throws Exception {
        ScheduleParser parser = new ScheduleParser(new ScheduleLexer(new StringReader(text)));
        return (List<LocalDate>) parser.parse().value;
    }
}
