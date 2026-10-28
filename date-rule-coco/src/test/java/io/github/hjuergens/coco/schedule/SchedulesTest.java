package io.github.hjuergens.coco.schedule;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Runs the shared cases of {@code examples/schedule.txt} (project root).
 */
public class SchedulesTest {

    @DataProvider
    public Object[][] examples() throws IOException {
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
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "examples")
    public void schedule(String input, List<LocalDate> expected) {
        if (expected == null) {
            expectThrows(RuntimeException.class, () -> Schedules.parse(input));
        } else {
            assertEquals(Schedules.parse(input), expected, input);
        }
    }
}
