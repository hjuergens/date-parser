package de.juergens.text.rules

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.LocalDate

import org.scalatest.funsuite.AnyFunSuite

import scala.jdk.CollectionConverters._

/**
 * Runs the shared cases of examples/rules.txt (project root): blocks of declarations, each followed by its cases;
 * declarations after cases start a new block.
 */
class RuleCasesSuite extends AnyFunSuite {

  private val Schedule = """\[(\d{4}-\d{2}-\d{2})] (.*)""".r   // [base] schedule
  private val ScopeYear = """(\S+) (\d{4})""".r                 // scope year

  /** (case text, expected, declarations) in the order of the file */
  private val cases: Seq[(String, String, String)] = {
    val out = Seq.newBuilder[(String, String, String)]
    var declarations = Vector.empty[String]
    var afterCases = false
    for (line <- Files.readAllLines(Paths.get("..", "examples", "rules.txt"), StandardCharsets.UTF_8).asScala
         if !line.isBlank && !line.startsWith("#")) {
      val arrow = line.lastIndexOf("=>")
      if (arrow >= 0) {
        out += ((line.substring(0, arrow).trim, line.substring(arrow + 2).trim, declarations.mkString("\n")))
        afterCases = true
      } else {
        if (afterCases) {
          declarations = Vector.empty
          afterCases = false
        }
        declarations :+= line
      }
    }
    out.result()
  }

  private def evaluate(declarations: String, text: String): Seq[String] = {
    val catalog = RuleFiles.parseRules(declarations)
    text match {
      case Schedule(base, schedule) =>
        catalog.schedule(schedule).evaluate(catalog.context(LocalDate.parse(base), null)).asScala.map(_.toString).toSeq
      case ScopeYear(scope, year) =>
        catalog.holidays(scope, year.toInt).asScala.map(o => o.date.toString + Option(o.scenario).fold("")(" " + _)).toSeq
    }
  }

  private def result(declarations: String, text: String): String =
    try {
      val dates = evaluate(declarations, text).mkString(", ")
      if (dates.isEmpty) "none" else dates
    } catch {
      case _: Exception => "error"
    }

  for (((text, expected, declarations), index) <- cases.zipWithIndex) {
    test(s"$index: '$text' => $expected") {
      assert(result(declarations, text) === expected)
    }
  }
}
