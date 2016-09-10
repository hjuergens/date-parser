lexer grammar TimeLexer;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

TWODIGITNUMBER  : NONNULLDIGIT?DIGIT ;

fragment DIGIT  : [0-9];
fragment NONNULLDIGIT  : '1'..'9' ;

YEAR : 'Y';
MONTH : 'M';
WEEK : 'W';
DAY : 'D';
QUARTER : 'Q';

BEGL : '[';
ENDL : ']';
LPARAM : '(';
RPARAM : ')';
SEP : ',';
DP: ':';

PLUS   : '+' ;
MULT   : '.*' ;

NEWLINE   : '\r'? '\n'; //'\r' '\n' | '\n' | '\r';

//WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};
//SPACE : (' ' | '\t' | '\r' | '\n') {skip();};


