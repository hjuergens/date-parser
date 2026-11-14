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
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import java.util.Arrays;

@Ignore
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CustomParameterizedTest.RunnerFactory.class)
public class CustomParameterizedTest {

    @Parameterized.Parameters
    public static Iterable<Integer> data() {
        return Arrays.asList(new Integer[]{1, 2, 3});
    }

    private int i;

    public CustomParameterizedTest(int i) {
        this.i = i;
    }

    @Test
    public void test() {
        System.out.println(i);
    }

    public class RunnerFactory implements ParametersRunnerFactory {
        @Override
        public org.junit.runner.Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            return new A(test);
        }
    }

    public static class A extends BlockJUnit4ClassRunnerWithParameters {
        private final Object[] parameters;

        public A(TestWithParameters test) throws InitializationError {
            super(test);
            parameters = test.getParameters().toArray(new Object[test.getParameters().size()]);
        }

        @Override
        public Object createTest() throws Exception {
            return getTestClass().getOnlyConstructor().newInstance(parameters);
        }
    }
}