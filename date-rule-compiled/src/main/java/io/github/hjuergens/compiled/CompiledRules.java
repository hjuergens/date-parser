package io.github.hjuergens.compiled;

import io.github.hjuergens.time.DateScheduleLexer;
import io.github.hjuergens.time.DateScheduleParser;
import io.github.hjuergens.time.ScheduleSemantics;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

/**
 * Entry point: a schedule parsed with the grammar of date-rule-antlr-java and compiled to a class of its own, loaded by
 * a {@link RuleClassLoader} of its own. A rule that is no longer referenced is unloaded with its loader, so rules can be
 * replaced at run time by parsing the new text.
 */
public final class CompiledRules {

    private static final BaseErrorListener FAIL = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            throw new ParseCancellationException(line + ":" + charPositionInLine + " " + msg, e);
        }
    };

    private CompiledRules() {
    }

    /**
     * The compiled rule of a schedule; it yields the same dates as {@code DateSchedules.parse(text)}.
     *
     * @throws ParseCancellationException on a syntax error
     * @throws java.time.DateTimeException on an invalid date
     * @throws IllegalArgumentException on a range without positive step
     */
    public static ScheduleSemantics.Rule parse(String text) {
        DateScheduleLexer lexer = new DateScheduleLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(FAIL);
        DateScheduleParser parser = new DateScheduleParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(FAIL);
        parser.setErrorHandler(new BailErrorStrategy());
        return RuleCompiler.compile(parser.schedule());
    }
}
