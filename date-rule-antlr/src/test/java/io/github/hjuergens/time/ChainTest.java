package io.github.hjuergens.time;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


public class ChainTest {

    @Test
    public void testNextQuarter() throws Exception {
        DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule("^>Q").next();

        DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
        DateTime actual = adjuster.adjustInto(referenceDateTime);
        DateTime expected = new DateTime(2017, 1, 1, 0, 0, 0, 0);

        assertEquals( actual, expected, adjuster.toString() );
    }

}
