/* The rule catalog: the port of catalog.py (date-rule-lark) and RuleCatalog.java to C.
 *
 * catalog_holidays evaluates the rules of a scope for a year in two phases: each rule on its own, then passes in the
 * order of the nominal dates (every day open) until nothing changes, for the rules that read the holidays of their own
 * scope; a rule sees the dates of the other rules, never its own. The calendar of a scope is a market of its own
 * (business, holiday); ON resolves calendars here (catalog_resolve), @key reads the dates of rules
 * (catalog_references). Errors of nested evaluations come back as messages and end the evaluation that asked. */
#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "catalog.h"
#include "eval.h"
#include "rulefile.h"
#define YYSTYPE RFSTYPE
#include "rulefile_scanner.h"

enum { MAX_PASSES = 8, SAT_SUN = 1 << 5 | 1 << 6 };

typedef struct {
    DateList dates;   /* sorted, without duplicates */
    int projected;
} Result;

/* the rules of a scope evaluated with the first of January of a year */
typedef struct State {
    char *scope;
    int year;
    int count;
    const Declaration **rules;
    Result *results;
    struct State *next;
} State;

struct RuleCatalog {
    Schedule *file;   /* the arena of the declarations */
    const Declaration **rules, **weekends, **calendars;
    int rule_count, weekend_count, calendar_count;
    struct {
        char *key;
        DateList dates;
    } *announced;
    int announced_count;
    State *states, *shallow, *running;   /* full evaluations, shallow ones (their own year only), those under way */
    struct {
        char *scope;
        Market *market;
    } *calendars_of;   /* the calendar of each scope asked for, made once */
    int calendars_of_count;
};

/* ---- declarations ---- */

static const char *kind_of(const Declaration *rule) { return rule->kind_of_day ? rule->kind_of_day : "closed"; }

static int closes(const Declaration *rule) {
    return !strcmp(kind_of(rule), "closed") || !strcmp(kind_of(rule), "non_working");
}

static int estimated(const Declaration *rule) { return rule->estimated || rule->alternatives; }

static int valid(const Declaration *rule, int date) {
    return (!rule->has_from || date >= rule->valid_from) && (!rule->has_to || date <= rule->valid_to);
}

static int in_year(const Declaration *rule, int year) {
    return valid(rule, date_of(year, 12, 31)) || valid(rule, date_of(year, 1, 1))
        || (rule->has_from && year_of(rule->valid_from) == year);
}

/* country:AU contains country:AU and region:AU-NSW */
static int contains(const char *outer, const char *inner) {
    if (!strcmp(outer, inner)) return 1;
    const char *o = strchr(outer, ':'), *i = strchr(inner, ':');
    if (!o || !i) return 0;
    size_t n = strlen(o + 1);
    return !strncmp(i + 1, o + 1, n) && i[1 + n] == '-';
}

static int applies(const Declaration *rule, const char *scope) {
    int in = 0;
    for (const Name *s = rule->include; s; s = s->next) in |= contains(s->text, scope);
    for (const Name *s = rule->exclude; s; s = s->next)
        if (contains(s->text, scope)) return 0;
    return in;
}

static int referenced(const RuleCatalog *c, const Declaration *rule, const char *name) {
    (void) c;
    return !strcmp(rule->key, name) || !strcmp(rule->event_class, name);
}

static int any_referenced(const RuleCatalog *c, const char *name) {
    for (int i = 0; i < c->rule_count; i++)
        if (referenced(c, c->rules[i], name)) return 1;
    return 0;
}

static const DateList *announcements_of(const RuleCatalog *c, const char *key) {
    for (int i = 0; i < c->announced_count; i++)
        if (!strcmp(c->announced[i].key, key)) return &c->announced[i].dates;
    return NULL;
}

/* the dates announced for the key in the year into out */
static int announcement(const RuleCatalog *c, const char *key, int year, DateList *out) {
    const DateList *all = announcements_of(c, key);
    int n = 0;
    for (int i = 0; all && i < all->count; i++)
        if (year_of(all->dates[i]) == year) {
            if (out) datelist_push(out, all->dates[i]);
            n++;
        }
    return n;
}

/* the weekend days of the scope on the date (a bit each): its own regime, else that of the scope containing it, else
 * SAT SUN */
static int weekend(const RuleCatalog *c, const char *scope, int date) {
    const Declaration *best = NULL;
    for (int i = 0; i < c->weekend_count; i++) {
        const Declaration *w = c->weekends[i];
        if (!contains(w->key, scope) || (w->has_from && w->valid_from > date)) continue;
        size_t length = strlen(w->key), best_length = best ? strlen(best->key) : 0;
        if (!best || length > best_length
            || (length == best_length && (w->has_from ? w->valid_from : INT_MIN) >= (best->has_from ? best->valid_from : INT_MIN)))
            best = w;
    }
    return best ? best->weekdays : SAT_SUN;
}

/* ---- the calendar of a scope ---- */

/* the weekend regime of a scope and the dates of its rules, from the evaluations of the year of a date and of the
 * years before and after. During the evaluation of a year (home) skip leaves out the rule being evaluated, the other
 * years are read shallow (evaluated with their own year only), and a shallow calendar reads its home year only */
typedef struct {
    RuleCatalog *catalog;
    char *scope;
    const Declaration *skip;
    int home, shallow;
} ScopeCalendar;

static State *state(RuleCatalog *c, const char *scope, int year, int shallow, char *error, size_t size);

/* whether a rule of the scope closes the date; opened: whether one opens it (working_day) */
static int closed(Eval *eval, ScopeCalendar *s, int date, int *opened) {
    int is_closed = 0, year = year_of(date);
    *opened = 0;
    for (int y = year - 1; y <= year + 1; y++) {
        if (s->shallow && y != s->home) continue;
        char error[256];
        State *st = state(s->catalog, s->scope, y, s->home && y != s->home, error, sizeof error);
        if (!st) fail(eval, "%s", error);
        for (int i = 0; i < st->count; i++) {
            if ((st->rules[i] == s->skip && y == s->home) || !st->results[i].dates.count
                || !bsearch(&date, st->results[i].dates.dates, st->results[i].dates.count, sizeof(int), compare_dates))
                continue;
            if (closes(st->rules[i])) is_closed = 1;
            else if (!strcmp(kind_of(st->rules[i]), "working_day")) *opened = 1;
        }
    }
    return is_closed;
}

static int scope_business(Eval *eval, Market *market, int date) {
    ScopeCalendar *s = market->data;
    int opened, is_closed = closed(eval, s, date, &opened);
    if (opened) return 1;
    return !(weekend(s->catalog, s->scope, date) >> weekday_of(date) & 1) && !is_closed;
}

static int scope_holiday(Eval *eval, Market *market, int date) {
    int opened;
    return closed(eval, market->data, date, &opened);
}

static void scope_release(Market *market) {
    ScopeCalendar *s = market->data;
    free(s->scope);
    free(s);
}

static Market *scope_market(RuleCatalog *c, const char *scope, const Declaration *skip, int home, int shallow) {
    Market *market = calloc(1, sizeof(Market));
    ScopeCalendar *s = calloc(1, sizeof(ScopeCalendar));
    if (!market || !s || !(s->scope = strdup(scope))) abort();
    s->catalog = c, s->skip = skip, s->home = home, s->shallow = shallow;
    market->business = scope_business, market->holiday = scope_holiday, market->data = s, market->release = scope_release;
    return market;
}

static void market_release(Market *market) {
    if (market->release) market->release(market);
    free(market);
}

/* the calendar of a scope, for contexts of the schedule language; made once per scope */
static Market *scope_calendar(RuleCatalog *c, const char *scope) {
    for (int i = 0; i < c->calendars_of_count; i++)
        if (!strcmp(c->calendars_of[i].scope, scope)) return c->calendars_of[i].market;
    c->calendars_of = realloc(c->calendars_of, (c->calendars_of_count + 1) * sizeof *c->calendars_of);
    if (!c->calendars_of || !(c->calendars_of[c->calendars_of_count].scope = strdup(scope))) abort();
    return c->calendars_of[c->calendars_of_count++].market = scope_market(c, scope, NULL, 0, 0);
}

/* every day a business day: the calendar of the nominal dates, which give the order of the passes */
static int open_business(Eval *eval, Market *market, int date) { (void) eval, (void) market, (void) date; return 1; }
static int open_holiday(Eval *eval, Market *market, int date) { (void) eval, (void) market, (void) date; return 0; }

/* ---- combined calendars: A & B ---- */

/* a business day in every term; a term with ignore does not count on the ignore-th business day after T of the other
 * terms, unless the currency pair contains one of its currencies */
typedef struct {
    int count;
    Market **markets;
    int *ignored;   /* per term the day it doesn't count, or INT_MIN */
} Combined;

static int combined_business(Eval *eval, Market *market, int date) {
    Combined *m = market->data;
    for (int i = 0; i < m->count; i++)
        if (m->ignored[i] != date && !is_business_day(eval, m->markets[i], date)) return 0;
    return 1;
}

static int combined_holiday(Eval *eval, Market *market, int date) {
    Combined *m = market->data;
    for (int i = 0; i < m->count; i++)
        if (is_holiday(eval, m->markets[i], date)) return 1;
    return 0;
}

static void combined_release(Market *market) {
    Combined *m = market->data;
    free(m->markets);
    free(m->ignored);
    free(m);
}

/* whether the pair contains one of the currencies (three letters each) */
static int pair_has(const char *pair, const char *currencies) {
    for (const char *c = currencies; pair && c[0] && c[1] && c[2]; c += 3)
        if (!strncmp(pair, c, 3) || !strncmp(pair + 3, c, 3)) return 1;
    return 0;
}

static const Calendar *declared(const RuleCatalog *c, const char *key) {
    for (int i = 0; i < c->calendar_count; i++)
        if (!strcmp(c->calendars[i]->key, key)) return c->calendars[i]->calendar;
    return NULL;
}

Market *catalog_resolve(Eval *eval, const Calendar *spec, const Ctx *ctx, const char *pair) {
    RuleCatalog *c = ctx->catalog;
    if (spec->kind == CAL_NAMED) {
        const Calendar *calendar = declared(c, spec->name);
        if (!calendar) fail(eval, "no calendar %s", spec->name);
        return catalog_resolve(eval, calendar, ctx, pair);
    }
    if (spec->kind == CAL_SCOPE && !spec->ignore)
        return ctx->scope && !strcmp(spec->name, ctx->scope) ? ctx->market : scope_calendar(c, spec->name);
    const Calendar *terms = spec->kind == CAL_COMBINED ? spec->terms : spec;
    int count = 0;
    for (const Calendar *t = terms; t; t = spec->kind == CAL_COMBINED ? t->next : NULL) count++;
    Combined *m = calloc(1, sizeof(Combined));
    Market *market = calloc(1, sizeof(Market));
    if (!m || !market || !(m->markets = calloc(count, sizeof(Market *))) || !(m->ignored = calloc(count, sizeof(int))))
        abort();
    market->business = combined_business, market->holiday = combined_holiday, market->data = m;
    market->release = combined_release;
    own_market(eval, market);
    m->count = count;
    int i = 0;
    for (const Calendar *t = terms; t; t = spec->kind == CAL_COMBINED ? t->next : NULL, i++) {
        if (t->kind == CAL_SCOPE) {
            Calendar plain = { CAL_SCOPE, t->name, 0, NULL, NULL, NULL };
            m->markets[i] = catalog_resolve(eval, &plain, ctx, NULL);
        } else {
            m->markets[i] = catalog_resolve(eval, t, ctx, pair);
        }
        m->ignored[i] = INT_MIN;
    }
    i = 0;
    for (const Calendar *t = terms; t; t = spec->kind == CAL_COMBINED ? t->next : NULL, i++) {
        if (t->kind != CAL_SCOPE || !t->ignore || !ctx->has_base || pair_has(pair, t->unless)) continue;
        int day = ctx->base;
        for (int k = 0; k < t->ignore; k++) {
            for (int n = 0;; n++) {
                if (n == 3700) fail(eval, "no business day within 3700 days");
                day = add_days(eval, day, 1);
                int all = 1;
                for (int j = 0; j < count && all; j++)
                    if (j != i && !is_business_day(eval, m->markets[j], day)) all = 0;
                if (all) break;
            }
        }
        m->ignored[i] = day;
    }
    return market;
}

/* ---- evaluation ---- */

static void result_free(Result *r) { datelist_free(&r->dates); }

static int same(const Result *a, const Result *b) {
    return a->projected == b->projected && a->dates.count == b->dates.count
        && (!a->dates.count || !memcmp(a->dates.dates, b->dates.dates, a->dates.count * sizeof(int)));
}

static int evaluate_body(const Item *items, const Alternative *alternatives, const Ctx *ctx, DateList *out, char *error,
                         size_t size) {
    if (items) return evaluate_items(items, ctx, out, error, size);
    *out = (DateList) { 0 };
    for (const Alternative *a = alternatives; a; a = a->next) {
        DateList one;
        if (evaluate_items(a->items, ctx, &one, error, size)) {
            datelist_free(out);
            return -1;
        }
        for (int i = 0; i < one.count; i++) datelist_push(out, one.dates[i]);
        datelist_free(&one);
    }
    return 0;
}

static Ctx rule_context(RuleCatalog *c, const char *scope, int year, int shallow, Market *market) {
    return (Ctx) { 1, date_of(year, 1, 1), market, NULL, NULL, 0, NULL, 0, NULL, c, scope, year, shallow };
}

/* the dates of the rule evaluated in the year, valid, sorted; an announcement for the year always wins. market: NULL
 * for the calendar of the scope without the rule */
static int evaluate_rule(RuleCatalog *c, const Declaration *rule, const char *scope, int year, int shallow,
                         Market *market, Result *out, char *error, size_t size) {
    *out = (Result) { { 0 }, 0 };
    DateList found = { 0 };
    if (announcement(c, rule->key, year, &found)) {
        for (int i = 0; i < found.count; i++)
            if (valid(rule, found.dates[i])) datelist_push(&out->dates, found.dates[i]);
        datelist_free(&found);
        return 0;
    }
    Market *own = market ? NULL : scope_market(c, scope, rule, year, shallow);
    Ctx ctx = rule_context(c, scope, year, shallow, market ? market : own);
    int status = evaluate_body(rule->items, rule->alternatives, &ctx, &found, error, size);
    if (own) market_release(own);
    if (status) return -1;
    for (int i = 0; i < found.count; i++)
        if (valid(rule, found.dates[i])) datelist_push(&out->dates, found.dates[i]);
    datelist_free(&found);
    distinct(&out->dates);
    out->projected = estimated(rule) || announcements_of(c, rule->key);
    return 0;
}

static State *find_state(State *list, const char *scope, int year) {
    for (State *s = list; s; s = s->next)
        if (s->year == year && !strcmp(s->scope, scope)) return s;
    return NULL;
}

static void state_free(State *s) {
    for (int i = 0; i < s->count; i++) result_free(&s->results[i]);
    free(s->results);
    free(s->rules);
    free(s->scope);
    free(s);
}

static void unlink_state(State **list, State *s) {
    for (State **p = list; *p; p = &(*p)->next)
        if (*p == s) {
            *p = s->next;
            return;
        }
}

/* the first date of the rule with every day open, which orders the passes; INT_MAX if it needs more */
static int nominal(RuleCatalog *c, const Declaration *rule, const char *scope, int year) {
    static Market open = { .business = open_business, .holiday = open_holiday };
    char error[256];
    Result r;
    if (evaluate_rule(c, rule, scope, year, 1, &open, &r, error, sizeof error)) return INT_MAX;
    int first = r.dates.count ? r.dates.dates[0] : INT_MAX;
    result_free(&r);
    return first;
}

/* a rule's place in the passes: its nominal date, then its place in the file */
typedef struct {
    int nominal, index;
} Rank;

static int by_rank(const void *a, const void *b) {
    const Rank *x = a, *y = b;
    return x->nominal != y->nominal ? (x->nominal > y->nominal) - (x->nominal < y->nominal) : x->index - y->index;
}

/* the rules of the scope evaluated with the first of January of the year, or NULL and the message */
static State *state(RuleCatalog *c, const char *scope, int year, int shallow, char *error, size_t size) {
    State *found = find_state(c->states, scope, year);
    if (!found && shallow) found = find_state(c->shallow, scope, year);
    if (!found) found = find_state(c->running, scope, year);
    if (found) return found;
    State *s = calloc(1, sizeof(State));
    if (!s || !(s->scope = strdup(scope)) || !(s->rules = calloc(c->rule_count + 1, sizeof *s->rules))) abort();
    s->year = year;
    for (int i = 0; i < c->rule_count; i++)
        if (applies(c->rules[i], scope) && in_year(c->rules[i], year)) s->rules[s->count++] = c->rules[i];
    if (!(s->results = calloc(s->count + 1, sizeof(Result)))) abort();
    for (int i = 0; i < s->count; i++) s->results[i].projected = estimated(s->rules[i]) || announcements_of(c, s->rules[i]->key);
    s->next = c->running;
    c->running = s;
    Rank *order = calloc(s->count + 1, sizeof(Rank));
    if (!order) abort();
    for (int i = 0; i < s->count; i++) order[i] = (Rank) { nominal(c, s->rules[i], scope, year), i };
    qsort(order, s->count, sizeof(Rank), by_rank);
    int settled = 0, failed = 0;
    for (int pass = 0; pass < MAX_PASSES && !settled && !failed; pass++) {
        settled = 1;
        for (int k = 0; k < s->count && !failed; k++) {
            int i = order[k].index;
            Result r;
            if (evaluate_rule(c, s->rules[i], scope, year, shallow, NULL, &r, error, size)) {
                failed = 1;
            } else if (same(&r, &s->results[i])) {
                result_free(&r);
            } else {
                result_free(&s->results[i]);
                s->results[i] = r;
                settled = 0;
            }
        }
    }
    free(order);
    unlink_state(&c->running, s);
    if (!failed && !settled) {
        snprintf(error, size, "the rules of %s in %d do not settle in %d passes", scope, year, MAX_PASSES);
        failed = 1;
    }
    if (failed) {
        state_free(s);
        return NULL;
    }
    State **cache = shallow ? &c->shallow : &c->states;
    s->next = *cache;
    *cache = s;
    return s;
}

DateList *catalog_references(Eval *eval, const Ctx *ctx, const char *name, int year) {
    RuleCatalog *c = ctx->catalog;
    int rules = any_referenced(c, name);
    if (!rules && !announcements_of(c, name)) fail(eval, "no rule, event class or announced key %s", name);
    DateList *out = temp(eval);
    if (ctx->shallow && year != ctx->home) return out;
    int shallow = ctx->home && year != ctx->home;
    if (!rules) announcement(c, name, year, out);
    for (int i = 0; i < c->rule_count; i++) {
        const Declaration *rule = c->rules[i];
        if (!referenced(c, rule, name)) continue;
        const char *in_scope = ctx->scope && applies(rule, ctx->scope) ? ctx->scope : rule->include->text;
        char error[256];
        State *st = state(c, in_scope, year, shallow, error, sizeof error);
        if (!st) fail(eval, "%s", error);
        for (int j = 0; j < st->count; j++)
            if (st->rules[j] == rule)
                for (int k = 0; k < st->results[j].dates.count; k++) datelist_push(out, st->results[j].dates.dates[k]);
    }
    distinct(out);
    return out;
}

/* ---- holidays ---- */

static void push_occurrence(OccurrenceList *list, int date, const Declaration *rule, const char *scenario, int projected) {
    for (int i = 0; i < list->count; i++) {
        const Occurrence *o = &list->items[i];
        if (o->date == date && !strcmp(o->rule, rule->key) && o->projected == projected
            && (o->scenario == scenario || (o->scenario && scenario && !strcmp(o->scenario, scenario))))
            return;
    }
    if (list->count == list->capacity) {
        list->capacity = list->capacity ? 2 * list->capacity : 16;
        list->items = realloc(list->items, list->capacity * sizeof(Occurrence));
        if (!list->items) abort();
    }
    list->items[list->count++] = (Occurrence) {
        date, rule->key, rule->event_class, kind_of(rule), rule->precision ? rule->precision : "day", scenario,
        { rule->session[0], rule->session[1], rule->session[2] }, rule->citations, projected,
    };
}

/* an occurrence and its place in the list, as qsort isn't stable */
typedef struct {
    Occurrence occurrence;
    int index;
} Placed;

/* by date and rule key; the order of insertion (the alternatives as written) breaks ties */
static int by_date_and_rule(const void *a, const void *b) {
    const Placed *x = a, *y = b;
    if (x->occurrence.date != y->occurrence.date) return (x->occurrence.date > y->occurrence.date) - (x->occurrence.date < y->occurrence.date);
    int rule = strcmp(x->occurrence.rule, y->occurrence.rule);
    return rule ? rule : x->index - y->index;
}

int catalog_holidays(RuleCatalog *c, const char *scope, int year, OccurrenceList *out, char *error, size_t size) {
    *out = (OccurrenceList) { 0 };
    for (int y = year - 1; y <= year + 1; y++) {
        State *st = state(c, scope, y, 0, error, size);
        if (!st) {
            occurrencelist_free(out);
            return -1;
        }
        for (int i = 0; i < st->count; i++) {
            const Declaration *rule = st->rules[i];
            const Result *r = &st->results[i];
            if (!rule->alternatives || announcement(c, rule->key, y, NULL)) {
                for (int k = 0; k < r->dates.count; k++)
                    if (year_of(r->dates.dates[k]) == year) push_occurrence(out, r->dates.dates[k], rule, NULL, r->projected);
                continue;
            }
            /* per alternative of ONE OF; a date can belong to several */
            for (const Alternative *a = rule->alternatives; a; a = a->next) {
                Market *market = scope_market(c, scope, rule, y, 0);
                Ctx ctx = rule_context(c, scope, y, 0, market);
                DateList dates;
                int status = evaluate_items(a->items, &ctx, &dates, error, size);
                market_release(market);
                if (status) {
                    occurrencelist_free(out);
                    return -1;
                }
                distinct(&dates);
                for (int k = 0; k < dates.count; k++)
                    if (valid(rule, dates.dates[k]) && year_of(dates.dates[k]) == year)
                        push_occurrence(out, dates.dates[k], rule, a->name, r->projected);
                datelist_free(&dates);
            }
        }
    }
    Placed *placed = calloc(out->count + 1, sizeof(Placed));
    if (!placed) abort();
    for (int i = 0; i < out->count; i++) placed[i] = (Placed) { out->items[i], i };
    qsort(placed, out->count, sizeof(Placed), by_date_and_rule);
    for (int i = 0; i < out->count; i++) out->items[i] = placed[i].occurrence;
    free(placed);
    return 0;
}

void occurrencelist_free(OccurrenceList *list) {
    free(list->items);
    *list = (OccurrenceList) { 0 };
}

/* ---- parsing ---- */

/* the declarations or the schedule of a text, or NULL and the message */
static Schedule *parse_text(const char *text, char *error, size_t size) {
    Schedule *schedule = calloc(1, sizeof(Schedule));
    if (!schedule) abort();
    RuleParse parse = { { schedule, error, size }, NULL, 0, 0 };
    error[0] = 0;
    yyscan_t scanner;
    if (rflex_init_extra(&parse, &scanner)) abort();
    YY_BUFFER_STATE buffer = rf_scan_string(text, scanner);
    int status = rfparse(scanner, &parse);
    rf_delete_buffer(buffer, scanner);
    rflex_destroy(scanner);
    if (status) {
        if (!error[0]) snprintf(error, size, "syntax error");
        schedule_free(schedule);
        return NULL;
    }
    return schedule;
}

static void push_declaration(const Declaration ***list, int *count, const Declaration *d) {
    *list = realloc(*list, (*count + 1) * sizeof **list);
    if (!*list) abort();
    (*list)[(*count)++] = d;
}

static int overlap(const Declaration *a, const Declaration *b) {
    int a_from = a->has_from ? a->valid_from : INT_MIN, a_to = a->has_to ? a->valid_to : INT_MAX;
    int b_from = b->has_from ? b->valid_from : INT_MIN, b_to = b->has_to ? b->valid_to : INT_MAX;
    return a_from <= b_to && b_from <= a_to;
}

/* a cycle through the references of the rule with this key: 0, or -1 and the path in error */
static int visit(const RuleCatalog *c, const char *key, const char **path, int depth, char *done, char *error,
                 size_t size) {
    for (int i = 0; i < depth; i++)
        if (!strcmp(path[i], key)) {
            size_t n = snprintf(error, size, "cyclic references: ");
            for (int j = i; j < depth && n < size; j++) n += snprintf(error + n, size - n, "%s -> ", path[j]);
            if (n < size) snprintf(error + n, size - n, "%s", key);
            return -1;
        }
    for (int r = 0; r < c->rule_count; r++) {
        const Declaration *rule = c->rules[r];
        if (strcmp(rule->key, key) || done[r]) continue;
        path[depth] = key;
        for (const Name *n = rule->references; n; n = n->next)
            for (int t = 0; t < c->rule_count; t++)
                if (referenced(c, c->rules[t], n->text) && visit(c, c->rules[t]->key, path, depth + 1, done, error, size))
                    return -1;
        done[r] = 1;
    }
    return 0;
}

static int check(const RuleCatalog *c, char *error, size_t size) {
    for (int i = 0; i < c->rule_count; i++)
        for (int j = i + 1; j < c->rule_count; j++)
            if (!strcmp(c->rules[i]->key, c->rules[j]->key) && overlap(c->rules[i], c->rules[j])) {
                snprintf(error, size, "rule %s: versions overlap in time", c->rules[i]->key);
                return -1;
            }
    for (int i = 0; i < c->calendar_count; i++)
        for (int j = i + 1; j < c->calendar_count; j++)
            if (!strcmp(c->calendars[i]->key, c->calendars[j]->key)) {
                snprintf(error, size, "calendar %s declared twice", c->calendars[i]->key);
                return -1;
            }
    for (int i = 0; i < c->rule_count; i++)
        for (const Name *n = c->rules[i]->references; n; n = n->next)
            if (!any_referenced(c, n->text) && !announcements_of(c, n->text)) {
                snprintf(error, size, "rule %s: no rule, event class or announced key %s", c->rules[i]->key, n->text);
                return -1;
            }
    const char **path = calloc(c->rule_count + 1, sizeof(char *));
    char *done = calloc(c->rule_count + 1, 1);
    if (!path || !done) abort();
    int status = 0;
    for (int i = 0; i < c->rule_count && !status; i++) status = visit(c, c->rules[i]->key, path, 0, done, error, size);
    free(path);
    free(done);
    return status;
}

RuleCatalog *catalog_parse(const char *text, const Announcement *announcements, int count, char *error, size_t size) {
    Schedule *file = parse_text(text, error, size);
    if (!file) return NULL;
    RuleCatalog *c = calloc(1, sizeof(RuleCatalog));
    if (!c) abort();
    c->file = file;
    if (file->items) {
        snprintf(error, size, "a schedule, not a rule file");
        catalog_free(c);
        return NULL;
    }
    for (const Declaration *d = file->declarations; d; d = d->next) {
        if (d->kind == DECL_RULE) push_declaration(&c->rules, &c->rule_count, d);
        else if (d->kind == DECL_WEEKEND) push_declaration(&c->weekends, &c->weekend_count, d);
        else push_declaration(&c->calendars, &c->calendar_count, d);
    }
    if (count) {
        c->announced = calloc(count, sizeof *c->announced);
        if (!c->announced) abort();
    }
    for (int i = 0; i < count; i++) {
        if (!(c->announced[i].key = strdup(announcements[i].key))) abort();
        for (int k = 0; k < announcements[i].count; k++) datelist_push(&c->announced[i].dates, announcements[i].dates[k]);
        if (c->announced[i].dates.count) qsort(c->announced[i].dates.dates, c->announced[i].dates.count, sizeof(int), compare_dates);
        c->announced_count++;
    }
    if (check(c, error, size)) {
        catalog_free(c);
        return NULL;
    }
    return c;
}

static void states_free(State *s) {
    while (s) {
        State *next = s->next;
        state_free(s);
        s = next;
    }
}

void catalog_free(RuleCatalog *c) {
    if (!c) return;
    states_free(c->states);
    states_free(c->shallow);
    for (int i = 0; i < c->calendars_of_count; i++) {
        free(c->calendars_of[i].scope);
        market_release(c->calendars_of[i].market);
    }
    free(c->calendars_of);
    for (int i = 0; i < c->announced_count; i++) {
        free(c->announced[i].key);
        datelist_free(&c->announced[i].dates);
    }
    free(c->announced);
    free(c->rules);
    free(c->weekends);
    free(c->calendars);
    schedule_free(c->file);
    free(c);
}

int catalog_schedule(RuleCatalog *c, const char *text, int base, const char *scope, DateList *out, char *error,
                     size_t size) {
    *out = (DateList) { 0 };
    Schedule *schedule = parse_text(text, error, size);
    if (!schedule) return -1;
    if (!schedule->items) {
        snprintf(error, size, "not a schedule: %s", text);
        schedule_free(schedule);
        return -1;
    }
    Ctx ctx = { 1, base, scope ? scope_calendar(c, scope) : NULL, NULL, NULL, 0, NULL, 0, NULL, c, scope, 0, 0 };
    int status = evaluate_items(schedule->items, &ctx, out, error, size);
    schedule_free(schedule);
    return status;
}
