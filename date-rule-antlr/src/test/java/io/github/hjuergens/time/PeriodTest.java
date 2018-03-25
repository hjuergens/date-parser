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

        assertEquals(parser.days().p, Period.weeks(4));
    }

    @Test
    public void test28daysSub() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("28D");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.dayssub().p, Period.weeks(4));
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

            assertEquals(parser.weekssub().p, Period.parse("P26W1D"));
        }
    }

    @Test
    public void test14M7W9D() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("14M7W9D");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.monthssub().p, Period.parse("P1Y2M8W2D"));
    }

    /*
    @Test(expectedExceptions= org.antlr.v4.runtime.misc.ParseCancellationException.class)
    public void test1M2D3W() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("1M2D3W");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        lexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);
        parser.setErrorHandler(new BailErrorStrategy());
        assertNotNull( parser.period().p );
    }
    */

    @Test
    public void testMonths() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("1M");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.months().p, Period.parse("P1M"));
    }

    @Test
    public void testMonthsOverYear() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("15M");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        final Period p = parser.months().p;
        assertEquals(p, Period.parse("P15M").normalizedStandard());
        assertEquals(p, Period.parse("P1Y3M").normalizedStandard());
    }

    @Test
    public void concat() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("1M2D.1Y2M7W");
        PeriodLexer lexer = new PeriodLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodParser parser = new PeriodParser(tokenStream);

        assertEquals(parser.period().p, Period.parse("P1Y3M7W2D"));
    }
}