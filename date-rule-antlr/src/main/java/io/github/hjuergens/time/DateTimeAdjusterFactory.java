package io.github.hjuergens.time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by juergens on 10.09.16.
 */
final class DateTimeAdjusterFactory {

    static LocalTime zero = new LocalTime(0,0,0,0);
    static LocalTime twentyFour = zero.minusMillis(1);

    static final Logger logger = LoggerFactory.getLogger(DateTimeAdjuster.class);

    private DateTimeAdjusterFactory(){}

    static DateTimeAdjuster apply() {
        final Logger logger = LoggerFactory.getLogger(DateTimeAdjuster.class);
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return dateTime;
            }

            @Override
            public String toString() {
                return "identity";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    static DateTimeAdjuster apply(final Period period, final int scalar) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {

                return dateTime.withPeriodAdded(period, scalar);
            }

            @Override
            public String toString() {
                return period.toString();
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    public static DateTimeAdjuster quarter(final int scalar) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return new Quarterly(dateTime, scalar).next();
            }

            @Override
            public String toString() {
                return "io.github.hjuergens.time.Quarterly";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    static DateTimeAdjuster field(DateTimeFieldType dateTimeFieldType, int scale) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return dateTime.property(dateTimeFieldType).addToCopy(scale).withTime(scale >= 0 ? zero : twentyFour);
            }

            @Override
            public String toString() {
                return dateTimeFieldType.toString();
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    // WEDNESDAY = the third day of the week (ISO)
    static DateTimeAdjuster dayOfWeek(final int dayOfWeek, final int scalar) {

        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                DateTime expected = dateTime;
                while(expected.getDayOfWeek() != dayOfWeek)
                    expected = expected.plusDays( Integer.signum(scalar) );
                expected = expected.plusWeeks(scalar - Integer.signum(scalar));
                expected = expected.withTime(LocalTime.MIDNIGHT).minusMillis(scalar<0?1:0);
                return expected;
            }

            @Override
            public String toString() {
                return "dayOfWeek";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }


}
