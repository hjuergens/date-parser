package io.github.hjuergens.time;

import org.antlr.v4.runtime.*;
import org.joda.time.PeriodType;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

public class DateRuleFactory {
    public DateRule createDateRule(InputStream in) throws IOException {
        DateRuleLexer l = new DateRuleLexer(new ANTLRInputStream(in));
        DateRuleParser p = new DateRuleParser(new CommonTokenStream(l));
        p.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalStateException("failed to parse at line " + line + " due to " + msg, e);
            }
        });

        final AtomicReference<Integer> cardinal = new AtomicReference<Integer>();
        final AtomicReference<PeriodType> unit = new AtomicReference<PeriodType>();

        p.addParseListener(new DateRuleBaseListener() {
            /*
            @Override
            public void exitDuration(DateRuleParser.DurationContext ctx) {
                .set( Integer.valueOf(ctx.cardinal().getText()) );
            }
            */

            @Override
            public void exitCardinal(DateRuleParser.CardinalContext ctx) {
                cardinal.set( Integer.valueOf(ctx.getText()) );
            }

            @Override
            public void exitUnit(DateRuleParser.UnitContext ctx) {
                if("days".equalsIgnoreCase(ctx.getText()))
                    unit.set(PeriodType.days());
                else if("days".equalsIgnoreCase(ctx.getText()))
                    unit.set(PeriodType.minutes());
                else
                    throw new RuntimeException("unit");
            }

        });

        return new DateRule(cardinal.get().intValue(), unit.get());
    }
}