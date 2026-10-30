/*
 * Lexer of the shared "schedule" example, delivers the tokens of schedule.cup.
 */
package io.github.hjuergens.schedule;

import java_cup.runtime.Symbol;

%%

%class ScheduleLexer
%public
%cupsym ScheduleSym
%cup
%unicode
%line
%column

%{
private Symbol symbol(int type) {
    return new Symbol(type, yyline, yycolumn);
}

private Symbol symbol(int type, Object value) {
    return new Symbol(type, yyline, yycolumn, value);
}
%}

%%

[0-9]+          { return symbol(ScheduleSym.NUM, Integer.valueOf(yytext())); }
"IMM"           { return symbol(ScheduleSym.IMM); }
"SPOT"          { return symbol(ScheduleSym.SPOT); }
"T"             { return symbol(ScheduleSym.BASE); }
"TOM"           { return symbol(ScheduleSym.TOM); }
"NEXT"          { return symbol(ScheduleSym.NEXT); }
"PREV"          { return symbol(ScheduleSym.PREV); }
"BD"            { return symbol(ScheduleSym.BD); }
"LAST"          { return symbol(ScheduleSym.LAST); }
"JAN" | "FEB" | "MAR" | "APR" | "MAY" | "JUN" | "JUL" | "AUG" | "SEP" | "OCT" | "NOV" | "DEC"
                { return symbol(ScheduleSym.MONTH, yytext()); }
"MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN"
                { return symbol(ScheduleSym.WEEKDAY, yytext()); }
"FOLLOWING" | "PRECEDING" | "MODFOLLOWING" | "BOM" | "EOM" | "BOY" | "EOY"
                { return symbol(ScheduleSym.ROLL, yytext()); }
/* before the pair: on a match of the same length the earlier rule wins */
"EASTER"        { return symbol(ScheduleSym.EASTER); }
[A-Z]{6}        { return symbol(ScheduleSym.PAIR, yytext()); }
[DWMQYB]        { return symbol(ScheduleSym.UNIT, yytext()); }
"+"             { return symbol(ScheduleSym.PLUS); }
"-"             { return symbol(ScheduleSym.MINUS); }
">"             { return symbol(ScheduleSym.GT); }
"{"             { return symbol(ScheduleSym.LBRACE); }
"}"             { return symbol(ScheduleSym.RBRACE); }
"."             { return symbol(ScheduleSym.DOT); }
"/"             { return symbol(ScheduleSym.SLASH); }
","             { return symbol(ScheduleSym.COMMA); }
":"             { return symbol(ScheduleSym.COLON); }
"("             { return symbol(ScheduleSym.LPAREN); }
")"             { return symbol(ScheduleSym.RPAREN); }
[ \t\r\n]+      { /* ignore */ }
[^]             { throw new IllegalArgumentException("illegal character '" + yytext() + "' at column " + yycolumn); }
