package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.testng.annotations.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TermStructureTest {

    final static private Logger logger = LoggerFactory.getLogger(Test.class);

    @Test
    public void sequenceSingle() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("[7W]");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Period> result = parser.sequence().listOut;
        assertEquals(result.size(), 1);
        logger.debug("testSequence:" + result);
        assertEquals(result.get(0), Period.weeks(7));
    }

    @Test
    public void sequence() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("[3M,5M,7M]");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Period> result = parser.sequence().listOut;
        assertEquals(result.size(), 3);
        logger.debug("testSequence:" + result);
        assertEquals(result.get(0), Period.months(3));
        assertEquals(result.get(1), Period.months(5));
        assertEquals(result.get(2), Period.months(7));
    }

    @Test
    public void loopWithStep() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("(4M:1M:9M)");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        Period p = Period.parse("P9M").minus(Period.parse("P4M3972773W"));
        logger.debug("period:" + p);

        List<Period> result = parser.loop().listOut;
        assertEquals(result.size(), 6);
        assertEquals(result.get(0), Period.months(4));
        assertEquals(result.get(5), Period.months(9));
    }

    @Test
    public void loop() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("(4Y:9Y)");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Period> result = parser.loop().listOut;
        assertEquals(result.size(), (9-4)*12+1);
        assertEquals(result.get(0), Period.years(4));
        assertEquals(result.get(60), Period.years(9));
    }

    @Test
    public void futures() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("[3M,5M,7M]+3M1W");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        DateTime dt = DateTime.parse("2016-11-13");

        List<Pair<Period,DateTimeAdjuster>> list = parser.futures().listOut;

        assertEquals(list.size(), 3);

        Pair<Period, DateTimeAdjuster> pair;

        pair = list.get(0);
        assertEquals(pair.getFirst(), Period.months(3));
        assertEquals(pair.getSecond().adjustInto(dt), DateTime.parse("2017-02-20"));

        pair = list.get(1);
        assertEquals(pair.getFirst(), Period.months(5));
        assertEquals(pair.getSecond().adjustInto(dt), DateTime.parse("2017-02-20"));

        pair = list.get(2);
        assertEquals(pair.getFirst(), Period.months(7));
        assertEquals(pair.getSecond().adjustInto(dt), DateTime.parse("2017-02-20"));
    }

    @Test
    public void concat1() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("[3M,5M,7M].3M1W");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Pair<Period,DateTimeAdjuster>> result = parser.futures().listOut;
        assertEquals(result.size(), 3);
        logger.debug("testSequence:" + result);
        assertEquals(result.get(0).getFirst(), Period.months(3+3).plusWeeks(1));
        assertEquals(result.get(1).getFirst(), Period.months(5+3).plusWeeks(1));
        assertEquals(result.get(2).getFirst(), Period.months(7+3).plusWeeks(1));
    }

    @Test
    public void concat2() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("(4M:1M:9M).1D");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Period> list = parser.list().periods;

        assertEquals(list.get(0), Period.parse("P4M1D"));
        assertEquals(list.get(1), Period.parse("P5M1D"));
        assertEquals(list.get(5), Period.parse("P9M1D"));
    }

    @Test
    public void forward() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("3Mx6M");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        Pair<DateTimeAdjuster, DateTimeAdjuster> pair = parser.forward().fwd;
        DateTime dt = DateTime.parse("2016-10-29");
        assertEquals(pair.getFirst().adjustInto(dt), dt.withPeriodAdded(Period.months(3),1));
        assertEquals(pair.getSecond().adjustInto(dt), dt.withPeriodAdded(Period.months(6),1));
    }

    @Test
    public void forwards() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("[3M,4M,6M]x(4M:1M:9M)");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Pair<DateTimeAdjuster, DateTimeAdjuster>> list = parser.forwards().listOut;
        DateTime dt = DateTime.parse("2016-10-29");

        Pair<DateTimeAdjuster, DateTimeAdjuster> pair;

        pair = list.get(0);
        assertEquals(pair.getFirst().adjustInto(dt), dt.withPeriodAdded(Period.months(3),1));
        assertEquals(pair.getSecond().adjustInto(dt), dt.withPeriodAdded(Period.months(4),1));

        pair = list.get(1);
        assertEquals(pair.getFirst().adjustInto(dt), dt.withPeriodAdded(Period.months(4),1));
        assertEquals(pair.getSecond().adjustInto(dt), dt.withPeriodAdded(Period.months(5),1));

        pair = list.get(2);
        assertEquals(pair.getFirst().adjustInto(dt), dt.withPeriodAdded(Period.months(6),1));
        assertEquals(pair.getSecond().adjustInto(dt), dt.withPeriodAdded(Period.months(6),1));
    }

    @Test
    public void listVsSequence() throws Exception {
        ANTLRInputStream inputStream;
        TermStructureLexer lexer;
        CommonTokenStream tokenStream;
        TermStructureParser parser;

        inputStream  = new ANTLRInputStream("[3M,5M,7M]");
        lexer      = new TermStructureLexer(inputStream);
        tokenStream = new CommonTokenStream(lexer);
        parser    = new TermStructureParser(tokenStream);

        List<Pair<DateTimeAdjuster, DateTimeAdjuster>> list = parser.forwards().listOut;

        inputStream = new ANTLRInputStream("(3M:2M:7M)");
        lexer = new TermStructureLexer(inputStream);
        tokenStream = new CommonTokenStream(lexer);
        parser = new TermStructureParser(tokenStream);

        List<Pair<DateTimeAdjuster, DateTimeAdjuster>> sequence = parser.forwards().listOut;

        assertEquals(list, sequence);
    }

}