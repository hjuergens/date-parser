package de.juergens.time

import java.time.{LocalDateTime, LocalDate}

/**
 * Equinoxes and Solstices
 * algorithm from Meeus
 * adapted by Juergen Giesen
 * 6.5.2012
 */

/*
object Season {
  val K=Math.PI/180.0
  var string1="ex1"

  def computeSeasons(year:Integer) = {

      y = (year - 2000.0)/1000.0
    val Year = year
    println(" " + spring(Year) + " UT")
    println(" " + summer(Year) + " UT")
    println(" " + autumn(Year) + " UT")
    println(" " + winter(Year) + " UT")
  }


  private def S(T : Double) : Double = {

    var x : Double
    x = 	485*Math.cos(K*(324.96 + 1934.136*T))
    x = x + 203*Math.cos(K*(337.23 + 32964.467*T))
    x = x + 199*Math.cos(K*(342.08 + 20.186*T))
    x = x + 182*Math.cos(K*(27.85  + 445267.112*T))
    x = x + 156*Math.cos(K*(73.14  + 45036.886*T))
    x = x + 136*Math.cos(K*(171.52 + 22518.443*T))
    x = x + 77*Math.cos(K*(222.54  + 65928.934*T))
    x = x + 74*Math.cos(K*(296.72  + 3034.906*T))
    x = x + 70*Math.cos(K*(243.58  + 9037.513*T))
    x = x + 58*Math.cos(K*(119.81  + 33718.147*T))
    x = x + 52*Math.cos(K*(297.17  + 150.678*T))
    x = x + 50*Math.cos(K*(21.02   + 2281.226*T))

    x = x + 45*Math.cos(K*(247.54  + 29929.562*T))
    x = x + 44*Math.cos(K*(325.15  + 31555.956*T))
    x = x + 29*Math.cos(K*(60.93   + 4443.417*T))
    x = x + 18*Math.cos(K*(155.12   + 67555.328*T))

    x = x + 17*Math.cos(K*(288.79  + 4562.452*T))
    x = x + 16*Math.cos(K*(198.04  + 62894.029*T))
    x = x + 14*Math.cos(K*(199.76  + 31436.921*T))
    x = x + 12*Math.cos(K*(95.39   + 14577.848*T))
    x = x + 12*Math.cos(K*(287.11  + 31931.756*T))
    x = x + 12*Math.cos(K*(320.81  + 34777.259*T))
    x = x + 9*Math.cos(K*(227.73   + 1222.114*T))
    x = x + 8*Math.cos(K*(15.45    + 16859.074*T))

    x
  }

  def march(Year : Int) : Double = {
    //var JDE0 = 2451623.80984 + (365242.37404 + (0.05169  - (0.00411 - 0.00057*y)*y)*y)*y
    val JDE0 = 2451623.80984 + 365242.37404*y + 0.05169*y*y  - 0.00411*y*y*y - 0.00057*y*y*y*y


    val T = (JDE0 - 2451545.0)/36525.0
    var W = 35999.373*T - 2.47
    W = K*W
    val dL = 1.0 + 0.0334*Math.cos(W) + 0.0007*Math.cos(2*W)


    JDE0 + (0.00001*S(T))/dL - (66.0 + (Year-2000)*1.0)/86400.0
  }

  def june() : Double = {
    var W, dL
    //var JDE0 = 2451716.56767 + (365241.62603 + (0.00325  - (0.00888 - 0.00030*y)*y)*y)*y
    var JDE0 = 2451716.56767 + 365241.62603*y + 0.00325*y*y  + 0.00888*y*y*y - 0.00030*y*y*y*y

    T = (JDE0 - 2451545.0)/36525.0
    W = 35999.373*T - 2.47
    W = K*W
    dL = 1.0 + 0.0334*Math.cos(W) + 0.0007*Math.cos(2*W)
    JDE0 + (0.00001*S())/dL - (66.0 + (Year-2000)*1.0)/86400.0
  }

  def september() : Double = {
    //var JDE0 = 2451810.21715 + (365242.01767 + (0.11575  - (0.00337 - 0.00078*y)*y)*y)*y
    val JDE0 = 2451810.21715 + 365242.01767*y - 0.11575*y*y  + 0.00337*y*y*y + 0.00078*y*y*y*y

    T = (JDE0 - 2451545.0)/36525.0
    var W = 35999.373*T - 2.47
    W = K*W
    val dL = 1.0 + 0.0334*Math.cos(W) + 0.0007*Math.cos(2*W)
    JDE0 + (0.00001*S(T))/dL - (66.0 + (Year-2000)*1.0)/86400.0
  }

  def december() : Double = {
    var W, dL
    //var JDE0 = 2451900.05952 + (365242.74049 + (0.06223  - (0.00823 - 0.00032*y)*y)*y)*y
    var JDE0 = 2451900.05952 + 365242.74049*y - 0.06223*y*y  - 0.00823*y*y*y + 0.00032*y*y*y*y

    T = (JDE0 - 2451545.0)/36525.0
    W = 35999.373*T - 2.47
    W = K*W
    dL = 1.0 + 0.0334*Math.cos(W) + 0.0007*Math.cos(2*W)
    JDE0 + (0.00001*S())/dL - (66.0 + (Year-2000)*1.0)/86400.0
  }

  def caldat (JD:Double) = {

    val date = LocalDateTime.now().`with`(
    java.time.temporal.JulianFields.MODIFIED_JULIAN_DAY,
      Math.floor(JD + 0.5).toLong)
    date.
    val  JD0 = Math.floor(JD + 0.5)
    val B = Math.floor((JD0-1867216.25)/36524.25)
    val C = JD0 + B - Math.floor(B/4) + 1525.0
    val D = Math.floor((C-122.1)/365.25)
    val E = 365.0*D + Math.floor(D/4)
    val F = Math.floor((C-E)/30.6001)
    val day = Math.floor(C-E+0.5) - Math.floor(30.6001*F)

    //if ((url.lastIndexOf(String.fromCharCode(106,103,105,101,115))!=-1)  ||(url.lastIndexOf(string1)!=-1))
    //	dayStr = "" + day else dayStr=ok
    var dayStr = "" + day
    if (day<10) dayStr = " " + dayStr
    val month = F - 1 - 12*Math.floor(F/14)
    val year = D - 4715 - Math.floor((7+month)/10)
    var hour = 24.0*(JD + 0.5 - JD0)
    val diff = Math.abs(hour) - Math.floor(Math.abs(hour))
    var min = Math.floor(Math.round(diff*60.0))
    if (min==60) {min=0; hour=hour+1.0}
    val str = if (min>9) ":" else ":0"
    var hourStr = Math.floor(hour) + str + min
    if (Math.floor(hour)<10) hourStr = " " + hourStr

    monthStr = monthString(month)
    dayNameStr = dayString(JD)
    java.time.Month.of(month)
  }

  def monthString(m:Int) = {
    var monthArray = new Array(12)
    monthArray[0] = "Jan" monthArray[1] = "Feb"
    monthArray[2] = "Mar" monthArray[3] = "Apr"
    monthArray[4] = "May" monthArray[5] = "Jun"
    monthArray[6] = "Jul" monthArray[7] = "Aug"
    monthArray[8] = "Sep" monthArray[9] = "Oct"
    monthArray[10] = "Nov" monthArray[11] = "Dec"
    if (m<1) return "month = " + m
    else return monthArray[m-1]
  }

  def dayString(jd:Double) = {
    var num
    var dayArray = new Array(8)
    dayArray[1] = "Mon" dayArray[2] = "Tue"
    dayArray[3] = "Wed" dayArray[4] = "Thu"
    dayArray[5] = "Fri" dayArray[6] = "Sat"
    dayArray[7] = "Sun"

    num = Math.floor(jd+0.5)
    num = num - Math.floor(num/7)*7 + 1

    return dayArray[Math.floor(num)]
  }

  def spring(year:Integer) = {
    caldat(march(year))
    nSpring+=1
    if (dayStr==19) n19+=1
    if (dayStr==20) n20+=1
    if (dayStr==21) n21+=1
    if (dayStr==22) n22+=1
    return dayNameStr + ", " + monthStr + " " + dayStr + " at " + hourStr
  }

  def summer() = {
    caldat(june())
    return dayNameStr + ", " + monthStr + " " + dayStr + " at " + hourStr
  }

  def autumn() = {
    caldat(september())
    return dayNameStr + ", " + monthStr + " " + dayStr + " at " + hourStr
  }

  def winter() = {
    caldat(december())
    return dayNameStr + ", " + monthStr + " " + dayStr + " at " + hourStr
  }
}
*/