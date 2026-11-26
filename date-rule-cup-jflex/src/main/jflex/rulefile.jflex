/*
 * Lexer of rule files, delivers the tokens of rulefile.cup. On a match of the same length the earlier rule wins, so
 * the keywords come before KEY, PAIR and CCY.
 */
package io.github.hjuergens.schedule.rules;

import java_cup.runtime.Symbol;

%%

%class RuleFileLexer
%public
%cupsym RuleFileSym
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

/* the words of declarations */
"rule"          { return symbol(RuleFileSym.L_RULE); }
"for"           { return symbol(RuleFileSym.L_FOR); }
"in"            { return symbol(RuleFileSym.L_IN); }
"except"        { return symbol(RuleFileSym.L_EXCEPT); }
"valid"         { return symbol(RuleFileSym.L_VALID); }
"from"          { return symbol(RuleFileSym.L_FROM); }
"to"            { return symbol(RuleFileSym.L_TO); }
"with"          { return symbol(RuleFileSym.L_WITH); }
"kind"          { return symbol(RuleFileSym.L_KIND); }
"session"       { return symbol(RuleFileSym.L_SESSION); }
"tz"            { return symbol(RuleFileSym.L_TZ); }
"precision"     { return symbol(RuleFileSym.L_PRECISION); }
"note"          { return symbol(RuleFileSym.L_NOTE); }
"cites"         { return symbol(RuleFileSym.L_CITES); }
"art"           { return symbol(RuleFileSym.L_ART); }
"url"           { return symbol(RuleFileSym.L_URL); }
"label"         { return symbol(RuleFileSym.L_LABEL); }
"weekend"       { return symbol(RuleFileSym.L_WEEKEND); }
"calendar"      { return symbol(RuleFileSym.L_CALENDAR); }
"ignore"        { return symbol(RuleFileSym.L_IGNORE); }
"at"            { return symbol(RuleFileSym.L_AT); }
"unless"        { return symbol(RuleFileSym.L_UNLESS); }
"closed" | "non_working" | "short_day" | "working_day" | "paid_no_rest" | "tourist" | "event" | "special_session"
  | "changed_hours"
                { return symbol(RuleFileSym.KIND_WORD, yytext()); }
"day" | "month" { return symbol(RuleFileSym.PRECISION_WORD, yytext()); }
"official" | "benchmark" | "practice" | "secondary"
                { return symbol(RuleFileSym.LABEL_WORD, yytext()); }

[0-9]+          { return symbol(RuleFileSym.NUM, Integer.valueOf(yytext())); }
[0-9]+ ("ST" | "ND" | "RD" | "TH")
                { return symbol(RuleFileSym.ORDINAL, yytext()); }
"ONE"           { return symbol(RuleFileSym.ONE); }
"OF"            { return symbol(RuleFileSym.OF); }
"AS"            { return symbol(RuleFileSym.AS); }
"T"             { return symbol(RuleFileSym.BASE); }
"TOM"           { return symbol(RuleFileSym.TOM); }
"SPOT"          { return symbol(RuleFileSym.SPOT); }
"FIX"           { return symbol(RuleFileSym.FIX); }
"IMM"           { return symbol(RuleFileSym.IMM); }
"NEXT"          { return symbol(RuleFileSym.NEXT); }
"PREV"          { return symbol(RuleFileSym.PREV); }
"NEAREST"       { return symbol(RuleFileSym.NEAREST); }
"CUTOFF"        { return symbol(RuleFileSym.CUTOFF); }
"IN"            { return symbol(RuleFileSym.IN); }
"POSTPONE"      { return symbol(RuleFileSym.POSTPONE); }
"MAX"           { return symbol(RuleFileSym.MAX); }
"CUMULATIVE"    { return symbol(RuleFileSym.CUMULATIVE); }
"ENDEND"        { return symbol(RuleFileSym.ENDEND); }
"ON"            { return symbol(RuleFileSym.ON); }
"LAST"          { return symbol(RuleFileSym.LAST); }
"BD"            { return symbol(RuleFileSym.BD); }
"FD"            { return symbol(RuleFileSym.FD); }
"HOLIDAY"       { return symbol(RuleFileSym.HOLIDAY); }
"NONHOLIDAY"    { return symbol(RuleFileSym.NONHOLIDAY); }
"HOLIDAYS"      { return symbol(RuleFileSym.HOLIDAYS); }
"EASTER"        { return symbol(RuleFileSym.EASTER); }
"ORTHODOX"      { return symbol(RuleFileSym.ORTHODOX); }
"EQUINOX" | "SOLSTICE"
                { return symbol(RuleFileSym.SEASON, yytext()); }
"SOLARTERM"     { return symbol(RuleFileSym.SOLARTERM); }
"HINDU"         { return symbol(RuleFileSym.HINDU); }
"BY"            { return symbol(RuleFileSym.BY); }
"UMMALQURA" | "SIGHTING" | "DIYANET"
                { return symbol(RuleFileSym.METHOD, yytext()); }
"FOLLOW"        { return symbol(RuleFileSym.FOLLOW); }
"IF"            { return symbol(RuleFileSym.IF); }
"OR"            { return symbol(RuleFileSym.OR); }
"AND"           { return symbol(RuleFileSym.AND); }
"NOT"           { return symbol(RuleFileSym.NOT); }
"DAY"           { return symbol(RuleFileSym.DAY); }
"IS"            { return symbol(RuleFileSym.IS); }
"BETWEEN"       { return symbol(RuleFileSym.BETWEEN); }
"EVERY"         { return symbol(RuleFileSym.EVERY); }
"FROM"          { return symbol(RuleFileSym.FROM); }
/* the periods of a selection; WEEK is Monday to Sunday */
"WEEK" | "MONTH" | "QUARTER" | "HALFYEAR" | "YEAR"
                { return symbol(RuleFileSym.PERIOD, yytext()); }
/* Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months */
"JAN" | "FEB" | "MAR" | "APR" | "MAY" | "JUN" | "JUL" | "AUG" | "SEP" | "OCT" | "NOV" | "DEC"
  | "MUHARRAM" | "SAFAR" | "RABIALAWWAL" | "RABIALTHANI" | "JUMADAALULA" | "JUMADAALAKHIRA" | "RAJAB" | "SHABAN"
  | "RAMADAN" | "SHAWWAL" | "DHULQADAH" | "DHULHIJJAH"
  | "NISAN" | "IYAR" | "SIVAN" | "TAMMUZ" | "AV" | "ELUL" | "TISHRI" | "HESHVAN" | "KISLEV" | "TEVET" | "SHEVAT"
  | "ADAR" | "ADARII"
  | "ZHENGYUE" | "ERYUE" | "SANYUE" | "SIYUE" | "WUYUE" | "LIUYUE" | "QIYUE" | "BAYUE" | "JIUYUE" | "SHIYUE"
  | "DONGYUE" | "LAYUE"
                { return symbol(RuleFileSym.MONTH, yytext()); }
"MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN"
                { return symbol(RuleFileSym.WEEKDAY, yytext()); }
"FOLLOWING" | "PRECEDING" | "MODFOLLOWING" | "MODPRECEDING" | "BOW" | "EOW" | "BOM" | "EOM" | "BOQ" | "EOQ" | "BOH"
  | "EOH" | "BOY" | "EOY"
                { return symbol(RuleFileSym.ROLL, yytext()); }
"UTC" [+-] [0-9]+
                { return symbol(RuleFileSym.ZONE, yytext()); }
[A-Z]{6}        { return symbol(RuleFileSym.PAIR, yytext()); }
/* a currency, only inside "unless {...}" */
[A-Z]{3}        { return symbol(RuleFileSym.CCY, yytext()); }
/* a reference to a rule, an event class or an event; the value is the name without the sigil */
"@" [A-Za-z] [A-Za-z0-9_]* ("." [A-Za-z0-9_]+)*
                { return symbol(RuleFileSym.EVENT, yytext().substring(1)); }
/* a scope: country:JP, region:AU-NSW, settlement_system:TARGET2 */
[a-z] [a-z_]* ":" [A-Z0-9] [A-Z0-9_]* ("-" [A-Z0-9] [A-Z0-9_]*)*
                { return symbol(RuleFileSym.SCOPE, yytext()); }
/* keys, event classes and named calendars */
[a-z] [a-z0-9_]* ("." [a-z] [a-z0-9_]*)*
                { return symbol(RuleFileSym.KEY, yytext()); }
/* the value is the text without the quotes */
\" [^\"]* \"    { return symbol(RuleFileSym.STRING, yytext().substring(1, yytext().length() - 1)); }
[DWMQHYBF]      { return symbol(RuleFileSym.UNIT, yytext()); }
"+"             { return symbol(RuleFileSym.PLUS); }
"-"             { return symbol(RuleFileSym.MINUS); }
">"             { return symbol(RuleFileSym.GT); }
"{"             { return symbol(RuleFileSym.LBRACE); }
"}"             { return symbol(RuleFileSym.RBRACE); }
"("             { return symbol(RuleFileSym.LPAREN); }
")"             { return symbol(RuleFileSym.RPAREN); }
"["             { return symbol(RuleFileSym.LBRACKET); }
"]"             { return symbol(RuleFileSym.RBRACKET); }
","             { return symbol(RuleFileSym.COMMA); }
":"             { return symbol(RuleFileSym.COLON); }
";"             { return symbol(RuleFileSym.SEMI); }
"="             { return symbol(RuleFileSym.EQ); }
"&"             { return symbol(RuleFileSym.AMP); }
"\\"            { return symbol(RuleFileSym.BACKSLASH); }
".."            { return symbol(RuleFileSym.DOTDOT); }
"."             { return symbol(RuleFileSym.DOT); }
"/"             { return symbol(RuleFileSym.SLASH); }
"#" [^\r\n]*    { /* a comment */ }
[ \t\r\n]+      { /* ignore */ }
[^]             { throw new IllegalArgumentException("illegal character '" + yytext() + "' at line " + (yyline + 1) + ", column " + (yycolumn + 1)); }
