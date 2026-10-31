package io.github.hjuergens.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Runs the shared cases of {@code examples/schedule.txt} (project root).
 */
@RunWith(Parameterized.class)
public class ScheduleTest {

    private static final Pattern CONTEXT = Pattern.compile("\\[(\\S+)(?: (\\S+))?] (.*)");

    @Parameterized.Parameters(name = "{index}: {0} => {2}")
    public static List<Object[]> examples() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        Map<String, ScheduleSemantics.Market> markets = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get("..", "examples", "schedule.txt"), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            if (line.startsWith("market ")) {   // market NAME = schedule of the holidays
                String[] market = line.substring("market ".length()).split("=", 2);
                markets.put(market[0].trim(), ScheduleSemantics.withHolidayRules(Schedule.parse(market[1].trim())));
                continue;
            }
            int arrow = line.lastIndexOf("=>");
            String expected = line.substring(arrow + 2).trim();
            List<LocalDate> dates = new ArrayList<>();
            if (expected.equals("error")) dates = null;
            else if (!expected.equals("none")) for (String date : expected.split(",")) dates.add(LocalDate.parse(date.trim()));
            String input = line.substring(0, arrow).trim();
            ScheduleSemantics.Context context = new ScheduleSemantics.Context(null, null);
            Matcher prefix = CONTEXT.matcher(input);   // [base] or [base market]
            if (prefix.matches()) {
                ScheduleSemantics.Market market = prefix.group(2) == null ? null
                        : Objects.requireNonNull(markets.get(prefix.group(2)), "unknown market " + prefix.group(2));
                context = new ScheduleSemantics.Context(LocalDate.parse(prefix.group(1)), market);
                input = prefix.group(3);
            }
            cases.add(new Object[]{input, context, dates});
        }
        return cases;
    }

    private final String input;
    private final ScheduleSemantics.Context context;
    private final List<LocalDate> expected;

    public ScheduleTest(String input, ScheduleSemantics.Context context, List<LocalDate> expected) {
        this.input = input;
        this.context = context;
        this.expected = expected;
    }

    @Test
    public void schedule() throws Exception {
        if (expected == null) {
            assertThrows(input, Exception.class, () -> Schedule.parse(input).evaluate(context));
        } else {
            assertEquals(input, expected, Schedule.parse(input).evaluate(context));
        }
    }
}
