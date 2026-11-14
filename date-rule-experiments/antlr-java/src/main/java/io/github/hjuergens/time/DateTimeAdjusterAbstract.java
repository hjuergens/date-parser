package io.github.hjuergens.time;

import org.joda.time.DateTime;

/**
 * Created by juergens on 10.09.16.
 */
abstract class DateTimeAdjusterAbstract implements DateTimeAdjuster {

    @Override
    public DateTimeAdjuster andThen(final DateTimeAdjuster andThenDateTimeAdjuster) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return andThenDateTimeAdjuster.adjustInto(
                        DateTimeAdjusterAbstract.this.adjustInto(dateTime)
                );
            }

            @Override
            public String toString() {
                return DateTimeAdjusterAbstract.this.toString()
                        + "->" + andThenDateTimeAdjuster.toString();
            }
        };
        return adjuster;
    }
}
