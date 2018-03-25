grammar PeriodTerm;

import Period, List;


options {
    language = Java;
}

@header
{
    import org.joda.time.Period;
    import java.util.List;
    import java.util.LinkedList;
    import io.github.hjuergens.time.DateTimeAdjuster;
    import io.github.hjuergens.time.DateTimeAdjusterFactory;
    import static io.github.hjuergens.time.DateTimeAdjusterFactory.*;
    import org.joda.time.DateTimeConstants;
    import org.joda.time.DateTimeFieldType;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
}
@members
{
     private final Logger logger = LoggerFactory.getLogger(this.getClass());
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

input           : (adjust NEWLINE)+ EOF;

chain : '^' adjust
    ;

adjust returns [DateTimeAdjuster adjusterOut]
    :
    {
        logger.info("adjust start");
    }
    shift { $adjusterOut = $shift.adjusterOut; }
    |
    selector { $adjusterOut = $selector.adjusterOut; }
    ;


// +3M
shift  returns [DateTimeAdjuster adjusterOut]
    :
    {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.apply();
        int i = 0;
    }
    ( operator  period
    {
        final int scalar = $operator.signum;

        logger.info("scalar=" + scalar);

        final DateTimeAdjuster loopPeriodAdjuster = apply($period.p, scalar);

        adjuster = adjuster.andThen(loopPeriodAdjuster);
        logger.info("adjuster=" + adjuster);
        i += 1;
    }
    )*
    {
         $adjusterOut = adjuster;
    }
    ;

// 3M.2W = 3M2W

// +,-
operator returns [int signum]
    : PLUS { $signum = 1; } | MINUS { $signum = -1; }
    {
        logger.info("operator=" + $signum);
    }
    ;


selector returns [DateTimeAdjuster adjusterOut]
    :
    {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.apply();
        int i = 0;
    }
    (
    direction { final int scale = $direction.dir.getFirst(); }
    (
    DAY {
        final DateTimeAdjuster adjusterOut = field(DateTimeFieldType.dayOfMonth(), scale);
        adjuster = adjuster.andThen(adjusterOut);
        }
    |
    MONTH {
        final DateTimeAdjuster adjusterOut = field(DateTimeFieldType.monthOfYear(), scale);
        adjuster = adjuster.andThen(adjusterOut);
        }
    |
    QUARTER {
        final DateTimeAdjuster adjusterOut = quarter(scale);
        adjuster = adjuster.andThen(adjusterOut);
        }
    |
    dayWeek {
            final int dayWeek = $dayWeek.weekDayConstant;
            final DateTimeAdjuster adjusterOut = dayOfWeek(dayWeek, scale, $direction.dir.getSecond());
            adjuster = adjuster.andThen(adjusterOut);
        }
    ) )*
    {
            $adjusterOut = adjuster;
    }
    ;
//selectorByName
//selectorByUnit


// <<
direction returns [Pair<Integer,Boolean> dir]
    :
    {
        int steps = 0;
        boolean eqAllowed = false;
    }
    ((PREVIOUS { steps-=1; })+ | (NEXT { steps+=1; })+) (ORTHIS { eqAllowed = true; })?
    {
        $dir = new Pair<Integer,Boolean>(steps, eqAllowed);
    }
    ;

dayWeek returns [int weekDayConstant]
    :
    MONDAY { $weekDayConstant = DateTimeConstants.MONDAY; }
    | TUESDAY { $weekDayConstant = DateTimeConstants.TUESDAY; }
    | WEDNESDAY { $weekDayConstant = DateTimeConstants.WEDNESDAY; }
    | THURSDAY { $weekDayConstant = DateTimeConstants.THURSDAY; }
    | FRIDAY { $weekDayConstant = DateTimeConstants.FRIDAY; }
    | SATURDAY { $weekDayConstant = DateTimeConstants.SATURDAY; }
    | SUNDAY { $weekDayConstant = DateTimeConstants.SUNDAY; }
    ;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

TWODIGITNUMBER  : NONNULLDIGIT?DIGIT ;

fragment DIGIT  : '0'..'9' ;
fragment NONNULLDIGIT  : '1'..'9' ;


YEAR : 'Y';
MONTH : 'M';
WEEK : 'W';
DAY : 'D';
QUARTER : 'Q';

//DAY_OF_WEEK : 'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'
//    ;
MONDAY : 'monday';
TUESDAY : 'tuesday';
WEDNESDAY : 'wednesday';
THURSDAY : 'thursday';
FRIDAY : 'friday';
SATURDAY : 'saturday';
SUNDAY : 'sunday';

NEXT     : '>' ;
PREVIOUS : '<' ;
ORTHIS   : '=' ;
PLUS   : '+' ;
MINUS  : '-' ;
MULT   : '*' ;
DIV    : '/' ;

NEWLINE   : '\r' '\n' | '\n' | '\r';

WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};
SPACE : (' ' | '\t' | '\r' | '\n') {skip();};


