package io.github.hjuergens.runtime;

import io.github.hjuergens.runtime.RuleBook.Problem;
import io.github.hjuergens.runtime.RuleBook.RuleBookException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class RuleBookTest {

    static String example() throws IOException {
        try (var in = Objects.requireNonNull(RuleBookTest.class.getResourceAsStream("/example.rules"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void exampleParsesAndAllAssertionsHold() throws Exception {
        RuleBook book = RuleBook.parse(example());
        assertEquals(book.rules().size(), 11);
        assertEquals(book.markets().keySet(), java.util.Set.of("XNYS"));
        assertEquals(book.events().keySet(), java.util.Set.of("FOMC"));
        assertEquals(book.assertions().size(), 14);
        assertEquals(book.verify(), List.of());
    }

    @Test
    public void evaluate() throws Exception {
        RuleBook book = RuleBook.parse(example());
        assertEquals(book.evaluate("SECOND_LAST_BD", LocalDate.of(2026, 11, 2), "XNYS"), List.of(LocalDate.of(2026, 11, 27)));
        expectThrows(IllegalArgumentException.class, () -> book.evaluate("NOPE", LocalDate.of(2026, 11, 2), null));
        expectThrows(IllegalArgumentException.class, () -> book.evaluate("IMMH", LocalDate.of(2026, 11, 2), "XLON"));
    }

    /** every problem of the file, with its line, not only the first */
    @Test
    public void problemsWithLines() {
        String text = String.join("\n",
                "# comment",
                "rule @A = T + 1D",
                "rule @A = T + 2D",                          // 3: twice
                "rule @B = T + 3X",                          // 4: syntax
                "market @M = 2026-02-30",                    // 5: invalid date
                "this is no line of a rules file",           // 6
                "assert @C [2026-11-02] => 2026-11-03",       // 7: unknown rule
                "assert @A [2026-11-02 @XLON] => 2026-11-03", // 8: unknown market
                "assert @A [2026-13-01] => none",             // 9: invalid base date
                "assert @A [2026-11-02] => 2026-11-31",       // 10: invalid expected date
                "rule IMMH = T > IMM");                      // 11: a name without @
        RuleBookException e = expectThrows(RuleBookException.class, () -> RuleBook.parse(text));
        assertEquals(e.problems().stream().map(Problem::line).toList(), List.of(3, 4, 5, 6, 7, 8, 9, 10, 11));
        assertTrue(e.problems().get(0).message().contains("declared twice"), e.problems().get(0).message());
    }

    @Test
    public void failingAssertions() throws Exception {
        RuleBook book = RuleBook.parse(String.join("\n",
                "rule @A = T + 1D",
                "assert @A [2026-11-02] => 2026-11-03",
                "assert @A [2026-11-02] => 2026-11-04",
                "rule @E = T > 5TH WED IN MONTH",
                "assert @E [2026-11-02] => error",
                "assert @E [2026-11-02] => none"));
        assertEquals(book.verify().stream().map(Problem::line).toList(), List.of(3, 6));
    }

    /** the market and event lines of the shared spec files, with @ before the name, are lines of a rules file */
    @Test
    public void specDeclarationsAreRulesFileLines() throws Exception {
        Path examples = Path.of("..", "examples");
        String declarations = Files.readAllLines(examples.resolve("schedule.txt")).stream()
                .filter(line -> line.startsWith("market ") || line.startsWith("event "))
                .map(line -> line.replaceFirst("^(market|event) ", "$1 @")).collect(Collectors.joining("\n"));
        RuleBook book = RuleBook.parse(declarations);
        assertTrue(book.markets().containsKey("XNYS"));
        assertTrue(book.events().containsKey("FOMC"));
    }
}
