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
[A-Z]{6}        { return symbol(ScheduleSym.PAIR, yytext()); }
[DWMQY]         { return symbol(ScheduleSym.UNIT, yytext()); }
"+"             { return symbol(ScheduleSym.PLUS); }
"-"             { return symbol(ScheduleSym.MINUS); }
">"             { return symbol(ScheduleSym.NEXT); }
"."             { return symbol(ScheduleSym.DOT); }
"/"             { return symbol(ScheduleSym.SLASH); }
","             { return symbol(ScheduleSym.COMMA); }
":"             { return symbol(ScheduleSym.COLON); }
"("             { return symbol(ScheduleSym.LPAREN); }
")"             { return symbol(ScheduleSym.RPAREN); }
[ \t\r\n]+      { /* ignore */ }
[^]             { throw new IllegalArgumentException("illegal character '" + yytext() + "' at column " + yycolumn); }
