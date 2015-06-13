/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package samples

import org.junit.Assert._
import org.junit._
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class LoopParser_Test {

  @Before
  def setUp: Unit = {
  }

  @After
  def tearDown: Unit = {
  }

  @Test
  def statement = {
    val parser = new LoopParser()
    val result = parser.parseAll(parser.statement, "for i in 0 to 6 {}")
    println(result)
    assertTrue(true)
  }


}
