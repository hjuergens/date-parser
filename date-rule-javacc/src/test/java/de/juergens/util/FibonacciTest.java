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

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

@Ignore
@RunWith(SuiteWithParameters.class)
public class FibonacciTest {
    private static final Logger logger = Logger.getLogger(FibonacciTest.class.getSimpleName());

    @BeforeClass
    public static void beforeClass() {
        logger.info(String.format("Test %s.beforeClass",FibonacciTest.class.getSimpleName()));
    }

    @Parameterized.Parameters(name = "{0}")//"{index} line:\"{0}\"")
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][]{{0, 0}, {1, 1}, {2, 1}, {3, 2}, {4, 3}, {5, 5}, {6, 8}});
    }

    private String line;

    public FibonacciTest(String line) {
        this.line = line;
    }

    @Test
    public void notNull() {
        assertNotNull(line);
    }
    @Test
    public void notSame() {
        assertNotSame("gnelfluetz", line);
    }
}
