/*
 * Copyright 2001-2009 Artima, Inc.
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
package samples

import org.junit.runner.RunWith
import org.parboiled.errors.ParsingException
import org.scalatest.MustMatchers
import org.scalatest.junit.JUnitRunner

/*
Here's an example of a FunSuite with ShouldMatchers mixed in:
*/

import org.scalatest._

@RunWith(classOf[JUnitRunner])
class MapSpec extends FunSpec with MustMatchers {

  describe("A SimpleCalculator") {
    val parser = new SimpleCalculator()
    it("should return 27 when it calculates \"3*(7+2)\"") {
      val input = "3*(7+2)"
      parser.calculate(input) must be(27)
    }
  }
}

import org.scalatest.WordSpec

@RunWith(classOf[JUnitRunner])
class SetSpec extends WordSpec {

  val parser = new SimpleCalculator()
  "A SimpleCalculator" when {
    val input = "3*(7+2)"
    "calculates \"3*(7+2)\"\"" should {
      "should return 27" in {
        assert(parser.calculate(input) === 27)
      }
    "parsing \"uj5\"" should {
      "throw org.parboiled.errors.ParsingException" in {
        assertThrows[ParsingException] {
          parser.calculate("uj5")
        }
      }
    }
    }
  }
}