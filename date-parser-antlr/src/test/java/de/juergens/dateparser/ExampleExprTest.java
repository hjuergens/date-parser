package de.juergens.dateparser;


import org.antlr.v4.runtime.*;
import org.junit.Test;

/**
 * Created by haj on 4/20/2015.
 */
public class ExampleExprTest {
    @Test
    public void testExampleField() throws Exception {
        ExprLexer l = new ExprLexer(new ANTLRInputStream(getClass().getResourceAsStream("/example.expr")));
        ExprParser p = new ExprParser(new CommonTokenStream(l));
        p.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("failed to parse at line " + line + " due to " + msg, e);
            }
        });
        p.expr();
    }
}
