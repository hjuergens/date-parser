package de.juergens.text.complex

import java.time.LocalDate
import java.time.temporal.TemporalAdjuster

import de.juergens.text.{Composition, DateRuleParsers, ParserTest, TermParsers}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSpec, MustMatchers}

import scala.language.implicitConversions

@RunWith(classOf[JUnitRunner])
class DateRuleTermParsers_FunSpec
  extends FunSpec with MustMatchers {

  val test = new ParserTest(new DateRuleParsers with TermParsers)
  import test.TextParser

  implicit def string2LocalDate(str:String) : LocalDate = LocalDate.parse(str)

  describe("The parser cardinal") {
    it("should succeed when parse 'three'") {
      val parseResult = "cardinal" parse "three"
      parseResult mustBe 'successful
    }
  }
  describe("The parser period") {
    it("should succeed when parse 'three months'") {
      val parseResult = "period" parse "three months"
      parseResult mustBe 'successful
    }
  }
  describe("The parser shifter") {
    it("should succeed when parse 'three months after'") {
      val parseResult = "shifter" parse "three months after"
      parseResult mustBe 'successful
    }
  }
  describe("The parser imm") {
    it("should succeed when parse 'imm'") {
      val parseResult = "imm" parse "imm"
      parseResult mustBe 'successful
    }
    describe("when parse 'next imm'") {
      describe("should succeed") {
        val result2 : test.parser.ParseResult[TemporalAdjuster] = "imm" parse "next imm"
        result2 mustBe 'successful

        it("applied on '\"2016-05-15\"") {
          val result = result2.get.adjustInto("2016-05-15")
          val  expected : LocalDate = "2016-06-15"
          result must equal (expected) // can customize equality
          result must === (expected)   // can customize equality and enforce type constraints
          result must be (expected)    // cannot customize equality, so fastest to compile
          result mustEqual expected    // can customize equality, no parentheses required
          result mustBe expected       // cannot customize equality, so fastest to compile, no parentheses required
        }
        it("applied on '\"2016-06-15\"") {
          val result = result2.get.adjustInto("2016-06-15")
          val  expected : LocalDate = "2016-09-21"
          result must equal (expected) // can customize equality
          result must === (expected)   // can customize equality and enforce type constraints
          result must be (expected)    // cannot customize equality, so fastest to compile
          result mustEqual expected    // can customize equality, no parentheses required
          result mustBe expected       // cannot customize equality, so fastest to compile, no parentheses required
        }
      }
    }
  }

  describe("The parsers composition of DateRuleParsers with TermParsers") {
    val test = new ParserTest(new DateRuleParsers with TermParsers with Composition)
    import test.TextParser
    val parseResult: test.parser.ParseResult[TemporalAdjuster] = "compose" parse "three months after next imm"

    it("should succeed when parse \"three months after next imm\"") {
      parseResult mustBe 'successful
    }
    describe("applied on \"three months after next imm\"") {

      {
        val anchorDate : LocalDate = "2016-06-14"
        val expected   : LocalDate = "2016-09-15"
        it(s"with on '$anchorDate' result in '$expected'") {
          val result = parseResult.get.adjustInto(anchorDate)
          result mustEqual expected // can customize equality, no parentheses required
        }
      }

      {
        val anchorDate : LocalDate = "2016-06-15"
        val expected   : LocalDate = "2016-12-21"
        it(s"with on '$anchorDate' result in '$expected'") {
          val result = parseResult.get.adjustInto(anchorDate)
          result mustEqual expected // can customize equality, no parentheses required
        }
      }

      {
        val anchorDate : LocalDate = "2016-06-16"
        val expected   : LocalDate = "2016-12-21"
        it(s"with on '$anchorDate' result in '$expected'") {
          val result = parseResult.get.adjustInto(anchorDate)
          result mustEqual expected // can customize equality, no parentheses required
        }
      }
    }
    }
}
