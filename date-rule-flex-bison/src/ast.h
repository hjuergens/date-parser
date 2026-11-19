/* The syntax tree that the parser builds. Lists are linked through next; every node lives in the schedule's arena. */
#ifndef AST_H
#define AST_H

#include "schedule.h"
#include "catalog.h"

/* month codes: Gregorian, Hijri (Umm al-Qura), Hebrew (ADAR is Adar I in a leap year), Chinese */
enum { GREGORIAN = 0, HIJRI = 12, HEBREW = 24, CHINESE = 37, MONTHS = 49 };

/* IF DAY 8..14 APR in Item.weekdays: bit 7 set, the first and last day (capped at 63) and the month code + 1 (0: any
 * month); checked when the item is evaluated */
#define DAY_CAP(n) ((n) > 63 ? 63 : (n) < 0 ? 0 : (int) (n))
#define DAY_CONDITION(first, last, month) (1 << 7 | DAY_CAP(first) << 8 | DAY_CAP(last) << 14 | ((month) + 1) << 20)

typedef struct {
    long long months, days, business, fixing;
} Period;

typedef enum { MONTH_DAY, NTH_WEEKDAY, LAST_WEEKDAY, EASTER_SUNDAY, SUN_AT, SOLAR_TERM, ORTHODOX_EASTER, HINDU_DAY } AnnualKind;

typedef struct {
    AnnualKind kind;
    long n;          /* day of month, n-th weekday or solar term */
    int weekday;
    int month;       /* a month code */
    int equinox;     /* SUN_AT: 1 for EQUINOX, 0 for SOLSTICE */
    int offset;      /* minutes east of UTC */
    const char *zone;   /* rule files: an IANA time zone of SUN_AT and SOLAR_TERM ("Asia/Tokyo"), else NULL; HINDU_DAY: the key */
} Annual;

/* rule files: a calendar of ON or of a calendar declaration; country:BR, usd_eur, a & b */
typedef enum { CAL_SCOPE, CAL_NAMED, CAL_COMBINED } CalendarKind;

typedef struct Calendar {
    CalendarKind kind;
    const char *name;          /* CAL_SCOPE: the scope, CAL_NAMED: the calendar's key */
    int ignore;                /* CAL_SCOPE: ignored at T + ignore unless the pair has one of the currencies; 0: never */
    const char *unless;        /* the currencies, three letters each without separator: "ARSBRL" */
    struct Calendar *terms;    /* CAL_COMBINED: a business day in every term */
    struct Calendar *next;
} Calendar;

typedef enum { AT_WEEKDAY, AT_BUSINESS_DAY, AT_FIXING_DAY, AT_IMM, AT_ANNUAL, AT_EVENT, AT_ANY_OF, AT_HOLIDAY, AT_NONHOLIDAY } AnchorKind;

typedef struct Anchor {
    AnchorKind kind;
    int weekday;
    Annual annual;
    const char *event;
    struct Anchor *targets;   /* AT_ANY_OF */
    Calendar *on;             /* rule files: BD, HOLIDAY, NONHOLIDAY ON ...; NULL: the market of the context */
    struct Anchor *next;
} Anchor;

/* {SAT - 1D, SUN + 1D}; rule files: several weekdays (weekdays, a bit each) and a move as target (move) */
typedef struct Observe {
    int weekday, sign;
    Period period;
    int weekdays;          /* rule files: a bit per weekday; 0: weekday */
    struct Shift *move;    /* rule files: > NEXT, > PREV, > NEAREST, > ROLL instead of the period */
    struct Observe *next;
} Observe;

typedef enum { S_PLUS, S_MINUS, S_IMM, S_SPOT, S_FIX, S_ROLL, S_NEXT, S_PREV, S_SELECT, S_DATES_IN_YEAR, S_OBSERVANCE,
               S_NEAREST, S_POSTPONE } ShiftKind;

/* the conventions of > ROLL; MODPRECEDING only in rule files */
enum { FOLLOWING, PRECEDING, MODFOLLOWING, BOW, EOW, BOM, EOM, BOQ, EOQ, BOH, EOH, BOY, EOY, MODPRECEDING };

/* the periods of a selection (> 3RD WED IN MONTH) */
enum { P_WEEK, P_MONTH, P_QUARTER, P_HALFYEAR, P_YEAR };

typedef struct Shift {
    ShiftKind kind;
    Period period;
    const char *pair;      /* S_SPOT, S_FIX, may be NULL */
    int roll;
    long count;            /* S_NEXT, S_PREV, S_SELECT: the ordinal, 1 without one */
    int from_end, within;  /* S_SELECT: counted back from the end of the period; the period, a P_ code */
    Anchor *anchor;        /* S_DATES_IN_YEAR: a list of AT_ANNUAL anchors */
    Observe *observes;
    Calendar *on;          /* rule files: + 1M ON ..., > SPOT ON ..., > FOLLOWING ON ..., > POSTPONE ... ON ... */
    int endend;            /* rule files: + 1M ENDEND */
    int cumulative;        /* rule files: S_POSTPONE ... MAX period CUMULATIVE; the limit is in period */
    struct Shift *next;
} Shift;

typedef enum { D_BASE, D_TOM, D_SPOT, D_FIXED, D_ANNUAL, D_EVENT, D_SERIES } DateKind;

typedef struct {
    DateKind kind;
    const char *pair;      /* D_SPOT, may be NULL */
    Calendar *on;          /* rule files: SPOT ... ON ... */
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

/* rule files: a condition of IF */
typedef enum { C_ANY, C_ALL, C_NOT, C_WEEKDAY, C_DAY, C_IS_WEEKDAY, C_IS_HOLIDAY, C_DATE_IS_HOLIDAY, C_BETWEEN_HOLIDAYS,
               C_EVERY } ConditionKind;

typedef struct Condition {
    ConditionKind kind;
    struct Condition *parts;   /* C_ANY, C_ALL: a list; C_NOT: the one negated */
    int weekdays;              /* C_WEEKDAY, C_IS_WEEKDAY: a bit per weekday */
    long first, last;          /* C_DAY: the days; C_EVERY: every first years from last */
    int month;                 /* C_DAY: a month code, -1: any */
    struct Expr *subject;      /* C_IS_WEEKDAY, C_DATE_IS_HOLIDAY: 18 SEP, EASTER + 1D */
    Calendar *on;
    struct Condition *next;
} Condition;

typedef struct Item {
    Expr *expr;
    Combine *combines;
    int has_pick, from_end;   /* 5TH [SPOT : 1Q : ] ...: the pick-th date after T, 2ND LAST from the last one */
    long pick;
    int weekdays;          /* IF: a bit per weekday, 0 without IF; or DAY_CONDITION */
    Condition *condition;  /* rule files: IF condition, NULL without */
    Shift *if_shifts;      /* rule files: the shifts after the condition, applied to the dates it keeps */
    struct Item *next;
} Item;

/* ---- rule files (rulefile.y): declarations around the schedule language ---- */

typedef struct Name {
    const char *text;
    struct Name *next;
} Name;

/* ONE OF {item AS name, ...} */
typedef struct Alternative {
    const char *name;
    Item *items;
    struct Alternative *next;
} Alternative;

typedef enum { DECL_RULE, DECL_WEEKEND, DECL_CALENDAR } DeclarationKind;

typedef struct Declaration {
    DeclarationKind kind;
    const char *key;            /* the rule's key, the calendar's key, the weekend's scope */
    const char *event_class;
    Name *include, *exclude;
    int has_from, valid_from, has_to, valid_to;   /* a weekend regime only has from */
    Item *items;                /* the body, NULL for ONE OF */
    Alternative *alternatives;  /* ONE OF */
    const char *kind_of_day;    /* with kind = ...; NULL: closed */
    const char *precision, *note;
    const char *session[3];     /* opens, closes, time zone; NULL without session */
    Citation *citations;
    int weekdays;               /* weekend: a bit per weekday */
    Calendar *calendar;         /* calendar declaration */
    Name *references;           /* the @keys of the rule */
    int estimated;              /* a Hijri method other than UMMALQURA */
    struct Declaration *next;
} Declaration;

typedef struct Block Block;

struct Schedule {
    Block *arena;
    Item *items;
    Declaration *declarations;   /* rule files */
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
