/*-***
 *
 * This file defines a stand-alone lexical analyzer for a subset of the Pascal
 * programming language.  This is the same lexer that will later be integrated
 * with a CUP-based parser.  Here the lexer is driven by the simple Java test
 * program in ./PascalLexerTest.java, q.v.  See 330 Lecture Notes 2 and the
 * Assignment 2 writeup for further discussion.
 *
 */


import java_cup.runtime.*;


%%
/*-*
 * LEXICAL FUNCTIONS:
 */

%cup
%line
%column
%unicode
%class PascalLexer

%{

/**
 * Return a new Symbol with the given token id, and with the current line and
 * column numbers.
 */
Symbol newSym(int tokenId) {
    return new Symbol(tokenId, yyline, yycolumn);
}

/**
 * Return a new Symbol with the given token id, the current line and column
 * numbers, and the given token value.  The value is used for tokens such as
 * identifiers and numbers.
 */
Symbol newSym(int tokenId, Object value) {
    return new Symbol(tokenId, yyline, yycolumn, value);
}

%}


/*-*
 * PATTERN DEFINITIONS:
 */
letter          = [A-Za-z]
digit           = [0-9]
alphanumeric    = {letter}|{digit}
other_id_char   = [_]
identifier      = {letter}({alphanumeric}|{other_id_char})*
integer         = {digit}*
real            = {integer}\.{integer}
char            = '.'
leftbrace       = \{
rightbrace      = \}
nonrightbrace   = [^}]
comment_body    = {nonrightbrace}*
comment         = {leftbrace}{comment_body}{rightbrace}
whitespace      = [ \n\t]


%%
/**
 * LEXICAL RULES:
 */
and             { return newSym(Sym.AND); }
array           { return newSym(Sym.ARRAY); }
begin           { return newSym(Sym.BEGIN); }
else            { return newSym(Sym.ELSE); }
end             { return newSym(Sym.END); }
if              { return newSym(Sym.IF); }
of              { return newSym(Sym.OF); }
or              { return newSym(Sym.OR); }
program         { return newSym(Sym.PROGRAM); }
procedure       { return newSym(Sym.PROCEDURE); }
then            { return newSym(Sym.THEN); }
type            { return newSym(Sym.TYPE); }
var             { return newSym(Sym.VAR); }
"*"             { return newSym(Sym.TIMES); }
"+"             { return newSym(Sym.PLUS); }
"-"             { return newSym(Sym.MINUS); }
"/"             { return newSym(Sym.DIVIDE); }
";"             { return newSym(Sym.SEMI); }
","             { return newSym(Sym.COMMA); }
"("             { return newSym(Sym.LEFT_PAREN); }
")"             { return newSym(Sym.RT_PAREN); }
"["             { return newSym(Sym.LEFT_BRKT); }
"]"             { return newSym(Sym.RT_BRKT); }
"="             { return newSym(Sym.EQ); }
"<"             { return newSym(Sym.GTR); }
">"             { return newSym(Sym.LESS); }
"<="            { return newSym(Sym.LESS_EQ); }
">="            { return newSym(Sym.GTR_EQ); }
"!="            { return newSym(Sym.NOT_EQ); }
":"             { return newSym(Sym.COLON); }
":="            { return newSym(Sym.ASSMNT); }
"."             { return newSym(Sym.DOT); }
{identifier}    { return newSym(Sym.IDENT, yytext()); }
{integer}       { return newSym(Sym.INT, new Integer(yytext())); }
{real}          { return newSym(Sym.REAL, new Double(yytext())); }
{char}          { return newSym(Sym.CHAR, new Character(yytext().charAt(1))); }
{comment}       { /* For this stand-alone lexer, print out comments. */
                  System.out.println("Recognized comment: " + yytext()); }
{whitespace}    { /* Ignore whitespace. */ }
.               { System.out.println("Illegal char, '" + yytext() +
                    "' line: " + yyline + ", column: " + yychar); }
<<EOF>>         { return newSym(Sym.EOF); }
