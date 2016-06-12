package de.juergens;

import org.joda.time.DateTime;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
    volatile DateTime current = null;
    final int scalar;

    Quarterly(DateTime current, int scalar) {
        this.current = current.withTime(0,0,0,0);
        this.scalar = scalar;
    }
    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public DateTime next() {
        current = current.plusMonths(1).dayOfMonth().setCopy(1);
        int month = current.getMonthOfYear() % 4;
        current.monthOfYear().setCopy(month);
        return current;
    }

    @Override
    public void remove() { throw new NotImplementedException(); }
}
