/* Semantics of the shared "schedule" example: periods, business days, IMM and spot dates, ranges, annual dates
 * (holiday rules, also in the Hijri, Hebrew and Chinese calendars), rolling and business day conventions, named
 * events. The arithmetic follows java.time, like ScheduleSemantics.java and semantics.py in the other modules.
 *
 * An evaluation error unwinds with longjmp to schedule_evaluate, which frees what the evaluation allocated. */
#include <math.h>
#include <setjmp.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ast.h"
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

static int year_of(int date) {
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

/* ---- evaluation ---- */

typedef struct {
    jmp_buf jump;
    char *error;
    size_t size;
    DateList **temps;   /* freed at the end, also after an error */
    int count, capacity;
} Eval;

/* what a rule is evaluated against; occurrence is the date of the event an expression is evaluated for */
typedef struct {
    int has_base, base;
    Market *market;
    const Events *events;
    int has_occurrence, occurrence;
} Ctx;

struct Market {
    Schedule *rule;
    struct {
        int year;
        DateList holidays;  /* sorted */
    } *years;
    int count, capacity;
};

struct Events {
    char **names;
    Schedule **rules;
    int count;
};

static _Noreturn void fail(Eval *eval, const char *format, ...) {
    va_list args;
    va_start(args, format);
    vsnprintf(eval->error, eval->size, format, args);
    va_end(args);
    longjmp(eval->jump, 1);
}

static DateList *temp(Eval *eval) {
    if (eval->count == eval->capacity) {
        eval->capacity = eval->capacity ? 2 * eval->capacity : 8;
        eval->temps = realloc(eval->temps, eval->capacity * sizeof(DateList *));
        if (!eval->temps) abort();
    }
    DateList *list = calloc(1, sizeof(DateList));
    if (!list) abort();
    return eval->temps[eval->count++] = list;
}

static int make(Eval *eval, long long year, long long month, long long day) {
    if (!valid(year, month, day)) fail(eval, "not a date: %lld-%lld-%lld", year, month, day);
    return date_of((int) year, (int) month, (int) day);
}

static int add_days(Eval *eval, int date, long long days) {
    long long result = date + days;
    if (result < date_of(1, 1, 1) || result > date_of(9999, 12, 31)) fail(eval, "date out of range");
    return (int) result;
}

static void evaluate_into(Eval *eval, const Schedule *schedule, const Ctx *ctx, DateList *out);

/* a schedule evaluated on its own, e.g. the rule of a market; its errors become errors of this evaluation */
static DateList *nested(Eval *eval, const Schedule *schedule, const Ctx *ctx) {
    char error[256];
    Context context = { ctx->has_base, ctx->base, ctx->market, ctx->events };
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
static const DateList *holidays(Eval *eval, Market *market, int year) {
    for (int i = 0; i < market->count; i++)
        if (market->years[i].year == year) return &market->years[i].holidays;
    Ctx first = { 1, make(eval, year, 1, 1), NULL, NULL, 0, 0 };
    DateList *dates = nested(eval, market->rule, &first);
    if (market->count == market->capacity) {
        market->capacity = market->capacity ? 2 * market->capacity : 8;
        market->years = realloc(market->years, market->capacity * sizeof *market->years);
        if (!market->years) abort();
    }
    DateList *kept = &market->years[market->count].holidays;
    market->years[market->count++].year = year;
    *kept = (DateList) { 0 };
    for (int i = 0; i < dates->count; i++) datelist_push(kept, dates->dates[i]);
    if (kept->count) qsort(kept->dates, kept->count, sizeof(int), compare);
    return kept;
}

static int is_business_day(Eval *eval, Market *market, int date) {
    if (weekday_of(date) >= 5) return 0;
    if (!market) return 1;
    int year = year_of(date);
    for (int y = year - 1; y <= year + 1; y++) {
        const DateList *list = holidays(eval, market, y);
        if (list->count && bsearch(&date, list->dates, list->count, sizeof(int), compare)) return 0;
    }
    return 1;
}

/* count business days of the market later (earlier if negative) */
static int plus_business_days(Eval *eval, int date, long long count, Market *market) {
    int step = count > 0 ? 1 : -1;
    while (count) {
        date = add_days(eval, date, step);
        if (is_business_day(eval, market, date)) count -= step;
    }
    return date;
}

/* months first (day clamped to the end of the month), then days; business days of the market on their own */
static int plus(Eval *eval, int date, Period period, int sign, Market *market) {
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

/* FX spot: T+1 for USD against CAD, TRY, PHP or RUB, otherwise T+2 (also without pair) */
static int spot(Eval *eval, int trade, const char *pair, Market *market) {
    static const char *const T_PLUS_ONE[] = { "CAD", "TRY", "PHP", "RUB" };
    int one = 0;
    for (int i = 0; pair && i < 4; i++)
        one |= (!strncmp(pair, "USD", 3) && !strcmp(pair + 3, T_PLUS_ONE[i]))
            || (!strncmp(pair, T_PLUS_ONE[i], 3) && !strcmp(pair + 3, "USD"));
    return plus_business_days(eval, trade, one ? 1 : 2, market);
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

/* the day of a Hijri, Hebrew or Chinese month in the Gregorian year; an error if it occurs twice or not at all */
static int lunar(Eval *eval, long day, int month, long year) {
    int hits = 0, hit = 0, candidates[200], count = 0;
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
        if (candidates[i] >= date_of(1, 1, 1) && candidates[i] <= date_of(9999, 12, 31) && year_of(candidates[i]) == year)
            hits++, hit = candidates[i];
    if (hits != 1) fail(eval, "%ld of month %d occurs %d times in %ld", day, month, hits, year);
    return hit;
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

/* the day in the year on which the sun's apparent longitude reaches the degrees, in UTC + offset minutes */
static int sun_at(Eval *eval, int angle, long year, int offset) {
    double day = 79 + angle * 365.2422 / 360;   /* days after 1 January, from the March equinox on */
    if (day > 365.2422) day -= 365.2422;
    double jde = make(eval, year, 1, 1) + 2440587.5 + day;
    for (int i = 0; i < 50; i++) {
        double step = 58 * sin(radians(angle - sun_longitude(jde)));
        jde += step;
        if (fabs(step) < 1e-7) break;
    }
    double local = jde - delta_t(year + day / 365.25) / 86400 + offset / 1440.0;
    return add_days(eval, 0, (long long) floor(local - 2440587.5));
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
        return sun_at(eval, 90 * index, year, annual->offset);
    }
    case SOLAR_TERM:
        if (annual->n < 1 || annual->n > 24) fail(eval, "no solar term %ld", annual->n);
        return sun_at(eval, (int) (annual->n + 20) % 24 * 15, year, annual->offset);
    }
    fail(eval, "unknown annual date");
}

/* ---- events ---- */

/* the dates of the event in the year: its rule evaluated with the first of January as base date */
static DateList *occurrences(Eval *eval, const char *name, int year, const Ctx *ctx) {
    for (int i = 0; ctx->events && i < ctx->events->count; i++) {
        if (strcmp(ctx->events->names[i], name)) continue;
        Ctx first = { 1, make(eval, year, 1, 1), ctx->market, ctx->events, 0, 0 };
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
        return plus_business_days(eval, date, direction, ctx->market);
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

static int date_at(Eval *eval, const Date *date, const Ctx *ctx) {
    switch (date->kind) {
    case D_BASE: return base(eval, ctx);
    case D_TOM: return plus_business_days(eval, base(eval, ctx), 1, ctx->market);
    case D_SPOT: return spot(eval, base(eval, ctx), date->pair, ctx->market);
    case D_FIXED: return date->fixed;
    case D_ANNUAL: return annual_in(eval, &date->annual, year_of(base(eval, ctx)));
    case D_EVENT: case D_SERIES:
        if (!ctx->has_occurrence) fail(eval, "a date with several values is not bound");
        return ctx->occurrence;
    }
    fail(eval, "unknown date");
}

static int expr_at(Eval *eval, const Expr *expr, const Ctx *ctx) {
    int date = date_at(eval, &expr->date, ctx);
    for (const Shift *s = expr->shifts; s; s = s->next) {
        switch (s->kind) {
        case S_PLUS: date = plus(eval, date, s->period, 1, ctx->market); break;
        case S_MINUS: date = plus(eval, date, s->period, -1, ctx->market); break;
        case S_IMM: date = imm(eval, date, 1); break;
        case S_SPOT: date = spot(eval, date, s->pair, ctx->market); break;
        case S_ROLL: date = roll(eval, date, s->roll, ctx->market); break;
        case S_NEXT: case S_PREV:
            /* NEXT 3RD FRI: the anchor found count times, each time from the one before */
            if (s->count < 1) fail(eval, "count must be positive: %ld", s->count);
            for (long i = 0; i < s->count; i++) date = find(eval, s->anchor, date, s->kind == S_NEXT ? 1 : -1, ctx);
            break;
        case S_SELECT: date = select_in(eval, s, date, ctx); break;
        case S_OBSERVANCE:
            /* {SAT - 1D, SUN + 1D}: the first item for the weekday of the date applies, items don't chain */
            for (const Observe *o = s->observes; o; o = o->next)
                if (o->weekday == weekday_of(date)) {
                    date = plus(eval, date, o->period, o->sign, ctx->market);
                    break;
                }
            break;
        }
    }
    return date;
}

static int single(Eval *eval, const Expr *expr, const Ctx *ctx);

/* [first : step : last]: first + k * step for k = 0, 1, ... while not after last, without the endpoints left out */
static DateList *elements(Eval *eval, const Date *series, const Ctx *ctx) {
    int first = single(eval, series->first, ctx), last = single(eval, series->last, ctx);
    DateList *dates = temp(eval);
    for (long long k = 0;; k++) {
        Period step = { k * series->step.months, k * series->step.days, k * series->step.business };
        int date = plus(eval, first, step, 1, ctx->market);
        if (date > last) return dates;
        if (!(series->start_out && date <= first) && !(series->end_out && date >= last)) datelist_push(dates, date);
    }
}

/* the dates of an item: one, or one per date if it starts with an event (its occurrences in the base year) or a series
 * (its elements); IF drops dates */
static void values(Eval *eval, const Expr *expr, int weekdays, const Ctx *ctx, DateList *out) {
    if ((expr->date.kind == D_EVENT || expr->date.kind == D_SERIES) && !ctx->has_occurrence) {
        DateList *found = expr->date.kind == D_EVENT ? occurrences(eval, expr->date.event, year_of(base(eval, ctx)), ctx)
                                                      : elements(eval, &expr->date, ctx);
        for (int i = 0; i < found->count; i++) {
            Ctx bound = *ctx;
            bound.has_occurrence = 1, bound.occurrence = found->dates[i];
            values(eval, expr, weekdays, &bound, out);
        }
        return;
    }
    int date = expr_at(eval, expr, ctx);
    if (!weekdays || weekdays & 1 << weekday_of(date)) datelist_push(out, date);
}

/* the date of an expression that must have exactly one, such as the end of a range */
static int single(Eval *eval, const Expr *expr, const Ctx *ctx) {
    DateList *dates = temp(eval);
    values(eval, expr, 0, ctx, dates);
    if (dates->count != 1) fail(eval, "%d dates where one is needed", dates->count);
    return dates->dates[0];
}

static void evaluate_into(Eval *eval, const Schedule *schedule, const Ctx *ctx, DateList *out) {
    for (const Item *item = schedule->items; item; item = item->next) values(eval, item->expr, item->weekdays, ctx, out);
}

int schedule_evaluate(const Schedule *schedule, const Context *context, DateList *out, char *error, size_t size) {
    Eval *eval = calloc(1, sizeof(Eval));
    if (!eval) abort();
    eval->error = error, eval->size = size;
    *out = (DateList) { 0 };
    volatile int status = 0;
    if (setjmp(eval->jump)) {
        datelist_free(out);
        status = -1;
    } else {
        Ctx ctx = { context->has_base, context->base, context->market, context->events, 0, 0 };
        evaluate_into(eval, schedule, &ctx, out);
    }
    for (int i = 0; i < eval->count; i++) {
        datelist_free(eval->temps[i]);
        free(eval->temps[i]);
    }
    free(eval->temps);
    free(eval);
    return status;
}

/* ---- markets and events ---- */

Market *market_new(Schedule *rule) {
    Market *market = calloc(1, sizeof(Market));
    if (!market) abort();
    market->rule = rule;
    return market;
}

void market_free(Market *market) {
    if (!market) return;
    for (int i = 0; i < market->count; i++) datelist_free(&market->years[i].holidays);
    free(market->years);
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
