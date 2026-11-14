package de.juergens.time

import java.time.{Instant, LocalDate, ZoneId, ZoneOffset}

/**
 * Equinoxes, solstices and the other solar terms after Jean Meeus, Astronomical Algorithms (2nd edition, 1998): the
 * instant at which the apparent longitude of the sun (chapter 25, with the VSOP87 terms of appendix III) reaches a
 * multiple of 15 degrees, found as in chapter 27; ΔT after Espenak and Meeus (NASA, 2006) turns it into UT. The result
 * is within about a minute of the published instants, so a date can only be off when the event is that close to midnight.
 * The same algorithm is in ScheduleSemantics.java and semantics.py of the other modules (EQUINOX MAR, SOLARTERM 5).
 */
object Season {

  sealed abstract class Event(val month: Int, val degrees: Int)
  case object MarchEquinox extends Event(3, 0)
  case object JuneSolstice extends Event(6, 90)
  case object SeptemberEquinox extends Event(9, 180)
  case object DecemberSolstice extends Event(12, 270)

  // the terms of VSOP87D (appendix III) with A >= 100: A (1e-8 rad or AU), B, C
  private val earthL: Seq[Seq[(Double, Double, Double)]] = Seq(
    Seq((175347046.0, 0.0, 0.0), (3341656, 4.6692568, 6283.07585), (34894, 4.6261024, 12566.1517),
      (3418, 2.8288658, 3.5231183), (3497, 2.744118, 5753.3848849), (3136, 3.6276704, 77713.7714681),
      (2676, 4.4180835, 7860.4193924), (2343, 6.1351624, 3930.2096962), (1273, 2.0370966, 529.6909651),
      (1324, 0.7424636, 11506.7697698), (902, 2.0450544, 26.2983198), (1199, 1.1096294, 1577.3435424),
      (857, 3.5084916, 398.1490034), (780, 1.1788265, 5223.6939198), (990, 5.2326813, 5884.9268466),
      (753, 2.5333905, 5507.5532387), (505, 4.5829256, 18849.22755), (492, 4.2050664, 775.5226113),
      (357, 2.9195412, 0.0673103), (284, 1.8986903, 796.2980068), (243, 0.3448114, 5486.7778432),
      (317, 5.8490195, 11790.6290887), (271, 0.3148861, 10977.0788047), (206, 4.8064661, 2544.3144199),
      (205, 1.8694781, 5573.1428014), (202, 2.457678, 6069.7767546), (126, 1.0830263, 20.7753955),
      (156, 0.8330607, 213.2990954), (115, 0.6454491, 0.9803211), (103, 0.6359985, 4694.0029547),
      (102, 4.2667982, 7.113547), (132, 3.4111828, 2942.4634233), (102, 0.9756922, 15720.8387849)),
    Seq((628331966747.0, 0.0, 0.0), (206059, 2.6782346, 6283.07585), (4303, 2.6351265, 12566.1517),
      (425, 1.5904698, 3.5231183), (109, 2.96618, 1577.3435424), (119, 5.7955749, 26.2983198)),
    Seq((52919.0, 0.0, 0.0), (8720, 1.0720967, 6283.07585), (309, 0.8672882, 12566.1517)),
    Seq((289.0, 5.843842, 6283.07585)),
    Seq((114.0, 3.1415927, 0.0)))
  private val earthR: Seq[Seq[(Double, Double, Double)]] = Seq(
    Seq((100013989.0, 0.0, 0.0), (1670700, 3.0984635, 6283.07585), (13956, 3.0552461, 12566.1517)),
    Seq((103019.0, 1.1074897, 6283.07585)))

  private def vsop(series: Seq[Seq[(Double, Double, Double)]], tau: Double): Double =
    series.foldRight(0.0)((terms, sum) => sum * tau + terms.map { case (a, b, c) => a * Math.cos(b + c * tau) }.sum) * 1e-8

  /** the apparent longitude of the sun in degrees, with nutation and aberration */
  def sunLongitude(jde: Double): Double = {
    val tau = (jde - 2451545) / 365250
    val t = 10 * tau
    val omega = Math.toRadians(125.04452 - 1934.136261 * t)
    val sun = Math.toRadians(280.4665 + 36000.7698 * t)
    val moon = Math.toRadians(218.3165 + 481267.8813 * t)
    val nutation = -17.20 * Math.sin(omega) - 1.32 * Math.sin(2 * sun) - 0.23 * Math.sin(2 * moon) + 0.21 * Math.sin(2 * omega)
    Math.toDegrees(vsop(earthL, tau)) + 180 + (nutation - 0.09033 - 20.4898 / vsop(earthR, tau)) / 3600
  }

  /** TD - UT in seconds, the polynomials of Espenak and Meeus for 1986 to 2150, their long-term formula outside */
  def deltaT(year: Double): Double = {
    def longTerm = { val u = (year - 1820) / 100; -20 + 32 * u * u }
    val t = year - 2000
    if (year >= 1986 && year < 2005)
      63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * Math.pow(t, 3) + 0.000651814 * Math.pow(t, 4) +
        0.00002373599 * Math.pow(t, 5)
    else if (year >= 2005 && year < 2050) 62.92 + 0.32217 * t + 0.005589 * t * t
    else if (year >= 2050 && year < 2150) longTerm - 0.5628 * (2150 - year)
    else longTerm
  }

  /** the Julian Day (UT) in the year at which the sun's apparent longitude reaches the degrees */
  def julianDay(degrees: Int, year: Int): Double = {
    val day = { val d = 79 + degrees * 365.2422 / 360; if (d > 365.2422) d - 365.2422 else d }  // after 1 January
    var jde = LocalDate.of(year, 1, 1).toEpochDay + 2440587.5 + day
    var step = 1.0
    var i = 0
    while (Math.abs(step) >= 1e-7 && i < 50) {
      step = 58 * Math.sin(Math.toRadians(degrees - sunLongitude(jde)))
      jde += step
      i += 1
    }
    jde - deltaT(year + day / 365.25) / 86400
  }

  /** the date on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes */
  def sunAt(degrees: Int, year: Int, offset: Int = 0): LocalDate =
    LocalDate.ofEpochDay(Math.floor(julianDay(degrees, year) + offset / 1440.0 - 2440587.5).toLong)

  def instant(event: Event, year: Int): Instant =
    Instant.ofEpochMilli(Math.round((julianDay(event.degrees, year) - 2440587.5) * 86400000))

  /** the date of the event in a time zone, e.g. Asia/Tokyo for Japan's equinox days */
  def date(event: Event, year: Int, zone: ZoneId = ZoneOffset.UTC): LocalDate =
    LocalDate.ofInstant(instant(event, year), zone)
}
