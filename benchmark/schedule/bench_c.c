/* Benchmark of the C schedule library, native: see README.md for the protocol shared with bench_go and bench_antlr.py.
 *
 *   bench_c ROUNDS FILE...   prints one JSON object
 *   bench_c 0                start-up only: parses and evaluates one schedule and prints its date */
#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "spec.h"

typedef struct {
    long long dates, sum, errors;
} Checksum;

static double now(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec + t.tv_nsec * 1e-9;
}

/* parse every input of the loaded specs */
static long parse_round(Spec *specs, int count) {
    char error[512];
    long errors = 0;
    for (int f = 0; f < count; f++)
        for (int i = 0; i < specs[f].count; i++) {
            Schedule *s = schedule_parse(specs[f].cases[i].input, error, sizeof error);
            if (!s) errors++;
            schedule_free(s);
        }
    return errors;
}

/* read the files (fresh markets and events), then parse and evaluate every case */
static Checksum evaluate_round(char **files, int count) {
    Checksum sum = { 0 };
    char error[512];
    for (int f = 0; f < count; f++) {
        Spec spec;
        if (spec_load(files[f], &spec)) exit(2);
        for (int i = 0; i < spec.count; i++) {
            const Case *c = &spec.cases[i];
            DateList dates = { 0 };
            Schedule *s = c->missing_market ? NULL : schedule_parse(c->input, error, sizeof error);
            if (!s || schedule_evaluate(s, &c->context, &dates, error, sizeof error)) {
                sum.errors++;
            } else {
                sum.dates += dates.count;
                for (int k = 0; k < dates.count; k++) sum.sum += dates.dates[k];
            }
            datelist_free(&dates);
            schedule_free(s);
        }
        spec_free(&spec);
    }
    return sum;
}

int main(int argc, char **argv) {
    if (argc == 2 && !strcmp(argv[1], "0")) {
        char error[256], text[11];
        Context context = { 0, 0, NULL, NULL };
        DateList dates;
        Schedule *s = schedule_parse("2015-06-09 + 3M", error, sizeof error);
        if (!s || schedule_evaluate(s, &context, &dates, error, sizeof error)) return 1;
        date_format(dates.dates[0], text);
        puts(text);
        datelist_free(&dates);
        schedule_free(s);
        return 0;
    }
    if (argc < 3) {
        fprintf(stderr, "usage: bench_c ROUNDS FILE...\n");
        return 2;
    }
    int rounds = atoi(argv[1]), count = argc - 2, cases = 0;
    char **files = argv + 2;
    Spec *specs = calloc(count, sizeof(Spec));
    for (int f = 0; f < count; f++) {
        if (spec_load(files[f], &specs[f])) return 2;
        cases += specs[f].count;
    }
    printf("{\"implementation\": \"c\", \"cases\": %d, \"parse\": [", cases);
    long parse_errors = parse_round(specs, count);   /* warm-up */
    for (int r = 0; r < rounds; r++) {
        double start = now();
        parse_round(specs, count);
        printf("%s%.6f", r ? ", " : "", now() - start);
    }
    printf("], \"evaluate\": [");
    Checksum sum = evaluate_round(files, count);   /* warm-up */
    for (int r = 0; r < rounds; r++) {
        double start = now();
        evaluate_round(files, count);
        printf("%s%.6f", r ? ", " : "", now() - start);
    }
    printf("], \"checksum\": {\"parse_errors\": %ld, \"dates\": %lld, \"sum\": %lld, \"errors\": %lld}}\n",
           parse_errors, sum.dates, sum.sum, sum.errors);
    for (int f = 0; f < count; f++) spec_free(&specs[f]);
    free(specs);
    return 0;
}
