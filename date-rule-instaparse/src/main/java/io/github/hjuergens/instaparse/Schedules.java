package io.github.hjuergens.instaparse;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

/** Java entry point of the Clojure parser in {@code io/github/hjuergens/instaparse/schedule.clj}. */
public final class Schedules {

    private static final IFn PARSE;

    static {
        Clojure.var("clojure.core", "require").invoke(Clojure.read("io.github.hjuergens.instaparse.schedule"));
        PARSE = Clojure.var("io.github.hjuergens.instaparse.schedule", "parse");
    }

    private Schedules() {
    }

    /** the rule of a schedule; throws IllegalArgumentException on a syntax error */
    public static ScheduleSemantics.Rule parse(String text) {
        return (ScheduleSemantics.Rule) PARSE.invoke(text);
    }
}
