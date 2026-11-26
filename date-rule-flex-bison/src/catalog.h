/* Rule files in C: the declarations of rulefile.y evaluated by catalog.c, the port of catalog.py (date-rule-lark).
 *
 * catalog_parse reads a rule file (rule KEY for CLASS in SCOPES ... = ...;, weekend, calendar); catalog_holidays gives
 * the occurrences of the rules of a scope in a year, catalog_schedule evaluates one expression of the rule language.
 * Rules only project: announcements are data, the dates an authority published for a key. Dates are days since
 * 1970-01-01, as in schedule.h. */
#ifndef CATALOG_H
#define CATALOG_H

#include <stddef.h>
#include "schedule.h"

typedef struct RuleCatalog RuleCatalog;

/* the dates an authority published for a key: a rule's key, or a key without a rule that @key reads */
typedef struct {
    const char *key;
    const int *dates;
    int count;
} Announcement;

/* cites "Act No. 178 of 1948" art "3(2)" url "..." label official */
typedef struct Citation {
    const char *source, *article, *url, *label;   /* article and url may be NULL */
    struct Citation *next;
} Citation;

/* a date with the rule that produced it; the strings belong to the catalog */
typedef struct {
    int date;
    const char *rule, *event_class, *kind, *precision;
    const char *scenario;       /* the ONE OF alternative the date belongs to, or NULL */
    const char *session[3];     /* opens, closes, time zone (may be NULL); session[0] NULL without session */
    const Citation *citations;
    int projected;              /* from a projection: a Hijri method by sighting, ONE OF, a key with announced dates */
} Occurrence;

typedef struct {
    Occurrence *items;
    int count, capacity;
} OccurrenceList;

/* the catalog of a rule file, or NULL and the message in error: a syntax error, overlapping versions of a rule, an
 * unknown or a cyclic reference; announcements may be NULL */
RuleCatalog *catalog_parse(const char *text, const Announcement *announcements, int count, char *error, size_t size);
void catalog_free(RuleCatalog *catalog);

/* 0 and the occurrences of the rules of the scope in the year, sorted by date and rule key, or -1 and the message */
int catalog_holidays(RuleCatalog *catalog, const char *scope, int year, OccurrenceList *out, char *error, size_t size);

/* 0 and the dates of one expression of the rule language with T = base on the calendar of the scope (NULL: Monday to
 * Friday) and, for > CUTOFF, the base with its time of day (seconds since 1970; NULL: none), or -1 and the message */
int catalog_schedule(RuleCatalog *catalog, const char *text, int base, const char *scope, const long long *instant,
                     DateList *out, char *error, size_t size);

void occurrencelist_free(OccurrenceList *list);

#endif
