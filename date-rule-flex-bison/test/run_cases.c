/* Runs the shared cases of the spec files given as arguments (examples/schedule.txt, catalog.txt, date-holidays.txt),
 * read by spec.c. Prints every failing case and a summary; the exit status is 1 if a case fails. */
#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif
#include <stdio.h>
#include <string.h>
#include "spec.h"

/* the dates as "2015-06-09, 2015-06-10" or "none" */
static void describe(const DateList *dates, char *out, size_t size) {
    out[0] = 0;
    if (!dates->count) snprintf(out, size, "none");
    for (int i = 0; i < dates->count; i++) {
        char text[11];
        date_format(dates->dates[i], text);
        size_t length = strlen(out);
        snprintf(out + length, size - length, "%s%s", i ? ", " : "", text);
    }
}

static int same(const DateList *dates, const char *expected) {
    if (!strcmp(expected, "none")) return dates->count == 0;
    char copy[8192];
    snprintf(copy, sizeof copy, "%s", expected);
    int i = 0;
    for (char *item = strtok(copy, ", "); item; item = strtok(NULL, ", "), i++) {
        int date;
        if (date_parse_iso(item, &date) || i >= dates->count || dates->dates[i] != date) return 0;
    }
    return i == dates->count;
}

int main(int argc, char **argv) {
    int cases = 0, failed = 0;
    for (int f = 1; f < argc; f++) {
        Spec spec;
        if (spec_load(argv[f], &spec)) {
            fprintf(stderr, "cannot read %s\n", argv[f]);
            return 2;
        }
        failed += spec.broken;
        for (int i = 0; i < spec.count; i++) {
            const Case *c = &spec.cases[i];
            char error[512], got[8192];
            cases++;
            if (c->missing_market) {
                printf("FAIL %s:%d: no market in %s\n", argv[f], c->line, c->text);
                failed++;
                continue;
            }
            DateList dates = { 0 };
            Schedule *schedule = schedule_parse(c->input, error, sizeof error);
            int ok = schedule && !schedule_evaluate(schedule, &c->context, &dates, error, sizeof error);
            if (ok) describe(&dates, got, sizeof got);
            else snprintf(got, sizeof got, "error: %s", error);
            if (!strcmp(c->expected, "error") ? ok : !ok || !same(&dates, c->expected)) {
                failed++;
                printf("FAIL %s:%d: %s\n     got %s\n", argv[f], c->line, c->text, got);
            }
            datelist_free(&dates);
            schedule_free(schedule);
        }
        spec_free(&spec);
    }
    printf("%d cases, %d passed, %d failed\n", cases, cases - failed, failed);
    return failed ? 1 : 0;
}
