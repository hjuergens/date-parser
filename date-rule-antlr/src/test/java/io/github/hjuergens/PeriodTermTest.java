package io.github.hjuergens;

import de.juergens.DateTimeAdjuster;
import de.juergens.DateTimeAdjusterFactory;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Period;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


public class PeriodTermTest {
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
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.parseAdjuster("+2Y3D");

        DateTime dt = new DateTime(1972, 12, 3, 0, 0, 0, 0);
        DateTime actual = adjuster.adjustInto(dt);
        DateTime expected = new DateTime(1974, 12, 6, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void test30Yplus3D() throws Exception {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.parseAdjuster("+30Y+3D");

        DateTime referenceDateTime = new DateTime(2002, 9, 8, 0, 7, 0, 0);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusYears(30);
        expected = expected.plusDays(3);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void test2Y3Dminus7W() throws Exception {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.parseAdjuster("+2Y3D-7W");

        DateTime referenceDateTime = new DateTime(1983, 12, 3, 0, 7, 0, 0);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusYears(2);
        expected = expected.plusDays(3);
        expected = expected.minusWeeks(7);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextQuarter() throws Exception {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.parseAdjuster(">Q");

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = new DateTime(2017, 1, 1, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextWednesday() throws Exception {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.parseAdjuster(">wednesday");

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        while(expected.getDayOfWeek() != DateTimeConstants.WEDNESDAY)
            expected = expected.plusDays(1);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    @Ignore
    public void testThirdWednesday() throws Exception {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.parseAdjuster(">>>wednesday");

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        while(expected.getDayOfWeek() != DateTimeConstants.WEDNESDAY)
            expected = expected.plusDays(1);
        expected = expected.plusWeeks(2);

        assertEquals( actual, expected, adjuster.toString() );
    }


}
