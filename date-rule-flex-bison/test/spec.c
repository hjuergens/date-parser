#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "spec.h"

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

/* [base] text, [base market] text or [base market fixing] text, base an ISO date and the calendars of capitals, digits
 * and _: 1 if the prefix is there (then it is cut off); a series such as [2026-01-01 : 2026-01-31] is no prefix */
static int prefix(char *text, int *base, char **market, char **fixing, char **rest) {
    char date[11];
    if (text[0] != '[' || strlen(text) < 13) return 0;
    memcpy(date, text + 1, 10);
    date[10] = 0;
    if (date_parse_iso(date, base)) return 0;
    char *p = text + 11, *names[2] = { NULL, NULL };
    for (int i = 0; i < 2 && *p == ' ' && p[1] != ']'; i++) {
        char *name = ++p;
        while ((*p >= 'A' && *p <= 'Z') || (*p >= '0' && *p <= '9') || *p == '_') p++;
        if (p == name) return 0;
        names[i] = name;
    }
    if (p[0] != ']' || p[1] != ' ') return 0;
    if (names[1]) names[1][-1] = 0;
    *p = 0;
    *market = names[0];
    *fixing = names[1];
    *rest = p + 2;
    return 1;
}

static Market *find_market(Spec *spec, const char *name) {
    for (int m = 0; name && m < spec->market_count; m++)
        if (!strcmp(spec->names[m], name)) return spec->markets[m];
    return NULL;
}

static void declare(Spec *spec, char *line, int number, const char *path) {
    char error[512];
    int is_market = line[0] == 'm';
    char *equals = strchr(line, '=');
    if (!equals) return;
    *equals = 0;
    char *name = strip(line + (is_market ? 7 : 6));
    Schedule *rule = schedule_parse(strip(equals + 1), error, sizeof error);
    if (!rule) {
        printf("FAIL %s:%d: %s: %s\n", path, number, name, error);
        spec->broken++;
    } else if (is_market) {
        spec->markets = realloc(spec->markets, (spec->market_count + 1) * sizeof(Market *));
        spec->names = realloc(spec->names, (spec->market_count + 1) * sizeof(char *));
        spec->markets[spec->market_count] = market_new(rule);
        spec->names[spec->market_count++] = strdup(name);
    } else {
        events_add(spec->events, name, rule);
    }
}

static void add_case(Spec *spec, char *line, int number) {
    char *text = strdup(line), *trimmed = strip(line), *arrow = NULL;
    for (char *p = strstr(trimmed, "=>"); p; p = strstr(p + 1, "=>")) arrow = p;
    if (!arrow) {
        free(text);
        return;
    }
    *arrow = 0;
    Case c = { number, text, NULL, NULL, { 0, 0, NULL, spec->events, NULL }, NULL };
    char *input = strip(trimmed), *market, *fixing, *rest;
    c.expected = strdup(strip(arrow + 2));
    if (prefix(input, &c.context.base, &market, &fixing, &rest)) {
        input = rest;
        c.context.has_base = 1;
        c.context.market = find_market(spec, market);
        c.context.fixing = find_market(spec, fixing);
        if ((market && !c.context.market) || (fixing && !c.context.fixing)) c.missing_market = "missing";
    }
    c.input = strdup(input);
    spec->cases = realloc(spec->cases, (spec->count + 1) * sizeof(Case));
    spec->cases[spec->count++] = c;
}

int spec_load(const char *path, Spec *spec) {
    *spec = (Spec) { 0 };
    char *text = read_file(path);
    if (!text) return -1;
    spec->events = events_new();
    for (int pass = 0; pass < 2; pass++) {
        char *copy = strdup(text);
        int number = 0;
        for (char *line = copy, *next; line; line = next) {
            next = strchr(line, '\n');
            if (next) *next++ = 0;
            number++;
            int declaration = !strncmp(line, "market ", 7) || !strncmp(line, "event ", 6);
            if (line[0] == '#' || declaration != !pass) continue;
            char *check = line;
            while (isspace((unsigned char) *check)) check++;
            if (!*check) continue;
            if (declaration) declare(spec, line, number, path);
            else add_case(spec, line, number);
        }
        free(copy);
    }
    free(text);
    return 0;
}

void spec_free(Spec *spec) {
    for (int i = 0; i < spec->count; i++) {
        free(spec->cases[i].text);
        free(spec->cases[i].input);
        free(spec->cases[i].expected);
    }
    free(spec->cases);
    for (int m = 0; m < spec->market_count; m++) {
        free(spec->names[m]);
        market_free(spec->markets[m]);
    }
    free(spec->markets);
    free(spec->names);
    events_free(spec->events);
    *spec = (Spec) { 0 };
}
