/* Runs the shared cases of the spec files given as arguments (examples/schedule.txt, catalog.txt, date-holidays.txt):
 *
 *   <input> => <expected>      [<base>] <input> => ...      [<base> <market>] <input> => ...
 *   market <name> = <schedule>  event <name> = <schedule>
 *
 * <expected> is a comma-separated list of ISO dates, "none" or "error". Prints every failing case and a summary;
 * the exit status is 1 if a case fails. */
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "schedule.h"

typedef struct {
    char *name;
    Market *market;
} Named;

static char *strip(char *text) {
    while (isspace((unsigned char) *text)) text++;
    char *end = text + strlen(text);
    while (end > text && isspace((unsigned char) end[-1])) *--end = 0;
    return text;
}

static char *read_file(const char *path) {
    FILE *file = fopen(path, "rb");
    if (!file) return NULL;
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    rewind(file);
    char *text = malloc(size + 1);
    if (!text || fread(text, 1, size, file) != (size_t) size) abort();
    text[size] = 0;
    fclose(file);
    return text;
}

/* [base] text or [base market] text: 1 if the prefix is there */
static int prefix(char *text, char **base, char **market, char **rest) {
    if (text[0] != '[') return 0;
    char *close = strchr(text, ']');
    if (!close || close[1] != ' ' || close == text + 1) return 0;
    *close = 0;
    *base = text + 1;
    char *space = strchr(*base, ' ');
    *market = NULL;
    if (space) {
        *space = 0;
        *market = space + 1;
        if (!**market || strchr(*market, ' ')) return 0;
    }
    *rest = close + 2;
    return 1;
}

/* the dates as "2015-06-09, 2015-06-10", "none" or "error: ..." */
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

static int same(const DateList *dates, char *expected) {
    if (!strcmp(expected, "none")) return dates->count == 0;
    int i = 0;
    for (char *item = strtok(expected, ","); item; item = strtok(NULL, ","), i++) {
        int date;
        if (date_parse_iso(strip(item), &date) || i >= dates->count || dates->dates[i] != date) return 0;
    }
    return i == dates->count;
}

int main(int argc, char **argv) {
    int cases = 0, failed = 0;
    for (int f = 1; f < argc; f++) {
        char *text = read_file(argv[f]);
        if (!text) {
            fprintf(stderr, "cannot read %s\n", argv[f]);
            return 2;
        }
        Named *markets = NULL;
        int count = 0;
        Events *events = events_new();
        char error[512];
        /* the declarations first: the events are known to every case of the file */
        for (int pass = 0; pass < 2; pass++) {
            char *copy = strdup(text), *save = NULL;
            int number = 0;
            for (char *line = strtok_r(copy, "\n", &save); line; line = strtok_r(NULL, "\n", &save)) {
                number++;
                char *original = strdup(line), *trimmed = strip(line);
                int declaration = !strncmp(line, "market ", 7) || !strncmp(line, "event ", 6);
                if (!*trimmed || line[0] == '#' || declaration != !pass) {
                    free(original);
                    continue;
                }
                if (declaration) {
                    char *equals = strchr(line, '='), *name = strip(line + (line[0] == 'm' ? 7 : 6));
                    *equals = 0;
                    name = strip(name);
                    Schedule *rule = schedule_parse(strip(equals + 1), error, sizeof error);
                    if (!rule) {
                        printf("FAIL %s:%d: %s: %s\n", argv[f], number, name, error);
                        failed++;
                    } else if (line[0] == 'm') {
                        markets = realloc(markets, (count + 1) * sizeof(Named));
                        markets[count++] = (Named) { strdup(name), market_new(rule) };
                    } else {
                        events_add(events, name, rule);
                    }
                    free(original);
                    continue;
                }
                char *arrow = NULL;
                for (char *p = strstr(trimmed, "=>"); p; p = strstr(p + 1, "=>")) arrow = p;
                if (!arrow) {
                    free(original);
                    continue;
                }
                *arrow = 0;
                char *input = strip(trimmed), *expected = strip(arrow + 2), *base, *market, *rest;
                Context context = { 0, 0, NULL, events };
                if (prefix(input, &base, &market, &rest)) {
                    input = rest;
                    if (date_parse_iso(base, &context.base)) abort();
                    context.has_base = 1;
                    for (int m = 0; market && m < count; m++)
                        if (!strcmp(markets[m].name, market)) context.market = markets[m].market;
                    if (market && !context.market) {
                        printf("FAIL %s:%d: no market %s\n", argv[f], number, market);
                        failed++, cases++;
                        free(original);
                        continue;
                    }
                }
                char got[4096];
                DateList dates = { 0 };
                Schedule *schedule = schedule_parse(input, error, sizeof error);
                int ok = schedule && !schedule_evaluate(schedule, &context, &dates, error, sizeof error);
                if (ok) describe(&dates, got, sizeof got);
                else snprintf(got, sizeof got, "error: %s", error);
                int pass_case = !strcmp(expected, "error") ? !ok : ok && same(&dates, expected);
                cases++;
                if (!pass_case) {
                    failed++;
                    printf("FAIL %s:%d: %s\n     got %s\n", argv[f], number, original, got);
                }
                datelist_free(&dates);
                schedule_free(schedule);
                free(original);
            }
            free(copy);
        }
        for (int m = 0; m < count; m++) {
            free(markets[m].name);
            market_free(markets[m].market);
        }
        free(markets);
        events_free(events);
        free(text);
    }
    printf("%d cases, %d passed, %d failed\n", cases, cases - failed, failed);
    return failed ? 1 : 0;
}
