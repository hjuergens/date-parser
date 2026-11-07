/* Scanner of the shared "schedule" example. A word of capital letters is read as a whole and then classified:
 * a keyword, a month, a weekday, a roll, a unit letter or, with six letters, a currency pair. Keywords are
 * reserved, so EASTER or TISHRI is never a pair. */
%option reentrant bison-bridge noyywrap nounput noinput never-interactive
%option extra-type="Parse *"

%{
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ast.h"
#include "parser.h"

static int word(const char *text, YYSTYPE *value, Parse *parse);
static int number(const char *text, long *value, Parse *parse);
%}

%%

[ \t\r\n]+                  ;
[0-9]+(ST|ND|RD|TH)         {
                                size_t length = strlen(yytext);
                                char suffix[3] = { yytext[length - 2], yytext[length - 1], 0 };
                                yytext[length - 2] = 0;
                                if (number(yytext, &yylval->num, yyextra)) return YYerror;
                                long n = yylval->num;
                                const char *fits = n % 100 >= 11 && n % 100 <= 13 ? "TH"
                                    : n % 10 == 1 ? "ST" : n % 10 == 2 ? "ND" : n % 10 == 3 ? "RD" : "TH";
                                if (strcmp(suffix, fits)) {
                                    snprintf(yyextra->error, yyextra->size, "not an ordinal: %ld%s", n, suffix);
                                    return YYerror;
                                }
                                return ORDINAL;
                            }
[0-9]+                      { return number(yytext, &yylval->num, yyextra) ? YYerror : NUM; }
UTC[+-][0-9]+               {
                                if (number(yytext + 4, &yylval->num, yyextra)) return YYerror;
                                yylval->num *= yytext[3] == '-' ? -60 : 60;
                                return ZONE;
                            }
@[A-Z][A-Z0-9_]*            { yylval->text = arena_strdup(yyextra->schedule, yytext + 1); return EVENT; }
[A-Z]+                      { return word(yytext, yylval, yyextra); }
[-+>{},:\[\]./&\\]           { return yytext[0]; }
.                           { return YYUNDEF; }

%%

static const char *const MONTH_NAMES[MONTHS] = {
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    "MUHARRAM", "SAFAR", "RABIALAWWAL", "RABIALTHANI", "JUMADAALULA", "JUMADAALAKHIRA", "RAJAB", "SHABAN",
    "RAMADAN", "SHAWWAL", "DHULQADAH", "DHULHIJJAH",
    "NISAN", "IYAR", "SIVAN", "TAMMUZ", "AV", "ELUL", "TISHRI", "HESHVAN", "KISLEV", "TEVET", "SHEVAT", "ADAR",
    "ADARII",
    "ZHENGYUE", "ERYUE", "SANYUE", "SIYUE", "WUYUE", "LIUYUE", "QIYUE", "BAYUE", "JIUYUE", "SHIYUE", "DONGYUE",
    "LAYUE",
};
static const char *const WEEKDAY_NAMES[] = { "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", NULL };
static const char *const ROLL_NAMES[] = {
    "FOLLOWING", "PRECEDING", "MODFOLLOWING", "BOW", "EOW", "BOM", "EOM", "BOQ", "EOQ", "BOH", "EOH", "BOY", "EOY", NULL,
};
static const char *const PERIOD_NAMES[] = { "WEEK", "MONTH", "QUARTER", "HALFYEAR", "YEAR", NULL };
static const struct { const char *name; int token; } KEYWORDS[] = {
    { "T", TODAY }, { "TOM", TOM }, { "SPOT", SPOT }, { "IMM", IMM }, { "NEXT", NEXT }, { "PREV", PREV },
    { "BD", BD }, { "FD", FD }, { "FIX", FIX }, { "LAST", LAST }, { "EASTER", EASTER }, { "SOLARTERM", SOLARTERM }, { "IF", IF }, { "IN", IN },
    { NULL, 0 },
};

static int find(const char *const *names, const char *text) {
    for (int i = 0; names[i]; i++)
        if (!strcmp(names[i], text)) return i;
    return -1;
}

int month_code(const char *name) {
    for (int i = 0; i < MONTHS; i++)
        if (!strcmp(MONTH_NAMES[i], name)) return i;
    return -1;
}

static int word(const char *text, YYSTYPE *value, Parse *parse) {
    for (int i = 0; KEYWORDS[i].name; i++)
        if (!strcmp(KEYWORDS[i].name, text)) return KEYWORDS[i].token;
    if ((value->code = month_code(text)) >= 0) return MONTH;
    if ((value->code = find(WEEKDAY_NAMES, text)) >= 0) return WEEKDAY;
    if ((value->code = find(ROLL_NAMES, text)) >= 0) return ROLL;
    if ((value->code = find(PERIOD_NAMES, text)) >= 0) return PERIOD;
    if (!strcmp(text, "EQUINOX") || !strcmp(text, "SOLSTICE")) {
        value->code = text[0] == 'E';
        return SEASON;
    }
    if (strlen(text) == 1 && strchr("DWMQHYBF", text[0])) {
        value->code = text[0];
        return UNIT;
    }
    if (strlen(text) == 6) {
        value->text = arena_strdup(parse->schedule, text);
        return PAIR;
    }
    return YYUNDEF;
}

static int number(const char *text, long *value, Parse *parse) {
    *value = strtol(text, NULL, 10);
    if (*value > INT_MAX / 12) {
        snprintf(parse->error, parse->size, "number too large: %s", text);
        return -1;
    }
    return 0;
}
