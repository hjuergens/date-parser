package de.juergens;

import io.github.hjuergens.PeriodTermBaseListener;
import io.github.hjuergens.PeriodTermLexer;
import io.github.hjuergens.PeriodTermParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

public class SchedulerFactory {

    private SchedulerFactory(){}

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

    public static DateTimeAdjuster quarter(final int scalar) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return new Quarterly(dateTime, scalar).next();
            }

            @Override
            public String toString() {
                return "de.juergens.Quarterly";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }

    static LocalTime zero = new LocalTime(0,0,0,0);
    static LocalTime twentyFour = zero.minusMillis(1);


    public static DateTimeAdjuster field(DateTimeFieldType dateTimeFieldType, int scale) {
        DateTimeAdjuster adjuster = new DateTimeAdjusterAbstract() {
            @Override
            public DateTime adjustInto(DateTime dateTime) {
                return dateTime.property(dateTimeFieldType).addToCopy(scale).withTime(scale >= 0 ? zero : twentyFour);
            }

            @Override
            public String toString() {
                return dateTimeFieldType.toString();
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
                expected = expected.plusWeeks(scalar - Integer.signum(scalar));
                expected = expected.withTime(LocalTime.MIDNIGHT).minusMillis(scalar<0?1:0);
                return expected;
            }

            @Override
            public String toString() {
                return "dayOfWeek";
            }
        };
        return new DateTimeAdjusterLogWrapperLogger(logger, adjuster);
    }


    public static Iterator<DateTimeAdjuster> parseSchedule(String exprStr) {
        PeriodTermLexer lex = new PeriodTermLexer(new ANTLRInputStream(exprStr));
        CommonTokenStream tokens = new CommonTokenStream(lex);

        PeriodTermParser parser = new PeriodTermParser(tokens);

        final AtomicReference<DateTimeAdjuster> adjuster = new AtomicReference<DateTimeAdjuster>();

        parser.addParseListener(new PeriodTermBaseListener() {
            final Logger logger = LoggerFactory.getLogger(PeriodTermParser.class);
        });

        parser.addParseListener(new PeriodTermBaseListener() {
            @Override
            public void exitShift(PeriodTermParser.ShiftContext ctx) {
                adjuster.set(SchedulerFactory.apply());

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
                        loopPeriodAdjuster = SchedulerFactory.apply(loopPeriod, scalar);
                    } else throw new IllegalArgumentException("");

                    adjuster.set(adjuster.get().andThen(loopPeriodAdjuster));

                    i += 1;

                }
            }
            @Override
            public void exitSelector(PeriodTermParser.SelectorContext ctx) {
                adjuster.compareAndSet(null, SchedulerFactory.apply());

                int i = 0;
                for(PeriodTermParser.DirectionContext directionCtx : ctx.direction()) {

                    final int scale = directionCtx.NEXT().size() - directionCtx.PREVIOUS().size();

                    if(ctx.QUARTER(i) != null) {
                        DateTimeAdjuster shifter = quarter(scale);
                        adjuster.set(adjuster.get().andThen(shifter));
                    }
                    if(ctx.DAY_OF_WEEK(i) != null) {
                        // TODO consider all days of week
                        DateTimeAdjuster shifter = dayOfWeek(DateTimeConstants.WEDNESDAY, scale);
                        adjuster.set(adjuster.get().andThen(shifter));
                    }
                    if(ctx.DAY(i) != null) {
                        DateTimeAdjuster shifter = field(DateTimeFieldType.dayOfMonth(), scale);
                        adjuster.set(adjuster.get().andThen(shifter));
                    }
                    if(ctx.MONTH(i) != null) {
                        System.out.print(scale + "~~" + ctx.MONTH(i));
                        DateTimeAdjuster shifter = field(DateTimeFieldType.monthOfYear(), scale);

                        adjuster.set(adjuster.get().andThen(shifter));
                    }

                    i += 1;
                }
            }

            @Override
            public void exitPeriod(PeriodTermParser.PeriodContext ctx) {
            }

        });

        parser.adjust();

        ArrayList<DateTimeAdjuster> arrayList = new ArrayList<>();
        arrayList.add(adjuster.get());
        return arrayList.iterator();
    }

}
