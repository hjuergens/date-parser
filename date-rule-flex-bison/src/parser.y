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
    if (!last && end_out) {
        snprintf(parse->error, parse->size, "an open series ends with ]");
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
    Combine *combine;
    Operand *operand;
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
%type <item> item items picked_item
%type <num> pick
%type <code> condition
%type <expr> endpoint
%type <date> single_date
%type <combine> combine combines
%type <operand> operand operand_exprs
%type <shift> shifts
%type <anchor> annuals
%type <code> weekdays weekday_set start_out end_out
%type <text> pair

%%

start: items                    { parse->schedule->items = $1; }
     ;

items: item
     | items ',' item           { $$ = $1; APPEND(Item, $$, $3); }
     ;

/* 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays */
item: expr combines condition   { $$ = NEW(Item); $$->expr = $1; $$->combines = $2; $$->weekdays = $3; }
    | picked_item
    ;

/* 5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH: the 5th date after T of the item; it starts with a series or an event,
   where several dates come from */
picked_item: pick series shifts combines condition
                                { $$ = NEW(Item); $$->expr = NEW(Expr); $$->expr->date = $2; $$->expr->shifts = $3;
                                  $$->combines = $4; $$->weekdays = $5; $$->has_pick = 1;
                                  $$->pick = $1 < 0 ? -$1 : $1; $$->from_end = $1 < 0; }
           | pick EVENT shifts combines condition
                                { $$ = NEW(Item); $$->expr = NEW(Expr); $$->expr->date = (Date) { .kind = D_EVENT, .event = $2 };
                                  $$->expr->shifts = $3; $$->combines = $4; $$->weekdays = $5; $$->has_pick = 1;
                                  $$->pick = $1 < 0 ? -$1 : $1; $$->from_end = $1 < 0; }
           ;

/* 2ND LAST from the last one: a negative count (0 is an error anyway) */
pick: ORDINAL
    | ORDINAL LAST              { $$ = -$1; }
    ;

condition: %empty               { $$ = 0; }
         | IF weekdays          { $$ = $2; }
         | IF '{' weekday_set '}' { $$ = $3; }
         ;

/* A & B includes the dates of B, A \\ B excludes them; the shifts after it apply to the result */
combines: %empty                { $$ = NULL; }
        | combines combine      { $$ = $1; APPEND(Combine, $$, $2); }
        ;

combine: '&' operand shifts     { $$ = NEW(Combine); $$->include = 1; $$->operands = $2; $$->shifts = $3; }
       | '\\' operand shifts    { $$ = NEW(Combine); $$->include = 0; $$->operands = $2; $$->shifts = $3; }
       ;

shifts: %empty                  { $$ = NULL; }
      | shifts shift            { $$ = $1; APPEND(Shift, $$, $2); }
      ;

/* a date, a series or a set of dates */
operand: date                   { $$ = NEW(Operand); $$->expr = NEW(Expr); $$->expr->date = $1; }
       | '{' operand_exprs '}'  { $$ = $2; }
       ;

operand_exprs: expr             { $$ = NEW(Operand); $$->expr = $1; }
             | operand_exprs ',' expr { Operand *o = NEW(Operand); o->expr = $3; $$ = $1; APPEND(Operand, $$, o); }
             ;

weekdays: WEEKDAY               { $$ = 1 << $1; }
        | weekdays WEEKDAY      { $$ = $1 | 1 << $2; }
        ;

weekday_set: WEEKDAY            { $$ = 1 << $1; }
           | weekday_set ',' WEEKDAY { $$ = $1 | 1 << $3; }
           ;

/* [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out. A series is
   a date with several values, like an event: the shifts after it apply to each element */
series: start_out endpoint ':' period ':' endpoint end_out
                                { if (series(parse, &$$, $1, $2, $4, $6, $7)) YYERROR; }
      | start_out endpoint ':' period ':' end_out    /* an open series [SPOT : 1Q : ] */
                                { if (series(parse, &$$, $1, $2, $4, NULL, $6)) YYERROR; }
      | start_out endpoint ':' endpoint end_out
                                { if (series(parse, &$$, $1, $2, (Period) { 0, 1, 0 }, $4, $5)) YYERROR; }
      ;

/* an endpoint is one date, so it can't be a series: after the last ":" a "]" closes an open series */
endpoint: single_date           { $$ = NEW(Expr); $$->date = $1; }
        | endpoint shift        { $$ = $1; APPEND(Shift, $$->shifts, $2); }
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
     | '>' '{' annuals '}'      { $$ = shift(parse, S_DATES_IN_YEAR); $$->anchor = $3; }   /* > {1 FEB, 1 MAY} */
     | '{' observes '}'         { $$ = shift(parse, S_OBSERVANCE); $$->observes = $2; }
     ;

annuals: annual                 { $$ = anchor(parse, AT_ANNUAL); $$->annual = $1; }
       | annuals ',' annual     { Anchor *a = anchor(parse, AT_ANNUAL); a->annual = $3; $$ = $1; APPEND(Anchor, $$, a); }
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

date: single_date
    | series
    ;

/* a date that is not a series */
single_date: TODAY              { $$ = (Date) { .kind = D_BASE }; }
    | TOM                       { $$ = (Date) { .kind = D_TOM }; }
    | SPOT pair                 { $$ = (Date) { .kind = D_SPOT, .pair = $2 }; }
    | NUM '-' NUM '-' NUM       { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $1, $3, $5, &$$.fixed)) YYERROR; }
    | NUM '.' NUM '.' NUM       { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $5, $3, $1, &$$.fixed)) YYERROR; }
    | NUM '/' NUM '/' NUM       { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $5, $1, $3, &$$.fixed)) YYERROR; }
    | annual                    { $$ = (Date) { .kind = D_ANNUAL, .annual = $1 }; }
    | EVENT                     { $$ = (Date) { .kind = D_EVENT, .event = $1 }; }
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
