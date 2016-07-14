package io.github.hjuergens.util;

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
        ListParser lp = new ListParser(tokenStream);
        List expected = new ArrayList<Integer>(12);
        expected.add(3+0*5);
        expected.add(3+1*5);
        expected.add(3+2*5);

        final List<Integer> actual = lp.list().listOut;
        assertEquals( actual, expected);
        assertEquals( actual.toArray(), new Integer[]{3,8,13});
    }


}