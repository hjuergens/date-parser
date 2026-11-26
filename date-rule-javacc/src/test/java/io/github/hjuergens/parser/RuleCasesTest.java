package io.github.hjuergens.parser;

import io.github.hjuergens.parser.rules.RuleFiles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Runs the shared cases of {@code examples/rules.txt} (project root): blocks of declarations, each followed by its
 * cases; declarations after cases start a new block.
 */
@RunWith(Parameterized.class)
public class RuleCasesTest {

    private static final Pattern SCHEDULE =   // [base [hh:mm zone]] schedule
            Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2})(?: (\\d{2}:\\d{2}) (\"[^\"]+\"|UTC[+-]\\d+))?] (.*)");
    private static final Pattern SCOPE = Pattern.compile("(\\S+) (\\d{4})");                       // scope year

    @Parameterized.Parameters(name = "{index}: {0} => {1}")
    public static List<Object[]> cases() throws IOException {
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
        return cases;
    }

    private final String text;
    private final String expected;
    private final String declarations;

    public RuleCasesTest(String text, String expected, String declarations) {
        this.text = text;
        this.expected = expected;
        this.declarations = declarations;
    }

    @Test
    public void rule() {
        assertEquals(text, expected, result(declarations, text));
    }

    private static String result(String declarations, String text) {
        try {
            String dates = String.join(", ", evaluate(declarations, text));
            return dates.isEmpty() ? "none" : dates;
        } catch (Exception e) {
            return "error";
        }
    }

    private static List<String> evaluate(String declarations, String text) {
        RuleCatalog catalog = RuleFiles.parseRules(declarations);
        Matcher schedule = SCHEDULE.matcher(text);
        if (schedule.matches()) {
            LocalDate base = LocalDate.parse(schedule.group(1));
            ZonedDateTime instant = schedule.group(2) == null ? null
                    : LocalDateTime.of(base, LocalTime.parse(schedule.group(2))).atZone(zone(schedule.group(3)));
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

    /** "America/New_York" or UTC+9 */
    private static ZoneId zone(String text) {
        return text.startsWith("UTC") ? ZoneOffset.ofHours(Integer.parseInt(text.substring(3)))
                : ZoneId.of(text.substring(1, text.length() - 1));
    }
}
