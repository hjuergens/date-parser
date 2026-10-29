package io.github.hjuergens.coco.schedule;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Runs the shared cases of {@code examples/schedule.txt} (project root).
 */
public class SchedulesTest {

    private static final Pattern CONTEXT = Pattern.compile("\\[(\\S+)(?: (\\S+))?] (.*)");

    @DataProvider
    public Object[][] examples() throws IOException {
        List<Object[]> cases = new ArrayList<>();
        Map<String, ScheduleSemantics.Market> markets = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get("..", "examples", "schedule.txt"), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            if (line.startsWith("market ")) {   // market NAME = holiday, holiday, ...
                String[] market = line.substring("market ".length()).split("=", 2);
                Set<LocalDate> holidays = new HashSet<>();
                for (String date : market[1].split(",")) holidays.add(LocalDate.parse(date.trim()));
                markets.put(market[0].trim(), ScheduleSemantics.withHolidays(holidays));
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
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "examples")
    public void schedule(String input, ScheduleSemantics.Context context, List<LocalDate> expected) {
        if (expected == null) {
            expectThrows(RuntimeException.class, () -> Schedules.parse(input).evaluate(context));
        } else {
            assertEquals(Schedules.parse(input).evaluate(context), expected, input);
        }
    }
}
