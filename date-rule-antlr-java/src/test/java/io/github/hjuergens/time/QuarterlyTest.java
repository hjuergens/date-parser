package io.github.hjuergens.time;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class QuarterlyTest {

    @Test
    public void nextIsFirstDayOfFollowingQuarter() {
        Quarterly quarterly = new Quarterly(new DateTime(2016, 12, 5, 15, 18, 22, 65), 1);

        assertTrue(quarterly.hasNext());
        assertEquals(quarterly.next(), new DateTime(2017, 1, 1, 0, 0));
        assertEquals(quarterly.next(), new DateTime(2017, 4, 1, 0, 0));
        assertEquals(quarterly.next(), new DateTime(2017, 7, 1, 0, 0));
        assertEquals(quarterly.next(), new DateTime(2017, 10, 1, 0, 0));
    }

    @Test
    public void firstDayOfQuarterAdvancesToNextQuarter() {
        Quarterly quarterly = new Quarterly(new DateTime(2016, 4, 1, 0, 0), 1);

        assertEquals(quarterly.next(), new DateTime(2016, 7, 1, 0, 0));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void removeIsNotSupported() {
        new Quarterly(new DateTime(2016, 4, 1, 0, 0), 1).remove();
    }
}
