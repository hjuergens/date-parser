package de.juergens.tiny.parser;

import de.juergens.tiny.analysis.DepthFirstAdapter;
import de.juergens.tiny.lexer.Lexer;
import de.juergens.tiny.lexer.LexerException;
import de.juergens.tiny.node.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.testng.Assert.assertEquals;

/**
 * Evaluates the syntax tree of the 'tiny' grammar with a tree walker.
 */
public class EvaluatorTest {

    private static class Evaluator extends DepthFirstAdapter {
        final Deque<Integer> stack = new ArrayDeque<>();

        @Override
        public void outANumberTerm(ANumberTerm node) {
            stack.push(Integer.valueOf(node.getNumber().getText().trim()));
        }

        private void binary(java.util.function.IntBinaryOperator op) {
            int right = stack.pop();
            int left = stack.pop();
            stack.push(op.applyAsInt(left, right));
        }

        @Override public void outAPlusExpr(APlusExpr node) { binary((a, b) -> a + b); }
        @Override public void outAMinusExpr(AMinusExpr node) { binary((a, b) -> a - b); }
        @Override public void outAMultFactor(AMultFactor node) { binary((a, b) -> a * b); }
        @Override public void outADivFactor(ADivFactor node) { binary((a, b) -> a / b); }
        @Override public void outAModFactor(AModFactor node) { binary((a, b) -> a % b); }
    }

    private static Start parse(String input) throws ParserException, LexerException, IOException {
        return new Parser(new Lexer(new PushbackReader(new StringReader(input), 1024))).parse();
    }

    private static int evaluate(String input) throws ParserException, LexerException, IOException {
        Evaluator evaluator = new Evaluator();
        parse(input).apply(evaluator);
        assertEquals(evaluator.stack.size(), 1, input);
        return evaluator.stack.pop();
    }

    @DataProvider
    public Object[][] expressions() {
        return new Object[][]{
                {"4+(6*8)/3", 20},
                {"2+3*4", 14},      // '*' binds tighter than '+'
                {"(2+3)*4", 20},
                {"10-4-3", 3},      // left associative
                {"17 % 5", 2},
                {"7/2", 3},
        };
    }

    @Test(dataProvider = "expressions")
    public void evaluates(String input, int expected) throws Exception {
        assertEquals(evaluate(input), expected, input);
    }

    @Test(expectedExceptions = ParserException.class)
    public void incompleteExpression() throws Exception {
        parse("4+");
    }

    @Test(expectedExceptions = ParserException.class)
    public void unbalancedParenthesis() throws Exception {
        parse("(4+2");
    }

    @Test(expectedExceptions = LexerException.class)
    public void unknownCharacter() throws Exception {
        parse("4&2");
    }
}
