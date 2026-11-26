/* Semantics of the shared "schedule" example: periods, business days, IMM and spot dates, ranges, annual dates
 * (holiday rules, also in the Hijri, Hebrew and Chinese calendars), rolling and business day conventions, named
 * events. The arithmetic follows java.time, like ScheduleSemantics.java and semantics.py in the other modules.
 *
 * An evaluation error unwinds with longjmp to schedule_evaluate, which frees what the evaluation allocated. */
#include <limits.h>
#include <math.h>
#include <pthread.h>
#include <setjmp.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ast.h"
#include "eval.h"
#include "lunar_tables.h"

/* ---- arena ---- */

struct Block {
    Block *next;
    max_align_t data[];
};

void *arena_alloc(Schedule *schedule, size_t size) {
    Block *block = calloc(1, sizeof(Block) + size);
    if (!block) abort();
    block->next = schedule->arena;
    schedule->arena = block;
    return block->data;
}

char *arena_strdup(Schedule *schedule, const char *text) {
    return strcpy(arena_alloc(schedule, strlen(text) + 1), text);
}

void arena_free(Schedule *schedule) {
    while (schedule->arena) {
        Block *next = schedule->arena->next;
        free(schedule->arena);
        schedule->arena = next;
    }
}

/* ---- dates: days since 1970-01-01 in the proleptic Gregorian calendar ---- */

static long floor_div(long a, long b) { return a / b - (a % b != 0 && (a < 0) != (b < 0)); }
static long floor_mod(long a, long b) { return a - b * floor_div(a, b); }

int date_of(int year, int month, int day) {
    long y = year - (month <= 2);
    long era = floor_div(y, 400);
    long yoe = y - era * 400;
    long doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
    long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return (int) (era * 146097 + doe - 719468);
}

void date_split(int date, int *year, int *month, int *day) {
    long z = date + 719468L;
    long era = floor_div(z, 146097);
    long doe = z - era * 146097;
    long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    long mp = (5 * doy + 2) / 153;
    *day = (int) (doy - (153 * mp + 2) / 5 + 1);
    *month = (int) (mp < 10 ? mp + 3 : mp - 9);
    *year = (int) (yoe + era * 400 + (*month <= 2));
}

int year_of(int date) {
    int year, month, day;
    date_split(date, &year, &month, &day);
    return year;
}

static int month_of(int date) {
    int year, month, day;
    date_split(date, &year, &month, &day);
    return month;
}

int weekday_of(int date) { return (int) floor_mod(date + 3, 7); }

static int is_leap(long year) { return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0; }

static int month_length(long year, int month) {
    static const int LENGTHS[] = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
    return month == 2 && is_leap(year) ? 29 : LENGTHS[month - 1];
}

static int valid(long year, long month, long day) {
    return year >= 1 && year <= 9999 && month >= 1 && month <= 12 && day >= 1 && day <= month_length(year, (int) month);
}

int date_parse_iso(const char *text, int *date) {
    int year, month, day, length = 0;
    if (strlen(text) != 10 || sscanf(text, "%4d-%2d-%2d%n", &year, &month, &day, &length) != 3 || length != 10
        || !valid(year, month, day))
        return -1;
    *date = date_of(year, month, day);
    return 0;
}

void date_format(int date, char out[11]) {
    int year, month, day;
    date_split(date, &year, &month, &day);
    snprintf(out, 11, "%04u-%02u-%02u", (unsigned) year % 10000, (unsigned) month % 100, (unsigned) day % 100);
}

void datelist_push(DateList *list, int date) {
    if (list->count == list->capacity) {
        list->capacity = list->capacity ? 2 * list->capacity : 8;
        list->dates = realloc(list->dates, list->capacity * sizeof(int));
        if (!list->dates) abort();
    }
    list->dates[list->count++] = date;
}

void datelist_free(DateList *list) {
    free(list->dates);
    *list = (DateList) { 0 };
}

static int compare(const void *a, const void *b) {
    int x = *(const int *) a, y = *(const int *) b;
    return (x > y) - (x < y);
}

int compare_dates(const void *a, const void *b) { return compare(a, b); }

/* ---- evaluation ---- */

/* the value of a node with several values that isn't bound */
#define UNBOUND INT_MIN

_Noreturn void fail(Eval *eval, const char *format, ...) {
    va_list args;
    va_start(args, format);
    vsnprintf(eval->error, eval->size, format, args);
    va_end(args);
    longjmp(eval->jump, 1);
}

DateList *temp(Eval *eval) {
    if (eval->count == eval->capacity) {
        eval->capacity = eval->capacity ? 2 * eval->capacity : 8;
        eval->temps = realloc(eval->temps, eval->capacity * sizeof(DateList *));
        if (!eval->temps) abort();
    }
    DateList *list = calloc(1, sizeof(DateList));
    if (!list) abort();
    return eval->temps[eval->count++] = list;
}

void own_market(Eval *eval, Market *market) {
    if (eval->market_count == eval->market_capacity) {
        eval->market_capacity = eval->market_capacity ? 2 * eval->market_capacity : 8;
        eval->markets = realloc(eval->markets, eval->market_capacity * sizeof(Market *));
        if (!eval->markets) abort();
    }
    eval->markets[eval->market_count++] = market;
}

/* the temporary lists and the markets of an evaluation */
static void release(Eval *eval) {
    for (int i = 0; i < eval->count; i++) {
        datelist_free(eval->temps[i]);
        free(eval->temps[i]);
    }
    free(eval->temps);
    for (int i = 0; i < eval->market_count; i++) {
        if (eval->markets[i]->release) eval->markets[i]->release(eval->markets[i]);
        free(eval->markets[i]);
    }
    free(eval->markets);
    free(eval);
}

static int make(Eval *eval, long long year, long long month, long long day) {
    if (!valid(year, month, day)) fail(eval, "not a date: %lld-%lld-%lld", year, month, day);
    return date_of((int) year, (int) month, (int) day);
}

int add_days(Eval *eval, int date, long long days) {
    long long result = date + days;
    if (result < date_of(1, 1, 1) || result > date_of(9999, 12, 31)) fail(eval, "date out of range");
    return (int) result;
}

static void evaluate_into(Eval *eval, const Item *items, const Ctx *ctx, DateList *out);

/* a schedule evaluated on its own, e.g. the rule of a market; its errors become errors of this evaluation */
static DateList *nested(Eval *eval, const Schedule *schedule, const Ctx *ctx) {
    char error[256];
    Context context = { ctx->has_base, ctx->base, ctx->market, ctx->events, ctx->fixing };
    DateList *list = temp(eval);
    if (schedule_evaluate(schedule, &context, list, error, sizeof error)) fail(eval, "%s", error);
    return list;
}

static int base(Eval *eval, const Ctx *ctx) {
    if (!ctx->has_base) fail(eval, "no base date");
    return ctx->base;
}

/* Monday to Friday except the holidays of the year before, the year and the year after (an observed holiday can
 * fall into the neighbouring year) */
static const DateList *cached(Market *market, int year) {
    for (int i = 0; i < market->count; i++)
        if (market->years[i]->year == year) return &market->years[i]->holidays;
    return NULL;
}

/* computed without the lock (an error unwinds with longjmp); if another thread added the year meanwhile, its entry wins */
static const DateList *holidays(Eval *eval, Market *market, int year) {
    pthread_mutex_lock(&market->lock);
    const DateList *found = cached(market, year);
    pthread_mutex_unlock(&market->lock);
    if (found) return found;
    Ctx first = { 1, make(eval, year, 1, 1), NULL, NULL, NULL, 0, NULL, 0, NULL, NULL, NULL, 0, 0, 0, 0 };
    DateList *dates = nested(eval, market->rule, &first);
    __typeof__(*market->years) entry = calloc(1, sizeof *entry);
    if (!entry) abort();
    entry->year = year;
    for (int i = 0; i < dates->count; i++) datelist_push(&entry->holidays, dates->dates[i]);
    if (entry->holidays.count) qsort(entry->holidays.dates, entry->holidays.count, sizeof(int), compare);
    pthread_mutex_lock(&market->lock);
    found = cached(market, year);
    if (!found) {
        if (market->count == market->capacity) {
            market->capacity = market->capacity ? 2 * market->capacity : 8;
            market->years = realloc(market->years, market->capacity * sizeof *market->years);
            if (!market->years) abort();
        }
        market->years[market->count++] = entry;
        found = &entry->holidays;
        entry = NULL;
    }
    pthread_mutex_unlock(&market->lock);
    if (entry) {
        datelist_free(&entry->holidays);
        free(entry);
    }
    return found;
}

int is_business_day(Eval *eval, Market *market, int date) {
    if (market && market->business) return market->business(eval, market, date);
    if (weekday_of(date) >= 5) return 0;
    if (!market) return 1;
    int year = year_of(date);
    for (int y = year - 1; y <= year + 1; y++) {
        const DateList *list = holidays(eval, market, y);
        if (list->count && bsearch(&date, list->dates, list->count, sizeof(int), compare)) return 0;
    }
    return 1;
}

int is_holiday(Eval *eval, Market *market, int date) {
    if (market && market->holiday) return market->holiday(eval, market, date);
    return weekday_of(date) < 5 && !is_business_day(eval, market, date);
}

/* the calendar of ON ...; without ON the market of the context (in a rule: its own scope) */
static Market *market_of(Eval *eval, const Calendar *on, const Ctx *ctx, const char *pair) {
    if (!on) return ctx->market;
    if (!ctx->catalog) fail(eval, "ON needs a rule catalog");
    return catalog_resolve(eval, on, ctx, pair);
}

/* count business days of the market later (earlier if negative) */
int plus_business_days(Eval *eval, int date, long long count, Market *market) {
    int step = count > 0 ? 1 : -1;
    while (count) {
        date = add_days(eval, date, step);
        if (is_business_day(eval, market, date)) count -= step;
    }
    return date;
}

/* months first (day clamped to the end of the month), then days; business days of the market and fixing days of the
 * fixing calendar on their own */
static int plus(Eval *eval, int date, Period period, int sign, Market *market, Market *fixing) {
    if (period.fixing) return plus_business_days(eval, date, sign * period.fixing, fixing);
    if (period.business) return plus_business_days(eval, date, sign * period.business, market);
    int year, month, day;
    date_split(date, &year, &month, &day);
    long long total = year * 12LL + month - 1 + sign * period.months;
    long long y = total >= 0 ? total / 12 : -((-total + 11) / 12);
    int m = (int) (total - 12 * y) + 1;
    if (y < 1 || y > 9999) fail(eval, "year out of range: %lld", y);
    int clamped = date_of((int) y, m, day < month_length(y, m) ? day : month_length(y, m));
    return add_days(eval, clamped, sign * period.days);
}

/* the business days from trade to spot: 1 for USD against CAD, KZT, PHP, RUB, TRY or UZS, 0 against AZN, KGS, MDL,
 * MNT or UAH, otherwise 2 (also without pair; a cross takes T+2) */
static int spot_lag(const char *pair) {
    static const char *const T_PLUS_ONE[] = { "CAD", "KZT", "PHP", "RUB", "TRY", "UZS", NULL };
    static const char *const T_PLUS_ZERO[] = { "AZN", "KGS", "MDL", "MNT", "UAH", NULL };
    const char *other = !pair || strlen(pair) != 6 ? NULL
        : !strncmp(pair, "USD", 3) ? pair + 3 : !strcmp(pair + 3, "USD") ? pair : NULL;
    for (int i = 0; other && T_PLUS_ONE[i]; i++)
        if (!strncmp(other, T_PLUS_ONE[i], 3)) return 1;
    for (int i = 0; other && T_PLUS_ZERO[i]; i++)
        if (!strncmp(other, T_PLUS_ZERO[i], 3)) return 0;
    return 2;
}

/* FX spot: T+1, T+0 or T+2 by the pair, see spot_lag */
static int spot(Eval *eval, int trade, const char *pair, Market *market) {
    return plus_business_days(eval, trade, spot_lag(pair), market);
}

/* the IMM date (third Wednesday of March, June, September or December) strictly after (before) the date */
static int imm(Eval *eval, int date, int direction) {
    int year, month, day;
    date_split(date, &year, &month, &day);
    for (;;) {
        if (month % 3 == 0) {
            int first = make(eval, year, month, 1);
            int third = first + (int) floor_mod(2 - weekday_of(first), 7) + 14;
            if (direction > 0 ? third > date : third < date) return third;
        }
        month += direction;
        if (month == 13) month = 1, year++;
        if (month == 0) month = 12, year--;
    }
}

/* ---- calendars ---- */

/* Western Easter Sunday (anonymous Gregorian algorithm) */
static int easter(Eval *eval, long year) {
    long a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
    long g = (8 * b + 13) / 25, h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
    long l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
    long n = h + l - 7 * m + 114;
    return make(eval, year, n / 31, n % 31 + 1);
}

/* the Hebrew calendar after Reingold and Dershowitz, Calendrical Calculations; months from NISAN = 1, ADAR = 12
 * (Adar I in a leap year), ADARII = 13; the year begins with TISHRI = 7 */
static int hebrew_leap(long year) { return floor_mod(7 * year + 1, 19) < 7; }

static long hebrew_elapsed(long year) {
    long months = floor_div(235 * year - 234, 19);
    long parts = 12084 + 13753 * months;
    long day = 29 * months + floor_div(parts, 25920);
    return floor_mod(3 * (day + 1), 7) < 3 ? day + 1 : day;
}

static long hebrew_new_year(long year) {
    long before = hebrew_elapsed(year - 1), now = hebrew_elapsed(year), after = hebrew_elapsed(year + 1);
    long correction = after - now == 356 ? 2 : now - before == 382 ? 1 : 0;
    return -1373427 + now + correction;   /* R.D. days, day 1 is 0001-01-01 */
}

static int hebrew_month_length(long year, int month) {
    long days = hebrew_new_year(year + 1) - hebrew_new_year(year);
    if (month == 2 || month == 4 || month == 6 || month == 10 || month == 13) return 29;
    if (month == 12 && !hebrew_leap(year)) return 29;
    if (month == 8 && days % 10 != 5) return 29;   /* Heshvan is long in a year of 355 or 385 days */
    if (month == 9 && days % 10 == 3) return 29;   /* Kislev is short in a year of 353 or 383 days */
    return 30;
}

static long hebrew_day(long year, int month, int day) {
    long rd = hebrew_new_year(year) + day - 1;
    int last = hebrew_leap(year) ? 13 : 12;
    if (month < 7) {
        for (int m = 7; m <= last; m++) rd += hebrew_month_length(year, m);
        for (int m = 1; m < month; m++) rd += hebrew_month_length(year, m);
    } else {
        for (int m = 7; m < month; m++) rd += hebrew_month_length(year, m);
    }
    return rd - 719163;   /* R.D. of 1970-01-01 */
}

/* the year of the table that a day falls into, or the first year - 1 */
static int table_year(const int (*months)[12][2], int first, int last, int date) {
    int year = first - 1;
    for (int y = first; y <= last && months[y - first][0][0] <= date; y++) year = y;
    return year;
}

/* the days of a Hijri, Hebrew or Chinese month in the Gregorian year into hits[2]: one, or two when the lunar year is
 * shorter than the Gregorian one (1 RAMADAN in 2030: 5 January and 26 December); an error if there is none */
static int lunar_dates(Eval *eval, long day, int month, long year, int hits[2]) {
    int count_hits = 0, candidates[200], count = 0;
    if (month < HEBREW) {   /* Hijri */
        const int *end = HIJRI_MONTHS[HIJRI_LAST_YEAR - HIJRI_FIRST_YEAR][11];
        if (year < 1 || year > 9999 || date_of((int) year, 1, 1) < HIJRI_MONTHS[0][0][0]
            || date_of((int) year, 12, 31) >= end[0] + end[1])
            fail(eval, "no Hijri dates in %ld", year);
        int first = table_year(HIJRI_MONTHS, HIJRI_FIRST_YEAR, HIJRI_LAST_YEAR, date_of((int) year, 1, 1));
        int last = table_year(HIJRI_MONTHS, HIJRI_FIRST_YEAR, HIJRI_LAST_YEAR, date_of((int) year, 12, 31));
        for (int y = first; y <= last; y++) {
            const int *m = HIJRI_MONTHS[y - HIJRI_FIRST_YEAR][month - HIJRI];
            if (day >= 1 && day <= m[1]) candidates[count++] = m[0] + (int) day - 1;
        }
    } else if (month < CHINESE) {
        for (long y = year + 3760; y <= year + 3761; y++) {
            int m = month - HEBREW + 1;
            if (y >= 1 && (m < 13 || hebrew_leap(y)) && day >= 1 && day <= hebrew_month_length(y, m))
                candidates[count++] = (int) hebrew_day(y, m, (int) day);
        }
    } else {
        for (long y = year - 1; y <= year; y++) {
            if (y < CHINESE_FIRST_YEAR || y > CHINESE_LAST_YEAR) continue;
            const int *m = CHINESE_MONTHS[y - CHINESE_FIRST_YEAR][month - CHINESE];
            if (day >= 1 && day <= m[1]) candidates[count++] = m[0] + (int) day - 1;
        }
    }
    for (int i = 0; i < count; i++)
        if (candidates[i] >= date_of(1, 1, 1) && candidates[i] <= date_of(9999, 12, 31) && year_of(candidates[i]) == year) {
            if (count_hits == 2) fail(eval, "%ld of month %d occurs more than twice in %ld", day, month, year);
            hits[count_hits++] = candidates[i];
        }
    if (!count_hits) fail(eval, "%ld of month %d occurs 0 times in %ld", day, month, year);
    if (count_hits == 2 && hits[1] < hits[0]) { int t = hits[0]; hits[0] = hits[1]; hits[1] = t; }
    return count_hits;
}

/* the day of a lunar month in the Gregorian year, where one date is needed (an anchor); an error if it occurs twice */
static int lunar(Eval *eval, long day, int month, long year) {
    int hits[2], n = lunar_dates(eval, day, month, year, hits);
    if (n != 1) fail(eval, "%ld of month %d occurs %d times in %ld", day, month, n, year);
    return hits[0];
}

/* the terms of VSOP87D (Meeus, Astronomical Algorithms, appendix III) with A >= 100: A (1e-8 rad or AU), B, C */
static const double L0[][3] = {
    { 175347046, 0, 0 }, { 3341656, 4.6692568, 6283.07585 }, { 34894, 4.6261024, 12566.1517 },
    { 3418, 2.8288658, 3.5231183 }, { 3497, 2.744118, 5753.3848849 }, { 3136, 3.6276704, 77713.7714681 },
    { 2676, 4.4180835, 7860.4193924 }, { 2343, 6.1351624, 3930.2096962 }, { 1273, 2.0370966, 529.6909651 },
    { 1324, 0.7424636, 11506.7697698 }, { 902, 2.0450544, 26.2983198 }, { 1199, 1.1096294, 1577.3435424 },
    { 857, 3.5084916, 398.1490034 }, { 780, 1.1788265, 5223.6939198 }, { 990, 5.2326813, 5884.9268466 },
    { 753, 2.5333905, 5507.5532387 }, { 505, 4.5829256, 18849.22755 }, { 492, 4.2050664, 775.5226113 },
    { 357, 2.9195412, 0.0673103 }, { 284, 1.8986903, 796.2980068 }, { 243, 0.3448114, 5486.7778432 },
    { 317, 5.8490195, 11790.6290887 }, { 271, 0.3148861, 10977.0788047 }, { 206, 4.8064661, 2544.3144199 },
    { 205, 1.8694781, 5573.1428014 }, { 202, 2.457678, 6069.7767546 }, { 126, 1.0830263, 20.7753955 },
    { 156, 0.8330607, 213.2990954 }, { 115, 0.6454491, 0.9803211 }, { 103, 0.6359985, 4694.0029547 },
    { 102, 4.2667982, 7.113547 }, { 132, 3.4111828, 2942.4634233 }, { 102, 0.9756922, 15720.8387849 },
};
static const double L1[][3] = {
    { 628331966747, 0, 0 }, { 206059, 2.6782346, 6283.07585 }, { 4303, 2.6351265, 12566.1517 },
    { 425, 1.5904698, 3.5231183 }, { 109, 2.96618, 1577.3435424 }, { 119, 5.7955749, 26.2983198 },
};
static const double L2[][3] = { { 52919, 0, 0 }, { 8720, 1.0720967, 6283.07585 }, { 309, 0.8672882, 12566.1517 } };
static const double L3[][3] = { { 289, 5.843842, 6283.07585 } };
static const double L4[][3] = { { 114, 3.1415927, 0 } };
static const double R0[][3] = { { 100013989, 0, 0 }, { 1670700, 3.0984635, 6283.07585 }, { 13956, 3.0552461, 12566.1517 } };
static const double R1[][3] = { { 103019, 1.1074897, 6283.07585 } };

typedef struct {
    const double (*terms)[3];
    int count;
} Series;

#define SERIES(t) { t, sizeof t / sizeof t[0] }
static const Series EARTH_L[] = { SERIES(L0), SERIES(L1), SERIES(L2), SERIES(L3), SERIES(L4) };
static const Series EARTH_R[] = { SERIES(R0), SERIES(R1) };

static double vsop(const Series *series, int count, double tau) {
    double total = 0;
    for (int i = count - 1; i >= 0; i--) {
        double sum = 0;
        for (int j = 0; j < series[i].count; j++)
            sum += series[i].terms[j][0] * cos(series[i].terms[j][1] + series[i].terms[j][2] * tau);
        total = total * tau + sum;
    }
    return total * 1e-8;
}

static double radians(double degrees) { return degrees * (M_PI / 180.0); }
static double degrees(double radians) { return radians * (180.0 / M_PI); }

/* the apparent longitude of the sun in degrees (Meeus, chapter 25, with VSOP87, nutation and aberration) */
static double sun_longitude(double jde) {
    double tau = (jde - 2451545) / 365250, t = 10 * tau;
    double omega = radians(125.04452 - 1934.136261 * t), sun = radians(280.4665 + 36000.7698 * t);
    double moon = radians(218.3165 + 481267.8813 * t);
    double nutation = -17.20 * sin(omega) - 1.32 * sin(2 * sun) - 0.23 * sin(2 * moon) + 0.21 * sin(2 * omega);
    return degrees(vsop(EARTH_L, 5, tau)) + 180 + (nutation - 0.09033 - 20.4898 / vsop(EARTH_R, 2, tau)) / 3600;
}

/* TD - UT in seconds: Espenak and Meeus' polynomials for 1986 to 2150, their long-term formula outside */
static double delta_t(double year) {
    double t = year - 2000, u = (year - 1820) / 100;
    double long_term = -20 + 32 * u * u;
    if (1986 <= year && year < 2005)
        return 63.86 + 0.3345 * t - 0.060374 * pow(t, 2) + 0.0017275 * pow(t, 3) + 0.000651814 * pow(t, 4)
            + 0.00002373599 * pow(t, 5);
    if (2005 <= year && year < 2050) return 62.92 + 0.32217 * t + 0.005589 * t * t;
    return 2050 <= year && year < 2150 ? long_term - 0.5628 * (2150 - year) : long_term;
}

/* the Julian day (UT) on which the sun's apparent longitude reaches the degrees in the year */
static double sun_universal(Eval *eval, int angle, long year) {
    double day = 79 + angle * 365.2422 / 360;   /* days after 1 January, from the March equinox on */
    if (day > 365.2422) day -= 365.2422;
    double jde = make(eval, year, 1, 1) + 2440587.5 + day;
    for (int i = 0; i < 50; i++) {
        double step = 58 * sin(radians(angle - sun_longitude(jde)));
        jde += step;
        if (fabs(step) < 1e-7) break;
    }
    return jde - delta_t(year + day / 365.25) / 86400;
}

/* the day in the year on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes */
static int sun_at(Eval *eval, int angle, long year, int offset) {
    double local = sun_universal(eval, angle, year) + offset / 1440.0;
    return add_days(eval, 0, (long long) floor(local - 2440587.5));
}

/* ---- time zones (rule files): the offset of an IANA zone from the TZif file of the system's zoneinfo ---- */

/* a big-endian signed integer of 4 or 8 bytes */
static long long be(const unsigned char *p, int bytes) {
    unsigned long long v = 0;
    for (int i = 0; i < bytes; i++) v = v << 8 | p[i];
    return bytes == 4 ? (long long) (int32_t) (uint32_t) v : (long long) v;
}

/* a POSIX TZ string (the footer of a TZif file): a name in letters or in <...> */
static const char *posix_name(const char *p) {
    if (*p == '<') {
        while (*p && *p != '>') p++;
        return *p ? p + 1 : p;
    }
    while ((*p >= 'A' && *p <= 'Z') || (*p >= 'a' && *p <= 'z')) p++;
    return p;
}

/* [+-]hh[:mm[:ss]] in seconds; -1 without a digit */
static int posix_time(const char **p, long *seconds) {
    int sign = **p == '-' ? -1 : 1;
    if (**p == '+' || **p == '-') (*p)++;
    if (**p < '0' || **p > '9') return -1;
    long hours = strtol(*p, (char **) p, 10), minutes = 0, rest = 0;
    if (**p == ':') minutes = strtol(*p + 1, (char **) p, 10);
    if (**p == ':') rest = strtol(*p + 1, (char **) p, 10);
    *seconds = sign * (hours * 3600 + minutes * 60 + rest);
    return 0;
}

/* a change of a POSIX TZ string: Mm.w.d (day d, 0 Sunday, of week w, 5 the last, of month m), Jn (day n, 1 to 365,
 * without February 29) or n (day n, 0 to 365), then /time (local, 02:00 without one) */
typedef struct {
    char kind;   /* 'M', 'J' or 'n' */
    long m, w, d;
    long time;
} PosixChange;

static int posix_change(const char **p, PosixChange *change) {
    if (**p != ',') return -1;
    (*p)++;
    *change = (PosixChange) { 'n', 0, 0, 0, 7200 };
    if (**p == 'M') {
        change->kind = 'M';
        change->m = strtol(*p + 1, (char **) p, 10);
        if (**p != '.') return -1;
        change->w = strtol(*p + 1, (char **) p, 10);
        if (**p != '.') return -1;
        change->d = strtol(*p + 1, (char **) p, 10);
        if (change->m < 1 || change->m > 12 || change->w < 1 || change->w > 5 || change->d < 0 || change->d > 6)
            return -1;
    } else {
        if (**p == 'J') change->kind = 'J', (*p)++;
        if (**p < '0' || **p > '9') return -1;
        change->d = strtol(*p, (char **) p, 10);
        if (change->d > 365 || (change->kind == 'J' && change->d < 1)) return -1;
    }
    if (**p == '/' && (++*p, posix_time(p, &change->time))) return -1;
    return 0;
}

/* the local time of the change in the year, in seconds since 1970 as if local time were UTC */
static long long posix_local(const PosixChange *change, long year) {
    int date;
    if (change->kind == 'M') {
        int first = date_of((int) year, (int) change->m, 1);
        /* weekday_of counts from Monday, POSIX from Sunday */
        date = first + (int) floor_mod((change->d + 6) % 7 - weekday_of(first), 7) + 7 * (int) (change->w - 1);
        while (month_of(date) != change->m) date -= 7;   /* week 5: the last */
    } else {
        date = date_of((int) year, 1, 1) + (int) change->d;
        if (change->kind == 'J' && (change->d < 60 || month_length(year, 2) == 28)) date--;
    }
    return date * 86400LL + change->time;
}

/* the offset east of UTC in seconds at the instant of a POSIX TZ string (EST5EDT,M3.2.0,M11.1.0: UTC-5, from the second
 * Sunday in March 02:00 to the first Sunday in November 02:00 UTC-4; NZST-12NZDT,M9.5.0,M4.1.0/3: daylight saving
 * time over the turn of the year); -1 if it can't be read */
static int posix_offset(const char *tz, double instant, long *offset) {
    const char *p = posix_name(tz);
    long standard, daylight;
    if (posix_time(&p, &standard)) return -1;
    standard = -standard;   /* POSIX counts west: JST-9 is UTC+9 */
    if (!*p || *p == '\n') {
        *offset = standard;
        return 0;
    }
    const char *name = p;
    p = posix_name(p);
    if (p == name) return -1;
    if (*p == ',' || !*p) daylight = standard + 3600;
    else if (posix_time(&p, &daylight)) return -1;
    else daylight = -daylight;
    PosixChange start, end;
    if (posix_change(&p, &start) || posix_change(&p, &end) || (*p && *p != '\n')) return -1;
    /* the changes in the year of the instant (in standard time): the start in standard, the end in daylight saving
       time */
    long year = year_of((int) floor((instant + standard) / 86400));
    double from = (double) (posix_local(&start, year) - standard), to = (double) (posix_local(&end, year) - daylight);
    int saving = from < to ? instant >= from && instant < to : instant >= from || instant < to;
    *offset = saving ? daylight : standard;
    return 0;
}

/* the offset east of UTC in seconds of the zone at the instant (seconds since 1970) */
static long zone_offset(Eval *eval, const char *zone, double instant) {
    const char *dir = getenv("TZDIR");
    char path[512];
    if (!*zone || zone[0] == '/' || strstr(zone, "..")) fail(eval, "no time zone %s", zone);
    snprintf(path, sizeof path, "%s/%s", dir ? dir : "/usr/share/zoneinfo", zone);
    FILE *file = fopen(path, "rb");
    if (!file) fail(eval, "no time zone %s", zone);
    unsigned char *data = NULL;
    size_t size = 0, capacity = 0, n;
    do {
        if (size == capacity && !(data = realloc(data, capacity = capacity ? 2 * capacity : 4096))) abort();
        n = fread(data + size, 1, capacity - size, file);
        size += n;
    } while (n);
    fclose(file);
    long offset = 0;
    const char *problem = NULL;
    if (size < 44 || memcmp(data, "TZif", 4)) problem = "not a TZif file";
    else {
        /* the data of version 1 (32-bit times), then, from version 2 on, a second header with 64-bit times */
        const unsigned char *h = data;
        int bytes = 4;
        long long counts[6];
        for (int i = 0; i < 6; i++) counts[i] = be(h + 20 + 4 * i, 4);   /* isut, isstd, leap, time, type, char */
        if (data[4] >= '2') {
            size_t skip = 44 + counts[3] * 5 + counts[4] * 6 + counts[5] + counts[2] * 8 + counts[1] + counts[0];
            if (skip + 44 > size) problem = "a short TZif file";
            else {
                h = data + skip, bytes = 8;
                for (int i = 0; i < 6; i++) counts[i] = be(h + 20 + 4 * i, 4);
            }
        }
        size_t length = 44 + counts[3] * (bytes + 1) + counts[4] * 6 + counts[5] + counts[2] * (bytes + 4) + counts[1] + counts[0];
        if (!problem && (h - data) + length > size) problem = "a short TZif file";
        if (!problem) {
            const unsigned char *times = h + 44, *indices = times + counts[3] * bytes, *types = indices + counts[3];
            long long last = -1;
            for (long long i = 0; i < counts[3]; i++)
                if (be(times + i * bytes, bytes) <= instant) last = i;
            int type = last >= 0 ? indices[last] : 0;
            if (type >= counts[4]) problem = "a broken TZif file";
            else offset = (long) be(types + 6 * type, 4);
            /* after the last transition the POSIX string at the end of the file holds (slim TZif files end early) */
            const char *footer = (const char *) h + length;
            if (!problem && last == counts[3] - 1 && bytes == 8 && (size_t) (footer - (const char *) data) + 1 < size
                && *footer == '\n') {
                char tz[128];
                size_t k = 0;
                while (k + 1 < sizeof tz && footer + 1 + k < (const char *) data + size && footer[1 + k] != '\n') {
                    tz[k] = footer[1 + k];
                    k++;
                }
                tz[k] = 0;
                if (k && posix_offset(tz, instant, &offset)) problem = "a POSIX TZ string it can't read";
            }
        }
    }
    free(data);
    if (problem) fail(eval, "time zone %s: %s", zone, problem);
    return offset;
}

/* the day on which the sun's apparent longitude reaches the degrees, in the time zone */
static int sun_in_zone(Eval *eval, int angle, long year, const char *zone) {
    double instant = (sun_universal(eval, angle, year) - 2440587.5) * 86400;
    return add_days(eval, 0, (long long) floor((instant + zone_offset(eval, zone, instant)) / 86400));
}

/* Orthodox Easter Sunday: the Julian computus (Meeus), converted to the Gregorian calendar */
static int orthodox_easter(Eval *eval, long year) {
    long a = year % 4, b = year % 7, c = year % 19;
    long d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
    long month = (d + e + 114) / 31, day = (d + e + 114) % 31;
    long k = (14 - month) / 12, y = year + 4800 - k, m = month + 12 * k - 3;
    long jdn = day + 1 + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
    return add_days(eval, 0, jdn - 2440588);
}

/* an annual date in the year */
static int annual_in(Eval *eval, const Annual *annual, long year) {
    switch (annual->kind) {
    case MONTH_DAY:
        return annual->month < HIJRI ? make(eval, year, annual->month + 1, annual->n)
                                     : lunar(eval, annual->n, annual->month, year);
    case NTH_WEEKDAY: {
        if (annual->month >= HIJRI) fail(eval, "not a Gregorian month");
        int first = make(eval, year, annual->month + 1, 1);
        int date = add_days(eval, first, floor_mod(annual->weekday - weekday_of(first), 7) + 7LL * (annual->n - 1));
        if (annual->n < 1 || month_of(date) != annual->month + 1)
            fail(eval, "no weekday %ld of month %d in %ld", annual->n, annual->month + 1, year);
        return date;
    }
    case LAST_WEEKDAY: {
        if (annual->month >= HIJRI) fail(eval, "not a Gregorian month");
        int last = make(eval, year, annual->month + 1, month_length(year, annual->month + 1));
        return last - (int) floor_mod(weekday_of(last) - annual->weekday, 7);
    }
    case EASTER_SUNDAY:
        return easter(eval, year);
    case SUN_AT: {
        int index = annual->month == 2 ? 0 : annual->month == 5 ? 1 : annual->month == 8 ? 2 : annual->month == 11 ? 3 : -1;
        if (index < 0 || (index % 2 == 0) != annual->equinox) fail(eval, "no equinox or solstice in that month");
        return annual->zone ? sun_in_zone(eval, 90 * index, year, annual->zone) : sun_at(eval, 90 * index, year, annual->offset);
    }
    case SOLAR_TERM:
        if (annual->n < 1 || annual->n > 24) fail(eval, "no solar term %ld", annual->n);
        return annual->zone ? sun_in_zone(eval, (int) (annual->n + 20) % 24 * 15, year, annual->zone)
                            : sun_at(eval, (int) (annual->n + 20) % 24 * 15, year, annual->offset);
    case ORTHODOX_EASTER:
        return orthodox_easter(eval, year);
    case HINDU_DAY:
        fail(eval, "HINDU %s: no Hindu calendar in this implementation", annual->zone);
    }
    fail(eval, "unknown annual date");
}

/* ---- events ---- */

/* the dates of the event in the year: its rule evaluated with the first of January as base date */
static DateList *occurrences(Eval *eval, const char *name, int year, const Ctx *ctx) {
    if (ctx->catalog) {   /* rule files: @key reads the rules of the catalog */
        DateList *all = catalog_references(eval, ctx, name, year), *kept = temp(eval);
        for (int j = 0; j < all->count; j++)
            if (year_of(all->dates[j]) == year) datelist_push(kept, all->dates[j]);
        return kept;
    }
    for (int i = 0; ctx->events && i < ctx->events->count; i++) {
        if (strcmp(ctx->events->names[i], name)) continue;
        Ctx first = { 1, make(eval, year, 1, 1), ctx->market, ctx->events, NULL, 0, NULL, 0, ctx->fixing, NULL, NULL, 0,
                      0, 0, 0 };
        DateList *all = nested(eval, ctx->events->rules[i], &first), *kept = temp(eval);
        for (int j = 0; j < all->count; j++)
            if (year_of(all->dates[j]) == year) datelist_push(kept, all->dates[j]);
        if (kept->count) qsort(kept->dates, kept->count, sizeof(int), compare);
        return kept;
    }
    fail(eval, "no event %s", name);
}

/* ---- anchors of > NEXT and > PREV: the first match strictly after (direction 1) or before (-1) a date ---- */

static int find(Eval *eval, const Anchor *anchor, int date, int direction, const Ctx *ctx) {
    switch (anchor->kind) {
    case AT_WEEKDAY: {
        long days = floor_mod((long) (anchor->weekday - weekday_of(date)) * direction, 7);
        return add_days(eval, date, direction * (days ? days : 7));
    }
    case AT_BUSINESS_DAY:
        return plus_business_days(eval, date, direction, market_of(eval, anchor->on, ctx, NULL));
    case AT_HOLIDAY: case AT_NONHOLIDAY: {   /* rule files: the next (previous) day that is (isn't) a holiday */
        Market *market = market_of(eval, anchor->on, ctx, NULL);
        for (int i = 0; i < 3700; i++) {
            date = add_days(eval, date, direction);
            if (is_holiday(eval, market, date) == (anchor->kind == AT_HOLIDAY)) return date;
        }
        fail(eval, "no such day within 3700 days");
    }
    case AT_FIXING_DAY:
        return plus_business_days(eval, date, direction, ctx->fixing);
    case AT_IMM:
        return imm(eval, date, direction);
    case AT_ANNUAL: {
        int match = annual_in(eval, &anchor->annual, year_of(date));
        return (direction > 0 ? match > date : match < date) ? match
                                                              : annual_in(eval, &anchor->annual, year_of(date) + direction);
    }
    case AT_EVENT:
        for (int year = year_of(date); year != year_of(date) + 2 * direction; year += direction) {
            DateList *found = occurrences(eval, anchor->event, year, ctx);
            for (int i = 0; i < found->count; i++) {
                int o = found->dates[direction > 0 ? i : found->count - 1 - i];
                if (direction > 0 ? o > date : o < date) return o;
            }
        }
        fail(eval, "no %s %s the date", anchor->event, direction > 0 ? "after" : "before");
    case AT_ANY_OF: {
        /* NEXT {MON, FRI}, NEXT {1 FEB, 1 MAY}: the nearest match of any of the anchors */
        int best = find(eval, anchor->targets, date, direction, ctx);
        for (const Anchor *a = anchor->targets->next; a; a = a->next) {
            int d = find(eval, a, date, direction, ctx);
            if (direction > 0 ? d < best : d > best) best = d;
        }
        return best;
    }
    }
    fail(eval, "unknown anchor");
}

/* FOLLOWING, PRECEDING, MODFOLLOWING (following unless that leaves the month, then preceding);
 * BOW, EOW, BOM, EOM, BOQ, EOQ, BOH, EOH, BOY, EOY (first and last day of week (Monday to Sunday), month, quarter,
 * half year and year) */
static int roll(Eval *eval, int date, int convention, Market *market) {
    int year, month, day;
    date_split(date, &year, &month, &day);
    switch (convention) {
    case FOLLOWING:
        return is_business_day(eval, market, date) ? date : plus_business_days(eval, date, 1, market);
    case PRECEDING:
        return is_business_day(eval, market, date) ? date : plus_business_days(eval, date, -1, market);
    case MODFOLLOWING: {
        int following = roll(eval, date, FOLLOWING, market);
        return month_of(following) == month ? following : roll(eval, date, PRECEDING, market);
    }
    case ROLL_NEAREST:     /* rule files: a non-business Sunday or Monday follows, any other day precedes */
        if (is_business_day(eval, market, date)) return date;
        return roll(eval, date, weekday_of(date) == 6 || weekday_of(date) == 0 ? FOLLOWING : PRECEDING, market);
    case MODPRECEDING: {   /* rule files: preceding unless that leaves the month */
        int preceding = roll(eval, date, PRECEDING, market);
        return month_of(preceding) == month ? preceding : roll(eval, date, FOLLOWING, market);
    }
    case BOW: return date - weekday_of(date);
    case EOW: return date + 6 - weekday_of(date);
    case BOM: return date_of(year, month, 1);
    case EOM: return date_of(year, month, month_length(year, month));
    case BOQ: case EOQ: case BOH: case EOH: {
        int months = convention == BOQ || convention == EOQ ? 3 : 6;
        int first = (month - 1) / months * months + 1;
        return convention == BOQ || convention == BOH ? date_of(year, first, 1)
            : date_of(year, first + months - 1, month_length(year, first + months - 1));
    }
    case BOY: return date_of(year, 1, 1);
    default: return date_of(year, 12, 31);
    }
}

/* > 3RD WED IN MONTH: the anchor found count times from the day before the period of the date starts;
 * > 2ND LAST BD IN MONTH (from_end): backwards from the day after it ends; an error if that leaves the period */
static int select_in(Eval *eval, const Shift *s, int date, const Ctx *ctx) {
    static const int FIRST[] = { BOW, BOM, BOQ, BOH, BOY }, LAST[] = { EOW, EOM, EOQ, EOH, EOY };
    if (s->count < 1) fail(eval, "count must be positive: %ld", s->count);
    int first = roll(eval, date, FIRST[s->within], ctx->market), last = roll(eval, date, LAST[s->within], ctx->market);
    int found = s->from_end ? add_days(eval, last, 1) : add_days(eval, first, -1);
    for (long i = 0; i < s->count; i++) found = find(eval, s->anchor, found, s->from_end ? -1 : 1, ctx);
    if (found < first || found > last) fail(eval, "no %ld. match in the period", s->count);
    return found;
}

/* ---- expressions ---- */

/* the value of the next node with several values in this evaluation: bound by collect(), or UNBOUND with the node
 * noted as pending */
static int take(Eval *eval, const Ctx *ctx, int kind, const void *node, int date, const Item *item, int before) {
    if (ctx->cursor && *ctx->cursor < ctx->count) return ctx->bindings[(*ctx->cursor)++];
    eval->pending.kind = kind, eval->pending.node = node, eval->pending.date = date;
    eval->pending.item = item, eval->pending.before = before;
    return UNBOUND;
}

static int date_at(Eval *eval, const Date *date, const Ctx *ctx) {
    switch (date->kind) {
    case D_BASE: return base(eval, ctx);
    case D_TOM: return plus_business_days(eval, base(eval, ctx), 1, ctx->market);
    case D_SPOT: return spot(eval, base(eval, ctx), date->pair, market_of(eval, date->on, ctx, date->pair));
    case D_FIXED: return date->fixed;
    case D_ANNUAL:   /* a lunar date can fall twice in the year: a node with several values */
        if (date->annual.kind == MONTH_DAY && date->annual.month >= HIJRI) return take(eval, ctx, P_LUNAR, date, 0, NULL, 0);
        return annual_in(eval, &date->annual, year_of(base(eval, ctx)));
    case D_EVENT: return take(eval, ctx, P_EVENT, date, 0, NULL, 0);
    case D_SERIES: return take(eval, ctx, P_SERIES, date, 0, NULL, 0);
    }
    fail(eval, "unknown date");
}

/* the last business day of the month of the date */
static int last_business_day(Eval *eval, int date, Market *market) {
    return roll(eval, roll(eval, date, EOM, market), PRECEDING, market);
}

/* rule files: + 1M ENDEND keeps the last business day of a month at the last one of the target month */
static int plus_on(Eval *eval, int date, const Shift *s, int sign, const Ctx *ctx) {
    Market *market = market_of(eval, s->on, ctx, NULL);
    int moved = plus(eval, date, s->period, sign, market, ctx->fixing);
    return s->endend && date == last_business_day(eval, date, market) ? last_business_day(eval, moved, market) : moved;
}

/* rule files: > NEAREST: the date itself if it matches, else the nearer of the next and the previous match; ties go
 * forward */
static int nearest(Eval *eval, const Anchor *anchor, int date, const Ctx *ctx) {
    if (find(eval, anchor, date - 1, 1, ctx) == date) return date;
    int after = find(eval, anchor, date, 1, ctx), before = find(eval, anchor, date, -1, ctx);
    return after - date <= date - before ? after : before;
}

/* rule files: > CUTOFF 17:00 "America/New_York": the date of the base instant in the zone, the next one from the cutoff
 * time on */
static int cutoff(Eval *eval, const Shift *s, const Ctx *ctx) {
    if (!ctx->has_instant) fail(eval, "CUTOFF needs a base with a time of day");
    double instant = (double) ctx->instant;
    long long local = ctx->instant + (s->zone ? zone_offset(eval, s->zone, instant) : s->offset * 60LL);
    long long day = local >= 0 ? local / 86400 : -((-local + 86399) / 86400);
    return add_days(eval, 0, day + (local - day * 86400 >= s->cutoff * 60LL));
}

/* each shift applies to everything on its left; > POSTPONE ... MAX limits from the date before it, with CUMULATIVE
 * from the date before the first postponement */
static int shifted(Eval *eval, const Shift *shifts, int date, const Ctx *ctx) {
    int origin = UNBOUND;
    for (const Shift *s = shifts; s && date != UNBOUND; s = s->next) {
        switch (s->kind) {
        case S_PLUS: date = plus_on(eval, date, s, 1, ctx); break;
        case S_MINUS: date = plus_on(eval, date, s, -1, ctx); break;
        case S_FIX: date = plus_business_days(eval, date, -spot_lag(s->pair), ctx->fixing); break;
        case S_IMM: date = imm(eval, date, 1); break;
        case S_SPOT: date = spot(eval, date, s->pair, market_of(eval, s->on, ctx, s->pair)); break;
        case S_ROLL: date = roll(eval, date, s->roll, market_of(eval, s->on, ctx, NULL)); break;
        case S_NEAREST: date = nearest(eval, s->anchor, date, ctx); break;
        case S_CUTOFF: date = cutoff(eval, s, ctx); break;
        case S_CUTOFF_ON: {
            const Shift *attribute = s->on->kind == CAL_NAMED ? catalog_cutoff(ctx, s->on->name) : NULL;
            if (!attribute) fail(eval, "no cutoff for %s", s->on->kind == CAL_COMBINED ? "ON (...)" : s->on->name);
            date = cutoff(eval, attribute, ctx);
            break;
        }
        case S_POSTPONE: {
            if (origin == UNBOUND) origin = date;
            int from = s->cumulative ? origin : date;
            int target = roll(eval, date, s->roll, market_of(eval, s->on, ctx, NULL));
            int bound = plus(eval, from, s->period, target > date ? 1 : -1, ctx->market, NULL);
            date = target > date ? (target < bound ? target : bound) : (target > bound ? target : bound);
            break;
        }
        case S_NEXT: case S_PREV:
            /* NEXT 3RD FRI: the anchor found count times, each time from the one before */
            if (s->count < 1) fail(eval, "count must be positive: %ld", s->count);
            for (long i = 0; i < s->count; i++) date = find(eval, s->anchor, date, s->kind == S_NEXT ? 1 : -1, ctx);
            break;
        case S_SELECT: date = select_in(eval, s, date, ctx); break;
        case S_DATES_IN_YEAR: date = take(eval, ctx, P_DATES_IN_YEAR, s, date, NULL, 0); break;
        case S_OBSERVANCE:
            /* {SAT - 1D, SUN + 1D}: the first item for the weekday of the date applies, items don't chain; in rule
               files an item has several weekdays and may move to a target (TUE WED > PREV MON) */
            for (const Observe *o = s->observes; o; o = o->next)
                if (o->weekdays ? o->weekdays >> weekday_of(date) & 1 : o->weekday == weekday_of(date)) {
                    date = o->move ? shifted(eval, o->move, date, ctx) : plus(eval, date, o->period, o->sign, ctx->market, ctx->fixing);
                    break;
                }
            break;
        }
    }
    return date;
}

static int expr_at(Eval *eval, const Expr *expr, const Ctx *ctx) {
    int date = date_at(eval, &expr->date, ctx);
    return date == UNBOUND ? UNBOUND : shifted(eval, expr->shifts, date, ctx);
}

/* an item with its first `upto` combines: the expression, or the result of the last of them with its shifts */
static int item_at(Eval *eval, const Item *item, int upto, const Ctx *ctx) {
    if (upto == 0) return expr_at(eval, item->expr, ctx);
    const Combine *combine = item->combines;
    for (int i = 1; i < upto; i++) combine = combine->next;
    int date = take(eval, ctx, P_COMBINED, combine, 0, item, upto - 1);
    return date == UNBOUND ? UNBOUND : shifted(eval, combine->shifts, date, ctx);
}

static int single(Eval *eval, const Expr *expr, const Ctx *ctx);
static void collect(Eval *eval, const Item *item, int upto, const Expr *expr, const Ctx *ctx, const int *bindings,
                    int count, const Item *filter, DateList *out);

/* [first : step : last]: first + k * step for k = 0, 1, ... while not after last, without the endpoints left out */
static DateList *elements(Eval *eval, const Date *series, const Ctx *ctx) {
    DateList *dates = temp(eval);
    if (series->last) {   /* one range per occurrence: [@E - 3D : @E + 1D] */
        DateList *firsts = temp(eval), *lasts = temp(eval);
        collect(eval, NULL, 0, series->first, ctx, NULL, 0, NULL, firsts);
        collect(eval, NULL, 0, series->last, ctx, NULL, 0, NULL, lasts);
        if (firsts->count > 1 && firsts->count == lasts->count) {
            for (int i = 0; i < firsts->count; i++) {
                int a = firsts->dates[i], b = lasts->dates[i];
                for (long long k = 0;; k++) {
                    Period step = { k * series->step.months, k * series->step.days, k * series->step.business, k * series->step.fixing };
                    int date = plus(eval, a, step, 1, ctx->market, ctx->fixing);
                    if (date > b) break;
                    if (!(series->start_out && date <= a) && !(series->end_out && date >= b)) datelist_push(dates, date);
                }
            }
            distinct(dates);
            return dates;
        }
    }
    int first = single(eval, series->first, ctx);
    if (!series->last) {   /* an open series [first : step : ], only under a pick, which sets how many elements it yields */
        if (!ctx->limit) fail(eval, "an open series needs a pick in front of the item");
        for (long long k = 0; k < ctx->limit; k++) {
            Period step = { k * series->step.months, k * series->step.days, k * series->step.business, k * series->step.fixing };
            int date = plus(eval, first, step, 1, ctx->market, ctx->fixing);
            if (!(series->start_out && date <= first)) datelist_push(dates, date);
        }
        return dates;
    }
    int last = single(eval, series->last, ctx);
    for (long long k = 0;; k++) {
        Period step = { k * series->step.months, k * series->step.days, k * series->step.business, k * series->step.fixing };
        int date = plus(eval, first, step, 1, ctx->market, ctx->fixing);
        if (date > last) return dates;
        if (!(series->start_out && date <= first) && !(series->end_out && date >= last)) datelist_push(dates, date);
    }
}

static void collect(Eval *eval, const Item *item, int upto, const Expr *expr, const Ctx *ctx, const int *bindings,
                    int count, const Item *filter, DateList *out);

static int keeps(Eval *eval, int condition, int date);

/* rule files: whether the date meets the condition of IF */
static int holds(Eval *eval, const Condition *c, int date, const Ctx *ctx) {
    switch (c->kind) {
    case C_ANY:
        for (const Condition *p = c->parts; p; p = p->next)
            if (holds(eval, p, date, ctx)) return 1;
        return 0;
    case C_ALL:
        for (const Condition *p = c->parts; p; p = p->next)
            if (!holds(eval, p, date, ctx)) return 0;
        return 1;
    case C_NOT: return !holds(eval, c->parts, date, ctx);
    case C_WEEKDAY: return c->weekdays >> weekday_of(date) & 1;
    case C_DAY: return keeps(eval, DAY_CONDITION(c->first, c->last, c->month), date);
    case C_IS_WEEKDAY: return c->weekdays >> weekday_of(single(eval, c->subject, ctx)) & 1;
    case C_IS_HOLIDAY: return is_holiday(eval, market_of(eval, c->on, ctx, NULL), date);
    case C_DATE_IS_HOLIDAY: return is_holiday(eval, market_of(eval, c->on, ctx, NULL), single(eval, c->subject, ctx));
    case C_BETWEEN_HOLIDAYS: {
        Market *market = market_of(eval, c->on, ctx, NULL);
        return is_holiday(eval, market, add_days(eval, date, -1)) && is_holiday(eval, market, add_days(eval, date, 1));
    }
    case C_EVERY: return year_of(date) >= c->last && (year_of(date) - c->last) % c->first == 0;
    }
    fail(eval, "unknown condition");
}

/* IF: the weekdays of the date (a bit each), or DAY_CONDITION: its day of the month (of that Gregorian month) */
static int keeps(Eval *eval, int condition, int date) {
    if (!condition) return 1;
    if (!(condition & 1 << 7)) return condition & 1 << weekday_of(date);
    int first = condition >> 8 & 63, last = condition >> 14 & 63, month = (condition >> 20) - 1;
    if (first < 1 || first > last || last > 31) fail(eval, "days must run from 1 to 31 upwards: %d..%d", first, last);
    if (month >= HIJRI) fail(eval, "IF DAY takes a Gregorian month");
    int year, m, day;
    date_split(date, &year, &m, &day);
    return day >= first && day <= last && (month < 0 || m == month + 1);
}

/* sorted and without duplicates */
void distinct(DateList *dates) {
    if (!dates->count) return;
    qsort(dates->dates, dates->count, sizeof(int), compare);
    int n = 1;
    for (int i = 1; i < dates->count; i++)
        if (dates->dates[i] != dates->dates[n - 1]) dates->dates[n++] = dates->dates[i];
    dates->count = n;
}

/* the values of the pending node: an event's occurrences in the base year, a series' elements, the result of A & B or
 * A \\ B, the dates in year of a date */
static DateList *pending_dates(Eval *eval, const Ctx *ctx) {
    int kind = eval->pending.kind, date = eval->pending.date, before = eval->pending.before;
    const void *node = eval->pending.node;
    const Item *item = eval->pending.item;
    if (kind == P_EVENT) return occurrences(eval, ((const Date *) node)->event, year_of(base(eval, ctx)), ctx);
    if (kind == P_SERIES) return elements(eval, node, ctx);
    DateList *dates = temp(eval);
    if (kind == P_LUNAR) {
        const Annual *a = &((const Date *) node)->annual;
        int hits[2], n = lunar_dates(eval, a->n, a->month, year_of(base(eval, ctx)), hits);
        for (int i = 0; i < n; i++) datelist_push(dates, hits[i]);
        return dates;
    }
    if (kind == P_DATES_IN_YEAR) {
        for (const Anchor *a = ((const Shift *) node)->anchor; a; a = a->next)
            datelist_push(dates, annual_in(eval, &a->annual, year_of(date)));
    } else {
        const Combine *combine = node;
        DateList *right = temp(eval);
        collect(eval, item, before, NULL, ctx, NULL, 0, NULL, dates);
        for (const Operand *o = combine->operands; o; o = o->next) collect(eval, NULL, 0, o->expr, ctx, NULL, 0, NULL, right);
        distinct(right);
        if (combine->include) {
            for (int i = 0; i < right->count; i++) datelist_push(dates, right->dates[i]);
        } else {
            int n = 0;
            for (int i = 0; i < dates->count; i++)
                if (!bsearch(&dates->dates[i], right->dates, right->count, sizeof(int), compare)) dates->dates[n++] = dates->dates[i];
            dates->count = n;
        }
    }
    distinct(dates);
    return dates;
}

/* the dates of an item with its first `upto` combines (or of an expression): one, or one per combination of the values
 * of its nodes with several values, bound one after the other; IF drops dates */
static void collect(Eval *eval, const Item *item, int upto, const Expr *expr, const Ctx *ctx, const int *bindings,
                    int count, const Item *filter, DateList *out) {
    int cursor = 0;
    Ctx bound = *ctx;
    bound.bindings = bindings, bound.count = count, bound.cursor = &cursor;
    int date = item ? item_at(eval, item, upto, &bound) : expr_at(eval, expr, &bound);
    if (date != UNBOUND) {
        if (filter && !keeps(eval, filter->weekdays, date)) return;
        if (filter && filter->condition) {   /* rule files: IF condition, then the shifts after it */
            if (!holds(eval, filter->condition, date, &bound)) return;
            date = shifted(eval, filter->if_shifts, date, &bound);
            if (date == UNBOUND) fail(eval, "a date with several values after IF");
        }
        datelist_push(out, date);
        return;
    }
    DateList *found = pending_dates(eval, ctx), *more = temp(eval);
    for (int i = 0; i < count; i++) datelist_push(more, bindings[i]);
    datelist_push(more, 0);
    for (int i = 0; i < found->count; i++) {
        more->dates[count] = found->dates[i];
        collect(eval, item, upto, expr, ctx, more->dates, count + 1, filter, out);
    }
}

/* the date of an expression that must have exactly one, such as the end of a range */
static int single(Eval *eval, const Expr *expr, const Ctx *ctx) {
    DateList *dates = temp(eval);
    collect(eval, NULL, 0, expr, ctx, NULL, 0, NULL, dates);
    if (dates->count != 1) fail(eval, "%d dates where one is needed", dates->count);
    return dates->dates[0];
}

/* 5TH ...: the pick-th date of the item strictly after T, in the order the item yields them; 2ND LAST ...: counted
 * from the last one. An open series grows until there are enough dates, up to 10000 elements */
static int pick(Eval *eval, const Item *item, int combines, const Ctx *ctx) {
    static const int LIMITS[] = { 16, 256, 4096, 10000 };
    if (item->pick < 1) fail(eval, "count must be positive: %ld", item->pick);
    int t = base(eval, ctx);
    for (int l = 0; l < (item->from_end ? 1 : 4); l++) {
        Ctx limited = *ctx;
        limited.limit = item->from_end ? 0 : LIMITS[l];
        DateList *dates = temp(eval), *hits = temp(eval);
        collect(eval, item, combines, NULL, &limited, NULL, 0, item, dates);
        for (int i = 0; i < dates->count; i++)
            if (dates->dates[i] > t) datelist_push(hits, dates->dates[i]);
        if (hits->count >= item->pick) return hits->dates[item->from_end ? hits->count - item->pick : item->pick - 1];
    }
    fail(eval, "no %ld. date after the base date", item->pick);
}

static void evaluate_into(Eval *eval, const Item *items, const Ctx *ctx, DateList *out) {
    for (const Item *item = items; item; item = item->next) {
        int combines = 0;
        for (const Combine *c = item->combines; c; c = c->next) combines++;
        if (item->has_pick) datelist_push(out, pick(eval, item, combines, ctx));
        else collect(eval, item, combines, NULL, ctx, NULL, 0, item, out);
    }
}

int evaluate_items(const Item *items, const Ctx *ctx, DateList *out, char *error, size_t size) {
    Eval *eval = calloc(1, sizeof(Eval));
    if (!eval) abort();
    eval->error = error, eval->size = size;
    *out = (DateList) { 0 };
    volatile int status = 0;
    if (setjmp(eval->jump)) {
        datelist_free(out);
        status = -1;
    } else {
        evaluate_into(eval, items, ctx, out);
    }
    release(eval);
    return status;
}

int schedule_evaluate(const Schedule *schedule, const Context *context, DateList *out, char *error, size_t size) {
    Ctx ctx = { context->has_base, context->base, context->market, context->events, NULL, 0, NULL, 0, context->fixing,
                NULL, NULL, 0, 0, 0, 0 };
    return evaluate_items(schedule->items, &ctx, out, error, size);
}

/* ---- markets and events ---- */

Market *market_new(Schedule *rule) {
    Market *market = calloc(1, sizeof(Market));
    if (!market) abort();
    market->rule = rule;
    pthread_mutex_init(&market->lock, NULL);
    return market;
}

void market_free(Market *market) {
    if (!market) return;
    for (int i = 0; i < market->count; i++) {
        datelist_free(&market->years[i]->holidays);
        free(market->years[i]);
    }
    free(market->years);
    pthread_mutex_destroy(&market->lock);
    schedule_free(market->rule);
    free(market);
}

Events *events_new(void) {
    Events *events = calloc(1, sizeof(Events));
    if (!events) abort();
    return events;
}

void events_add(Events *events, const char *name, Schedule *rule) {
    events->names = realloc(events->names, (events->count + 1) * sizeof(char *));
    events->rules = realloc(events->rules, (events->count + 1) * sizeof(Schedule *));
    if (!events->names || !events->rules) abort();
    events->names[events->count] = strdup(name);
    events->rules[events->count++] = rule;
}

void events_free(Events *events) {
    if (!events) return;
    for (int i = 0; i < events->count; i++) {
        free(events->names[i]);
        schedule_free(events->rules[i]);
    }
    free(events->names);
    free(events->rules);
    free(events);
}
