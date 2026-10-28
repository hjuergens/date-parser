package de.juergens.text

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.LocalDate

import org.scalatest.funsuite.AnyFunSuite

import scala.jdk.CollectionConverters._

/**
 * Runs the shared cases of examples/schedule.txt (project root).
 */
class ScheduleParsersSuite extends AnyFunSuite {

  private val cases: Seq[(String, Option[List[LocalDate]])] =
    Files.readAllLines(Paths.get("..", "examples", "schedule.txt"), StandardCharsets.UTF_8).asScala.toSeq
      .filterNot(line => line.trim.isEmpty || line.trim.startsWith("#"))
      .map { line =>
        val arrow = line.lastIndexOf("=>")
        val expected = line.substring(arrow + 2).trim match {
          case "error" => None
          case "none" => Some(Nil)
          case dates => Some(dates.split(",").map(d => LocalDate.parse(d.trim)).toList)
        }
        (line.substring(0, arrow).trim, expected)
      }

  for (((input, expected), index) <- cases.zipWithIndex) {
    test(s"$index: '$input' => ${expected.fold("error")(_.mkString(", "))}") {
      expected match {
        case Some(dates) => assert(ScheduleParsers.parse(input) === dates)
        case None => assertThrows[RuntimeException](ScheduleParsers.parse(input))
      }
    }
  }
}
