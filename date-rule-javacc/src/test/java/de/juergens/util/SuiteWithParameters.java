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
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Parameterized;
import org.junit.runners.ParentRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;

@Ignore
public class SuiteWithParameters extends ParentRunner<Suite> {
    private final Class<?> testClass;
    private final Path rootPath;

    public SuiteWithParameters(Class<?> testClass) throws InitializationError {
        super(testClass); // TODO check this testClass
        this.testClass = testClass;
        try {
            rootPath = Paths.get(testClass.getResource("/").toURI());
        } catch (URISyntaxException e) {
            throw new InitializationError(e);
        }
    }


    @Override
    protected List<Suite> getChildren() {
        List<Suite> list = new LinkedList<Suite>();
        try {
            for(String dirname : getAnnotatedClasses(testClass)) {
                //getTestClass() // TODO check this out
                File directory = new File(rootPath.toFile(), dirname);
                for (File file : directory.listFiles()) {
                    if (file.isFile()) {
                        Path relPath = rootPath.relativize(Paths.get(file.getAbsolutePath()));
                        list.add(new ParameterizedFromFile(FibonacciTest.class, relPath.toFile()));
                    }
                    else
                        list.add(new ParameterizedFromFile(testClass, file));
                }
            }
        } catch (InitializationError initializationError) {
            initializationError.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return list; // Collections.unmodifiableList(list);
    }

    @Override
    protected Description describeChild(Suite parameterized) {
        System.out.println(((ParameterizedFromFile) parameterized).fFile.toString());
        return parameterized.getDescription();
    }

    @Override
    protected void runChild(Suite suite, RunNotifier notifier) {
        suite.run(notifier);
    }


    private static String[] getAnnotatedClasses(Class<?> klass) throws InitializationError {
        SuiteWithParameters.SuiteDirectories annotation = klass.getAnnotation(SuiteWithParameters.SuiteDirectories.class);
        if(annotation == null) {
            throw new InitializationError(String.format("class \'%s\' must have a SuiteDirectories annotation", new Object[]{klass.getName()}));
        } else {
            return annotation.value();
        }
    }

    private static final List<Runner> NO_RUNNERS = Collections.emptyList();
    static class ParameterizedFromFile extends /*Parameterized*/ Suite {
        private final File fFile;
        private static final ParametersRunnerFactory DEFAULT_FACTORY = new BlockJUnit4ClassRunnerWithParametersFactory();
        private static final List<Runner> NO_RUNNERS = Collections.emptyList();
        private final List<Runner> runners;

        public ParameterizedFromFile(Class<?> klass, File file) throws Throwable {
            super(klass, NO_RUNNERS);
            fFile = file;
            ParametersRunnerFactory runnerFactory = this.getParametersRunnerFactory(klass);
            Parameterized.Parameters parameters = (Parameterized.Parameters)this.getParametersMethod().getAnnotation(Parameterized.Parameters.class);
            this.runners = Collections.unmodifiableList(this.createRunnersForParameters(this.allParameters(), parameters.name(), runnerFactory));
        }

        private ParametersRunnerFactory getParametersRunnerFactory(Class<?> klass) throws InstantiationException, IllegalAccessException {
            Parameterized.UseParametersRunnerFactory annotation = (Parameterized.UseParametersRunnerFactory)klass.getAnnotation(Parameterized.UseParametersRunnerFactory.class);
            if(annotation == null) {
                return DEFAULT_FACTORY;
            } else {
                Class factoryClass = annotation.value();
                return (ParametersRunnerFactory)factoryClass.newInstance();
            }
        }

        protected List<Runner> getChildren() {
            return this.runners;
        }

        private TestWithParameters createTestWithNotNormalizedParameters(String pattern, int index, Object parametersOrSingleParameter) {
            Object[] parameters = parametersOrSingleParameter instanceof Object[]?(Object[])((Object[])parametersOrSingleParameter):new Object[]{parametersOrSingleParameter};
            return createTestWithParameters(this.getTestClass(), pattern, index, parameters);
        }

        private Iterable<Object> allParameters() throws Throwable {
//            Object parameters = this.getParametersMethod().invokeExplosively((Object)null, new Object[0]);
//            if(parameters instanceof Iterable) {
//                return (Iterable)parameters;
//            } else if(parameters instanceof Object[]) {
//                return Arrays.asList((Object[]) ((Object[]) parameters));
//            } else {
//                throw this.parametersMethodReturnedWrongType();
//            }

            List<Object> list = new LinkedList<Object>();
            String resourceName = "/" + fFile.toString();
            InputStream stream = getTestClass().getJavaClass().getClass().getResourceAsStream(resourceName);
            Scanner sc = new Scanner(stream);
            while(sc.hasNext()) {
                list.add(sc.nextLine());
            }
            return list;
        }

        private FrameworkMethod getParametersMethod() throws Exception {
            List methods = this.getTestClass().getAnnotatedMethods(Parameterized.Parameters.class);
            Iterator i$ = methods.iterator();

            FrameworkMethod each;
            do {
                if(!i$.hasNext()) {
                    throw new Exception("No public static parameters method on class " + this.getTestClass().getName());
                }

                each = (FrameworkMethod)i$.next();
            } while(!each.isStatic() || !each.isPublic());

            return each;
        }

        private List<Runner> createRunnersForParameters(Iterable<Object> allParameters, String namePattern, ParametersRunnerFactory runnerFactory) throws InitializationError, Exception {
            try {
                List e = this.createTestsForParameters(allParameters, namePattern);
                ArrayList runners = new ArrayList();
                Iterator i$ = e.iterator();

                while(i$.hasNext()) {
                    TestWithParameters test = (TestWithParameters)i$.next();
                    runners.add(runnerFactory.createRunnerForTestWithParameters(test));
                }

                return runners;
            } catch (ClassCastException var8) {
                throw this.parametersMethodReturnedWrongType();
            }
        }

        private List<TestWithParameters> createTestsForParameters(Iterable<Object> allParameters, String namePattern) throws Exception {
            int i = 0;
            ArrayList children = new ArrayList();
            Iterator i$ = allParameters.iterator();

            while(i$.hasNext()) {
                Object parametersOfSingleTest = i$.next();
                children.add(this.createTestWithNotNormalizedParameters(namePattern, i++, parametersOfSingleTest));
            }

            return children;
        }

        private Exception parametersMethodReturnedWrongType() throws Exception {
            String className = this.getTestClass().getName();
            String methodName = this.getParametersMethod().getName();
            String message = MessageFormat.format("{0}.{1}() must return an Iterable of arrays.", new Object[]{className, methodName});
            return new Exception(message);
        }

        private static TestWithParameters createTestWithParameters(TestClass testClass, String pattern, int index, Object[] parameters) {
            String finalPattern = pattern.replaceAll("\\{index\\}", Integer.toString(index));
            String name = MessageFormat.format(finalPattern, parameters);
            return new TestWithParameters("[" + name + "]", testClass, Arrays.asList(parameters));
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @Inherited
    public @interface SuiteDirectories {
        String[] value();
    }

}
