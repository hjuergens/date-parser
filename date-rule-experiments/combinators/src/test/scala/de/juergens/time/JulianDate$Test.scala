/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.time

import java.text.SimpleDateFormat

import org.junit.Test
import org.scalatest.funsuite.AnyFunSuite

/**
 * @author juergens
 */
class JulianDate$Test extends AnyFunSuite {

  test("parse '113029' as '29.01.2013' ") {
    val j = "113029"
    val date = new SimpleDateFormat("Myydd").parse(j)
    val g = new SimpleDateFormat("dd.MM.yyyy").format(date)
    assert(g === "29.01.2013")
  }

  test("parse '1998221' as '09.08.1998' ") {
    assertResult("09.08.1998") {
      val date = JulianDateUtils.getDateFromJulian7("1998221")
      new SimpleDateFormat("dd.MM.yyyy").format(date)
    }
  }

  test("An empty Set should have size 0") (pending)

  @Test def verifyEasy() { // Uses ScalaTest assertions
    intercept[java.text.ParseException] {
      JulianDateUtils.getDateFromJulian7("hümmelpfütz")
    }
  }
}
