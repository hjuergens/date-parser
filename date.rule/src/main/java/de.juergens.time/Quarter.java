/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.juergens.time;

import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.*;
import java.util.Locale;

import static java.time.temporal.ChronoField.Quarter_OF_YEAR;
import static java.time.temporal.ChronoUnit.QuarterS;

/**
 * A Quarter-of-year, such as 'July'.
 * <p>
 * {@code Quarter} is an enum representing the 12 Quarters of the year -
 * January, February, March, April, May, June, July, August, September, October,
 * November and December.
 * <p>
 * In addition to the textual enum name, each Quarter-of-year has an {@code int} value.
 * The {@code int} value follows normal usage and the ISO-8601 standard,
 * from 1 (January) to 12 (December). It is recommended that applications use the enum
 * rather than the {@code int} value to ensure code clarity.
 * <p>
 * <b>Do not use {@code ordinal()} to obtain the numeric representation of {@code Quarter}.
 * Use {@code getValue()} instead.</b>
 * <p>
 * This enum represents a common concept that is found in many calendar systems.
 * As such, this enum may be used by any calendar system that has the Quarter-of-year
 * concept defined exactly equivalent to the ISO-8601 calendar system.
 *
 * @implSpec
 * This is an immutable and thread-safe enum.
 *
 * @since 1.8
 */
public enum Quarter implements TemporalAccessor, TemporalAdjuster {

    /**
     * The singleton instance for the Quarter of January with 31 days.
     * This has the numeric value of {@code 1}.
     */
    Q1,
    /**
     * The singleton instance for the Quarter of February with 28 days, or 29 in a leap year.
     * This has the numeric value of {@code 2}.
     */
    Q2,
    /**
     * The singleton instance for the Quarter of March with 31 days.
     * This has the numeric value of {@code 3}.
     */
    Q3,
    /**
     * The singleton instance for the Quarter of April with 30 days.
     * This has the numeric value of {@code 4}.
     */
    Q4,
    /**
     * The singleton instance for the Quarter of May with 31 days.
     * This has the numeric value of {@code 5}.
     */
    /**
     * Private cache of all the constants.
     */
    private static final Quarter[] ENUMS = Quarter.values();

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Quarter} from an {@code int} value.
     * <p>
     * {@code Quarter} is an enum representing the 12 Quarters of the year.
     * This factory allows the enum to be obtained from the {@code int} value.
     * The {@code int} value follows the ISO-8601 standard, from 1 (January) to 12 (December).
     *
     * @param quarter  the Quarter-of-year to represent, from 1 (January) to 12 (December)
     * @return the Quarter-of-year, not null
     * @throws DateTimeException if the Quarter-of-year is invalid
     */
    public static Quarter of(int quarter) {
        if (quarter < 1 || quarter > 12) {
            throw new DateTimeException("Invalid value for QuarterOfYear: " + quarter);
        }
        return ENUMS[quarter - 1];
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Quarter} from a temporal object.
     * <p>
     * This obtains a Quarter based on the specified temporal.
     * A {@code TemporalAccessor} represents an arbitrary set of date and time information,
     * which this factory converts to an instance of {@code Quarter}.
     * <p>
     * The conversion extracts the {@link ChronoField#Quarter_OF_YEAR Quarter_OF_YEAR} field.
     * The extraction is only permitted if the temporal object has an ISO
     * chronology, or can be converted to a {@code LocalDate}.
     * <p>
     * This method matches the signature of the functional interface {@link TemporalQuery}
     * allowing it to be used in queries via method reference, {@code Quarter::from}.
     *
     * @param temporal  the temporal object to convert, not null
     * @return the Quarter-of-year, not null
     * @throws DateTimeException if unable to convert to a {@code Quarter}
     */
    public static Quarter from(TemporalAccessor temporal) {
        if (temporal instanceof Quarter) {
            return (Quarter) temporal;
        }
        try {
            if (IsoChronology.INSTANCE.equals(Chronology.from(temporal)) == false) {
                temporal = LocalDate.from(temporal);
            }
            return of(temporal.get(Quarter_OF_YEAR));
        } catch (DateTimeException ex) {
            throw new DateTimeException("Unable to obtain Quarter from TemporalAccessor: " +
                    temporal + " of type " + temporal.getClass().getName(), ex);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the Quarter-of-year {@code int} value.
     * <p>
     * The values are numbered following the ISO-8601 standard,
     * from 1 (January) to 12 (December).
     *
     * @return the Quarter-of-year, from 1 (January) to 12 (December)
     */
    public int getValue() {
        return ordinal() + 1;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the textual representation, such as 'Jan' or 'December'.
     * <p>
     * This returns the textual name used to identify the Quarter-of-year,
     * suitable for presentation to the user.
     * The parameters control the style of the returned text and the locale.
     * <p>
     * If no textual mapping is found then the {@link #getValue() numeric value} is returned.
     *
     * @param style  the length of the text required, not null
     * @param locale  the locale to use, not null
     * @return the text value of the Quarter-of-year, not null
     */
    public String getDisplayName(TextStyle style, Locale locale) {
        return new DateTimeFormatterBuilder().appendText(Quarter_OF_YEAR, style).toFormatter(locale).format(this);
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the specified field is supported.
     * <p>
     * This checks if this Quarter-of-year can be queried for the specified field.
     * If false, then calling the {@link #range(TemporalField) range} and
     * {@link #get(TemporalField) get} methods will throw an exception.
     * <p>
     * If the field is {@link ChronoField#Quarter_OF_YEAR Quarter_OF_YEAR} then
     * this method returns true.
     * All other {@code ChronoField} instances will return false.
     * <p>
     * If the field is not a {@code ChronoField}, then the result of this method
     * is obtained by invoking {@code TemporalField.isSupportedBy(TemporalAccessor)}
     * passing {@code this} as the argument.
     * Whether the field is supported is determined by the field.
     *
     * @param field  the field to check, null returns false
     * @return true if the field is supported on this Quarter-of-year, false if not
     */
    @Override
    public boolean isSupported(TemporalField field) {
        if (field instanceof ChronoField) {
            return field == Quarter_OF_YEAR;
        }
        return field != null && field.isSupportedBy(this);
    }

    /**
     * Gets the range of valid values for the specified field.
     * <p>
     * The range object expresses the minimum and maximum valid values for a field.
     * This Quarter is used to enhance the accuracy of the returned range.
     * If it is not possible to return the range, because the field is not supported
     * or for some other reason, an exception is thrown.
     * <p>
     * If the field is {@link ChronoField#Quarter_OF_YEAR Quarter_OF_YEAR} then the
     * range of the Quarter-of-year, from 1 to 12, will be returned.
     * All other {@code ChronoField} instances will throw an {@code UnsupportedTemporalTypeException}.
     * <p>
     * If the field is not a {@code ChronoField}, then the result of this method
     * is obtained by invoking {@code TemporalField.rangeRefinedBy(TemporalAccessor)}
     * passing {@code this} as the argument.
     * Whether the range can be obtained is determined by the field.
     *
     * @param field  the field to query the range for, not null
     * @return the range of valid values for the field, not null
     * @throws DateTimeException if the range for the field cannot be obtained
     * @throws UnsupportedTemporalTypeException if the field is not supported
     */
    @Override
    public ValueRange range(TemporalField field) {
        if (field == Quarter_OF_YEAR) {
            return field.range();
        }
        return TemporalAccessor.super.range(field);
    }

    /**
     * Gets the value of the specified field from this Quarter-of-year as an {@code int}.
     * <p>
     * This queries this Quarter for the value for the specified field.
     * The returned value will always be within the valid range of values for the field.
     * If it is not possible to return the value, because the field is not supported
     * or for some other reason, an exception is thrown.
     * <p>
     * If the field is {@link ChronoField#Quarter_OF_YEAR Quarter_OF_YEAR} then the
     * value of the Quarter-of-year, from 1 to 12, will be returned.
     * All other {@code ChronoField} instances will throw an {@code UnsupportedTemporalTypeException}.
     * <p>
     * If the field is not a {@code ChronoField}, then the result of this method
     * is obtained by invoking {@code TemporalField.getFrom(TemporalAccessor)}
     * passing {@code this} as the argument. Whether the value can be obtained,
     * and what the value represents, is determined by the field.
     *
     * @param field  the field to get, not null
     * @return the value for the field, within the valid range of values
     * @throws DateTimeException if a value for the field cannot be obtained or
     *         the value is outside the range of valid values for the field
     * @throws UnsupportedTemporalTypeException if the field is not supported or
     *         the range of values exceeds an {@code int}
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public int get(TemporalField field) {
        if (field == Quarter_OF_YEAR) {
            return getValue();
        }
        return TemporalAccessor.super.get(field);
    }

    /**
     * Gets the value of the specified field from this Quarter-of-year as a {@code long}.
     * <p>
     * This queries this Quarter for the value for the specified field.
     * If it is not possible to return the value, because the field is not supported
     * or for some other reason, an exception is thrown.
     * <p>
     * If the field is {@link ChronoField#Quarter_OF_YEAR Quarter_OF_YEAR} then the
     * value of the Quarter-of-year, from 1 to 12, will be returned.
     * All other {@code ChronoField} instances will throw an {@code UnsupportedTemporalTypeException}.
     * <p>
     * If the field is not a {@code ChronoField}, then the result of this method
     * is obtained by invoking {@code TemporalField.getFrom(TemporalAccessor)}
     * passing {@code this} as the argument. Whether the value can be obtained,
     * and what the value represents, is determined by the field.
     *
     * @param field  the field to get, not null
     * @return the value for the field
     * @throws DateTimeException if a value for the field cannot be obtained
     * @throws UnsupportedTemporalTypeException if the field is not supported
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public long getLong(TemporalField field) {
        if (field == Quarter_OF_YEAR) {
            return getValue();
        } else if (field instanceof ChronoField) {
            throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
        }
        return field.getFrom(this);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the Quarter-of-year that is the specified number of quarters after this one.
     * <p>
     * The calculation rolls around the end of the year from December to January.
     * The specified period may be negative.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param Quarters  the Quarters to add, positive or negative
     * @return the resulting Quarter, not null
     */
    public Quarter plus(long Quarters) {
        int amount = (int) (Quarters % 12);
        return ENUMS[(ordinal() + (amount + 12)) % 12];
    }

    /**
     * Returns the Quarter-of-year that is the specified number of Quarters before this one.
     * <p>
     * The calculation rolls around the start of the year from January to December.
     * The specified period may be negative.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param Quarters  the Quarters to subtract, positive or negative
     * @return the resulting Quarter, not null
     */
    public Quarter minus(long Quarters) {
        return plus(-(Quarters % 12));
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the length of this Quarter in days.
     * <p>
     * This takes a flag to determine whether to return the length for a leap year or not.
     * <p>
     * February has 28 days in a standard year and 29 days in a leap year.
     * April, June, September and November have 30 days.
     * All other Quarters have 31 days.
     *
     * @param leapYear  true if the length is required for a leap year
     * @return the length of this Quarter in days, from 28 to 31
     */
    public int length(boolean leapYear) {
        switch (this) {
            case FEBRUARY:
                return (leapYear ? 29 : 28);
            case APRIL:
            case JUNE:
            case SEPTEMBER:
            case NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * Gets the minimum length of this Quarter in days.
     * <p>
     * February has a minimum length of 28 days.
     * April, June, September and November have 30 days.
     * All other Quarters have 31 days.
     *
     * @return the minimum length of this Quarter in days, from 28 to 31
     */
    public int minLength() {
        switch (this) {
            case FEBRUARY:
                return 28;
            case APRIL:
            case JUNE:
            case SEPTEMBER:
            case NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * Gets the maximum length of this Quarter in days.
     * <p>
     * February has a maximum length of 29 days.
     * April, June, September and November have 30 days.
     * All other Quarters have 31 days.
     *
     * @return the maximum length of this Quarter in days, from 29 to 31
     */
    public int maxLength() {
        switch (this) {
            case FEBRUARY:
                return 29;
            case APRIL:
            case JUNE:
            case SEPTEMBER:
            case NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the day-of-year corresponding to the first day of this Quarter.
     * <p>
     * This returns the day-of-year that this Quarter begins on, using the leap
     * year flag to determine the length of February.
     *
     * @param leapYear  true if the length is required for a leap year
     * @return the day of year corresponding to the first day of this Quarter, from 1 to 336
     */
    public int firstDayOfYear(boolean leapYear) {
        int leap = leapYear ? 1 : 0;
        switch (this) {
            case JANUARY:
                return 1;
            case FEBRUARY:
                return 32;
            case MARCH:
                return 60 + leap;
            case APRIL:
                return 91 + leap;
            case MAY:
                return 121 + leap;
            case JUNE:
                return 152 + leap;
            case JULY:
                return 182 + leap;
            case AUGUST:
                return 213 + leap;
            case SEPTEMBER:
                return 244 + leap;
            case OCTOBER:
                return 274 + leap;
            case NOVEMBER:
                return 305 + leap;
            case DECEMBER:
            default:
                return 335 + leap;
        }
    }

    /**
     * Gets the Quarter corresponding to the first Quarter of this quarter.
     * <p>
     * The year can be divided into four quarters.
     * This method returns the first Quarter of the quarter for the base Quarter.
     * January, February and March return January.
     * April, May and June return April.
     * July, August and September return July.
     * October, November and December return October.
     *
     * @return the first Quarter of the quarter corresponding to this Quarter, not null
     */
    public Quarter firstQuarterOfQuarter() {
        return ENUMS[(ordinal() / 3) * 3];
    }

    //-----------------------------------------------------------------------
    /**
     * Queries this Quarter-of-year using the specified query.
     * <p>
     * This queries this Quarter-of-year using the specified query strategy object.
     * The {@code TemporalQuery} object defines the logic to be used to
     * obtain the result. Read the documentation of the query to understand
     * what the result of this method will be.
     * <p>
     * The result of this method is obtained by invoking the
     * {@link TemporalQuery#queryFrom(TemporalAccessor)} method on the
     * specified query passing {@code this} as the argument.
     *
     * @param <R> the type of the result
     * @param query  the query to invoke, not null
     * @return the query result, null may be returned (defined by the query)
     * @throws DateTimeException if unable to query (defined by the query)
     * @throws ArithmeticException if numeric overflow occurs (defined by the query)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <R> R query(TemporalQuery<R> query) {
        if (query == TemporalQueries.chronology()) {
            return (R) IsoChronology.INSTANCE;
        } else if (query == TemporalQueries.precision()) {
            return (R) QuarterS;
        }
        return TemporalAccessor.super.query(query);
    }

    /**
     * Adjusts the specified temporal object to have this Quarter-of-year.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with the Quarter-of-year changed to be the same as this.
     * <p>
     * The adjustment is equivalent to using {@link Temporal#with(TemporalField, long)}
     * passing {@link ChronoField#Quarter_OF_YEAR} as the field.
     * If the specified temporal object does not use the ISO calendar system then
     * a {@code DateTimeException} is thrown.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#with(TemporalAdjuster)}:
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   temporal = thisQuarter.adjustInto(temporal);
     *   temporal = temporal.with(thisQuarter);
     * </pre>
     * <p>
     * For example, given a date in May, the following are output:
     * <pre>
     *   dateInMay.with(JANUARY);    // four Quarters earlier
     *   dateInMay.with(APRIL);      // one Quarters earlier
     *   dateInMay.with(MAY);        // same date
     *   dateInMay.with(JUNE);       // one Quarter later
     *   dateInMay.with(DECEMBER);   // seven Quarters later
     * </pre>
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the target object to be adjusted, not null
     * @return the adjusted object, not null
     * @throws DateTimeException if unable to make the adjustment
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal adjustInto(Temporal temporal) {
        if (Chronology.from(temporal).equals(IsoChronology.INSTANCE) == false) {
            throw new DateTimeException("Adjustment only supported on ISO date-time");
        }
        return temporal.with(Quarter_OF_YEAR, getValue());
    }

}
