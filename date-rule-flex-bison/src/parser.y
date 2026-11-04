/* Grammar of the shared "schedule" example (see examples/schedule.txt in the project root), LALR(1).
 * The actions build the syntax tree of ast.h; semantics.c evaluates it. */
%define api.pure full
%define parse.error verbose
%param { yyscan_t scanner }
%parse-param { Parse *parse }

%code requires {
#include "ast.h"
typedef void *yyscan_t;
}

%code {
#include <stdio.h>
#include <string.h>

int yylex(YYSTYPE *value, yyscan_t scanner);
static void yyerror(yyscan_t scanner, Parse *parse, const char *message);

#define NEW(type) ((type *) arena_alloc(parse->schedule, sizeof(type)))
#define APPEND(type, list, node) do { type **end = &(list); while (*end) end = &(*end)->next; *end = (node); } while (0)

static Shift *shift(Parse *parse, ShiftKind kind) {
    Shift *s = NEW(Shift);
    s->kind = kind;
    return s;
}

static Anchor *anchor(Parse *parse, AnchorKind kind) {
    Anchor *a = NEW(Anchor);
    a->kind = kind;
    return a;
}

static Annual annual(AnnualKind kind, long n, int weekday, int month) {
    Annual a = { kind, n, weekday, month, 0, 0 };
    return a;
}

/* a series date; an error if the step isn't positive */
static int series(Parse *parse, Date *date, int start_out, Expr *first, Period step, Expr *last, int end_out) {
    if (step.months <= 0 && step.days <= 0 && step.business <= 0) {
        snprintf(parse->error, parse->size, "step must be positive");
        return -1;
    }
    *date = (Date) { .kind = D_SERIES, .first = first, .last = last, .step = step, .start_out = start_out, .end_out = end_out };
    return 0;
}

/* NUM - NUM - NUM: an error if it is no date */
static int fixed(Parse *parse, long year, long month, long day, int *date) {
    char text[64];
    snprintf(text, sizeof text, "%04ld-%02ld-%02ld", year, month, day);
    if (year > 9999 || month > 99 || day > 99 || date_parse_iso(text, date)) {
        snprintf(parse->error, parse->size, "not a date: %ld-%ld-%ld", year, month, day);
        return -1;
    }
    return 0;
}
}

%union {
    long num;
    int code;
    char *text;
    Period period;
    Annual annual;
    Date date;
    Expr *expr;
    Shift *shift;
    Anchor *anchor;
    Observe *observe;
    Item *item;
}

%token TODAY "T" TOM "TOM" SPOT "SPOT" IMM "IMM" NEXT "NEXT" PREV "PREV" BD "BD" LAST "LAST" EASTER "EASTER"
%token SOLARTERM "SOLARTERM" IF "IF" IN "IN"
%token <num> NUM ORDINAL ZONE
%token <code> MONTH WEEKDAY ROLL UNIT SEASON PERIOD
%token <text> PAIR EVENT

%type <period> period
%type <annual> annual fixed_annual
%type <date> date series
%type <expr> expr
%type <shift> shift
%type <anchor> anchor target targets select chosen
%type <num> count
%type <observe> observe observes
%type <item> item items
%type <code> weekdays weekday_set start_out end_out
%type <text> pair

%%

start: items                    { parse->schedule->items = $1; }
     ;

items: item
     | items ',' item           { $$ = $1; APPEND(Item, $$, $3); }
     ;

/* 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays */
item: expr                      { $$ = NEW(Item); $$->expr = $1; }
    | expr IF weekdays          { $$ = NEW(Item); $$->expr = $1; $$->weekdays = $3; }
    | expr IF '{' weekday_set '}' { $$ = NEW(Item); $$->expr = $1; $$->weekdays = $4; }
    ;

weekdays: WEEKDAY               { $$ = 1 << $1; }
        | weekdays WEEKDAY      { $$ = $1 | 1 << $2; }
        ;

weekday_set: WEEKDAY            { $$ = 1 << $1; }
           | weekday_set ',' WEEKDAY { $$ = $1 | 1 << $3; }
           ;

/* [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out. A series is
   a date with several values, like an event: the shifts after it apply to each element */
series: start_out expr ':' period ':' expr end_out
                                { if (series(parse, &$$, $1, $2, $4, $6, $7)) YYERROR; }
      | start_out expr ':' expr end_out
                                { if (series(parse, &$$, $1, $2, (Period) { 0, 1, 0 }, $4, $5)) YYERROR; }
      ;

start_out: '['                  { $$ = 0; }
         | ']'                  { $$ = 1; }
         ;

end_out: ']'                    { $$ = 0; }
       | '['                    { $$ = 1; }
       ;

/* shifts are applied from left to right */
expr: date                      { $$ = NEW(Expr); $$->date = $1; }
    | expr shift                { $$ = $1; APPEND(Shift, $$->shifts, $2); }
    ;

shift: '+' period               { $$ = shift(parse, S_PLUS); $$->period = $2; }
     | '-' period               { $$ = shift(parse, S_MINUS); $$->period = $2; }
     | '>' IMM                  { $$ = shift(parse, S_IMM); }
     | '>' SPOT pair            { $$ = shift(parse, S_SPOT); $$->pair = $3; }
     | '>' ROLL                 { $$ = shift(parse, S_ROLL); $$->roll = $2; }
     | '>' NEXT anchor          { $$ = shift(parse, S_NEXT); $$->count = 1; $$->anchor = $3; }
     | '>' NEXT ORDINAL anchor  { $$ = shift(parse, S_NEXT); $$->count = $3; $$->anchor = $4; }
     | '>' PREV anchor          { $$ = shift(parse, S_PREV); $$->count = 1; $$->anchor = $3; }
     | '>' PREV ORDINAL anchor  { $$ = shift(parse, S_PREV); $$->count = $3; $$->anchor = $4; }
     | '>' count select IN PERIOD
                                {
                                    $$ = shift(parse, S_SELECT);
                                    $$->count = $2 < 0 ? -$2 : $2;
                                    $$->from_end = $2 < 0;
                                    $$->anchor = $3;
                                    $$->within = $5;
                                }
     | '{' observes '}'         { $$ = shift(parse, S_OBSERVANCE); $$->observes = $2; }
     ;

pair: %empty                    { $$ = NULL; }
    | PAIR
    ;

/* {SAT - 1D, SUN + 1D}: the first item for the weekday of the date applies */
observes: observe
        | observes ',' observe  { $$ = $1; APPEND(Observe, $$, $3); }
        ;

observe: WEEKDAY '+' period     { $$ = NEW(Observe); $$->weekday = $1; $$->sign = 1; $$->period = $3; }
       | WEEKDAY '-' period     { $$ = NEW(Observe); $$->weekday = $1; $$->sign = -1; $$->period = $3; }
       ;

/* > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH: the n-th match in the period of the date, from its start or its end;
   counting from the end is a negative count (0 is an error anyway) */
count: ORDINAL
     | ORDINAL LAST             { $$ = -$1; }
     | LAST                     { $$ = -1; }
     ;

/* the targets of NEXT; an annual date starting with LAST only in braces, as LAST also starts the count */
select: chosen
      | '{' targets '}'         { $$ = anchor(parse, AT_ANY_OF); $$->targets = $2; }
      ;

/* NEXT 3RD FRI: the third Friday; NEXT {MON, FRI}: the nearest of a set */
anchor: target
      | '{' targets '}'         { $$ = anchor(parse, AT_ANY_OF); $$->targets = $2; }
      ;

targets: target
       | targets ',' target     { $$ = $1; APPEND(Anchor, $$, $3); }
       ;

target: chosen
      | LAST WEEKDAY MONTH      { $$ = anchor(parse, AT_ANNUAL); $$->annual = annual(LAST_WEEKDAY, 0, $2, $3); }
      ;

chosen: WEEKDAY                 { $$ = anchor(parse, AT_WEEKDAY); $$->weekday = $1; }
      | BD                      { $$ = anchor(parse, AT_BUSINESS_DAY); }
      | IMM                     { $$ = anchor(parse, AT_IMM); }
      | fixed_annual            { $$ = anchor(parse, AT_ANNUAL); $$->annual = $1; }
      | EVENT                   { $$ = anchor(parse, AT_EVENT); $$->event = $1; }
      ;

date: TODAY                     { $$ = (Date) { .kind = D_BASE }; }
    | TOM                       { $$ = (Date) { .kind = D_TOM }; }
    | SPOT pair                 { $$ = (Date) { .kind = D_SPOT, .pair = $2 }; }
    | NUM '-' NUM '-' NUM       { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $1, $3, $5, &$$.fixed)) YYERROR; }
    | NUM '.' NUM '.' NUM       { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $5, $3, $1, &$$.fixed)) YYERROR; }
    | NUM '/' NUM '/' NUM       { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $5, $1, $3, &$$.fixed)) YYERROR; }
    | annual                    { $$ = (Date) { .kind = D_ANNUAL, .annual = $1 }; }
    | EVENT                     { $$ = (Date) { .kind = D_EVENT, .event = $1 }; }
    | series
    ;

/* dates that recur every year, evaluated in the year of the base date */
annual: fixed_annual
      | LAST WEEKDAY MONTH      { $$ = annual(LAST_WEEKDAY, 0, $2, $3); }
      ;

fixed_annual: NUM MONTH         { $$ = annual(MONTH_DAY, $1, 0, $2); }
      | NUM WEEKDAY MONTH       { $$ = annual(NTH_WEEKDAY, $1, $2, $3); }
      | EASTER                  { $$ = annual(EASTER_SUNDAY, 0, 0, 0); }
      | SEASON MONTH            { $$ = annual(SUN_AT, 0, 0, $2); $$.equinox = $1; }
      | SEASON MONTH ZONE       { $$ = annual(SUN_AT, 0, 0, $2); $$.equinox = $1; $$.offset = $3; }
      | SOLARTERM NUM           { $$ = annual(SOLAR_TERM, $2, 0, 0); }
      | SOLARTERM NUM ZONE      { $$ = annual(SOLAR_TERM, $2, 0, 0); $$.offset = $3; }
      ;

period: NUM UNIT                {
                                    $$ = (Period) { 0, 0, 0 };
                                    switch ($2) {
                                    case 'D': $$.days = $1; break;
                                    case 'W': $$.days = 7 * $1; break;
                                    case 'M': $$.months = $1; break;
                                    case 'Q': $$.months = 3 * $1; break;
                                    case 'H': $$.months = 6 * $1; break;
                                    case 'Y': $$.months = 12 * $1; break;
                                    default: $$.business = $1;
                                    }
                                }
      ;

%%

static void yyerror(yyscan_t scanner, Parse *parse, const char *message) {
    (void) scanner;
    snprintf(parse->error, parse->size, "%s", message);
}
