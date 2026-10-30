package de.juergens.text

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.LocalDate

import de.juergens.text.ScheduleParsers.{Context, Market}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Runs the shared cases of examples/schedule.txt (project root).
 */
class ScheduleParsersSuite extends AnyFunSuite {

  private val Prefix = """\[(\S+)(?: (\S+))?] (.*)""".r   // [base] or [base market]

  private val cases: Seq[(String, Context, Option[List[LocalDate]])] = {
    val markets = mutable.Map.empty[String, Market]
    Files.readAllLines(Paths.get("..", "examples", "schedule.txt"), StandardCharsets.UTF_8).asScala.toSeq
      .filterNot(line => line.trim.isEmpty || line.trim.startsWith("#"))
      .flatMap { line =>
        if (line.startsWith("market ")) {   // market NAME = schedule of the holidays
          val Array(name, holidays) = line.stripPrefix("market ").split("=", 2)
          markets(name.trim) = Market.withHolidayRules(ScheduleParsers.parse(holidays.trim))
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
              Some((input, Context(Some(LocalDate.parse(base)), Option(market).map(markets).getOrElse(Market.Weekends)), expected))
            case input => Some((input, Context(), expected))
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
