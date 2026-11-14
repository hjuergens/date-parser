package io.github.hjuergens.coco;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DatesParserTest {

    private static Parser parse(String input) {
        Scanner scanner = new Scanner(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        Parser parser = new Parser(scanner);
        parser.Parse();
        return parser;
    }

    @DataProvider
    public Object[][] notations() {
        return new Object[][]{
                {"2015-06-09"},
                {"09.06.2015"},
                {"06/09/2015"},
        };
    }

    @Test(dataProvider = "notations")
    public void parsesEachNotation(String input) {
        Parser parser = parse(input);

        assertEquals(parser.errors.count, 0, input);
        assertEquals(parser.dates, Collections.singletonList(LocalDate.of(2015, 6, 9)), input);
    }

    @Test
    public void parsesListOfMixedNotations() {
        Parser parser = parse("2016-02-29, 1.1.2000 ,12/24/1999");

        assertEquals(parser.errors.count, 0);
        assertEquals(parser.dates, Arrays.asList(
                LocalDate.of(2016, 2, 29),
                LocalDate.of(2000, 1, 1),
                LocalDate.of(1999, 12, 24)));
    }

    @Test
    public void reportsInvalidCalendarDate() {
        Parser parser = parse("2015-02-29");

        assertEquals(parser.errors.count, 1);
        assertTrue(parser.dates.isEmpty());
    }

    @Test
    public void reportsSyntaxError() {
        Parser parser = parse("2015-06");

        assertTrue(parser.errors.count > 0);
        assertTrue(parser.dates.isEmpty());
    }
}
