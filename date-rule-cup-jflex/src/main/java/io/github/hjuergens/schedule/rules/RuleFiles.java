package io.github.hjuergens.schedule.rules;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.github.hjuergens.schedule.RuleCatalog;
import io.github.hjuergens.schedule.ScheduleSemantics;

/**
 * Entry point of rule files, see {@code rulefile.cup} and {@code rulefile.jflex}; the parser reads declarations or one
 * schedule.
 */
public final class RuleFiles {

    private RuleFiles() {
    }

    /**
     * the rule catalog of a rule file
     *
     * @throws IllegalArgumentException on a syntax error, an unknown character, overlapping versions, an unknown or a
     *         cyclic reference
     * @throws java.time.DateTimeException on an invalid date
     */
    public static RuleCatalog parseRules(String text) {
        return parseRules(text, null);
    }

    /** the same with announced dates: key -&gt; the dates an authority published */
    public static RuleCatalog parseRules(String text, Map<String, List<LocalDate>> announcements) {
        if (!(parse(text) instanceof List<?> declarations)) {
            throw new IllegalArgumentException("a schedule, not a rule file");
        }
        return new RuleCatalog(declarations, announcements, RuleFiles::schedule);
    }

    /** one expression of the rule language */
    public static ScheduleSemantics.Rule schedule(String text) {
        if (!(parse(text) instanceof ScheduleSemantics.Rule rule)) {
            throw new IllegalArgumentException("not a schedule: " + text);
        }
        return rule;
    }

    @SuppressWarnings("deprecation") // the SymbolFactory constructors need factory-made symbols from the lexer
    private static Object parse(String text) {
        try {
            return new RuleFileParser(new RuleFileLexer(new StringReader(text))).parse().value;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {   // CUP: "Can't recover from previous error(s)"
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
