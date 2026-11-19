package io.github.hjuergens.coco.schedule.rules;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.github.hjuergens.coco.schedule.RuleCatalog;
import io.github.hjuergens.coco.schedule.ScheduleSemantics;

/**
 * Entry point of rule files, see {@code RuleFile.atg}; the parser reads declarations or one schedule.
 */
public final class RuleFiles {

    private RuleFiles() {
    }

    /**
     * the rule catalog of a rule file
     *
     * @throws IllegalArgumentException with Coco/R's error messages on a syntax error; on overlapping versions, an
     *         unknown or a cyclic reference
     * @throws java.time.DateTimeException on an invalid date
     */
    public static RuleCatalog parseRules(String text) {
        return parseRules(text, null);
    }

    /** the same with announced dates: key -&gt; the dates an authority published */
    public static RuleCatalog parseRules(String text, Map<String, List<LocalDate>> announcements) {
        Parser parser = parse(text);
        if (parser.schedule != null) {
            throw new IllegalArgumentException("a schedule, not a rule file");
        }
        return new RuleCatalog(parser.declarations, announcements, RuleFiles::schedule);
    }

    /** one expression of the rule language */
    public static ScheduleSemantics.Rule schedule(String text) {
        Parser parser = parse(text);
        if (parser.schedule == null) {
            throw new IllegalArgumentException("not a schedule: " + text);
        }
        return parser.schedule;
    }

    private static Parser parse(String text) {
        Parser parser = new Parser(new Scanner(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        parser.errors.errorStream = new PrintStream(messages, true, StandardCharsets.UTF_8);
        try {
            parser.Parse();
        } catch (RuntimeException e) {
            if (parser.errors.count == 0) {
                throw e;   // an action failed: an invalid date, EVERY 6M, ...
            }
        }
        if (parser.errors.count > 0) {
            throw new IllegalArgumentException(messages.toString(StandardCharsets.UTF_8).trim());
        }
        return parser;
    }
}
