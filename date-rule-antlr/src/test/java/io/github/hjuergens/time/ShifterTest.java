package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


public class ShifterTest {
    {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
    }

    @Test
    public void model2Y3D() {
        Period period = Period.parse("P" + "2Y3D");
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.apply();
        DateTimeAdjuster periodAdjuster = DateTimeAdjusterFactory.apply(period,1);

        adjuster= adjuster.andThen(periodAdjuster);

        DateTime dt = new DateTime(1972, 12, 3, 0, 0, 0, 0);
        DateTime actual = adjuster.adjustInto(dt);
        DateTime expected = new DateTime(1974, 12, 6, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void test2Y3D() throws Exception {
        PeriodTermLexer lex = new PeriodTermLexer(new ANTLRInputStream("+2Y3D"));
        CommonTokenStream tokens = new CommonTokenStream(lex);
        PeriodTermParser parser = new PeriodTermParser(tokens);

        /*
        System.out.println("signum="+ parser.operator().signum);
        System.out.println("period="+ parser.period().p);
        */
        DateTimeAdjuster adjuster = parser.shift().adjusterOut;
        //DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule("+2Y3D").next();

        DateTime dt = new DateTime(1972, 12, 3, 0, 0, 0, 0);
        DateTime actual = adjuster.adjustInto(dt);
        DateTime expected = new DateTime(1974, 12, 6, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void test30Yplus3D() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule("+30Y+3D").next();

        DateTime referenceDateTime = new DateTime(2002, 9, 8, 0, 7, 0, 0);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusYears(30);
        expected = expected.plusDays(3);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void test2Y3Dminus7W() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule("+2Y3D-7W").next();

        DateTime referenceDateTime = new DateTime(1983, 12, 3, 0, 7, 0, 0);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusYears(2);
        expected = expected.plusDays(3);
        expected = expected.minusWeeks(7);

        assertEquals( actual, expected, adjuster.toString() );
    }
}
