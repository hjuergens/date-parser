package io.github.hjuergens.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Runs the shared cases of {@code examples/schedule.txt} (project root).
 */
@RunWith(Parameterized.class)
public class ScheduleTest {

    @Parameterized.Parameters(name = "{0} => {1}")
    public static List<Object[]> examples() throws IOException {
        List<Object[]> cases = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("..", "examples", "schedule.txt"), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int arrow = line.lastIndexOf("=>");
            String expected = line.substring(arrow + 2).trim();
            List<LocalDate> dates = new ArrayList<>();
            if (expected.equals("error")) dates = null;
            else if (!expected.equals("none")) for (String date : expected.split(",")) dates.add(LocalDate.parse(date.trim()));
            cases.add(new Object[]{line.substring(0, arrow).trim(), dates});
        }
        return cases;
    }

    private final String input;
    private final List<LocalDate> expected;

    public ScheduleTest(String input, List<LocalDate> expected) {
        this.input = input;
        this.expected = expected;
    }

    @Test
    public void schedule() throws Exception {
        if (expected == null) {
            assertThrows(input, Exception.class, () -> Schedule.parse(input));
        } else {
            assertEquals(input, expected, Schedule.parse(input));
        }
    }
}
