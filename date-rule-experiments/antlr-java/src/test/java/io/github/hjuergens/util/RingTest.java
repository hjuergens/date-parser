package io.github.hjuergens.util;

import org.joda.time.DateTimeConstants;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class RingTest {
    @Test
    public void minus() throws Exception {

        Ring ring = new Ring(7);

        {
            int result = ring.minus(DateTimeConstants.WEDNESDAY, DateTimeConstants.SUNDAY);
            assertEquals(result, 3);
        }

        {
            int result = ring.minus(DateTimeConstants.SUNDAY, DateTimeConstants.WEDNESDAY);
            assertEquals(result, 4);
        }

    }

    @Test
    public void plus() throws Exception {

        Ring ring = new Ring(7);

        {
            int result = ring.plus(DateTimeConstants.WEDNESDAY, 3*7);
            assertEquals(result, DateTimeConstants.WEDNESDAY);
        }

        {
            int result = ring.plus(DateTimeConstants.SUNDAY, 1);
            assertEquals(result, DateTimeConstants.MONDAY);
        }

        {
            int result = ring.plus(DateTimeConstants.MONDAY, -1-7);
            assertEquals(result, DateTimeConstants.SUNDAY);
        }

    }
}