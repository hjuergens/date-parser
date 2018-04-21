package io.github.hjuergens;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.LazySeq;
import clojure.lang.RT;
import clojure.lang.Var;

import java.io.StringReader;

public final class ClojureApp {
    /**
     * just to hide ctor.
     */
    private ClojureApp() { }
    /**
     * entry point.
     * @param args command line parameters
     */
    public static void main(final String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class.forName("clojure.java.api.Clojure");

        IFn plus = Clojure.var("clojure.core", "+");
        Object obj = plus.invoke(1, 2);

        System.out.println(obj);

        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("clojure.set"));

        IFn map = Clojure.var("clojure.core", "map");
        IFn inc = Clojure.var("clojure.core", "inc");
        clojure.lang.LazySeq seq = (LazySeq) map.invoke(inc, Clojure.read("[1 2 3]"));

        for(Object e : seq) {
            System.out.println(e);
        }

        // Load the Clojure script -- as a side effect this initializes the runtime.
        String str = "(ns user) (defn foo [a b]   (str a \" \" b))";

        //RT.loadResourceScript("foo.clj");
        clojure.lang.Compiler.load(new StringReader(str));

        // Get a reference to the foo function.
        Var foo = RT.var("user", "foo");

        // Call it!
        Object result = foo.invoke("Hi", "there");
        System.out.println(result);
    }
}

