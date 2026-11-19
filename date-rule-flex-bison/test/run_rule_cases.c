/* Runs the shared cases of examples/rules.txt (the argument): blocks of declarations, each followed by its cases;
 * declarations after cases start a new block. A case is "<scope> <year> => <dates>" (the occurrences of the scope,
 * a date of a ONE OF alternative followed by its name) or "[<base>] <schedule> => <dates>"; "none" and "error" as in
 * the other spec files. Prints every failing case and a summary; the exit status is 1 if a case fails. */
#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "catalog.h"

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

static void append(char *out, size_t size, const char *text) {
    size_t length = strlen(out);
    snprintf(out + length, size - length, "%s%s", length ? ", " : "", text);
}

/* the result of a case as the spec writes it */
static void evaluate(const char *declarations, const char *text, char *out, size_t size) {
    char error[512];
    out[0] = 0;
    RuleCatalog *catalog = catalog_parse(declarations, NULL, 0, error, sizeof error);
    if (!catalog) {
        snprintf(out, size, "error");
        return;
    }
    int base, year;
    char scope[256], date[11];
    if (text[0] == '[' && strlen(text) > 13 && text[11] == ']') {   /* [base] schedule */
        memcpy(date, text + 1, 10);
        date[10] = 0;
        DateList dates;
        if (date_parse_iso(date, &base) || catalog_schedule(catalog, text + 13, base, NULL, &dates, error, sizeof error)) {
            snprintf(out, size, "error");
        } else {
            for (int i = 0; i < dates.count; i++) {
                date_format(dates.dates[i], date);
                append(out, size, date);
            }
            datelist_free(&dates);
        }
    } else if (sscanf(text, "%255s %d", scope, &year) == 2) {   /* scope year */
        OccurrenceList occurrences;
        if (catalog_holidays(catalog, scope, year, &occurrences, error, sizeof error)) {
            snprintf(out, size, "error");
        } else {
            for (int i = 0; i < occurrences.count; i++) {
                char item[300];
                date_format(occurrences.items[i].date, date);
                snprintf(item, sizeof item, "%s%s%s", date, occurrences.items[i].scenario ? " " : "",
                         occurrences.items[i].scenario ? occurrences.items[i].scenario : "");
                append(out, size, item);
            }
            occurrencelist_free(&occurrences);
        }
    } else {
        snprintf(out, size, "error");
    }
    if (!out[0]) snprintf(out, size, "none");
    catalog_free(catalog);
}

static char *strip(char *text) {
    while (*text == ' ' || *text == '\t' || *text == '\r') text++;
    char *end = text + strlen(text);
    while (end > text && (end[-1] == ' ' || end[-1] == '\t' || end[-1] == '\r')) *--end = 0;
    return text;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: run_rule_cases examples/rules.txt\n");
        return 2;
    }
    char *text = read_file(argv[1]);
    if (!text) {
        fprintf(stderr, "cannot read %s\n", argv[1]);
        return 2;
    }
    size_t capacity = strlen(text) + 1;
    char *declarations = calloc(capacity, 1), got[8192];
    if (!declarations) abort();
    int cases = 0, failed = 0, after_cases = 0, number = 0;
    for (char *line = text, *next; line; line = next) {
        next = strchr(line, '\n');
        if (next) *next++ = 0;
        number++;
        if (!*strip(line) || line[0] == '#') continue;
        char *arrow = strstr(line, "=>");
        while (arrow && strstr(arrow + 2, "=>")) arrow = strstr(arrow + 2, "=>");
        if (arrow) {
            *arrow = 0;
            char *input = strip(line), *expected = strip(arrow + 2);
            cases++;
            evaluate(declarations, input, got, sizeof got);
            if (strcmp(got, expected)) {
                failed++;
                printf("FAIL %s:%d: %s => %s\n     got %s\n", argv[1], number, input, expected, got);
            }
            after_cases = 1;
            continue;
        }
        if (after_cases) declarations[0] = 0, after_cases = 0;
        strcat(strcat(declarations, line), "\n");
    }
    printf("%d rule cases, %d passed, %d failed\n", cases, cases - failed, failed);
    free(declarations);
    free(text);
    return failed ? 1 : 0;
}
