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

input           : (expr NEWLINE)+ EOF;

expr    : period ( direction  period )* ;

direction : PLUS | MINUS
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
years   : twodigit YEAR
    ;

months : twodigit MONTH
    ;

weeks : twodigit WEEK
    ;

days : twodigit DAY
    ;

// cardinal
twodigit  : (DIGIT?DIGIT)
    ;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

DIGIT   : '0'..'9'
    ;


YEAR : 'Y';
MONTH : 'M';
WEEK : 'W';
DAY : 'D';


PLUS    : '+' ;
MINUS   : '-' ;
MULT    : '*' ;
DIV : '/' ;

NEWLINE   : '\r' '\n' | '\n' | '\r';

WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};
SPACE : (' ' | '\t' | '\r' | '\n') {skip();};


