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

import junit.framework.TestSuite;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import java.io.File;

//@RunWith(org.junit.runners.Suite.class)
//@org.junit.runners.Suite.SuiteClasses(value = {TextFileTestCase.class})
@Ignore
public class TextFileTestSuite extends TestSuite {
    public TextFileTestSuite(File directory) {
        super(directory.getName());
        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                addTest(new TextFileTestSuite(file));
            else
                addTest(new TextFileTestCase(file));
        }
    }
}