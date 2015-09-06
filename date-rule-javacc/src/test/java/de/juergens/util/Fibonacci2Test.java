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

package de.juergens.util;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Ignore
@RunWith(SuiteWithParameters.class)
@SuiteWithParameters.SuiteDirectories({"/txt", "/pdf"})
public class Fibonacci2Test {
    @Parameterized.Parameters(name = "{index} %1 data()")
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][]{{0, 0}, {1, 1}, {2, 1}, {3, 2}, {4, 3}, {5, 5}, {6, 8}});
    }

    private int fInput;

    private int fExpected;

    public Fibonacci2Test(int input, int expected) {
        fInput= input;
        fExpected= expected;
    }

//    @Test
//    public void test() {
//        assertEquals(fExpected, fInput);
//    }
//    @Test
//    public void no() {
//
//    }

    @RunWith(Parameterized.class)
    static public class FibonacciInner2Test {
        @Parameterized.Parameters(name = "{index} %1 data()")
        public static List<Object[]> data() {
            return Arrays.asList(new Object[][]{{0, 0}, {1, 1}, {2, 1}, {3, 2}, {4, 3}, {5, 5}, {6, 8}});
        }

        private int fInput;

        private int fExpected;

        public FibonacciInner2Test(int input, int expected) {
            fInput= input;
            fExpected= expected;
        }

        @Test
        public void test() {
            assertEquals(fExpected, fInput);
        }
        @Test
        public void no() {

        }
    }
}
