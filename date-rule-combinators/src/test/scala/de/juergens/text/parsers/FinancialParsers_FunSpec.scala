package de.juergens.text.parsers

import de.juergens.text.{DateRuleParsers, FinancialParsers, ParserTest}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSpec, MustMatchers}

@RunWith(classOf[JUnitRunner])
class FinancialParsers_FunSpec
  extends FunSpec with MustMatchers {

  val test = new ParserTest(new DateRuleParsers with FinancialParsers)
  import test.TextParser

  describe("The parser businessDays") {
      it("should succeed when parse 'business days'") {
        val parseResult = "businessDays" parse "business days"
        parseResult mustBe 'successful
      }
    it("should succeed when parse 'London business days'") {
        val parseResult = "businessDays" parse "London business days"
        parseResult mustBe 'successful
      }

      it("should produce no result when parsing failed on 'New York business day'") {
        intercept[java.lang.RuntimeException] {
          ("businessDays" parse "London business day").get
        }
      }
  }
  describe("The parser seek3") {
    describe("when parse 'two business days prior'") {
      it("should succeed") {
        val parseResult = "seek3" parse "two business days prior"
        parseResult mustBe 'successful
      }
    }
  }
  describe("The parser afterOrBefore") {
    describe("when parse 'prior'") {
      it("should succeed") {
        val parseResult = "afterOrBefore" parse "prior"
        parseResult mustBe 'successful
      }
    }
  }
  describe("The parser seekDayOfWeek") {
    describe("when parse '3rd Wednesday'") {
      it("should succeed") {
        val parseResult = "seekDayOfWeek" parse "3rd Wednesday"
        parseResult mustBe 'successful
      }
    }
  }

}
