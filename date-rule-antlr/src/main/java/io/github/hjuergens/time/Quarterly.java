package io.github.hjuergens.time;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * The calendar year can be divided into four quarters, often abbreviated as Q1, Q2, Q3, and Q4.
 * First quarter, Q1: 1 January–31 March (90 days or 91 days in leap years)
 * Second quarter, Q2: 1 April-30 June (91 days)
 * Third quarter, Q3: 1 July-30 September (92 days* )
 * Fourth quarter, Q4: 1 October-31 December (92 days)
 * When combined with a year, the quarter/year combination is abbreviated as QxYYYY (Q42016) or
 * the slightly less confusing xQYYYY (4Q2016)
 * [https://en.wikipedia.org/wiki/Calendar_year]
 */
public class Quarterly implements Iterator<DateTime> {
    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    volatile DateTime current = null;
    final int scalar;

    Quarterly(DateTime current, int scalar) {
        this.current = current.withTime(0,0,0,0);
        this.scalar = scalar;
        logger.trace(current.toString() + ";" + scalar);
    }
    @Override
    public boolean hasNext() {
        logger.trace(current.toString() + ";" + scalar);
        return true;
    }

    @Override
    public DateTime next() {
        final int addMonth = 3 - (current.getMonthOfYear()-1) % 3;
        logger.trace("addMonth=" + addMonth);
        logger.trace("before=" + current);
        current = current.plusMonths(addMonth).dayOfMonth().setCopy(1); // next month first day
        logger.trace("after=" + current);
        return current;
    }

    @Override
    public void remove() {
        logger.trace("remove");
        throw new java.lang.UnsupportedOperationException();
    }
}
