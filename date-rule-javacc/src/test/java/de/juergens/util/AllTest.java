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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;

@Ignore
@RunWith(value = AllTests.class)
public class AllTest {
    public static Test suite() {
        String folder = AllTest.class.getResource("/").getFile();

        TestSuite suite = new TestSuite(
                "Test for net.jorgemanrubia.junitdinamically.sample");
        //$JUnit-BEGIN$
        suite.addTest(new TextFileTestSuite(new File(folder)));
        //$JUnit-END$
        return suite;
    }
}