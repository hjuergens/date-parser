grammar TermStructure;

import TimeLexer, PeriodTerm, List;

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

file           : ( row NEWLINE )+ EOF
    ;

row : (futures | forwards)
    ;

list returns [List<Period> listOut]
    : sequence { $listOut = $sequence.listOut; }
    | loop { $listOut  = $loop.listOut; }
    ;

//BaseInterval(ReadableInstant start, ReadableDuration duration)
futures returns [List<Pair<Period, DateTimeAdjuster>> listOut]
    : list shift
    {
        List<Pair<Period, DateTimeAdjuster>> listOfPairs
            = new LinkedList<Pair<Period, DateTimeAdjuster>>();
        for(Period p : $list.listOut) {
            listOfPairs.add( new Pair<Period, DateTimeAdjuster>(p, $shift.adjusterOut) );
        }
        $listOut = listOfPairs;
    }
    ;

future returns [Pair<Period, DateTimeAdjuster> fut]
    : period shift
    {
        $fut = new Pair<Period, DateTimeAdjuster>($period.p, $shift.adjusterOut);
    }
    ;

//BaseInterval(ReadableInstant start, ReadableInstant end)
// 3Mx6M, (3M,4M,5M)x(6M,7M,8M)
forward returns [Pair<DateTimeAdjuster, DateTimeAdjuster> fwd]
    :  p1=period 'x' p2=period
    {
        DateTimeAdjuster begin = DateTimeAdjusterFactory.apply($p1.p,1);
        DateTimeAdjuster end   = DateTimeAdjusterFactory.apply($p2.p,1);
        $fwd = new Pair<DateTimeAdjuster, DateTimeAdjuster>(begin, end);
    }
    ;

forwards returns [List<Pair<DateTimeAdjuster, DateTimeAdjuster>> listOut]
    :  lhs=list 'x' rhs=list
    {
        final int n = Math.min($lhs.listOut.size(), $rhs.listOut.size());

        List<Pair<DateTimeAdjuster, DateTimeAdjuster>> list
            = new LinkedList<Pair<DateTimeAdjuster, DateTimeAdjuster>>();
        for (int i = 0; i < n; i += 1) {
            DateTimeAdjuster begin = DateTimeAdjusterFactory.apply($lhs.listOut.get(i),1);
            DateTimeAdjuster end   = DateTimeAdjusterFactory.apply($rhs.listOut.get(i),1);
            list.add( new Pair<DateTimeAdjuster, DateTimeAdjuster>(begin, end) );
        }

        $listOut = list;
    }
    ;

// ON=TD+1D
// TN=ON+1D
// 3M=SP+3M
// SP=TD+2D


// [1M,2M,3M]+3M
// [1M,2M,3M] .* [4M,5M,6M]

// [1M,3M]
sequence returns [List<Period> listOut]
    :
    BEGL
    {
        final List<Period> list = new LinkedList<Period>();
    }
    a1=period     { list.add($a1.p); }
    (
    SEP a2=period { list.add($a2.p); }
    )*
    {
        $listOut = list;
    }
    ENDL
    ;

// (SP:3M:5Y) = [SP,3M,6M,...,57M,5Y]
loop returns [List<Period> listOut]
    @init
    {
        $listOut = new LinkedList<Period>();
    }
    @after {
        final Period start = $s.p;
        final Period step = ($step.ctx != null) ? $step.p : Period.months(1);
        final Period end = $end.p;
        for (Period j = start; !j.equals(end); j = j.plus(step).normalizedStandard()) {
            logger.debug("j="+j);
            $listOut.add(j);
        }
        $listOut.add(end);
    }
    :
    LPARAM
    s=yearMonth
    (DP step=yearMonth  )?
    DP end=yearMonth
    RPARAM
    ;



/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

WS      :   [ \t]+ ->  skip ;
// via import
