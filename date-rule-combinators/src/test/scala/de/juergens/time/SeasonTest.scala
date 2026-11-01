package de.juergens.time

import java.time.{Duration, Instant, LocalDate, ZoneId}

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class SeasonTest {
  import Season._

  // the published instants (UTC, to the minute)
  private val published = Seq(
    (MarchEquinox, 2024, "2024-03-20T03:06:00Z"), (JuneSolstice, 2024, "2024-06-20T20:51:00Z"),
    (SeptemberEquinox, 2024, "2024-09-22T12:44:00Z"), (DecemberSolstice, 2024, "2024-12-21T09:20:00Z"),
    (MarchEquinox, 2025, "2025-03-20T09:01:00Z"), (JuneSolstice, 2025, "2025-06-21T02:42:00Z"),
    (SeptemberEquinox, 2025, "2025-09-22T18:19:00Z"), (DecemberSolstice, 2025, "2025-12-21T15:03:00Z"),
    (MarchEquinox, 2026, "2026-03-20T14:46:00Z"), (JuneSolstice, 2026, "2026-06-21T08:24:00Z"),
    (SeptemberEquinox, 2026, "2026-09-23T00:05:00Z"), (DecemberSolstice, 2026, "2026-12-21T20:50:00Z"))

  @Test
  def withinAMinute(): Unit =
    for ((event, year, expected) <- published) {
      val off = Duration.between(Instant.parse(expected), instant(event, year)).abs
      assertTrue(s"$event $year off by $off", off.getSeconds <= 60)
    }

  // Japan's Vernal and Autumnal Equinox Days are the dates of the equinoxes in Tokyo
  @Test
  def japaneseEquinoxDays(): Unit = {
    val tokyo = ZoneId.of("Asia/Tokyo")
    val days = Seq(
      (2020, "2020-03-20", "2020-09-22"), (2021, "2021-03-20", "2021-09-23"), (2022, "2022-03-21", "2022-09-23"),
      (2023, "2023-03-21", "2023-09-23"), (2024, "2024-03-20", "2024-09-22"), (2025, "2025-03-20", "2025-09-23"))
    for ((year, vernal, autumnal) <- days) {
      assertEquals(LocalDate.parse(vernal), date(MarchEquinox, year, tokyo))
      assertEquals(LocalDate.parse(autumnal), date(SeptemberEquinox, year, tokyo))
    }
  }

  @Test
  def utcDateByDefault(): Unit = {
    // 2026-09-23 00:05 UTC is still the 22nd in New York
    assertEquals(LocalDate.of(2026, 9, 23), date(SeptemberEquinox, 2026))
    assertEquals(LocalDate.of(2026, 9, 22), date(SeptemberEquinox, 2026, ZoneId.of("America/New_York")))
  }

  @Test(expected = classOf[IllegalArgumentException])
  def outsideTheTable(): Unit = instant(MarchEquinox, 999)
}
