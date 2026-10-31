package de.juergens.util

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(value = classOf[JUnit4])
class SignTest {

  @Test
  def applyBySymbolAndWord(): Unit = {
    assertSame(Plus, Sign("+"))
    assertSame(Plus, Sign("plus"))
    assertSame(Minus, Sign("-"))
    assertSame(Minus, Sign("minus"))
  }

  @Test
  def multiply(): Unit = {
    assertEquals(3, Plus * 3)
    assertEquals(-3, Minus * 3)
    assertEquals(3, Minus * -3)
  }

  @Test
  def toStringIsSymbol(): Unit = {
    assertEquals("+", Plus.toString)
    assertEquals("-", Minus.toString)
  }

  @Test(expected = classOf[MatchError])
  def unknownSign(): Unit = Sign("times")
}
