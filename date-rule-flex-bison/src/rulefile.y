/* Rule files: the schedule language of parser.y inside declarations with scope, validity, kind and provenance, the same
 * language as date-rule-antlr-java's RuleFile.g4 and date-rule-lark's rules.lark (shared cases: examples/rules.txt).
 * LALR(1) without conflicts, lists left recursive. The actions build the syntax tree of ast.h, with the extensions of
 * rule files; catalog.c evaluates the declarations. One start symbol reads declarations or one schedule (the first
 * token tells them apart). The prefix rf keeps this parser and its scanner apart from those of the schedule grammar. */
%define api.pure full
%define api.prefix {rf}
%define parse.error verbose
%param { yyscan_t scanner }
%parse-param { RuleParse *parse }

%code requires {
#include "ast.h"
typedef void *yyscan_t;

/* what the scanner and the parser of rule files share; event_count and estimated count the @references and the Hijri
 * methods other than UMMALQURA in the order the scanner and the parser reach them, so a rule takes those since its
 * start (the lookahead after its ";" is never one of them) */
typedef struct {
    Parse base;
    Name *events;
    int event_count, estimated;
} RuleParse;

/* UTC+9 (offset in minutes) or an IANA time zone */
typedef struct {
    int offset;
    const char *name;
} ZoneSpec;

typedef struct {
    int has_from, from, has_to, to;
} Validity;
}

%code {
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int rflex(RFSTYPE *value, yyscan_t scanner);
static void rferror(yyscan_t scanner, RuleParse *parse, const char *message);

#define NEW(type) ((type *) arena_alloc(parse->base.schedule, sizeof(type)))
#define APPEND(type, list, node) do { type **end = &(list); while (*end) end = &(*end)->next; *end = (node); } while (0)
#define FAIL(...) do { snprintf(parse->base.error, parse->base.size, __VA_ARGS__); YYERROR; } while (0)

static Shift *shift(RuleParse *parse, ShiftKind kind) {
    Shift *s = NEW(Shift);
    s->kind = kind;
    return s;
}

static Anchor *anchor(RuleParse *parse, AnchorKind kind, Calendar *on) {
    Anchor *a = NEW(Anchor);
    a->kind = kind;
    a->on = on;
    return a;
}

static Annual annual(AnnualKind kind, long n, int weekday, int month) {
    Annual a = { kind, n, weekday, month, 0, 0, NULL };
    return a;
}

static Condition *condition(RuleParse *parse, ConditionKind kind) {
    Condition *c = NEW(Condition);
    c->kind = kind;
    c->month = -1;
    return c;
}

static Calendar *calendar(RuleParse *parse, CalendarKind kind, const char *name) {
    Calendar *c = NEW(Calendar);
    c->kind = kind;
    c->name = name;
    return c;
}

static Name *name(RuleParse *parse, const char *text) {
    Name *n = NEW(Name);
    n->text = text;
    return n;
}

/* the names of the events from the first-th on */
static Name *references(RuleParse *parse, int first) {
    Name *list = NULL;
    int i = 0;
    for (Name *e = parse->events; e; e = e->next, i++)
        if (i >= first) APPEND(Name, list, name(parse, e->text));
    return list;
}

/* NUM - NUM - NUM: an error if it is no date */
static int fixed(RuleParse *parse, long year, long month, long day, int *date) {
    char text[64];
    snprintf(text, sizeof text, "%04ld-%02ld-%02ld", year, month, day);
    if (year > 9999 || month > 99 || day > 99 || date_parse_iso(text, date)) {
        snprintf(parse->base.error, parse->base.size, "not a date: %ld-%ld-%ld", year, month, day);
        return -1;
    }
    return 0;
}

/* a series date; an error if the step isn't positive */
static int series(RuleParse *parse, Date *date, int start_out, Expr *first, Period step, Expr *last, int end_out) {
    if (step.months <= 0 && step.days <= 0 && step.business <= 0 && step.fixing <= 0) {
        snprintf(parse->base.error, parse->base.size, "step must be positive");
        return -1;
    }
    if (!last && end_out) {
        snprintf(parse->base.error, parse->base.size, "an open series ends with ]");
        return -1;
    }
    *date = (Date) { .kind = D_SERIES, .first = first, .last = last, .step = step, .start_out = start_out, .end_out = end_out };
    return 0;
}

/* the attributes of b in a, where b has one */
static void merge(Declaration *a, const Declaration *b) {
    if (b->kind_of_day) a->kind_of_day = b->kind_of_day;
    if (b->precision) a->precision = b->precision;
    if (b->note) a->note = b->note;
    if (b->session[0]) memcpy(a->session, b->session, sizeof a->session);
}

/* an item: the expression, its combines and IF; a pick around it if count isn't 0 (negative: from the end) */
static Item *item(RuleParse *parse, Expr *expr, Combine *combines, Condition *condition, Shift *if_shifts, long count) {
    Item *i = NEW(Item);
    i->expr = expr, i->combines = combines, i->condition = condition, i->if_shifts = if_shifts;
    if (count) i->has_pick = 1, i->pick = count < 0 ? -count : count, i->from_end = count < 0;
    return i;
}
}

%union {
    long num;
    int code;
    char *text;
    Period period;
    Annual annual;
    ZoneSpec zone;
    Validity validity;
    Date date;
    Expr *expr;
    Shift *shift;
    Anchor *anchor;
    Observe *observe;
    Item *item;
    Combine *combine;
    Operand *operand;
    Condition *condition;
    Calendar *calendar;
    Declaration *declaration;
    Alternative *alternative;
    Name *name;
    Citation *citation;
}

/* the words of declarations */
%token L_RULE "rule" L_FOR "for" L_IN "in" L_EXCEPT "except" L_VALID "valid" L_FROM "from" L_TO "to" L_WITH "with"
%token L_KIND "kind" L_SESSION "session" L_TZ "tz" L_PRECISION "precision" L_NOTE "note" L_CITES "cites" L_ART "art"
%token L_URL "url" L_LABEL "label" L_WEEKEND "weekend" L_CALENDAR "calendar" L_IGNORE "ignore" L_AT "at"
%token L_UNLESS "unless"
%token <text> KIND_WORD "kind of day" PRECISION_WORD "day or month" LABEL_WORD "kind of source"
/* the words of the schedule language */
%token TODAY "T" TOM "TOM" SPOT "SPOT" IMM "IMM" NEXT "NEXT" PREV "PREV" BD "BD" LAST "LAST" EASTER "EASTER"
%token FD "FD" FIX "FIX" SOLARTERM "SOLARTERM" IF "IF" IN "IN" DAY "DAY"
%token ONE "ONE" OF "OF" AS "AS" NEAREST "NEAREST" POSTPONE "POSTPONE" MAX "MAX" CUMULATIVE "CUMULATIVE" CUTOFF "CUTOFF"
%token ENDEND "ENDEND" ON "ON" HOLIDAY "HOLIDAY" NONHOLIDAY "NONHOLIDAY" HOLIDAYS "HOLIDAYS" ORTHODOX "ORTHODOX"
%token HINDU "HINDU" BY "BY" FOLLOW "FOLLOW" OR "OR" AND "AND" NOT "NOT" IS "IS" BETWEEN "BETWEEN" EVERY "EVERY"
%token FROM "FROM" DOTDOT ".."
%token <num> NUM ORDINAL ZONE
%token <code> MONTH WEEKDAY ROLL UNIT SEASON PERIOD METHOD
%token <text> PAIR CCY EVENT SCOPE KEY STRING

%type <declaration> declarations declaration body attributes attributes_opt attribute
%type <alternative> alternatives alternative
%type <text> name time closes_opt tz_opt article_opt url_opt pair_opt ccys method_opt
%type <name> scopes except_opt
%type <num> rule_start count pick iso_date
%type <validity> validity
%type <citation> citations citations_opt citation
%type <calendar> calendar cal_term on on_opt
%type <item> items item
%type <expr> expr endpoint subject
%type <date> date single_date series
%type <shift> shift moves
%type <combine> combines combine
%type <operand> operand operand_exprs
%type <condition> if_condition condition cond_and cond_not cond_atom
%type <anchor> anchor target targets select chosen annuals
%type <observe> observe observes
%type <annual> annual fixed_annual
%type <zone> zone zone_opt
%type <period> period
%type <code> weekday_set weekday_list weekday_commas start_out end_out endend_opt cumulative_opt month_opt

%%

/* a rule file, or one schedule (for catalog_schedule) */
start: declarations                     { parse->base.schedule->declarations = $1; }
     | items                            { parse->base.schedule->items = $1; }
     ;

declarations: %empty                    { $$ = NULL; }
            | declarations declaration  { $$ = $1; APPEND(Declaration, $$, $2); }
            ;

/* ================= declarations ================= */

/* the number of events and of estimated methods before the rule, so its own are those after */
rule_start: "rule"                      { $$ = parse->event_count * 100000L + parse->estimated; }
          ;

declaration: rule_start name "for" name "in" scopes except_opt validity '=' body attributes_opt citations_opt ';'
        {
            Declaration *d = $10;
            d->kind = DECL_RULE, d->key = $2, d->event_class = $4, d->include = $6, d->exclude = $7;
            d->has_from = $8.has_from, d->valid_from = $8.from, d->has_to = $8.has_to, d->valid_to = $8.to;
            if ($11) merge(d, $11);
            d->citations = $12;
            d->references = references(parse, (int) ($1 / 100000));
            d->estimated = parse->estimated > $1 % 100000;
            $$ = d;
        }
    | "weekend" SCOPE '=' weekday_set ';'
        { $$ = NEW(Declaration); $$->kind = DECL_WEEKEND, $$->key = $2, $$->weekdays = $4; }
    | "weekend" SCOPE '=' weekday_set "from" iso_date ';'
        { $$ = NEW(Declaration); $$->kind = DECL_WEEKEND, $$->key = $2, $$->weekdays = $4, $$->has_from = 1, $$->valid_from = (int) $6; }
    | "calendar" KEY '=' calendar ';'   { $$ = NEW(Declaration); $$->kind = DECL_CALENDAR, $$->key = $2, $$->calendar = $4; }
    ;

/* exactly one of the alternatives will happen: ONE OF {item AS name, ...}; a new declaration with items or alternatives */
body: "ONE" "OF" '{' alternatives '}'   { $$ = NEW(Declaration); $$->alternatives = $4; }
    | items                             { $$ = NEW(Declaration); $$->items = $1; }
    ;

alternatives: alternative
            | alternatives ',' alternative
                {
                    for (Alternative *a = $1; a; a = a->next)
                        if (!strcmp(a->name, $3->name)) FAIL("ONE OF: name %s twice", $3->name);
                    $$ = $1;
                    APPEND(Alternative, $$, $3);
                }
            ;

alternative: item "AS" name             { $$ = NEW(Alternative); $$->items = $1; $$->name = $3; }
           ;

/* a key or an event class; the words of kinds, precisions and labels are tokens, so they are names here as well */
name: KEY | KIND_WORD | PRECISION_WORD | LABEL_WORD
    ;

scopes: SCOPE                           { $$ = name(parse, $1); }
      | scopes ',' SCOPE                { $$ = $1; APPEND(Name, $$, name(parse, $3)); }
      ;

except_opt: %empty                      { $$ = NULL; }
          | "except" scopes             { $$ = $2; }
          ;

validity: %empty                        { $$ = (Validity) { 0, 0, 0, 0 }; }
        | "valid"                       { $$ = (Validity) { 0, 0, 0, 0 }; }
        | "valid" "from" iso_date       { $$ = (Validity) { 1, (int) $3, 0, 0 }; }
        | "valid" "to" iso_date         { $$ = (Validity) { 0, 0, 1, (int) $3 }; }
        | "valid" "from" iso_date "to" iso_date { $$ = (Validity) { 1, (int) $3, 1, (int) $5 }; }
        ;

iso_date: NUM '-' NUM '-' NUM           { int d; if (fixed(parse, $1, $3, $5, &d)) YYERROR; $$ = d; }
        ;

attributes_opt: %empty                  { $$ = NULL; }
              | "with" attributes       { $$ = $2; }
              ;

attributes: attribute
          | attributes ',' attribute    { $$ = $1; merge($$, $3); }
          ;

attribute: "kind" '=' KIND_WORD         { $$ = NEW(Declaration); $$->kind_of_day = $3; }
         | "session" time closes_opt tz_opt
                                        { $$ = NEW(Declaration); $$->session[0] = $2; $$->session[1] = $3 ? $3 : $2; $$->session[2] = $4; }
         | "precision" '=' PRECISION_WORD { $$ = NEW(Declaration); $$->precision = $3; }
         | "note" STRING                { $$ = NEW(Declaration); $$->note = $2; }
         ;

time: NUM ':' NUM                       { $$ = arena_alloc(parse->base.schedule, 8); snprintf($$, 8, "%02ld:%02ld", $1 % 100, $3 % 100); }
    ;

closes_opt: %empty                      { $$ = NULL; }
          | '-' time                    { $$ = $2; }
          ;

tz_opt: %empty                          { $$ = NULL; }
      | "tz" STRING                     { $$ = $2; }
      ;

citations_opt: %empty                   { $$ = NULL; }
             | "cites" citations        { $$ = $2; }
             ;

citations: citation
         | citations ',' citation       { $$ = $1; APPEND(Citation, $$, $3); }
         ;

citation: STRING article_opt url_opt "label" LABEL_WORD
                                        { $$ = NEW(Citation); $$->source = $1, $$->article = $2, $$->url = $3, $$->label = $5; }
        ;

article_opt: %empty                     { $$ = NULL; }
           | "art" STRING               { $$ = $2; }
           ;

url_opt: %empty                         { $$ = NULL; }
       | "url" STRING                   { $$ = $2; }
       ;

/* ================= calendars ================= */

/* a business day in every term of "&" */
calendar: cal_term
        | calendar '&' cal_term
            {
                if ($1->kind != CAL_COMBINED) { Calendar *c = calendar(parse, CAL_COMBINED, NULL); c->terms = $1; $1 = c; }
                $$ = $1;
                APPEND(Calendar, $$->terms, $3);
            }
        ;

/* USD on T+1 does not count, unless for MXN */
cal_term: SCOPE                         { $$ = calendar(parse, CAL_SCOPE, $1); }
        | SCOPE "ignore" "at" "T" '+' NUM
                                        { $$ = calendar(parse, CAL_SCOPE, $1); $$->ignore = (int) $6; $$->unless = ""; }
        | SCOPE "ignore" "at" "T" '+' NUM "unless" '{' ccys '}'
                                        { $$ = calendar(parse, CAL_SCOPE, $1); $$->ignore = (int) $6; $$->unless = $9; }
        | KEY                           { $$ = calendar(parse, CAL_NAMED, $1); }
        | '(' calendar ')'              { $$ = $2; }
        ;

/* three letters each, without separator: ARSBRL */
ccys: CCY
    | ccys ',' CCY                      { $$ = arena_alloc(parse->base.schedule, strlen($1) + 4); strcat(strcpy($$, $1), $3); }
    ;

/* in an expression one term only, as "&" there is include: ON country:BR, ON usd_eur, ON (a & b) */
on: "ON" SCOPE                          { $$ = calendar(parse, CAL_SCOPE, $2); }
  | "ON" KEY                            { $$ = calendar(parse, CAL_NAMED, $2); }
  | "ON" '(' calendar ')'               { $$ = $3; }
  ;

on_opt: %empty                          { $$ = NULL; }
      | on
      ;

/* ================= expressions ================= */

items: item
     | items ',' item                   { $$ = $1; APPEND(Item, $$, $3); }
     ;

/* the condition applies to every date of the item; the shifts after it to the dates it keeps. A pick starts with a
   series or an event, where several dates come from */
item: expr combines                     { $$ = item(parse, $1, $2, NULL, NULL, 0); }
    | expr combines if_condition moves  { $$ = item(parse, $1, $2, $3, $4, 0); }
    | pick series moves combines        { Expr *e = NEW(Expr); e->date = $2; e->shifts = $3; $$ = item(parse, e, $4, NULL, NULL, $1); }
    | pick series moves combines if_condition moves
                                        { Expr *e = NEW(Expr); e->date = $2; e->shifts = $3; $$ = item(parse, e, $4, $5, $6, $1); }
    | pick EVENT moves combines         { Expr *e = NEW(Expr); e->date = (Date) { .kind = D_EVENT, .event = $2 }; e->shifts = $3; $$ = item(parse, e, $4, NULL, NULL, $1); }
    | pick EVENT moves combines if_condition moves
                                        { Expr *e = NEW(Expr); e->date = (Date) { .kind = D_EVENT, .event = $2 }; e->shifts = $3; $$ = item(parse, e, $4, $5, $6, $1); }
    ;

/* 5TH ...: the 5th date after T of the item; 2ND LAST from the last one, a negative count */
pick: ORDINAL
    | ORDINAL "LAST"                    { $$ = -$1; }
    ;

if_condition: "IF" condition            { $$ = $2; }
            ;

/* A & B includes the dates of B, A \ B excludes them; the shifts after it apply to the result */
combines: %empty                        { $$ = NULL; }
        | combines combine              { $$ = $1; APPEND(Combine, $$, $2); }
        ;

combine: '&' operand moves              { $$ = NEW(Combine); $$->include = 1; $$->operands = $2; $$->shifts = $3; }
       | '\\' operand moves             { $$ = NEW(Combine); $$->include = 0; $$->operands = $2; $$->shifts = $3; }
       ;

/* a date, a series or a set of dates */
operand: date                           { $$ = NEW(Operand); $$->expr = NEW(Expr); $$->expr->date = $1; }
       | '{' operand_exprs '}'          { $$ = $2; }
       ;

operand_exprs: expr                     { $$ = NEW(Operand); $$->expr = $1; }
             | operand_exprs ',' expr   { Operand *o = NEW(Operand); o->expr = $3; $$ = $1; APPEND(Operand, $$, o); }
             ;

/* shifts are applied from left to right */
expr: date moves                        { $$ = NEW(Expr); $$->date = $1; $$->shifts = $2; }
    ;

moves: %empty                           { $$ = NULL; }
     | moves shift                      { $$ = $1; APPEND(Shift, $$, $2); }
     ;

/* [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out */
series: start_out endpoint ':' period ':' endpoint end_out
                                        { if (series(parse, &$$, $1, $2, $4, $6, $7)) YYERROR; }
      | start_out endpoint ':' period ':' end_out    /* an open series [SPOT : 1Q : ] */
                                        { if (series(parse, &$$, $1, $2, $4, NULL, $6)) YYERROR; }
      | start_out endpoint ':' endpoint end_out
                                        { if (series(parse, &$$, $1, $2, (Period) { 0, 1, 0, 0 }, $4, $5)) YYERROR; }
      ;

/* an endpoint is one date, so it can't be a series */
endpoint: single_date moves             { $$ = NEW(Expr); $$->date = $1; $$->shifts = $2; }
        ;

start_out: '['                          { $$ = 0; }
         | ']'                          { $$ = 1; }
         ;

end_out: ']'                            { $$ = 0; }
       | '['                            { $$ = 1; }
       ;

shift: '+' period endend_opt on_opt     { $$ = shift(parse, S_PLUS); $$->period = $2; $$->endend = $3; $$->on = $4; }
     | '-' period endend_opt on_opt     { $$ = shift(parse, S_MINUS); $$->period = $2; $$->endend = $3; $$->on = $4; }
     | '>' "IMM"                        { $$ = shift(parse, S_IMM); }
     | '>' "SPOT" pair_opt on_opt       { $$ = shift(parse, S_SPOT); $$->pair = $3; $$->on = $4; }
     | '>' "FIX" pair_opt               { $$ = shift(parse, S_FIX); $$->pair = $3; }
     | '>' ROLL on_opt                  { $$ = shift(parse, S_ROLL); $$->roll = $2; $$->on = $3; }
     | '>' "NEXT" anchor                { $$ = shift(parse, S_NEXT); $$->count = 1; $$->anchor = $3; }
     | '>' "NEXT" ORDINAL anchor        { $$ = shift(parse, S_NEXT); $$->count = $3; $$->anchor = $4; }
     | '>' "PREV" anchor                { $$ = shift(parse, S_PREV); $$->count = 1; $$->anchor = $3; }
     | '>' "PREV" ORDINAL anchor        { $$ = shift(parse, S_PREV); $$->count = $3; $$->anchor = $4; }
     | '>' "NEAREST" anchor             { $$ = shift(parse, S_NEAREST); $$->anchor = $3; }   /* ties go forward */
     | '>' "NEAREST" on                 { $$ = shift(parse, S_ROLL); $$->roll = ROLL_NEAREST; $$->on = $3; }   /* FpML */
       /* > CUTOFF 17:00 "America/New_York": the trade date of a base with a time of day */
     | '>' "CUTOFF" NUM ':' NUM zone
                                        {
                                            if ($3 > 23 || $5 > 59) FAIL("not a time of day: %ld:%02ld", $3, $5);
                                            $$ = shift(parse, S_CUTOFF);
                                            $$->cutoff = (int) ($3 * 60 + $5);
                                            $$->offset = $6.offset;
                                            $$->zone = $6.name;
                                        }
       /* > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH */
     | '>' count select "IN" PERIOD
                                        {
                                            $$ = shift(parse, S_SELECT);
                                            $$->count = $2 < 0 ? -$2 : $2;
                                            $$->from_end = $2 < 0;
                                            $$->anchor = $3;
                                            $$->within = $5;
                                        }
       /* > {1 FEB, 1 MAY}: the dates of the set in the year of the date */
     | '>' '{' annuals '}'              { $$ = shift(parse, S_DATES_IN_YEAR); $$->anchor = $3; }
     | '>' "POSTPONE" ROLL on_opt "MAX" period cumulative_opt
                                        { $$ = shift(parse, S_POSTPONE); $$->roll = $3; $$->on = $4; $$->period = $6; $$->cumulative = $7; }
       /* {SAT - 1D, TUE WED > PREV MON}: the first entry for the weekday of the date moves it */
     | '{' observes '}'                 { $$ = shift(parse, S_OBSERVANCE); $$->observes = $2; }
     ;

endend_opt: %empty                      { $$ = 0; }
          | "ENDEND"                    { $$ = 1; }
          ;

cumulative_opt: %empty                  { $$ = 0; }
              | "CUMULATIVE"            { $$ = 1; }
              ;

pair_opt: %empty                        { $$ = NULL; }
        | PAIR
        ;

observes: observe
        | observes ',' observe          { $$ = $1; APPEND(Observe, $$, $3); }
        ;

observe: weekday_list '+' period        { $$ = NEW(Observe); $$->weekdays = $1; $$->sign = 1; $$->period = $3; }
       | weekday_list '-' period        { $$ = NEW(Observe); $$->weekdays = $1; $$->sign = -1; $$->period = $3; }
       | weekday_list '>' "NEXT" anchor
            { $$ = NEW(Observe); $$->weekdays = $1; $$->move = shift(parse, S_NEXT); $$->move->count = 1; $$->move->anchor = $4; }
       | weekday_list '>' "PREV" anchor
            { $$ = NEW(Observe); $$->weekdays = $1; $$->move = shift(parse, S_PREV); $$->move->count = 1; $$->move->anchor = $4; }
       | weekday_list '>' "NEAREST" anchor
            { $$ = NEW(Observe); $$->weekdays = $1; $$->move = shift(parse, S_NEAREST); $$->move->anchor = $4; }
       | weekday_list '>' ROLL on_opt
            { $$ = NEW(Observe); $$->weekdays = $1; $$->move = shift(parse, S_ROLL); $$->move->roll = $3; $$->move->on = $4; }
       ;

annuals: annual                         { $$ = anchor(parse, AT_ANNUAL, NULL); $$->annual = $1; }
       | annuals ',' annual             { Anchor *a = anchor(parse, AT_ANNUAL, NULL); a->annual = $3; $$ = $1; APPEND(Anchor, $$, a); }
       ;

/* counting from the end is a negative count */
count: ORDINAL
     | ORDINAL "LAST"                   { $$ = -$1; }
     | "LAST"                           { $$ = -1; }
     ;

/* the targets of a selection; an annual date starting with LAST only in braces, as LAST also starts the count */
select: chosen
      | '{' targets '}'                 { $$ = anchor(parse, AT_ANY_OF, NULL); $$->targets = $2; }
      ;

/* NEXT {MON, FRI}: the nearest of a set */
anchor: target
      | '{' targets '}'                 { $$ = anchor(parse, AT_ANY_OF, NULL); $$->targets = $2; }
      ;

targets: target
       | targets ',' target             { $$ = $1; APPEND(Anchor, $$, $3); }
       ;

target: chosen
      | "LAST" WEEKDAY MONTH            { $$ = anchor(parse, AT_ANNUAL, NULL); $$->annual = annual(LAST_WEEKDAY, 0, $2, $3); }
      ;

chosen: WEEKDAY                         { $$ = anchor(parse, AT_WEEKDAY, NULL); $$->weekday = $1; }
      | "BD" on_opt                     { $$ = anchor(parse, AT_BUSINESS_DAY, $2); }
      | "FD"                            { $$ = anchor(parse, AT_FIXING_DAY, NULL); }
      | "IMM"                           { $$ = anchor(parse, AT_IMM, NULL); }
      | fixed_annual                    { $$ = anchor(parse, AT_ANNUAL, NULL); $$->annual = $1; }
      | EVENT                           { $$ = anchor(parse, AT_EVENT, NULL); $$->event = $1; }
      | "HOLIDAY" on_opt                { $$ = anchor(parse, AT_HOLIDAY, $2); }
      | "NONHOLIDAY" on_opt             { $$ = anchor(parse, AT_NONHOLIDAY, $2); }
      ;

date: single_date
    | series
    ;

/* a date that is not a series */
single_date: "T"                        { $$ = (Date) { .kind = D_BASE }; }
    | "TOM"                             { $$ = (Date) { .kind = D_TOM }; }
    | "SPOT" pair_opt on_opt            { $$ = (Date) { .kind = D_SPOT, .pair = $2, .on = $3 }; }
    | NUM '-' NUM '-' NUM               { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $1, $3, $5, &$$.fixed)) YYERROR; }
    | NUM '.' NUM '.' NUM               { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $5, $3, $1, &$$.fixed)) YYERROR; }
    | NUM '/' NUM '/' NUM               { $$ = (Date) { .kind = D_FIXED }; if (fixed(parse, $5, $1, $3, &$$.fixed)) YYERROR; }
    | annual                            { $$ = (Date) { .kind = D_ANNUAL, .annual = $1 }; }
    | EVENT                             { $$ = (Date) { .kind = D_EVENT, .event = $1 }; }
    ;

/* dates that recur every year */
annual: fixed_annual
      | "LAST" WEEKDAY MONTH            { $$ = annual(LAST_WEEKDAY, 0, $2, $3); }
      ;

fixed_annual: NUM MONTH method_opt
                {
                    if ($3 && ($2 < HIJRI || $2 >= HEBREW)) FAIL("BY %s only for Hijri months", $3);
                    $$ = annual(MONTH_DAY, $1, 0, $2);
                }
      | NUM WEEKDAY MONTH               { $$ = annual(NTH_WEEKDAY, $1, $2, $3); }
      | "EASTER"                        { $$ = annual(EASTER_SUNDAY, 0, 0, 0); }
      | "ORTHODOX" "EASTER"             { $$ = annual(ORTHODOX_EASTER, 0, 0, 0); }
      | SEASON MONTH zone_opt           { $$ = annual(SUN_AT, 0, 0, $2); $$.equinox = $1; $$.offset = $3.offset; $$.zone = $3.name; }
      | "SOLARTERM" NUM zone_opt        { $$ = annual(SOLAR_TERM, $2, 0, 0); $$.offset = $3.offset; $$.zone = $3.name; }
      | "HINDU" KEY                     { $$ = annual(HINDU_DAY, 0, 0, 0); $$.zone = $2; }
      ;

/* 1 SHAWWAL BY SIGHTING: a method other than UMMALQURA marks the rule's dates as projections */
method_opt: %empty                      { $$ = NULL; }
          | "BY" METHOD                 { $$ = $2 ? "ESTIMATED" : "UMMALQURA"; if ($2) parse->estimated++; }
          | "BY" "FOLLOW" SCOPE         { $$ = "FOLLOW"; parse->estimated++; }
          ;

/* UTC unless a zone follows: UTC+9 or an IANA time zone such as "Asia/Tokyo" */
zone_opt: %empty                        { $$ = (ZoneSpec) { 0, NULL }; }
        | zone
        ;

zone: ZONE                              { $$ = (ZoneSpec) { (int) $1, NULL }; }
    | STRING
        {
            if (!strncmp($1, "UTC", 3) && ($1[3] == '+' || $1[3] == '-'))
                $$ = (ZoneSpec) { (int) strtol($1 + 3, NULL, 10) * 60, NULL };
            else
                $$ = (ZoneSpec) { 0, $1 };
        }
    ;

period: NUM UNIT                        {
                                            $$ = (Period) { 0, 0, 0, 0 };
                                            switch ($2) {
                                            case 'D': $$.days = $1; break;
                                            case 'W': $$.days = 7 * $1; break;
                                            case 'M': $$.months = $1; break;
                                            case 'Q': $$.months = 3 * $1; break;
                                            case 'H': $$.months = 6 * $1; break;
                                            case 'Y': $$.months = 12 * $1; break;
                                            case 'F': $$.fixing = $1; break;
                                            default: $$.business = $1;
                                            }
                                        }
      ;

/* ================= conditions ================= */

condition: cond_and
         | condition "OR" cond_and
            {
                if ($1->kind != C_ANY) { Condition *c = condition(parse, C_ANY); c->parts = $1; $1 = c; }
                $$ = $1;
                APPEND(Condition, $$->parts, $3);
            }
         ;

cond_and: cond_not
        | cond_and "AND" cond_not
            {
                if ($1->kind != C_ALL) { Condition *c = condition(parse, C_ALL); c->parts = $1; $1 = c; }
                $$ = $1;
                APPEND(Condition, $$->parts, $3);
            }
        ;

cond_not: cond_atom
        | "NOT" cond_atom               { $$ = condition(parse, C_NOT); $$->parts = $2; }
        ;

cond_atom: weekday_set                  { $$ = condition(parse, C_WEEKDAY); $$->weekdays = $1; }   /* the weekday of the date itself */
         | "DAY" NUM ".." NUM month_opt { $$ = condition(parse, C_DAY); $$->first = $2; $$->last = $4; $$->month = $5; }   /* DAY 8..14 APR */
         | subject "IS" weekday_set     { $$ = condition(parse, C_IS_WEEKDAY); $$->subject = $1; $$->weekdays = $3; }
         | subject "IS" "HOLIDAY" on_opt { $$ = condition(parse, C_DATE_IS_HOLIDAY); $$->subject = $1; $$->on = $4; }
         | "HOLIDAY" on_opt             { $$ = condition(parse, C_IS_HOLIDAY); $$->on = $2; }
         | "BETWEEN" "HOLIDAYS" on_opt  { $$ = condition(parse, C_BETWEEN_HOLIDAYS); $$->on = $3; }   /* JP art. 3(3) */
         | "EVERY" period "FROM" NUM    /* EVERY 4Y FROM 1965 */
            {
                if ($2.days || $2.business || $2.fixing || $2.months <= 0 || $2.months % 12) FAIL("EVERY needs whole years: EVERY 4Y FROM 1965");
                $$ = condition(parse, C_EVERY); $$->first = $2.months / 12; $$->last = $4;
            }
         | '(' condition ')'            { $$ = $2; }
         ;

month_opt: %empty                       { $$ = -1; }
         | MONTH
         ;

/* 18 SEP, EASTER + 1D */
subject: single_date                    { $$ = NEW(Expr); $$->date = $1; }
       | subject '+' period             { Shift *s = shift(parse, S_PLUS); s->period = $3; $$ = $1; APPEND(Shift, $$->shifts, s); }
       | subject '-' period             { Shift *s = shift(parse, S_MINUS); s->period = $3; $$ = $1; APPEND(Shift, $$->shifts, s); }
       ;

weekday_set: weekday_list
           | '{' weekday_commas '}'     { $$ = $2; }
           ;

weekday_list: WEEKDAY                   { $$ = 1 << $1; }
            | weekday_list WEEKDAY      { $$ = $1 | 1 << $2; }
            ;

weekday_commas: WEEKDAY                 { $$ = 1 << $1; }
              | weekday_commas ',' WEEKDAY { $$ = $1 | 1 << $3; }
              ;

%%

static void rferror(yyscan_t scanner, RuleParse *parse, const char *message) {
    (void) scanner;
    if (!parse->base.error[0]) snprintf(parse->base.error, parse->base.size, "%s", message);
}
