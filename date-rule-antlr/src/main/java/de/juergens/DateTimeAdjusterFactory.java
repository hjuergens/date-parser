package de.juergens;

import de.juergens.dateparser.PeriodTermBaseListener;
import de.juergens.dateparser.PeriodTermLexer;
import de.juergens.dateparser.PeriodTermParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

class DateTimeAdjusterLogWrapperLogger implements DateTimeAdjuster {
    private final DateTimeAdjuster adjuster;
    private final Logger logger;

    DateTimeAdjusterLogWrapperLogger(final Logger logger, final DateTimeAdjuster adjuster) {
        this.logger = logger;
        this.adjuster = adjuster;
    }

    @Override
    public DateTime adjustInto(DateTime dateTime) {
        DateTime adjustedDatetime = adjuster.adjustInto(dateTime);
        logger.debug(adjuster.toString() +":" + dateTime + "->" + adjustedDatetime);
        return adjustedDatetime;
    }

    @Override
    public DateTimeAdjuster andThen(DateTimeAdjuster dateTimeAdjuster) {
        DateTimeAdjuster concatenated = adjuster.andThen(dateTimeAdjuster);
        logger.debug("*****" + adjuster.toString() +":" + adjuster + "->" + concatenated);
        return concatenated;
    }

    @Override
    public String toString() {
        return adjuster.toString();
    }
}

abstract class DateTimeAdjusterAbstract implements DateTimeAdjuster {

    @Override
    public DateTimeAdjuster andThen(final DateTimeAdjuster andThenDateTimeAdjuster) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return andThenDateTimeAdjuster.adjustInto(
                        DateTimeAdjusterAbstract.this.adjustInto(dateTime)
                );
            }

            @Override
            public String toString() {
                return DateTimeAdjusterAbstract.this.toString()
                        + "->" + andThenDateTimeAdjuster.toString();
            }
        };
        return adjuster;
    }
}

public class DateTimeAdjusterFactory {

    private DateTimeAdjusterFactory(){}

    public static DateTimeAdjuster apply() {
        final Logger logger = LoggerFactory.getLogger(DateTimeAdjuster.class);
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return dateTime;
            }

            @Override
            public String toString() {
                return "identity";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }
    public static DateTimeAdjuster apply(final Period period, final int scalar) {
        final Logger logger = LoggerFactory.getLogger(DateTimeAdjuster.class);
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {

                return dateTime.withPeriodAdded(period, scalar);
            }

            @Override
            public String toString() {
                return period.toString();
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    public static DateTimeAdjuster parseAdjuster(String exprStr) {
        PeriodTermLexer lex = new PeriodTermLexer(new ANTLRInputStream(exprStr));
        CommonTokenStream tokens = new CommonTokenStream(lex);

        PeriodTermParser parser = new PeriodTermParser(tokens);

        /*
        final AtomicReference<Integer> number = new AtomicReference<Integer>();
        final AtomicReference<Period> period = new AtomicReference<Period>();
        */
        final AtomicReference<Integer> direction = new AtomicReference<Integer>();
        final AtomicReference<DateTimeAdjuster> adjuster = new AtomicReference<DateTimeAdjuster>();

        parser.addParseListener(new PeriodTermBaseListener() {
            final Logger logger = LoggerFactory.getLogger(PeriodTermParser.class);
        });

        parser.addParseListener(new PeriodTermBaseListener() {
            @Override
            public void exitExpr(PeriodTermParser.ExprContext ctx) {
                Period period = Period.parse("P" + ctx.period(0).getText());

                DateTimeAdjuster periodAdjuster = DateTimeAdjusterFactory.apply(period,1);
                adjuster.set(periodAdjuster);

                int i = 0;
                for(PeriodTermParser.DirectionContext periodCtx : ctx.direction()) {
                    i += 1;

                    final int scalar;
                    if( periodCtx.PLUS() != null ) {
                        scalar = 1;
                    }
                    else if( periodCtx.MINUS() != null ) {
                        scalar = -1;
                    }
                    else {
                        throw new IllegalArgumentException("");
                    }

                    Period loopPeriod = Period.parse("P" + ctx.period(i).getText());
                    DateTimeAdjuster loopPeriodAdjuster = DateTimeAdjusterFactory.apply(loopPeriod, scalar);

                    adjuster.set(adjuster.get().andThen(loopPeriodAdjuster));
                }
            }


            @Override public void exitDirection(PeriodTermParser.DirectionContext ctx) {
                if( ctx.PLUS() != null ) {
                    direction.set(1);
                }
                else if( ctx.MINUS() != null ) {
                    direction.set(-1);
                }
                else {
                    direction.set(1);
                }
            }

            @Override
            public void exitPeriod(PeriodTermParser.PeriodContext ctx) {
            }

        });

        parser.expr();

        return adjuster.get();
    }

}
