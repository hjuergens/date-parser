package io.github.hjuergens.time.dateparser;


import io.github.hjuergens.time.ICalendarLexer;
import io.github.hjuergens.time.ICalendarParser;
import org.antlr.v4.runtime.*;
import org.testng.annotations.Test;

/**
 * Created by haj on 01/02/2015.
 */
public class ICalendarExprTest {
    @Test
    public void testExampleField() throws Exception {
        ICalendarLexer l = new ICalendarLexer(new ANTLRInputStream(getClass().getResourceAsStream("/example.expr")));
        ICalendarParser p = new ICalendarParser(new CommonTokenStream(l));
        p.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("failed to parse at line " + line + " due to " + msg, e);
            }
        });
        p.eventc();
    }
}
