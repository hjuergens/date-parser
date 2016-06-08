grammar PeriodTerm;

options {
    language = Java;
}
/*
@header {
    package de.juergens.dateparser;
}
*/

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

input           : (adjust NEWLINE)+ EOF;

adjust : (shift | selector)
    ;


shift  :  ( operator  period )* ;


operator : PLUS | MINUS
    ;


selector : ( direction (QUARTER|WEDNESDAY) )*
    ;

direction : PREVIOUS* | NEXT*
    ;

direction3 returns [int z]
    : { $z = 0; }
      (PREVIOUS    { $z = $z-1; } )+ |  (NEXT    { $z = $z+1; } )+
    ;

/*
schedul    : period ( scheduling  period )* ;

scheduling : MULT | DIV
    ;
*/

// period, TODO consider The standard ISO format - PyYmMwWdDThHmMsS.
period : yearssub | monthssub | weekssub | dayssub
    ;

yearssub : years (monthssub | weekssub | dayssub)?
    ;

monthssub : months (weekssub | dayssub)?
    ;

weekssub : weeks (dayssub)?
    ;

dayssub : days
    ;

// units
years   : cardinal=twodigit YEAR // Labels become fields in the appropriate parse tree node class
    ;

months : twodigit MONTH
    ;

weeks : twodigit WEEK
    ;

days : twodigit DAY
    ;

// cardinal
twodigit  : (DIGIT?DIGIT) { System.out.println($DIGIT.text); }
    ;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

DIGIT   : '0'..'9'
    ;


// DIRECTION : PREVIOUS | NEXT ;

YEAR : 'Y';
MONTH : 'M';
WEEK : 'W';
DAY : 'D';
QUARTER : 'Q';

WEDNESDAY : 'wednesday'
    ;

NEXT     : '>' ;
PREVIOUS : '<' ;
PLUS   : '+' ;
MINUS  : '-' ;
MULT   : '*' ;
DIV    : '/' ;

NEWLINE   : '\r' '\n' | '\n' | '\r';

WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};
SPACE : (' ' | '\t' | '\r' | '\n') {skip();};


