/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * The rules of date-rule-antlr-java/.../DateSchedule.g4, but without actions: Java actions can't be
 * generated into Python, so a visitor (src/antlr_schedule/__init__.py) builds the rule.
 */
grammar DateSchedule;

schedule
    : item (',' item)* EOF
    ;

// 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays
item
    : expr combine* ('IF' (WEEKDAY+ | '{' WEEKDAY (',' WEEKDAY)* '}'))?
    ;

// A & B includes the dates of B, A \ B excludes them; the shifts after it apply to the result
combine
    : op=('&' | '\\') operand shift*
    ;

// a date, a series or a set of dates
operand
    : date                          # singleOperand
    | '{' expr (',' expr)* '}'      # setOperand
    ;

// [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out (ISO 80000-2).
// A series is a date with several values, like an event: the shifts after it apply to each element
series
    : left=('[' | ']') first=expr ':' (step=period ':')? last=expr right=(']' | '[')
    ;

// shifts are applied from left to right
expr
    : date shift*
    ;

shift
    : '+' period                    # plus
    | '-' period                    # minus
    | '>' 'IMM'                     # imm
    | '>' 'SPOT' PAIR?              # spot
    | '>' ROLL                      # roll
    | '>' 'NEXT' ORDINAL? anchor    # next
    | '>' 'PREV' ORDINAL? anchor    # prev
    | '>' count select 'IN' PERIOD  # selectIn
    | '>' '{' annual (',' annual)* '}' # datesInYear
    | '{' observe (',' observe)* '}' # observance
    ;

observe
    : WEEKDAY sign=('+' | '-') period
    ;

// > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH: the n-th match in the period of the date, from its start or its end
count
    : ORDINAL last='LAST'?
    | last='LAST'
    ;

// the targets of NEXT; an annual date starting with LAST only in braces, as LAST also starts the count
select
    : chosen                        # singleSelect
    | '{' target (',' target)* '}'  # anySelect
    ;

// NEXT {MON, FRI}: the nearest of a set
anchor
    : target                        # singleAnchor
    | '{' target (',' target)* '}'  # anyOf
    ;

target
    : chosen                        # chosenTarget
    | 'LAST' WEEKDAY MONTH          # lastWeekdayAnchor
    ;

chosen
    : WEEKDAY                       # weekdayAnchor
    | 'BD'                          # businessDayAnchor
    | 'IMM'                         # immAnchor
    | fixedAnnual                   # annualAnchor
    | EVENT                         # eventAnchor
    ;

date
    : 'T'                           # base
    | 'TOM'                         # tom
    | 'SPOT' PAIR?                  # spotDate
    | y=NUM '-' m=NUM '-' d=NUM     # iso
    | d=NUM '.' m=NUM '.' y=NUM     # german
    | m=NUM '/' d=NUM '/' y=NUM     # us
    | annual                        # inYear
    | EVENT                         # event
    | series                        # seriesDate
    ;

// dates that recur every year, evaluated in the year of the base date
annual
    : fixedAnnual                   # fixed
    | 'LAST' WEEKDAY MONTH          # lastWeekday
    ;

fixedAnnual
    : NUM MONTH                     # monthDay
    | NUM WEEKDAY MONTH             # nthWeekday
    | 'EASTER'                      # easter
    | k=('EQUINOX' | 'SOLSTICE') MONTH ZONE?   # season
    | 'SOLARTERM' NUM ZONE?         # solarTerm
    ;

period
    : NUM UNIT
    ;

NUM  : [0-9]+ ;
// 1ST, 2ND, 3RD, 4TH: the longer match wins over NUM
ORDINAL : [0-9]+ ('ST' | 'ND' | 'RD' | 'TH') ;
// the time zone of an equinox, solstice or solar term: UTC unless an offset follows
ZONE : 'UTC' [+-] [0-9]+ ;
UNIT : [DWMQHYB] ;
// Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months
MONTH   : 'JAN' | 'FEB' | 'MAR' | 'APR' | 'MAY' | 'JUN' | 'JUL' | 'AUG' | 'SEP' | 'OCT' | 'NOV' | 'DEC'
        | 'MUHARRAM' | 'SAFAR' | 'RABIALAWWAL' | 'RABIALTHANI' | 'JUMADAALULA' | 'JUMADAALAKHIRA' | 'RAJAB' | 'SHABAN'
        | 'RAMADAN' | 'SHAWWAL' | 'DHULQADAH' | 'DHULHIJJAH'
        | 'NISAN' | 'IYAR' | 'SIVAN' | 'TAMMUZ' | 'AV' | 'ELUL' | 'TISHRI' | 'HESHVAN' | 'KISLEV' | 'TEVET' | 'SHEVAT'
        | 'ADAR' | 'ADARII'
        | 'ZHENGYUE' | 'ERYUE' | 'SANYUE' | 'SIYUE' | 'WUYUE' | 'LIUYUE' | 'QIYUE' | 'BAYUE' | 'JIUYUE' | 'SHIYUE'
        | 'DONGYUE' | 'LAYUE' ;
// a named event such as @FOMC; the sigil keeps names apart from keywords and pairs
EVENT   : '@' [A-Z] [A-Z0-9_]* ;
WEEKDAY : 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN' ;
ROLL    : 'FOLLOWING' | 'PRECEDING' | 'MODFOLLOWING' | 'BOW' | 'EOW' | 'BOM' | 'EOM' | 'BOQ' | 'EOQ' | 'BOH' | 'EOH' | 'BOY' | 'EOY' ;
// the periods of a selection; WEEK is Monday to Sunday
PERIOD  : 'WEEK' | 'MONTH' | 'QUARTER' | 'HALFYEAR' | 'YEAR' ;
// currency pair such as EURUSD; six-letter keywords ('EASTER', 'TISHRI', ...) have the same length and win, as they are defined first
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;
WS   : [ \t\r\n]+ -> skip ;
