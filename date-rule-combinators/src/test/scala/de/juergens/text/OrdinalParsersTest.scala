/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License") you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */

package de.juergens.text

import org.testng.Assert.assertTrue
import org.testng.Reporter
import org.testng.annotations.{BeforeMethod, Test}

class OrdinalParsersTest extends DateRuleParsers {

    @BeforeMethod(alwaysRun=true)
    def setup() {
        assertTrue(ordinal != null)
    }

    @Test
    def testTextual()  {
        Reporter.log("testTextual")

        assertTrue(parseAll(ordinal, "first").successful)
        assertTrue(parseAll(ordinal, "first").get.toInt == 1)
        assertTrue(parseAll(ordinal, "eleventh").successful)
        assertTrue(parseAll(ordinal, "eleventh").get.toInt == 11)
    }

    @Test
    def testNumerical() {
        assertTrue(parseAll(ordinal, "3.").successful)
        assertTrue(parseAll(ordinal, "700.").successful)
        assertTrue(!parseAll(ordinal, "70.0").successful)
    }

    @Test
    def testOrdinalNumbers()   {
        assertTrue(parseAll(ordinal, "1st").successful)
        assertTrue(parseAll(ordinal, "1st").get.toInt == 1)
        assertTrue(parseAll(ordinal, "2nd").successful)
        assertTrue(parseAll(ordinal, "2nd").get.toInt == 2)
        assertTrue(parseAll(ordinal, "3rd").successful)
        assertTrue(parseAll(ordinal, "3rd").get.toInt == 3)
        assertTrue(parseAll(ordinal, "4th").successful)
        assertTrue(parseAll(ordinal, "4th").get.toInt == 4)
    }
}