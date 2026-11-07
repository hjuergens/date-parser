grammar Dates;

options {
    language = Java;
}
/*
@header {
    package de.juergens.dateparser;
}
*/

// ***********************************************************************
// PARSER
// ***********************************************************************
dates  : (date NEWLINE)+ EOF ;
//input           : date+ EOF;

date : month=twodigit SLASH day=twodigit (SLASH year=twodigit+)?;

//dd: DIGIT? DIGIT ;

twodigit : (ZERODIGIT DIGITWOZERO?) | (DIGITWOZERO? (ZERODIGIT | DIGITWOZERO));

// ***********************************************************************
// TOKEN
// ***********************************************************************
ZERODIGIT   : [0];
DIGITWOZERO : [1-9];
NEWLINE : [\r\n]+ ;
SLASH   : '/' ;
//DELIMETER : ',' ;
//WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};