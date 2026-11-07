package de.juergens.time

import java.time.{DayOfWeek, LocalDate}

import de.juergens.time.predicate.Attribute
import org.junit.Assert._
import org.junit.Test

class AttributeTest {

  @Test
  def test_1: Unit = {
    val attribute = new Attribute(DayOfWeek.SATURDAY)
    assertTrue( attribute.test(LocalDate.parse("2016-07-09")) )
  }
}