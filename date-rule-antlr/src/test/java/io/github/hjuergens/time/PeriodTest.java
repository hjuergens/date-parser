package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.Period;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class PeriodTest {
    @Test
    public void test89() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("89");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.twodigit().value, 89);
    }

    @Test
    public void test28days() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("28D");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.days().p, Period.days(28));
    }

    @Test
    public void test28daysSub() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("28D");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.dayssub().p, Period.days(28));
    }

    @Test
    public void testWeeks() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("7W");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.weeks().p, Period.parse("P7W"));
    }

    @Test
    public void testWeekSubs() throws Exception {
        {
            ANTLRInputStream inputStream = new ANTLRInputStream("7W");
            PeriodLexer lexer = new PeriodLexer(inputStream);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            PeriodParser parser = new PeriodParser(tokenStream);

            assertEquals(parser.weekssub().p, Period.parse("P7W"));
        }

        {
            ANTLRInputStream inputStream = new ANTLRInputStream("12W99D");
            PeriodLexer lexer = new PeriodLexer(inputStream);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            PeriodParser parser = new PeriodParser(tokenStream);

            assertEquals(parser.weekssub().p, Period.parse("P12W99D"));
        }
    }

    @Test
    public void test14M7W9D() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("14M7W9D");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.monthssub().p, Period.parse("P14M7W9D"));
    }

}