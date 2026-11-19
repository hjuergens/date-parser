/* The evaluator's internals that semantics.c and catalog.c share: the state of one evaluation, the context of a rule,
 * markets and events. Not part of the library's interface (schedule.h, catalog.h). */
#ifndef EVAL_H
#define EVAL_H

#include <pthread.h>
#include <setjmp.h>
#include "ast.h"

typedef struct RuleCatalog RuleCatalog;

/* a node with several values that wasn't bound yet: an event, a series, a combination or dates in year */
enum { P_EVENT, P_SERIES, P_COMBINED, P_DATES_IN_YEAR, P_LUNAR };

typedef struct {
    jmp_buf jump;
    char *error;
    size_t size;
    DateList **temps;   /* freed at the end, also after an error */
    int count, capacity;
    Market **markets;   /* markets made during the evaluation (calendars of ON), freed at the end */
    int market_count, market_capacity;
    struct {
        int kind, date;          /* P_DATES_IN_YEAR: the date whose year counts */
        const void *node;        /* the Date, Combine or Shift */
        const Item *item;        /* P_COMBINED: the item and the number of combines before this one */
        int before;
    } pending;
} Eval;

/* what a rule is evaluated against; bindings are the values collect() chose for the nodes with several values, in the
 * order the evaluation reaches them, counted by cursor. In a rule file the catalog and the scope come along: ON reads
 * the catalog's calendars, @key its rules; home and shallow: the year evaluated (0: none) and whether other years are
 * read at all (see catalog.c) */
typedef struct {
    int has_base, base;
    Market *market;
    const Events *events;
    const int *bindings;
    int count, *cursor;
    int limit;   /* how many elements an open series yields; set by a pick, 0 elsewhere */
    Market *fixing;   /* the fixing calendar; NULL: Monday to Friday */
    RuleCatalog *catalog;
    const char *scope;
    int home, shallow;
} Ctx;

/* The holidays of a market of a rule are cached per year. Threads may share such a market: the lock guards the cache,
 * and an entry, once added, is neither moved nor changed, so its holidays can be read without the lock. A market of a
 * rule file (catalog.c) answers with its own functions instead (business, holiday) and keeps its state in data. */
struct Market {
    Schedule *rule;
    pthread_mutex_t lock;
    struct {
        int year;
        DateList holidays;  /* sorted */
    } **years;
    int count, capacity;
    int (*business)(Eval *eval, Market *market, int date);
    int (*holiday)(Eval *eval, Market *market, int date);
    void *data;
    void (*release)(Market *market);   /* frees data */
};

struct Events {
    char **names;
    Schedule **rules;
    int count;
};

_Noreturn void fail(Eval *eval, const char *format, ...);
DateList *temp(Eval *eval);
/* a market made during the evaluation, freed with it */
void own_market(Eval *eval, Market *market);
int compare_dates(const void *a, const void *b);
void distinct(DateList *dates);
int year_of(int date);
int add_days(Eval *eval, int date, long long days);
int is_business_day(Eval *eval, Market *market, int date);
/* closed although not a weekend day */
int is_holiday(Eval *eval, Market *market, int date);
int plus_business_days(Eval *eval, int date, long long count, Market *market);

/* the dates of the items with the context (a rule of a rule file): 0, or -1 and the message in error */
int evaluate_items(const Item *items, const Ctx *ctx, DateList *out, char *error, size_t size);

/* catalog.c: the calendar of ON ..., the dates of @key in the year */
Market *catalog_resolve(Eval *eval, const Calendar *calendar, const Ctx *ctx, const char *pair);
DateList *catalog_references(Eval *eval, const Ctx *ctx, const char *name, int year);

#endif
