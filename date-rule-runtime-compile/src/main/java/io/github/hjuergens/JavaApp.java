package io.github.hjuergens;

import net.openhft.compiler.CachedCompiler;
import net.openhft.compiler.CompilerUtils;

import java.io.File;

/**
 *
 */
public final class JavaApp {
    private static final File parent = new File(
            ClassLoader.getSystemResource(".").getPath()
    );

    private static final CachedCompiler JCC = CompilerUtils.DEBUGGING ?
            new CachedCompiler(new File(parent, "src/test/java"), new File(parent, "target/compiled")) :
            CompilerUtils.CACHED_COMPILER;
    /**
     * just to hide ctor.
     */
    private JavaApp() { }
    /**
     * entry point.
     * @param args command line parameters
     */
    public static void main(final String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        // dynamically you can call
        ClassLoader.getSystemResourceAsStream(".");
        String className = "mypackage.MyClass";
        String javaCode = "package mypackage;\n" +
                "public class MyClass implements Runnable {\n" +
                "    public void run() {\n" +
                "        System.out.println(\"Hello World\");\n" +
                "    }\n" +
                "}\n";
        Class aClass = CompilerUtils.CACHED_COMPILER.loadFromJava(className, javaCode);
        Runnable runner = (Runnable) aClass.newInstance();
        runner.run();
    }
}
