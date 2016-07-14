grammar List;

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

list returns [List<Integer> listOut] : LPARAM s=intExp (':' p=intExp)? ':' e=intExp RPARAM
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
    : n=INT { $value = Integer.parseInt($n.text); }
;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

INT : DIGIT+ ;


fragment
DIGIT : [0-9] ;


NEWLINE   : '\r' '\n' | '\n' | '\r';

WS      : (' '|'\t'|'\f'|'\r'|'\n')+ -> skip;
SPACE : (' ' | '\t' | '\r' | '\n') {skip();};

BEGL : '[';
ENDL : ']';
LPARAM : '(';
RPARAM : ')';
SEP : ',';
