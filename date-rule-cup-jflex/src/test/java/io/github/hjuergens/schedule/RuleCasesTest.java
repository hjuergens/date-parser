package io.github.hjuergens.schedule;

import io.github.hjuergens.schedule.rules.RuleFiles;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;

/**
 * Runs the shared cases of {@code examples/rules.txt} (project root): blocks of declarations, each followed by its
 * cases; declarations after cases start a new block.
 */
public class RuleCasesTest {

    private static final Pattern SCHEDULE =      // [base [hh:mm zone]] schedule
            Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2})(?: (\\d{2}:\\d{2}) (\"[^\"]+\"|UTC[+-]\\d+))?] (.*)");
    private static final Pattern SCOPE = Pattern.compile("(\\S+) (\\d{4})");                       // scope year

    @DataProvider
    public Object[][] cases() throws IOException {
        List<Object[]> cases = new ArrayList<>();
        List<String> declarations = new ArrayList<>();
        boolean afterCases = false;
        for (String line : Files.readAllLines(Paths.get("..", "examples", "rules.txt"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int arrow = line.lastIndexOf("=>");
            if (arrow >= 0) {
                cases.add(new Object[] {line.substring(0, arrow).trim(), line.substring(arrow + 2).trim(),
                        String.join("\n", declarations)});
                afterCases = true;
                continue;
            }
            if (afterCases) {
                declarations = new ArrayList<>();
                afterCases = false;
            }
            declarations.add(line);
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "cases")
    public void rule(String text, String expected, String declarations) {
        assertEquals(result(declarations, text), expected, text);
    }

    private static String result(String declarations, String text) {
        try {
            String dates = String.join(", ", evaluate(declarations, text));
            return dates.isEmpty() ? "none" : dates;
        } catch (RuntimeException e) {
            return "error";
        }
    }

    private static List<String> evaluate(String declarations, String text) {
        RuleCatalog catalog = RuleFiles.parseRules(declarations);
        Matcher schedule = SCHEDULE.matcher(text);
        if (schedule.matches()) {
            LocalDate base = LocalDate.parse(schedule.group(1));
            ZonedDateTime instant = null;
            if (schedule.group(2) != null) {
                String zone = schedule.group(3).replace("\"", "");
                ZoneId id = zone.startsWith("UTC") ? ZoneOffset.ofHours(Integer.parseInt(zone.substring(3))) : ZoneId.of(zone);
                instant = ZonedDateTime.of(base, LocalTime.parse(schedule.group(2)), id);
            }
            return catalog.schedule(schedule.group(4)).evaluate(catalog.context(base, null, instant))
                    .stream().map(LocalDate::toString).toList();
        }
        Matcher scope = SCOPE.matcher(text);
        if (!scope.matches()) {
            throw new IllegalArgumentException("not a case: " + text);
        }
        return catalog.holidays(scope.group(1), Integer.parseInt(scope.group(2))).stream()
                .map(o -> o.date() + (o.scenario() == null ? "" : " " + o.scenario())).toList();
    }
}
