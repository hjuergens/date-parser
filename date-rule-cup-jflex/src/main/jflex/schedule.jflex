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
/* 1ST, 2ND, 3RD, 4TH: the longest match wins over the number */
[0-9]+ ("ST" | "ND" | "RD" | "TH")
                { return symbol(ScheduleSym.ORDINAL, yytext()); }
"IMM"           { return symbol(ScheduleSym.IMM); }
"SPOT"          { return symbol(ScheduleSym.SPOT); }
"T"             { return symbol(ScheduleSym.BASE); }
"TOM"           { return symbol(ScheduleSym.TOM); }
"NEXT"          { return symbol(ScheduleSym.NEXT); }
"PREV"          { return symbol(ScheduleSym.PREV); }
"BD"            { return symbol(ScheduleSym.BD); }
"LAST"          { return symbol(ScheduleSym.LAST); }
"IF"            { return symbol(ScheduleSym.IF); }
"IN"            { return symbol(ScheduleSym.IN); }
/* the periods of a selection; WEEK is Monday to Sunday */
"WEEK" | "MONTH" | "QUARTER" | "HALFYEAR" | "YEAR"
                { return symbol(ScheduleSym.PERIOD, yytext()); }
/* Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months */
"JAN" | "FEB" | "MAR" | "APR" | "MAY" | "JUN" | "JUL" | "AUG" | "SEP" | "OCT" | "NOV" | "DEC"
  | "MUHARRAM" | "SAFAR" | "RABIALAWWAL" | "RABIALTHANI" | "JUMADAALULA" | "JUMADAALAKHIRA" | "RAJAB" | "SHABAN"
  | "RAMADAN" | "SHAWWAL" | "DHULQADAH" | "DHULHIJJAH"
  | "NISAN" | "IYAR" | "SIVAN" | "TAMMUZ" | "AV" | "ELUL" | "TISHRI" | "HESHVAN" | "KISLEV" | "TEVET" | "SHEVAT"
  | "ADAR" | "ADARII"
  | "ZHENGYUE" | "ERYUE" | "SANYUE" | "SIYUE" | "WUYUE" | "LIUYUE" | "QIYUE" | "BAYUE" | "JIUYUE" | "SHIYUE"
  | "DONGYUE" | "LAYUE"
                { return symbol(ScheduleSym.MONTH, yytext()); }
"MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN"
                { return symbol(ScheduleSym.WEEKDAY, yytext()); }
"FOLLOWING" | "PRECEDING" | "MODFOLLOWING" | "BOW" | "EOW" | "BOM" | "EOM" | "BOQ" | "EOQ" | "BOH" | "EOH" | "BOY" | "EOY"
                { return symbol(ScheduleSym.ROLL, yytext()); }
/* the months above and EASTER come before the pair: on a match of the same length the earlier rule wins */
"EASTER"        { return symbol(ScheduleSym.EASTER); }
"EQUINOX" | "SOLSTICE"
                { return symbol(ScheduleSym.SEASON, yytext()); }
"SOLARTERM"     { return symbol(ScheduleSym.SOLARTERM); }
/* the time zone of an equinox, solstice or solar term */
"UTC" [+-] [0-9]+
                { return symbol(ScheduleSym.ZONE, yytext()); }
[A-Z]{6}        { return symbol(ScheduleSym.PAIR, yytext()); }
/* a named event such as @FOMC; the value is the name without the sigil */
"@" [A-Z] [A-Z0-9_]*
                { return symbol(ScheduleSym.EVENT, yytext().substring(1)); }
[DWMQHYB]       { return symbol(ScheduleSym.UNIT, yytext()); }
"+"             { return symbol(ScheduleSym.PLUS); }
"-"             { return symbol(ScheduleSym.MINUS); }
">"             { return symbol(ScheduleSym.GT); }
"{"             { return symbol(ScheduleSym.LBRACE); }
"}"             { return symbol(ScheduleSym.RBRACE); }
"."             { return symbol(ScheduleSym.DOT); }
"/"             { return symbol(ScheduleSym.SLASH); }
","             { return symbol(ScheduleSym.COMMA); }
":"             { return symbol(ScheduleSym.COLON); }
"["             { return symbol(ScheduleSym.LBRACKET); }
"]"             { return symbol(ScheduleSym.RBRACKET); }
[ \t\r\n]+      { /* ignore */ }
[^]             { throw new IllegalArgumentException("illegal character '" + yytext() + "' at column " + yycolumn); }
