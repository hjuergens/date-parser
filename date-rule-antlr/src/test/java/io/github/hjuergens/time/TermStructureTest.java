package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.Period;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TermStructureTest {
    @Test
    public void testSequence() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("[3M,5M,7M]");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Period> result = parser.sequence().listOut;
        assertEquals(result.size(), 2);
        System.out.println("testSequence:" + result);
        assertEquals(result.get(0), Period.months(3));
        assertEquals(result.get(1), Period.months(7));
    }

    @Test
    public void testLoop() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("(4M:1M:9M)");
        TermStructureLexer lexer = new TermStructureLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        TermStructureParser parser = new TermStructureParser(tokenStream);

        List<Period> result = parser.loop().listOut;
        assertEquals(result.size(), 6);
        assertEquals(result.get(0), Period.months(4));
        assertEquals(result.get(5), Period.months(9));
    }


}