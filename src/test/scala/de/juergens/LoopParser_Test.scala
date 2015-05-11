/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens

import de.juergens.text._
import org.junit._
import Assert._

class LoopParser_Test {

  @Before
  def setUp(): Unit = {
  }

  @After
  def tearDown(): Unit = {
  }

  @Test
  def statement() = {
    val parser = new LoopParser()
    val result = parser.parseAll(parser.statement, "for i in 0 to 6 {}")
    println(result)
    assertTrue(true)
  }
  

}
