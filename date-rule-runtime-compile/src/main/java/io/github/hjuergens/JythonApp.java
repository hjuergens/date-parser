package io.github.hjuergens;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class JythonApp {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();

        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine pyEngine = mgr.getEngineByName("python");
        try {
            pyEngine.eval("print \"Python - Hello, world!\"");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        ScriptEngine engine = manager.getEngineByName("python");
        engine.eval("import sys");
        engine.eval("print sys");
        engine.put("a", 42);
        engine.eval("print a");
        engine.eval("x = 2 + 2");
        Object x = engine.get("x");
        System.out.println("x: " + x);
    }

}
