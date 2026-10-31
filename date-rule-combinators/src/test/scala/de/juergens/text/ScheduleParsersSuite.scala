package de.juergens.text

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.LocalDate

import de.juergens.text.ScheduleParsers.{Context, Market}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Runs the shared cases of examples/schedule.txt, examples/catalog.txt and examples/date-holidays.txt (project root).
 */
class ScheduleParsersSuite extends AnyFunSuite {

  private val Prefix = """\[(\S+)(?: (\S+))?] (.*)""".r   // [base] or [base market]

  private val cases: Seq[(String, Context, Option[List[LocalDate]])] = {
    val markets = mutable.Map.empty[String, Market]
    val events = mutable.Map.empty[String, Context => List[LocalDate]]
    Seq("schedule.txt", "catalog.txt", "date-holidays.txt").flatMap(file => Files.readAllLines(Paths.get("..", "examples", file), StandardCharsets.UTF_8).asScala)
      .filterNot(line => line.trim.isEmpty || line.trim.startsWith("#"))
      .flatMap { line =>
        if (line.startsWith("market ")) {   // market NAME = schedule of the holidays
          val Array(name, holidays) = line.stripPrefix("market ").split("=", 2)
          markets(name.trim) = Market.withHolidayRules(ScheduleParsers.parse(holidays.trim))
          None
        } else if (line.startsWith("event ")) {   // event NAME = schedule of its dates
          val Array(name, dates) = line.stripPrefix("event ").split("=", 2)
          events(name.trim) = ScheduleParsers.parse(dates.trim)
          None
        } else {
          val arrow = line.lastIndexOf("=>")
          val expected = line.substring(arrow + 2).trim match {
            case "error" => None
            case "none" => Some(Nil)
            case dates => Some(dates.split(",").map(d => LocalDate.parse(d.trim)).toList)
          }
          line.substring(0, arrow).trim match {
            case Prefix(base, market, input) =>
              Some((input, Context(Some(LocalDate.parse(base)), Option(market).map(markets).getOrElse(Market.Weekends),
                events.toMap), expected))
            case input => Some((input, Context(events = events.toMap), expected))
          }
        }
      }
  }

  for (((input, context, expected), index) <- cases.zipWithIndex) {
    test(s"$index: '$input' => ${expected.fold("error")(_.mkString(", "))}") {
      expected match {
        case Some(dates) => assert(ScheduleParsers.parse(input)(context) === dates)
        case None => assertThrows[RuntimeException](ScheduleParsers.parse(input)(context))
      }
    }
  }
}
