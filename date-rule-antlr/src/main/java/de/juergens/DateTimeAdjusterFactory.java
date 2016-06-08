package de.juergens;

import io.github.hjuergens.PeriodTermBaseListener;
import io.github.hjuergens.PeriodTermLexer;
import io.github.hjuergens.PeriodTermParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Iterator;
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
    static class Quarterly implements Iterator<DateTime> {
        volatile DateTime current = null;
        final int scalar;

        Quarterly(DateTime current, int scalar) {
            this.current = current.withTime(0,0,0,0);
            this.scalar = scalar;
        }
        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public DateTime next() {
            current = current.plusMonths(1).dayOfMonth().setCopy(1);
            int month = current.getMonthOfYear() % 4;
            current.monthOfYear().setCopy(month);
            return current;
        }

        @Override
        public void remove() { throw new NotImplementedException(); }
    }

    public static DateTimeAdjuster quarter(final int scalar) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return new Quarterly(dateTime, scalar).next();
            }

            @Override
            public String toString() {
                return "Quarterly";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    static final Logger logger = LoggerFactory.getLogger(DateTimeAdjuster.class);

    // WEDNESDAY = the third day of the week (ISO)
    private static DateTimeAdjuster dayOfWeek(final int dayOfWeek, final int scalar) {

        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                DateTime expected = dateTime;
                while(expected.getDayOfWeek() != dayOfWeek)
                    expected = expected.plusDays( Integer.signum(scalar) );
                expected = expected.plusWeeks(scalar - Integer.signum(scalar) * 1);
                return expected;
            }

            @Override
            public String toString() {
                return "dayOfWeek";
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
            public void exitShift(PeriodTermParser.ShiftContext ctx) {
                adjuster.set(DateTimeAdjusterFactory.apply());

                int i = 0;
                for(PeriodTermParser.OperatorContext operatorCtx : ctx.operator()) {

                    final int scalar;
                    if( operatorCtx.PLUS() != null )
                        scalar = 1;
                    else if( operatorCtx.MINUS() != null )
                        scalar = -1;
                    else throw new IllegalArgumentException("");

                    final DateTimeAdjuster loopPeriodAdjuster;
                    if(ctx.period(i) != null) {
                        Period loopPeriod = Period.parse("P" + ctx.period(i).getText());
                        loopPeriodAdjuster = DateTimeAdjusterFactory.apply(loopPeriod, scalar);
                    } else throw new IllegalArgumentException("");

                    adjuster.set(adjuster.get().andThen(loopPeriodAdjuster));

                    i += 1;

                }
            }
            @Override
            public void exitSelector(PeriodTermParser.SelectorContext ctx) {
                adjuster.compareAndSet(null, DateTimeAdjusterFactory.apply());

                int i = 0;
                for(PeriodTermParser.DirectionContext directionCtx : ctx.direction()) {

                    final int scale = directionCtx.NEXT().size() - directionCtx.PREVIOUS().size();

                    if(ctx.QUARTER(i) != null) {
                        DateTimeAdjuster shifter = DateTimeAdjusterFactory.quarter(scale);
                        adjuster.set(adjuster.get().andThen(shifter));
                    } else if(ctx.WEDNESDAY(i) != null) {
                        DateTimeAdjuster shifter = DateTimeAdjusterFactory.dayOfWeek(DateTimeConstants.WEDNESDAY, scale);
                        adjuster.set(adjuster.get().andThen(shifter));
                    }

                    i += 1;
                }
            }

            /*
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
            */

            @Override
            public void exitPeriod(PeriodTermParser.PeriodContext ctx) {
            }

        });

        parser.adjust();

        return adjuster.get();
    }

}
