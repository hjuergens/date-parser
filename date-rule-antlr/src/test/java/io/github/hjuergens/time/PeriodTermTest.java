package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class PeriodTermTest {
    @Test
    public void testPlus3M1W() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("+3M1W");
        PeriodTermLexer lexer = new PeriodTermLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodTermParser parser = new PeriodTermParser(tokenStream);

        //assertNotNull(parser.adjust().shift().operator());
        //assertNotNull(parser.adjust().shift().period());
    }

    @Test
    public void testMonday() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream(">>=monday");
        PeriodTermLexer lexer = new PeriodTermLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodTermParser parser = new PeriodTermParser(tokenStream);

        assertNotNull(parser.adjust().selector().direction());
    }


}