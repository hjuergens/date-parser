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
        System.out.println("adjust start");
    }
    shift { $adjusterOut = $shift.adjusterOut; }
    |
    selector { $adjusterOut = $selector.adjusterOut; }
    ;


shift3  returns [DateTimeAdjuster adjusterOut]
    :
    (operator period)*
    {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.apply();
        int i = 0;

        final int scalar = $operator.signum;

        System.out.println("scalar=" + $operator.signum);

        assert($period.p != null);

        final DateTimeAdjuster loopPeriodAdjuster;
        loopPeriodAdjuster = apply($period.p, scalar);

        assert(loopPeriodAdjuster != null);

        adjuster = adjuster.andThen(loopPeriodAdjuster);
        System.out.println("adjuster=" + adjuster);
        i += 1;

         $adjusterOut = adjuster;
    }
    ;

shift  returns [DateTimeAdjuster adjusterOut]
    :
    {
        DateTimeAdjuster adjuster = DateTimeAdjusterFactory.apply();
        int i = 0;
    }
    ( operator  period
    {
        final int scalar = $operator.signum;

        System.out.println("scalar=" + scalar);

        final DateTimeAdjuster loopPeriodAdjuster = apply($period.p, scalar);

        adjuster = adjuster.andThen(loopPeriodAdjuster);
        System.out.println("adjuster=" + adjuster);
        i += 1;
    }
    )*
    {
         $adjusterOut = adjuster;
    }
    ;

operator returns [int signum]
    : PLUS { $signum = 1; } | MINUS { $signum = -1; }
    {
        System.out.println("operator=" + $signum);
    }
    ;


selector returns [DateTimeAdjuster adjusterOut]
    :
    (
    direction { final int scale = $direction.dir.getFirst(); }
    (
    DAY { $adjusterOut = field(DateTimeFieldType.dayOfMonth(), scale); }
    |
    MONTH { $adjusterOut = field(DateTimeFieldType.monthOfYear(), scale); }
    |
    QUARTER { $adjusterOut = quarter(scale);}
    |
    DAY_OF_WEEK {
            final int dayWeek = DateTimeConstants.WEDNESDAY;
            $adjusterOut = dayOfWeek(dayWeek, scale);
        } // TODO
    ) )*
    ;

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

DAY_OF_WEEK : 'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'
    ;

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


