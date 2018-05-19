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

package io.github.hjuergens.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

@RunWith(Parameterized.class)
public class Simple1Test {
    private static final Logger logger = Logger.getLogger(Simple1Test.class.getSimpleName());

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][]{{"{}"}, {"{c"}, {"c}"},});
    }

    final private String line;

    public Simple1Test(String line) {
        this.line = line;

    }

    @Test
    public void viaInputStream() {
        InputStream inputStream = new ByteArrayInputStream(line.getBytes());
        Simple1 parser = new Simple1(inputStream);
        parser.ReInit(inputStream);
        assertNotNull(parser.jj_input_stream);
        try {
            parser.Input();
        }
        catch (ParseException e) {
            logger.log(Level.WARNING, "", e);
        }
        catch (TokenMgrException e) {
            logger.log(Level.INFO, "", e);
        }
    }
    @Test //(expected = IllegalStateException.class)
    public void viaReader() {
        StringReader sr = new StringReader(line);
        Simple1 parser = new Simple1(sr);
        parser.ReInit(sr);
        assertNotNull(parser.jj_input_stream);
        try {
            parser.Input();
        }
        catch (ParseException e) {
            logger.log(Level.WARNING, "", e);
        }
        catch (TokenMgrException e) {
            logger.log(Level.INFO, "", e);
        }
    }
}
