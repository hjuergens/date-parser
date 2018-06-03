package io.github.hjuergens.time;

import org.joda.time.DateTime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


public class SelectorTest {

    private static LocalTime zero = new LocalTime(0,0,0,0);
    private static LocalTime twentyFour = zero.minusMillis(1);

    @Test
    public void testNextQuarter_0() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">Q").next();

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = new DateTime(2017, 1, 1, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextQuarter_1() {
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
    public void testNextQuarter_2() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">=Q").next();


        {
            DateTime referenceDateTime = new LocalDate(2018, 4, 1).toDateTime(LocalTime.now());
            DateTime actual = adjuster.adjustInto(referenceDateTime);
            DateTime expected = new LocalDate(2018, 4, 1).toDateTime(LocalTime.MIDNIGHT);

            assertEquals(actual, expected, adjuster.toString());
        }
    }

    @Test
    public void testNextWednesday() {
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
    public void testThirdWednesday() {
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
    public void testThirdWednesday8() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>>wednesday").next();

        LocalDateTime referenceDateTime = LocalDateTime.of(2016, 12, 5, 15, 18, 22, 65);
        DateTime jodasReferenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(jodasReferenceDateTime);

        java.time.LocalTime zero = java.time.LocalTime.of(0,0,0,0);
        TemporalAdjuster thirdWednesday = new TemporalAdjuster() {
            private TemporalAdjuster w = TemporalAdjusters.next(DayOfWeek.WEDNESDAY);
            @Override
            public Temporal adjustInto(Temporal temporal) {
                Temporal rtnTemporal = temporal;
                for (int i = 0; i < 3; i++) {
                    rtnTemporal = w.adjustInto(rtnTemporal);

                }
                return rtnTemporal;
            }
        };
        LocalDateTime expectation = LocalDateTime.from(thirdWednesday.adjustInto(referenceDateTime)).with(zero);
        assertEquals( actual.getYear(), expectation.getYear(), adjuster.toString() );
        assertEquals( actual.getMonthOfYear(), expectation.getMonth().getValue(), adjuster.toString() );
        assertEquals( actual.getDayOfMonth(), expectation.getDayOfMonth(), adjuster.toString() );
        assertEquals( actual.getHourOfDay(), expectation.getHour(), adjuster.toString() );
        assertEquals( actual.getMinuteOfHour(), expectation.getMinute(), adjuster.toString() );
    }

    @Test
    public void testNextDay() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">D").next();

        DateTime referenceDateTime = new DateTime(2016, 2, 28, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusDays(1);
        expected = expected.withTime(zero);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testPreviousDay() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule("<<D").next();

        DateTime referenceDateTime = new DateTime(2016, 1, 2, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.minusDays(2);
        expected = expected.withTime(twentyFour);

        assertEquals( actual, expected, adjuster.toString() );
    }

    @Test
    public void testNextButOneMonth() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>M").next();

        DateTime referenceDateTime = new DateTime(2016, 1, 2, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = referenceDateTime;
        expected = expected.plusMonths(2);
        expected = expected.withTime(zero);

        assertEquals( actual, expected, adjuster.toString() );
    }


    @Test(enabled = true, dependsOnMethods = { "testThirdWednesday", "testNextButOneMonth" })
    public void testThirdWedesdayInSecondMonth() {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>M>>>wednesday").next();

        DateTime referenceDateTime = new LocalDate(2018,1,1).toDateTime(LocalTime.now());
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = new LocalDate(2018, 3, 21).toDateTime(LocalTime.MIDNIGHT);

        assertEquals( actual, expected, adjuster.toString() );
    }

}
