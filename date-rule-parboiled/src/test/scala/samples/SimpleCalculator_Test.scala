/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package samples

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@RunWith(classOf[JUnit4])
class SimpleCalculator_Test {

  @Test
  @throws[Exception]
  def calculate(): Unit = {
    val input = "3*(7+2)"
    val parsingResult = new SimpleCalculator().calculate(input)
    assertThat(parsingResult, equalTo(27))
  }

}
