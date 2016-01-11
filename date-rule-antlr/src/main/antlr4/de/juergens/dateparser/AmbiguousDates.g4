grammar AmbiguousDates;

options {
    language = Java;
}

/*
@header {
    package de.juergens.dateparser;
}
*/

input           : date+ EOF;

date            : date_complete
                    {System.out.println("date_complete: " + $date_complete.str);}
                | date_day 
                    {System.out.println("date_day: " + $date_day.str);}
                | date_time 
                    {System.out.println("date_time: " + $date_time.str);}
                ;

date_complete   returns [String str]
                : date_day date_time 
                    {$str = String.format("\\%s \\%s", $date_day.str, $date_time.str);}
                ;

date_day        returns [String str]
                : day=INT ID year=INT 
                    {$str = String.format("\\%s \\%s \\%s", $day.text, $ID.text, $year.text);}
                ;

date_time       returns [String str]
                : TIME 
                    {$str = $TIME.text;}
                ;

INT     : ('0'..'9')+;
TIME    : INT 'h';
ID      : ('a'..'z'|'A'..'Z')+;
WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};