grammar List;

import TimeLexer;

/*
http://stackoverflow.com/questions/27405893/using-visitors-in-antlr4-in-a-simple-integer-list-grammar
https://github.com/ericharley/matlab-parser/blob/master/MATLAB.g4
*/

options {
    language = Java;
}

@header
{
    import java.util.List;
    import java.util.LinkedList;
}


/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

list returns [List<Integer> listOut] : LPARAM s=intExp (DP p=intExp)? DP e=intExp RPARAM
    {
        final int start = $s.value;
        final int step = $p.value;
        final int end = $e.value;
        List<Integer> list = new LinkedList<Integer>();
        for (int j = start; j <= end; j += step) { list.add(j); }
        $listOut = list;
    }
    ;

intExp returns [int value]
    : n=TWODIGITNUMBER { $value = Integer.parseInt($n.text); }
    ;

