/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.time

import java.time.{ZoneId, ZonedDateTime, LocalDate}
import java.util.{Calendar => JCalendar, GregorianCalendar}

/**
 * @author juergens
 */
object JulianDate {
  /**
   * Returns the Julian day number that begins at noon of
   * this day, Positive year signifies A.D., negative year B.C.
   * Remember that the year after 1 B.C. was 1 A.D.
   *
   * ref :
   *  Numerical Recipes in C, 2nd ed., Cambridge University Press 1992
   */
  // Gregorian Calendar adopted Oct. 15, 1582 (2299161)
  val JGREG :Int = 15 + 31*(10+12*1582)
  val HALFSECOND:Double = 0.5

  def toJulian(date:ZonedDateTime):Double = {
    require(date.getZone.getId equals "UT")
    ZonedDateTime.now(ZoneId.of("UT"))
    val year= date.getYear
    val month = date.getMonthValue // jan=1, feb=2,...
    val day = date.getDayOfMonth
    var julianYear :Int= year
    if (year < 0) julianYear+=1
    var julianMonth = month
    if (month > 2) {
      julianMonth+=1
    }
    else {
      julianYear-=1
      julianMonth += 13
    }

    var julian :Double = (java.lang.Math.floor(365.25 * julianYear)
      + java.lang.Math.floor(30.6001*julianMonth) + day + 1720995.0)
    if (day + 31 * (month + 12 * year) >= JGREG) {
      // change over to Gregorian calendar
      val ja:Int = (0.01 * julianYear).toInt
      julian += 2 - ja + (0.25 * ja)
    }
    java.lang.Math.floor(julian)
  }

  /**
   * Converts a Julian day to a calendar date
   * ref :
   * Numerical Recipes in C, 2nd ed., Cambridge University Press 1992
   */
  def fromJulian(inJulian:Double) : LocalDate ={
    val julian :Double = inJulian + HALFSECOND / 86400.0
    var ja :Int = julian.toInt
    if (ja >= JGREG) {
      val jalpha = (((ja - 1867216) - 0.25) / 36524.25).toInt
      ja = ja + 1 + jalpha - jalpha / 4
    }

    val jb :Int = ja + 1524
    val jc = (6680.0 + ((jb - 2439870) - 122.1) / 365.25).toInt
    val jd :Int = 365 * jc + jc / 4
    val je = ((jb - jd) / 30.6001).toInt
    val day :Int = jb - jd - (30.6001 * je).toInt
    var month :Int = je - 1
    if (month > 12) month = month - 12
    var year = jc - 4715
    if (month > 2) year-=1
    if (year <= 0) year-=1

    LocalDate.of(year, month, day)
  }

    def dateToJulian(date : java.util.Calendar) : Double ={
        val year = date.get(java.util.Calendar.YEAR)
        val month = date.get(java.util.Calendar.MONTH) + 1
        val day = date.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = date.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = date.get(java.util.Calendar.MINUTE)
        val second = date.get(java.util.Calendar.SECOND)

        val extra = (100.0 * year) + month - 190002.5
        (367.0 * year) -
                (Math.floor(7.0 * (year + Math.floor((month + 9.0) / 12.0)) / 4.0)) +
                Math.floor((275.0 * month) / 9.0) +
                day + ((hour + ((minute + (second / 60.0)) / 60.0)) / 24.0) +
                1721013.5 - ((0.5 * extra) / Math.abs(extra)) + 0.5
    }

      def currentDateToJulianDate(cal:GregorianCalendar): Double = {
          var jd = 0.0
              val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)   // 0..23
              val min = cal.get(java.util.Calendar.MINUTE)  //0..59
              val sec = cal.get(java.util.Calendar.SECOND)
              val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
              val month = cal.get(java.util.Calendar.MONTH) + 1
              val year = cal.get(java.util.Calendar.YEAR)

              jd = (367 * year) - (7 * (year + ((month + 9) / 12).toInt) / 4).toInt -
                ((3 * ((year + (month - 9) / 7) / 100).toInt + 1) / 4).toInt
                      + ((275 * month) / 9).toInt + day + 1721028.5
                      + (hour24 + (min / 60) + (sec / 3600)) / 24
          jd
      }

  /*
  public static void main(String args[]) {
    // FIRST TEST reference point
    System.out.println("Julian date for May 23, 1968 : "
      + toJulian( new int[] {1968, 5, 23 } ));
    // output : 2440000
    int results[] = fromJulian(toJulian(new int[] {1968, 5, 23 }));
    System.out.println
    ("... back to calendar : " + results[0] + " "
    + results[1] + " " + results[2]);

    // SECOND TEST today
    Calendar today = Calendar.getInstance();
    double todayJulian = toJulian
    (new int[]{today.get(Calendar.YEAR), today.get(Calendar.MONTH)+1,
      today.get(Calendar.DATE)});
    System.out.println("Julian date for today : " + todayJulian);
    results = fromJulian(todayJulian);
    System.out.println
    ("... back to calendar : " + results[0] + " " + results[1]
    + " " + results[2]);

    // THIRD TEST
    double date1 = toJulian(new int[]{2005,1,1});
    double date2 = toJulian(new int[]{2005,1,31});
    System.out.println("Between 2005-01-01 and 2005-01-31 : "
      + (date2 - date1) + " days");
*/
    /*
       expected output :
          Julian date for May 23, 1968 : 2440000.0
          ... back to calendar 1968 5 23
          Julian date for today : 2453487.0
          ... back to calendar 2005 4 26
          Between 2005-01-01 and 2005-01-31 : 30.0 days
    */

}

/*
There is a lot of variation around the idea of a "Julian date". You can have the Modified Julian Date (JD) or the Truncated Julian Date (TJD). The main difference is the starting for counting the days.

Before Y2K, many applications (especially mainframe systems) were storing dates in a format called "the Julian format". This format is a 5 digit number, consisting of a 2 digit year and a 3 digit day-of-year number. For example, 17-July-1998 is stored as 98221, since 17-July is the 221th day of the year. This format is not really useful since Y2K! The main reason for using the 5-digits Julian date was to save disk space and still have a format easy to use to handle. dates.

A variation of this idea is to used the first four digits for the year and 3 digits for day-of-year, 17-July-1998 will be represented by 1998221.

This is a very simple format which can be manipulated easily from Java.
*/

import java.util.{Calendar=>JCalendar}
import java.util.Date
import java.text.ParseException
import java.text.SimpleDateFormat

object JulianDateUtils {

  def getDateFromJulian7(julianDate: String) : Date =
      new SimpleDateFormat("yyyyD").parse(julianDate)

  def getJulian7FromDate(date:Date):String = {
    val sb = new StringBuilder()
    val cal  = JCalendar.getInstance()
    cal.setTime(date)

    sb.append(cal.get(JCalendar.YEAR))
      .append(s"%03d${cal.get(JCalendar.DAY_OF_YEAR)}")
      .toString()
  }

  /*
  public static void main(String[] args) throws Exception {
    String test = "1998221";
    Date d = DateUtils.getDateFromJulian7(test);
    System.out.println(d);
    System.out.println(DateUtils.getJulian7FromDate(d));
*/
    /*
     * output :
     *    Sun Aug 09 00:00:00 EDT 1998
     *    1998221
     */

}


