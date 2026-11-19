package io.github.hjuergens.instaparse.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import io.github.hjuergens.instaparse.RuleCatalog;
import io.github.hjuergens.instaparse.ScheduleSemantics;

/** Java entry point of rule files, parsed in Clojure by {@code io/github/hjuergens/instaparse/rulefile.clj}. */
public final class RuleFiles {

    private static final IFn PARSE_RULES;
    private static final IFn SCHEDULE;

    static {
        Clojure.var("clojure.core", "require").invoke(Clojure.read("io.github.hjuergens.instaparse.rulefile"));
        PARSE_RULES = Clojure.var("io.github.hjuergens.instaparse.rulefile", "parse-rules");
        SCHEDULE = Clojure.var("io.github.hjuergens.instaparse.rulefile", "schedule");
    }

    private RuleFiles() {
    }

    /**
     * the rule catalog of a rule file
     *
     * @throws IllegalArgumentException on a syntax error, overlapping versions, an unknown or a cyclic reference
     * @throws java.time.DateTimeException on an invalid date
     */
    public static RuleCatalog parseRules(String text) {
        return parseRules(text, null);
    }

    /** the same with announced dates: key -&gt; the dates an authority published */
    public static RuleCatalog parseRules(String text, Map<String, List<LocalDate>> announcements) {
        return new RuleCatalog((List<?>) PARSE_RULES.invoke(text), announcements, RuleFiles::schedule);
    }

    /** one expression of the rule language */
    public static ScheduleSemantics.Rule schedule(String text) {
        return (ScheduleSemantics.Rule) SCHEDULE.invoke(text);
    }
}
