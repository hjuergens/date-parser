/* The syntax tree that the parser builds. Lists are linked through next; every node lives in the schedule's arena. */
#ifndef AST_H
#define AST_H

#include "schedule.h"

/* month codes: Gregorian, Hijri (Umm al-Qura), Hebrew (ADAR is Adar I in a leap year), Chinese */
enum { GREGORIAN = 0, HIJRI = 12, HEBREW = 24, CHINESE = 37, MONTHS = 49 };

typedef struct {
    long long months, days, business;
} Period;

typedef enum { MONTH_DAY, NTH_WEEKDAY, LAST_WEEKDAY, EASTER_SUNDAY, SUN_AT, SOLAR_TERM } AnnualKind;

typedef struct {
    AnnualKind kind;
    long n;          /* day of month, n-th weekday or solar term */
    int weekday;
    int month;       /* a month code */
    int equinox;     /* SUN_AT: 1 for EQUINOX, 0 for SOLSTICE */
    int offset;      /* minutes east of UTC */
} Annual;

typedef enum { AT_WEEKDAY, AT_BUSINESS_DAY, AT_IMM, AT_ANNUAL, AT_EVENT, AT_ANY_OF } AnchorKind;

typedef struct Anchor {
    AnchorKind kind;
    int weekday;
    Annual annual;
    const char *event;
    struct Anchor *targets;   /* AT_ANY_OF */
    struct Anchor *next;
} Anchor;

typedef struct Observe {
    int weekday, sign;
    Period period;
    struct Observe *next;
} Observe;

typedef enum { S_PLUS, S_MINUS, S_IMM, S_SPOT, S_ROLL, S_NEXT, S_PREV, S_SELECT, S_DATES_IN_YEAR, S_OBSERVANCE } ShiftKind;

/* the conventions of > ROLL */
enum { FOLLOWING, PRECEDING, MODFOLLOWING, BOW, EOW, BOM, EOM, BOQ, EOQ, BOH, EOH, BOY, EOY };

/* the periods of a selection (> 3RD WED IN MONTH) */
enum { P_WEEK, P_MONTH, P_QUARTER, P_HALFYEAR, P_YEAR };

typedef struct Shift {
    ShiftKind kind;
    Period period;
    const char *pair;      /* S_SPOT, may be NULL */
    int roll;
    long count;            /* S_NEXT, S_PREV, S_SELECT: the ordinal, 1 without one */
    int from_end, within;  /* S_SELECT: counted back from the end of the period; the period, a P_ code */
    Anchor *anchor;        /* S_DATES_IN_YEAR: a list of AT_ANNUAL anchors */
    Observe *observes;
    struct Shift *next;
} Shift;

typedef enum { D_BASE, D_TOM, D_SPOT, D_FIXED, D_ANNUAL, D_EVENT, D_SERIES } DateKind;

typedef struct {
    DateKind kind;
    const char *pair;      /* D_SPOT, may be NULL */
    int fixed;             /* D_FIXED */
    Annual annual;
    const char *event;
    struct Expr *first, *last;  /* D_SERIES: [first : step : last], like an event a date with several values; last NULL: open */
    Period step;
    int start_out, end_out;     /* "]" at the start, "[" at the end: that endpoint is left out */
} Date;

typedef struct Expr {
    Date date;
    Shift *shifts;
} Expr;

/* A & B, A \\ B: a date, a series or a set of dates */
typedef struct Operand {
    Expr *expr;
    struct Operand *next;
} Operand;

typedef struct Combine {
    int include;           /* & (include) or \\ (exclude) */
    Operand *operands;
    Shift *shifts;         /* applied to the result */
    struct Combine *next;
} Combine;

typedef struct Item {
    Expr *expr;
    Combine *combines;
    int has_pick, from_end;   /* 5TH [SPOT : 1Q : ] ...: the pick-th date after T, 2ND LAST from the last one */
    long pick;
    int weekdays;          /* IF: a bit per weekday, 0 without IF */
    struct Item *next;
} Item;

typedef struct Block Block;

struct Schedule {
    Block *arena;
    Item *items;
};

void *arena_alloc(Schedule *schedule, size_t size);
char *arena_strdup(Schedule *schedule, const char *text);
void arena_free(Schedule *schedule);

/* what the scanner and the parser share */
typedef struct {
    Schedule *schedule;
    char *error;
    size_t size;
} Parse;

/* the code of a word of capital letters: a month, a weekday, a roll, ... see scanner */
int month_code(const char *name);

#endif
