package io.github.hjuergens.time;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Grammars that have no semantic actions of their own: they are checked for accepting and rejecting input.
 */
public class GrammarTest {

    private static final BaseErrorListener FAIL_ON_ERROR = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg, e);
        }
    };

    private static <P extends Parser> P strict(P parser) {
        parser.removeErrorListeners();
        parser.addErrorListener(FAIL_ON_ERROR);
        return parser;
    }

    private static ISO8601datesParser iso(String input) {
        ISO8601datesLexer lexer = new ISO8601datesLexer(CharStreams.fromString(input));
        lexer.removeErrorListeners();
        lexer.addErrorListener(FAIL_ON_ERROR);
        return strict(new ISO8601datesParser(new CommonTokenStream(lexer)));
    }

    @Test
    public void iso8601DatesFromResource() throws IOException {
        ISO8601datesLexer lexer = new ISO8601datesLexer(CharStreams.fromStream(getClass().getResourceAsStream("/iso8601dates.expr")));
        List<ISO8601datesParser.DateContext> dates =
                strict(new ISO8601datesParser(new CommonTokenStream(lexer))).input().date();

        assertEquals(dates.size(), 3);
        assertEquals(dates.get(0).getText(), "2001-05-03");
        assertEquals(dates.get(2).getText(), "2010-02-29"); // syntax only, no calendar check
    }

    @Test(expectedExceptions = ParseCancellationException.class)
    public void iso8601RejectsMissingDay() {
        iso("2015-06").input();
    }

    @Test
    public void ambiguousDatesFromResource() throws IOException {
        AmbiguousDatesLexer lexer = new AmbiguousDatesLexer(CharStreams.fromStream(getClass().getResourceAsStream("/ambiguousdates.expr")));
        List<AmbiguousDatesParser.DateContext> dates =
                strict(new AmbiguousDatesParser(new CommonTokenStream(lexer))).input().date();

        assertEquals(dates.size(), 2);
        assertEquals(dates.get(0).date_complete().str, "32 May 1980 12h");
        assertEquals(dates.get(1).date_complete().date_day().str, "01 June 1980");
    }

    @Test
    public void ambiguousDatesDistinguishesDayAndTime() {
        AmbiguousDatesParser parser = strict(new AmbiguousDatesParser(new CommonTokenStream(
                new AmbiguousDatesLexer(CharStreams.fromString("1 May 2000 17h")))));
        AmbiguousDatesParser.Date_completeContext complete = parser.input().date(0).date_complete();

        assertEquals(complete.date_day().str, "1 May 2000");
        assertEquals(complete.date_time().str, "17h");
    }

    @DataProvider
    public Object[][] cardinals() {
        return new Object[][]{{"one", CardinalParser.ONE}, {"two", CardinalParser.TWO},
                {"three", CardinalParser.THREE}, {"four", CardinalParser.FOUR}};
    }

    @Test(dataProvider = "cardinals")
    public void cardinal(String word, int tokenType) {
        CardinalParser parser = strict(new CardinalParser(new CommonTokenStream(new CardinalLexer(CharStreams.fromString(word)))));

        assertEquals(parser.cardinal().getStart().getType(), tokenType);
    }

    @Test(expectedExceptions = ParseCancellationException.class)
    public void cardinalRejectsFive() {
        strict(new CardinalParser(new CommonTokenStream(new CardinalLexer(CharStreams.fromString("five"))))).cardinal();
    }

    @DataProvider
    public Object[][] units() {
        return new Object[][]{{"seconds"}, {"minutes"}, {"hours"}, {"days"}, {"weeks"}, {"months"},
                {"years"}, {"decades"}, {"centuries"}, {"eras"}, {"weekyears"}, {"halfdays"}, {"millis"}};
    }

    @Test(dataProvider = "units")
    public void unit(String word) {
        UnitParser parser = strict(new UnitParser(new CommonTokenStream(new UnitLexer(CharStreams.fromString(word)))));

        assertEquals(parser.unit().getText(), word);
    }

    @Test(expectedExceptions = ParseCancellationException.class)
    public void unitIsLexedButNotAcceptedByRule() {
        // 'forever' is a token of the lexer, but not an alternative of rule 'unit'
        strict(new UnitParser(new CommonTokenStream(new UnitLexer(CharStreams.fromString("forever"))))).unit();
    }
}
