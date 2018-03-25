package io.github.hjuergens;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.List;

public class Overview {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        List<ScriptEngineFactory> factories = manager.getEngineFactories();
        for (ScriptEngineFactory f : factories) {
            System.out.println("egine name:" + f.getEngineName());
            System.out.println("engine version:" + f.getEngineVersion());
            System.out.println("language name:" + f.getLanguageName());
            System.out.println("language version:" + f.getLanguageVersion());
            System.out.println("names:" + f.getNames());
            System.out.println("mime:" + f.getMimeTypes());
            System.out.println("extension:" + f.getExtensions());
            System.out.println("-----------------------------------------------");
        }
    }

}
