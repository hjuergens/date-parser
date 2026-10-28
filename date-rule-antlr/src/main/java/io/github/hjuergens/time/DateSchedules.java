package io.github.hjuergens.time;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.time.LocalDate;
import java.util.List;

/**
 * Entry point of the shared "schedule" example, see {@code DateSchedule.g4}.
 */
public final class DateSchedules {

    private static final BaseErrorListener FAIL = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            throw new ParseCancellationException(line + ":" + charPositionInLine + " " + msg, e);
        }
    };

    private DateSchedules() {
    }

    /**
     * @throws ParseCancellationException on a syntax error
     * @throws java.time.DateTimeException on an invalid date
     * @throws IllegalArgumentException on a range without positive step
     */
    public static List<LocalDate> parse(String text) {
        DateScheduleLexer lexer = new DateScheduleLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(FAIL);
        DateScheduleParser parser = new DateScheduleParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(FAIL);
        parser.setErrorHandler(new BailErrorStrategy());
        return parser.schedule().dates;
    }
}
