package io.github.hjuergens.util

import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck.Properties
import org.scalatest.prop.Checkers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test

/**
  * ScalaCheck for TestNG via scalatest
  */
class RingCheck extends TestNGSuite with Checkers {
  @Test
  def testConcat() {
    check((a: List[Int], b: List[Int]) => a.size + b.size == (a ::: b).size)
  }

  val propReverseList = forAll { l: List[String] => l.reverse.reverse == l }

  val propConcatString = forAll { (s1: String, s2: String) =>
    (s1 + s2).endsWith(s2)
  }

  @Test
  def testPropReverseList() { check(propReverseList) }

}

object RingCheck extends Properties("String") {

  property("startsWith") = forAll { (a: String, b: String) =>
    (a+b).startsWith(a)
  }

  property("concatenate") = forAll { (a: String, b: String) =>
    (a+b).length > a.length && (a+b).length > b.length
  }

  property("substring") = forAll { (a: String, b: String, c: String) =>
    (a+b+c).substring(a.length, a.length+b.length) == b
  }

}

