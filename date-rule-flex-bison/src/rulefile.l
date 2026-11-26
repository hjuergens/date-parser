/* Scanner of rule files (rulefile.y). As in scanner.l, a word of capital letters is read as a whole and then
 * classified: a keyword, a month, a weekday, a roll, a unit letter, with six letters a currency pair, with three a
 * currency. A lowercase word is a keyword, a word of a kind, precision or label, or a key; with a colon a scope. */
%option reentrant bison-bridge noyywrap nounput noinput never-interactive
%option prefix="rf"
%option extra-type="RuleParse *"

%{
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ast.h"
#include "rulefile.h"
#define YYSTYPE RFSTYPE

static int upper(const char *text, RFSTYPE *value, RuleParse *parse);
static int lower(const char *text, RFSTYPE *value, RuleParse *parse);
static int number(const char *text, long *value, RuleParse *parse);
%}

%%

[ \t\r\n]+                  ;
#[^\n]*                     ;
[0-9]+(ST|ND|RD|TH)         {
                                size_t length = strlen(yytext);
                                char suffix[3] = { yytext[length - 2], yytext[length - 1], 0 };
                                yytext[length - 2] = 0;
                                if (number(yytext, &yylval->num, yyextra)) return RFerror;
                                long n = yylval->num;
                                const char *fits = n % 100 >= 11 && n % 100 <= 13 ? "TH"
                                    : n % 10 == 1 ? "ST" : n % 10 == 2 ? "ND" : n % 10 == 3 ? "RD" : "TH";
                                if (strcmp(suffix, fits)) {
                                    snprintf(yyextra->base.error, yyextra->base.size, "not an ordinal: %ld%s", n, suffix);
                                    return RFerror;
                                }
                                return ORDINAL;
                            }
[0-9]+                      { return number(yytext, &yylval->num, yyextra) ? RFerror : NUM; }
UTC[+-][0-9]+               {
                                if (number(yytext + 4, &yylval->num, yyextra)) return RFerror;
                                yylval->num *= yytext[3] == '-' ? -60 : 60;
                                return ZONE;
                            }
@[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)* {
                                /* the rule being read refers to it */
                                yylval->text = arena_strdup(yyextra->base.schedule, yytext + 1);
                                Name *event = arena_alloc(yyextra->base.schedule, sizeof(Name)), **end = &yyextra->events;
                                event->text = yylval->text;
                                while (*end) end = &(*end)->next;
                                *end = event;
                                yyextra->event_count++;
                                return EVENT;
                            }
[a-z][a-z_]*:[A-Z0-9][A-Z0-9_]*(-[A-Z0-9][A-Z0-9_]*)* {
                                yylval->text = arena_strdup(yyextra->base.schedule, yytext);
                                return SCOPE;
                            }
[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)* { return lower(yytext, yylval, yyextra); }
[A-Z]+                      { return upper(yytext, yylval, yyextra); }
\"[^"]*\"                   {
                                yytext[strlen(yytext) - 1] = 0;
                                yylval->text = arena_strdup(yyextra->base.schedule, yytext + 1);
                                return STRING;
                            }
".."                        { return DOTDOT; }
[-+>{},:\[\]./&\\;=()]       { return yytext[0]; }
.                           { return RFUNDEF; }

%%

static const char *const WEEKDAY_NAMES[] = { "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", NULL };
static const char *const ROLL_NAMES[] = {
    "FOLLOWING", "PRECEDING", "MODFOLLOWING", "BOW", "EOW", "BOM", "EOM", "BOQ", "EOQ", "BOH", "EOH", "BOY", "EOY",
    "MODPRECEDING", NULL,
};
static const char *const PERIOD_NAMES[] = { "WEEK", "MONTH", "QUARTER", "HALFYEAR", "YEAR", NULL };
/* the Hijri methods; the code is 0 for UMMALQURA, the only one that is no projection */
static const char *const METHOD_NAMES[] = { "UMMALQURA", "SIGHTING", "DIYANET", NULL };
static const struct { const char *name; int token; } UPPER[] = {
    { "T", TODAY }, { "TOM", TOM }, { "SPOT", SPOT }, { "IMM", IMM }, { "NEXT", NEXT }, { "PREV", PREV }, { "BD", BD },
    { "FD", FD }, { "FIX", FIX }, { "LAST", LAST }, { "EASTER", EASTER }, { "SOLARTERM", SOLARTERM }, { "IF", IF },
    { "IN", IN }, { "DAY", DAY }, { "ONE", ONE }, { "OF", OF }, { "AS", AS }, { "NEAREST", NEAREST },
    { "POSTPONE", POSTPONE }, { "MAX", MAX }, { "CUMULATIVE", CUMULATIVE }, { "CUTOFF", CUTOFF }, { "ENDEND", ENDEND },
    { "ON", ON },
    { "HOLIDAY", HOLIDAY }, { "NONHOLIDAY", NONHOLIDAY }, { "HOLIDAYS", HOLIDAYS }, { "ORTHODOX", ORTHODOX },
    { "HINDU", HINDU }, { "BY", BY }, { "FOLLOW", FOLLOW }, { "OR", OR }, { "AND", AND }, { "NOT", NOT }, { "IS", IS },
    { "BETWEEN", BETWEEN }, { "EVERY", EVERY }, { "FROM", FROM },
    { NULL, 0 },
};
static const struct { const char *name; int token; } LOWER[] = {
    { "rule", L_RULE }, { "for", L_FOR }, { "in", L_IN }, { "except", L_EXCEPT }, { "valid", L_VALID },
    { "from", L_FROM }, { "to", L_TO }, { "with", L_WITH }, { "kind", L_KIND }, { "session", L_SESSION },
    { "tz", L_TZ }, { "precision", L_PRECISION }, { "note", L_NOTE }, { "cites", L_CITES }, { "art", L_ART },
    { "url", L_URL }, { "label", L_LABEL }, { "weekend", L_WEEKEND }, { "calendar", L_CALENDAR },
    { "ignore", L_IGNORE }, { "at", L_AT }, { "unless", L_UNLESS }, { "cutoff", L_CUTOFF },
    { NULL, 0 },
};
static const char *const KIND_NAMES[] = { "closed", "non_working", "short_day", "working_day", "paid_no_rest", "tourist",
    "event", "special_session", "changed_hours", NULL };
static const char *const PRECISION_NAMES[] = { "day", "month", NULL };
static const char *const LABEL_NAMES[] = { "official", "benchmark", "practice", "secondary", NULL };

static int find(const char *const *names, const char *text) {
    for (int i = 0; names[i]; i++)
        if (!strcmp(names[i], text)) return i;
    return -1;
}

static int upper(const char *text, RFSTYPE *value, RuleParse *parse) {
    for (int i = 0; UPPER[i].name; i++)
        if (!strcmp(UPPER[i].name, text)) return UPPER[i].token;
    if ((value->code = month_code(text)) >= 0) return MONTH;
    if ((value->code = find(WEEKDAY_NAMES, text)) >= 0) return WEEKDAY;
    if ((value->code = find(ROLL_NAMES, text)) >= 0) return ROLL;
    if ((value->code = find(PERIOD_NAMES, text)) >= 0) return PERIOD;
    if ((value->code = find(METHOD_NAMES, text)) >= 0) return METHOD;
    if (!strcmp(text, "EQUINOX") || !strcmp(text, "SOLSTICE")) {
        value->code = text[0] == 'E';
        return SEASON;
    }
    if (strlen(text) == 1 && strchr("DWMQHYBF", text[0])) {
        value->code = text[0];
        return UNIT;
    }
    if (strlen(text) == 6 || strlen(text) == 3) {
        value->text = arena_strdup(parse->base.schedule, text);
        return strlen(text) == 6 ? PAIR : CCY;
    }
    return RFUNDEF;
}

static int lower(const char *text, RFSTYPE *value, RuleParse *parse) {
    for (int i = 0; LOWER[i].name; i++)
        if (!strcmp(LOWER[i].name, text)) return LOWER[i].token;
    value->text = arena_strdup(parse->base.schedule, text);
    if (find(KIND_NAMES, text) >= 0) return KIND_WORD;
    if (find(PRECISION_NAMES, text) >= 0) return PRECISION_WORD;
    if (find(LABEL_NAMES, text) >= 0) return LABEL_WORD;
    return KEY;
}

static int number(const char *text, long *value, RuleParse *parse) {
    *value = strtol(text, NULL, 10);
    if (*value > INT_MAX / 12) {
        snprintf(parse->base.error, parse->base.size, "number too large: %s", text);
        return -1;
    }
    return 0;
}
