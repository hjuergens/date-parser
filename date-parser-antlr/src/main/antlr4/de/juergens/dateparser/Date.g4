grammar Date;

date: month=DIGIT? DIGIT '/' day=INT ( year='/' INT )? ; //{ year==null ? throw new java.lang.IllegalArgumentException("date_day: " + $year) : $year } ;

//date2: (INT | DATENUM) '/' (INT | DATENUM) ('/' (INT | DATENUM) )? ;

prog:	(expr NEWLINE)* ;
expr:	expr ('*'|'/') expr
    |	expr ('+'|'-') expr
    |	INT
    |	'(' expr ')'
    ;


NEWLINE : [\r\n]+ ;
INT     : [0-9]+ ;
DIGIT   : [0-9] ;
