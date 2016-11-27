package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class PeriodTermTest {
    @Test
    public void plus3M1W() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream("+3M1W");
        PeriodTermLexer lexer = new PeriodTermLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodTermParser parser = new PeriodTermParser(tokenStream);

        final DateTime result
                = parser.shift().adjusterOut.adjustInto(DateTime.parse("2016-11-13"));
        assertEquals(result, DateTime.parse("2017-02-20"));
    }

    @Test
    public void monday() throws Exception {
        ANTLRInputStream inputStream = new ANTLRInputStream(">>=monday");
        PeriodTermLexer lexer = new PeriodTermLexer(inputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        PeriodTermParser parser = new PeriodTermParser(tokenStream);

        final DateTime result
                = parser.selector().adjusterOut.adjustInto(DateTime.parse("2016-11-13"));
        assertEquals(result, DateTime.parse("2016-11-21"));
    }

    @Test
    public void sameMonday() throws Exception {
        {
            ANTLRInputStream inputStream = new ANTLRInputStream("<<=monday");
            PeriodTermLexer lexer = new PeriodTermLexer(inputStream);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            PeriodTermParser parser = new PeriodTermParser(tokenStream);

            final DateTime result
                    = parser.selector().adjusterOut.adjustInto(DateTime.parse("2016-11-14"));

            Assert.assertEquals(result,
                    DateTime.parse("2016-11-07T23:59:59.999"));
        }

        {
            ANTLRInputStream inputStream = new ANTLRInputStream("<<monday");
            PeriodTermLexer lexer = new PeriodTermLexer(inputStream);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            PeriodTermParser parser = new PeriodTermParser(tokenStream);

            final DateTime result
                    = parser.selector().adjusterOut.adjustInto(DateTime.parse("2016-11-14"));

            Assert.assertEquals(result,
                    DateTime.parse("2016-10-31T23:59:59.999"));
        }


    }


}