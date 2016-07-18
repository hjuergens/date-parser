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
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

input           : (adjust NEWLINE)+ EOF;

chain : '^' adjust
    ;

adjust : shift | selector
    ;


shift  :  ( operator  period )*
    ;

operator : PLUS | MINUS
    ;


selector : ( direction (DAY|MONTH|QUARTER|DAY_OF_WEEK) )*
    ;

direction : (PREVIOUS+ | NEXT+) ORTHIS?
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


