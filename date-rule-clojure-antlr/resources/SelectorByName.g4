grammar SelectorByName;

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

selector
    :
    direction
    (
    dayWeek
    )
    ;

direction
    :
    (PREVIOUS+ PREVIOUSORSAME?) | (NEXT+ NEXTORSAME?) | PREVIOUSORSAME | NEXTORSAME
    ;

dayWeek
    :
    MONDAY
    | TUESDAY
    | WEDNESDAY
    | THURSDAY
    | FRIDAY
    | SATURDAY
    | SUNDAY
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

//DAY_OF_WEEK : 'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'
//    ;
MONDAY : 'monday';
TUESDAY : 'tuesday';
WEDNESDAY : 'wednesday';
THURSDAY : 'thursday';
FRIDAY : 'friday';
SATURDAY : 'saturday';
SUNDAY : 'sunday';

NEXT       : '>' ;
NEXTORSAME : '>=' ;
PREVIOUS       : '<' ;
PREVIOUSORSAME : '<=' ;
PLUS   : '+' ;
MINUS  : '-' ;
MULT   : '*' ;
DIV    : '/' ;

NEWLINE   : '\r' '\n' | '\n' | '\r';

WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};
SPACE : (' ' | '\t' | '\r' | '\n') {skip();};
