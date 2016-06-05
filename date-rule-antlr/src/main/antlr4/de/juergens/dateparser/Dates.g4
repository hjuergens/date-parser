grammar Dates;

options {
    language = Java;
}
/*
@header {
    package de.juergens.dateparser;
}
*/

//dates  : date+ EOF ;
input           : date+ EOF;

date : month=twodigit SLASH day=twodigit (SLASH year=twodigit+)? '\n';

twodigit : DIGIT? DIGIT ;

INT     : ('0'..'9')+;
NEWLINE : [\r\n]+ ;
//INT     : [0-9]+ ;
DIGIT   : [0-9] ;
//TWODIGIT: DIGIT? DIGIT ;
SLASH   : '/' ;
DELIMETER : ',' ;
WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};