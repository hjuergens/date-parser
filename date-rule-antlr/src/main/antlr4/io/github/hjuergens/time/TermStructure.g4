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
    :  p1=period MULT p2=period
    {
        DateTimeAdjuster begin = DateTimeAdjusterFactory.apply($p1.p,1);
        DateTimeAdjuster end   = DateTimeAdjusterFactory.apply($p2.p,1);
        $fwd = new Pair<DateTimeAdjuster, DateTimeAdjuster>(begin, end);
    }
    ;

forwards returns [List<Pair<DateTimeAdjuster, DateTimeAdjuster>> listOut]
    @init {
        final int sn = Math.min($lhs.listOut.size(), $rhs.listOut.size());
    }
    :  lhs=list MULT rhs=list
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
    : BEGL p=period (SEP s=period)* ENDL
    {
        List<Period> list = new LinkedList<Period>();
        list.add($p.p);
        // TODO
        if($s.ctx != null) {
            list.add($s.p);
        }
        $listOut = list;
    }
    ;

// (SP:3M:5Y) = [SP,3M,6M,...,57M,5Y]
loop returns [List<Period> listOut] :  LPARAM s=period (DP p=period)? DP e=period RPARAM
    {
        final Period start = $s.p;
        System.out.println("start="+start);
        final Period step;
        if($p.ctx != null)
            step = $p.p;
        else step = new Period();
        System.out.println("step="+step);
        final Period end = $e.p;
        System.out.println("end="+end);
        List<Period> list = new LinkedList<Period>();
        for (Period j = start; !j.equals(end); j = j.plus(step)) { list.add(j); }
        list.add(end);
        $listOut = list;
    }
    ;



/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

WS      :   [ \t]+ ->  skip ;
// via import
