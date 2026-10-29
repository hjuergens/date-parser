/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * The rules of date-rule-antlr/.../DateSchedule.g4, but without actions: Java actions can't be
 * generated into Python, so a visitor (src/antlr_schedule/__init__.py) computes the values.
 */
grammar DateSchedule;

schedule
    : range EOF
    | expr (',' expr)* EOF
    ;

range
    : '(' first=expr ':' step=period ':' last=expr ')'
    ;

// shifts are applied from left to right
expr
    : date shift*
    ;

shift
    : '+' period                    # plus
    | '-' period                    # minus
    | '>' 'IMM'                     # imm
    | '>' 'SPOT' PAIR?              # spot
    ;

date
    : y=NUM '-' m=NUM '-' d=NUM     # iso
    | d=NUM '.' m=NUM '.' y=NUM     # german
    | m=NUM '/' d=NUM '/' y=NUM     # us
    ;

period
    : NUM UNIT
    ;

NUM  : [0-9]+ ;
UNIT : [DWMQYB] ;
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;   // currency pair such as EURUSD
WS   : [ \t\r\n]+ -> skip ;
