grammar SelectorByName;

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

selector
    :
    direction
    (
    dayWeek | month
    )
    ;

direction
    :
    PREVIOUSORSAME
    | NEXTORSAME
    | (PREVIOUSORSAME? PREVIOUS+)
    | (NEXTORSAME? NEXT+)
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

month
    :
    JAN
    | FEB
    | MAR
    | APR
    | MAY
    | JUN
    | JUL
    | AUG
    | SEP
    | OCT
    | NOV
    | DEC
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


MONDAY : 'monday';
TUESDAY : 'tuesday';
WEDNESDAY : 'wednesday';
THURSDAY : 'thursday';
FRIDAY : 'friday';
SATURDAY : 'saturday';
SUNDAY : 'sunday';

JAN : 'january';
FEB : 'february';
MAR : 'march';
APR : 'april';
MAY : 'may';
JUN : 'june';
JUL : 'july';
AUG : 'august';
SEP : 'september';
OCT : 'october';
NOV : 'november';
DEC : 'december';

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
