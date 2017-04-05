package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class ListParserTest {

    @Test
    public void testList() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("(3:5:14)");
        ListLexer lexer = new ListLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        ListParser parser = new ListParser(tokenStream);

        List<Integer> expected = new ArrayList<Integer>() {
            {
                add(3+0*5);
                add(3+1*5);
                add(3+2*5);
            }
        };

        final List<Integer> actual = new IntegerListVisitor().visit(parser.list());

        assertEquals( actual, expected);
        assertEquals( actual.toArray(), new Integer[]{3,8,13});
    }

    @Test
    public void testVisitor() {
        ListLexer lexer = new ListLexer(new ANTLRInputStream("(2:7:23)"));
        ListParser parser = new ListParser(new CommonTokenStream(lexer));
        List<Integer> sum = new IntegerListVisitor().visit(parser.list());
        assertEquals(sum.toString(), "[2, 9, 16, 23]");
    }
}