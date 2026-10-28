package io.github.hjuergens;

import java_cup.runtime.Symbol;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class PascalLexerTest {

    private static List<Symbol> tokens(String input) throws IOException {
        PascalLexer lexer = new PascalLexer(new StringReader(input));
        List<Symbol> symbols = new ArrayList<>();
        Symbol symbol;
        do {
            symbol = lexer.next_token();
            symbols.add(symbol);
        } while (symbol.sym != Sym.EOF);
        return symbols;
    }

    private static int[] kinds(String input) throws IOException {
        return tokens(input).stream().mapToInt(s -> s.sym).toArray();
    }

    @Test
    public void keywordsAndIdentifiers() throws IOException {
        assertEquals(kinds("program foo; begin end."),
                new int[]{Sym.PROGRAM, Sym.IDENT, Sym.SEMI, Sym.BEGIN, Sym.END, Sym.DOT, Sym.EOF});
        assertEquals(tokens("begin_date").get(0).value, "begin_date");
    }

    @Test
    public void comparisonOperators() throws IOException {
        // '<' and '>' used to be swapped
        assertEquals(kinds("< > <= >= = !="),
                new int[]{Sym.LESS, Sym.GTR, Sym.LESS_EQ, Sym.GTR_EQ, Sym.EQ, Sym.NOT_EQ, Sym.EOF});
    }

    @Test
    public void assignmentIsOneToken() throws IOException {
        assertEquals(kinds("x := 1"), new int[]{Sym.IDENT, Sym.ASSMNT, Sym.INT, Sym.EOF});
    }

    @Test
    public void literalValues() throws IOException {
        List<Symbol> symbols = tokens("42 3.25 'c'");

        assertEquals(symbols.get(0).sym, Sym.INT);
        assertEquals(symbols.get(0).value, 42);
        assertEquals(symbols.get(1).sym, Sym.REAL);
        assertEquals(symbols.get(1).value, 3.25);
        assertEquals(symbols.get(2).sym, Sym.CHAR);
        assertEquals(symbols.get(2).value, 'c');
    }

    @Test
    public void commentsAndWhitespaceAreSkipped() throws IOException {
        assertEquals(kinds("if { a comment } x\n\tthen"), new int[]{Sym.IF, Sym.IDENT, Sym.THEN, Sym.EOF});
    }

    @Test
    public void positionIsTracked() throws IOException {
        Symbol then = tokens("if x\n  then").get(2);

        assertEquals(then.left, 1);  // line, zero based
        assertEquals(then.right, 2); // column
    }

    @Test
    public void illegalCharacterIsSkipped() throws IOException {
        assertEquals(kinds("x # y"), new int[]{Sym.IDENT, Sym.IDENT, Sym.EOF});
    }
}
