package io.github.hjuergens.time;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.hjuergens.time.DateTimeAdjusterFactory.*;

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

public class SchedulerFactory {

    private SchedulerFactory(){}

    public static Iterator<DateTimeAdjuster> parseSchedule(String exprStr) {
        PeriodTermLexer lex = new PeriodTermLexer(new ANTLRInputStream(exprStr));
        CommonTokenStream tokens = new CommonTokenStream(lex);

        PeriodTermParser parser = new PeriodTermParser(tokens);

        /*
        final AtomicReference<DateTimeAdjuster> adjuster = new AtomicReference<DateTimeAdjuster>();

        parser.addParseListener(new PeriodTermBaseListener() {
            final Logger logger = LoggerFactory.getLogger(PeriodTermParser.class);
        });

        parser.addParseListener(new PeriodTermBaseListener() {
            @Override
            public void exitShift(PeriodTermParser.ShiftContext ctx) {
                adjuster.set(apply());

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
                        loopPeriodAdjuster = apply(loopPeriod, scalar);
                    } else throw new IllegalArgumentException("");

                    adjuster.set(adjuster.get().andThen(loopPeriodAdjuster));

                    i += 1;

                }
            }
            @Override
            public void exitSelector(PeriodTermParser.SelectorContext ctx) {
                adjuster.compareAndSet(null, apply());

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
        */

        DateTimeAdjuster adjuster = parser.adjust().adjusterOut;

        ArrayList<DateTimeAdjuster> arrayList = new ArrayList<>();
        arrayList.add(adjuster);
        return arrayList.iterator();
    }

}
