package de.juergens;

import org.joda.time.DateTime;

/**
 * @FunctionalInterface
 */
public interface DateTimeAdjuster {
    /**
     * Adjusts the specified dateTime object.
     * <p>
     * This adjusts the specified dateTime object using the logic
     * encapsulated in the implementing class.
     * @param dateTime date and time to adjust
     * @return adjusted date time
     */
    DateTime adjustInto(DateTime dateTime);

    DateTimeAdjuster andThen(DateTimeAdjuster dateTimeAdjuster);
}
