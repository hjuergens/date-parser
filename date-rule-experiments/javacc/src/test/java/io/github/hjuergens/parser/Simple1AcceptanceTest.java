package io.github.hjuergens.parser;

import org.junit.Test;

import java.io.StringReader;

/**
 * Simple1 accepts exactly one group of matched braces, optionally followed by line breaks.
 */
public class Simple1AcceptanceTest {

    private static void parse(String input) throws ParseException {
        new Simple1(new StringReader(input)).Input();
    }

    @Test
    public void acceptsMatchedBraces() throws ParseException {
        parse("{}");
        parse("{{{}}}");
        parse("{}\n");
        parse("{{}}\r\n");
    }

    @Test(expected = ParseException.class)
    public void rejectsUnclosedBrace() throws ParseException {
        parse("{{}");
    }

    @Test(expected = ParseException.class)
    public void rejectsSurplusClosingBrace() throws ParseException {
        parse("{}}");
    }

    @Test(expected = ParseException.class)
    public void rejectsSecondGroup() throws ParseException {
        parse("{}{}");
    }

    @Test(expected = TokenMgrException.class)
    public void rejectsUnknownCharacter() throws ParseException {
        parse("{c}");
    }
}
