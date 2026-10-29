package io.github.hjuergens.coco.schedule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Entry point of the shared "schedule" example, see {@code Schedule.atg}.
 */
public final class Schedules {

    private Schedules() {
    }

    /**
     * The rule of a schedule; evaluate it with {@code rule.evaluate(new ScheduleSemantics.Context(base, market))}.
     *
     * @throws IllegalArgumentException with Coco/R's error messages on a syntax or semantic error
     */
    public static ScheduleSemantics.Rule parse(String text) {
        Parser parser = new Parser(new Scanner(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        parser.errors.errorStream = new PrintStream(messages, true, StandardCharsets.UTF_8);
        parser.Parse();
        if (parser.errors.count > 0) {
            throw new IllegalArgumentException(messages.toString(StandardCharsets.UTF_8).trim());
        }
        return parser.rule;
    }
}
