package io.github.hjuergens.time;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;


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
     * The rule of a schedule; evaluate it with {@code rule.evaluate(new ScheduleSemantics.Context(base, market))}.
     *
     * @throws ParseCancellationException on a syntax error
     * @throws java.time.DateTimeException on an invalid date
     * @throws IllegalArgumentException on a range without positive step
     */
    public static ScheduleSemantics.Rule parse(String text) {
        return parser(text).schedule().result;
    }

    /**
     * One line of a rules file of date-rule-runtime: {@code rule @NAME = ...}, {@code market @NAME = ...},
     * {@code event @NAME = ...} or {@code assert @NAME [base @MARKET] => expected}.
     *
     * @throws ParseCancellationException on a syntax error, with column and message
     * @throws java.time.DateTimeException on an invalid date
     */
    public static DateScheduleParser.RuleLineContext parseRuleLine(String text) {
        return parser(text).ruleLine();
    }

    private static DateScheduleParser parser(String text) {
        DateScheduleLexer lexer = new DateScheduleLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(FAIL);
        DateScheduleParser parser = new DateScheduleParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(FAIL);
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }
}
