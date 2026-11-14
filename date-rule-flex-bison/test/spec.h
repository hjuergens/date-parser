/* Reads a spec file (examples/schedule.txt, catalog.txt, date-holidays.txt):
 *
 *   <input> => <expected>      [<base>] <input> => ...      [<base> <market>] <input> => ...
 *   market <name> = <schedule>  event <name> = <schedule>
 *
 * The declarations are read first, so the events are known to every case of the file; each spec has its own markets,
 * whose holiday caches fill as the cases are evaluated. */
#ifndef SPEC_H
#define SPEC_H

#include "schedule.h"

typedef struct {
    int line;
    char *text;       /* the line as it is in the file */
    char *input;      /* without the prefix */
    char *expected;   /* a comma-separated list of ISO dates, "none" or "error" */
    Context context;
    const char *missing_market;   /* the prefix names a market that isn't declared */
} Case;

typedef struct {
    Case *cases;
    int count;
    int broken;       /* declarations that don't parse, reported on stdout */
    Market **markets;
    char **names;
    int market_count;
    Events *events;
} Spec;

/* 0 and the spec, or -1 if the file can't be read */
int spec_load(const char *path, Spec *spec);
void spec_free(Spec *spec);

#endif
