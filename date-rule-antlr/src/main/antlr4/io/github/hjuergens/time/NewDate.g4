lexer grammar NewDate;

DATE	:	INT '/' INT '/' INT
    ;
    
DIGIT   : '0'..'9' 
    ;

INT :	'0'..'9'+
    ;

COMMENT
    :   ('//' ~('\n'|'\r')* '\r'? '\n'
    |   '/*' .*? '*/') -> skip
    ;
BlockComment
    : '/*' .*? '*/' -> skip
    ;

WS  :   ( ' '
        | '\t'
        | '\r'
        | '\n'
        ) -> skip
    ;

STRING
    :  '\'' ( ESC_SEQ | ~('\\'|'\'') )* '\''
    ;

fragment
HEX_DIGIT : ('0'..'9'|'a'..'f'|'A'..'F') ;

fragment
ESC_SEQ
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\')
    |   UNICODE_ESC
    |   OCTAL_ESC
    ;

fragment
OCTAL_ESC
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;

fragment
UNICODE_ESC
    :   '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;
