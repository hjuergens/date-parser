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
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

file           : ( row NEWLINE )+ EOF
    ;

row : (futures | forwards)
    ;

list : sequence | loop
    ;

//BaseInterval(ReadableInstant start, ReadableDuration duration)
futures returns [List<Period> listOut] : list PLUS period
    ;

future returns [List<Period> listOut] : period PLUS period
    ;

//BaseInterval(ReadableInstant start, ReadableInstant end)
// 3Mx6M, (3M,4M,5M)x(6M,7M,8M)
forward returns [List<Period> listOut] :  period MULT period
    ;

forwards returns [List<Period> listOut] :  list MULT list
    ;

// ON=TD+1D
// TN=ON+1D
// 3M=SP+3M
// SP=TD+2D

// ?? 3M | +3M | SP+3M | +2D+3M
term : (PLUS | period PLUS)* period
    ;

// [1M,2M,3M]+3M
// [1M,2M,3M] .* [4M,5M,6M]


// [1M,3M]
sequence returns [List<Period> listOut] : BEGL p=period (SEP s=period)* ENDL
    {
        List<Period> list = new LinkedList<Period>();
        list.add($p.p);
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
