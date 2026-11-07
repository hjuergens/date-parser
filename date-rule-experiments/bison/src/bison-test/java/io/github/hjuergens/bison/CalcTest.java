package io.github.hjuergens.bison;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CalcTest {

    private static CalcLexer lexer(String input) {
        return new CalcLexer(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    @DataProvider
    public Object[][] expressions() {
        return new Object[][]{
                {"1+2*3\n", 7},
                {"(1+2)*3\n", 9},
                {"2^3^2\n", 512},   // '^' is right associative
                {"10-4-3\n", 3},    // '-' is left associative
                {"-2*3\n", -6},     // unary minus binds tighter than '*'
                {"7/2\n", 3},
        };
    }

    @Test(dataProvider = "expressions")
    public void evaluates(String input, int expected) throws IOException {
        CalcLexer lexer = lexer(input);
        Calc calc = new Calc(lexer);

        assertTrue(calc.parse(), input);
        assertEquals(calc.results, Arrays.asList(expected), input);
        assertEquals(lexer.errors, 0, input);
    }

    @Test
    public void evaluatesEveryLine() throws IOException {
        Calc calc = new Calc(lexer("1+1\n\n2*2\n"));

        assertTrue(calc.parse());
        assertEquals(calc.results, Arrays.asList(2, 4));
    }

    @Test
    public void recoversFromSyntaxErrorAtEndOfLine() throws IOException {
        CalcLexer lexer = lexer("1+*2\n3+4\n");
        Calc calc = new Calc(lexer);

        assertTrue(calc.parse(), "the 'error' rule resynchronizes at the newline");
        assertEquals(lexer.errors, 1);
        assertEquals(calc.results, Arrays.asList(7));
    }
}
