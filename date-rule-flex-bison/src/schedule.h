/* The shared "schedule" example in C: a Flex scanner, a Bison parser that builds the syntax tree, and an evaluator.
 *
 * A parsed schedule is a rule: evaluate it with a context (base date, market and named events).
 * Dates are days since 1970-01-01. */
#ifndef SCHEDULE_H
#define SCHEDULE_H

#include <stddef.h>

typedef struct Schedule Schedule;
typedef struct Market Market;
typedef struct Events Events;

typedef struct {
    int *dates;
    int count, capacity;
} DateList;

typedef struct {
    int has_base, base;   /* T, only needed if the schedule uses it */
    Market *market;       /* NULL: Monday to Friday */
    const Events *events; /* NULL: none */
    Market *fixing;       /* the fixing calendar (F, FD, FIX); NULL: Monday to Friday */
} Context;

/* NULL on a syntax error, with the message in error */
Schedule *schedule_parse(const char *text, char *error, size_t size);
void schedule_free(Schedule *schedule);

/* 0 and the dates in out (to be freed with datelist_free), or -1 and the message in error */
int schedule_evaluate(const Schedule *schedule, const Context *context, DateList *out, char *error, size_t size);

/* Saturdays and Sundays plus the dates the rule yields with the first of January of a year as base date;
 * the market keeps the rule */
Market *market_new(Schedule *rule);
void market_free(Market *market);

/* name -> rule; the events keep the rules */
Events *events_new(void);
void events_add(Events *events, const char *name, Schedule *rule);
void events_free(Events *events);

void datelist_push(DateList *list, int date);
void datelist_free(DateList *list);

int date_of(int year, int month, int day);
void date_split(int date, int *year, int *month, int *day);
int weekday_of(int date); /* Monday 0 to Sunday 6 */
/* "2015-06-09"; 0 if it is a valid date */
int date_parse_iso(const char *text, int *date);
void date_format(int date, char out[11]);

#endif
