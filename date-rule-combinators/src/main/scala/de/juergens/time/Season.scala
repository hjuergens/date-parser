package de.juergens.time

import java.time.{Instant, LocalDate, ZoneId, ZoneOffset}

/**
 * Equinoxes and solstices after Jean Meeus, Astronomical Algorithms (2nd edition, 1998), chapter 27:
 * the mean instant of table 27.B, corrected by the periodic terms of table 27.C, is in dynamical time (TD);
 * ΔT after Espenak and Meeus (NASA, 2006) turns it into UT. The result is within about a minute of the
 * published instants, so the date can only be off when the event is that close to midnight in the zone.
 */
object Season {

  sealed abstract class Event(val month: Int, private[Season] val mean: Seq[Double])
  case object MarchEquinox extends Event(3, Seq(2451623.80984, 365242.37404, 0.05169, -0.00411, -0.00057))
  case object JuneSolstice extends Event(6, Seq(2451716.56767, 365241.62603, 0.00325, 0.00888, -0.00030))
  case object SeptemberEquinox extends Event(9, Seq(2451810.21715, 365242.01767, -0.11575, 0.00337, 0.00078))
  case object DecemberSolstice extends Event(12, Seq(2451900.05952, 365242.74049, -0.06223, -0.00823, 0.00032))

  // table 27.C: amplitude, phase and speed (degrees) of the periodic terms
  private val terms = Seq(
    (485, 324.96, 1934.136), (203, 337.23, 32964.467), (199, 342.08, 20.186), (182, 27.85, 445267.112),
    (156, 73.14, 45036.886), (136, 171.52, 22518.443), (77, 222.54, 65928.934), (74, 296.72, 3034.906),
    (70, 243.58, 9037.513), (58, 119.81, 33718.147), (52, 297.17, 150.678), (50, 21.02, 2281.226),
    (45, 247.54, 29929.562), (44, 325.15, 31555.956), (29, 60.93, 4443.417), (18, 155.12, 67555.328),
    (17, 288.79, 4562.452), (16, 198.04, 62894.029), (14, 199.76, 31436.921), (12, 95.39, 14577.848),
    (12, 287.11, 31931.756), (12, 320.81, 34777.259), (9, 227.73, 1222.114), (8, 15.45, 16859.074))

  private def cos(degrees: Double) = Math.cos(Math.toRadians(degrees))

  /** the Julian Ephemeris Day of the event (TD); table 27.B covers the years 1000 to 3000 */
  def julianEphemerisDay(event: Event, year: Int): Double = {
    require(year >= 1000 && year <= 3000, s"year $year outside 1000 to 3000")
    val y = (year - 2000) / 1000.0
    val jde0 = event.mean.zipWithIndex.map { case (c, i) => c * Math.pow(y, i) }.sum
    val t = (jde0 - 2451545.0) / 36525
    val w = 35999.373 * t - 2.47
    val dLambda = 1 + 0.0334 * cos(w) + 0.0007 * cos(2 * w)
    val s = terms.map { case (a, b, c) => a * cos(b + c * t) }.sum
    jde0 + 0.00001 * s / dLambda
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

  def instant(event: Event, year: Int): Instant = {
    val ut = julianEphemerisDay(event, year) - deltaT(year + (event.month - 0.5) / 12) / 86400
    Instant.ofEpochMilli(Math.round((ut - 2440587.5) * 86400000))
  }

  /** the date of the event in a time zone, e.g. Asia/Tokyo for Japan's equinox days */
  def date(event: Event, year: Int, zone: ZoneId = ZoneOffset.UTC): LocalDate =
    LocalDate.ofInstant(instant(event, year), zone)
}
