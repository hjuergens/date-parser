package io.github.hjuergens.time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


public class SelectorTest {

    private static LocalTime zero = new LocalTime(0,0,0,0);
    private static LocalTime twentyFour = zero.minusMillis(1);

    @Test
    public void testNextQuarter_0() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">Q").next();

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = new DateTime(2017, 1, 1, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextQuarter_1() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">Q").next();

        {
            DateTime referenceDateTime = new LocalDate(2018, 1, 1).toDateTime(LocalTime.now());
            DateTime actual = adjuster.adjustInto(referenceDateTime);
            DateTime expected = new LocalDate(2018, 4, 1).toDateTime(LocalTime.MIDNIGHT);

            assertEquals(actual, expected, adjuster.toString());
        }

        {
            DateTime referenceDateTime = new LocalDate(2018, 3, 31).toDateTime(LocalTime.now());
            DateTime actual = adjuster.adjustInto(referenceDateTime);
            DateTime expected = new LocalDate(2018, 4, 1).toDateTime(LocalTime.MIDNIGHT);

            assertEquals(actual, expected, adjuster.toString());
        }
    }

    @Test(enabled = false)
    public void testNextQuarter_2() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">=Q").next();


        {
            DateTime referenceDateTime = new LocalDate(2018, 4, 1).toDateTime(LocalTime.now());
            DateTime actual = adjuster.adjustInto(referenceDateTime);
            DateTime expected = new LocalDate(2018, 4, 1).toDateTime(LocalTime.MIDNIGHT);

            assertEquals(actual, expected, adjuster.toString());
        }
    }

    @Test
    public void testNextWednesday() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">wednesday").next();

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        while(expected.getDayOfWeek() != DateTimeConstants.WEDNESDAY)
            expected = expected.plusDays(1);
        expected = expected.withTime(zero);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testThirdWednesday() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>>wednesday").next();

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        while(expected.getDayOfWeek() != DateTimeConstants.WEDNESDAY)
            expected = expected.plusDays(1);
        expected = expected.plusWeeks(2);
        expected = expected.withTime(zero);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextDay() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">D").next();

        DateTime referenceDateTime = new DateTime(2016, 2, 28, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusDays(1);
        expected = expected.withTime(zero);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testPreviousDay() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule("<<D").next();

        DateTime referenceDateTime = new DateTime(2016, 1, 2, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.minusDays(2);
        expected = expected.withTime(twentyFour);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextButOneMonth() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>M").next();

        DateTime referenceDateTime = new DateTime(2016, 1, 2, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusMonths(2);
        expected = expected.withTime(zero);

        assertEquals( actual, expected, adjuster.toString() );
    }


    @Test(enabled = true, dependsOnMethods = { "testThirdWednesday", "testNextButOneMonth" })
    public void testThirdWedesdayInSecondMonth() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>M>>>wednesday").next();

        DateTime referenceDateTime = new LocalDate(2018,1,1).toDateTime(LocalTime.now());
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = new LocalDate(2018, 3, 21).toDateTime(LocalTime.MIDNIGHT);

        assertEquals( actual, expected, adjuster.toString() );
    }

}
