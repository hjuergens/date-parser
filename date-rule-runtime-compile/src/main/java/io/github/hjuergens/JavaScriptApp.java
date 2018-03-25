package io.github.hjuergens;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class JavaScriptApp {
    /**
     * just to hide ctor.
     */
    private JavaScriptApp() { }
    /**
     * entry point.
     * @param args command line parameters
     */
    public static void main(final String[] args) throws ScriptException {
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("JavaScript");
        engine.eval("print('Hello, World')");
    }

}
