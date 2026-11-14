grammar DateRule;

import Cardinal, Unit;

duration: cardinal unit ;

//NEWLINE : [\r\n]+ ;
INT     : [0-9]+ ;
//WS : [ \t\r\n]+ -> skip ;
COMMENT
:
'/*' .*? '*/'
-> channel(HIDDEN) // match anything between /* and */
;
WS :
[ \r\t\u000C\n]+ -> channel(HIDDEN)
;

