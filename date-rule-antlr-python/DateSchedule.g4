/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * The rules of date-rule-antlr/.../DateSchedule.g4, but without actions: Java actions can't be
 * generated into Python, so a visitor (src/antlr_schedule/__init__.py) builds the rule.
 */
grammar DateSchedule;

schedule
    : range EOF
    | item (',' item)* EOF
    ;

// 24 DEC IF MON TUE WED THU: the date only on these weekdays
item
    : expr ('IF' WEEKDAY+)?
    ;

range
    : '(' first=expr ':' step=period ':' last=expr ')'
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
    | '>' 'NEXT' anchor             # next
    | '>' 'PREV' anchor             # prev
    | '{' observe (',' observe)* '}' # observance
    ;

observe
    : WEEKDAY sign=('+' | '-') period
    ;

anchor
    : WEEKDAY                       # weekdayAnchor
    | 'BD'                          # businessDayAnchor
    | 'IMM'                         # immAnchor
    | annual                        # annualAnchor
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
    ;

// dates that recur every year, evaluated in the year of the base date
annual
    : NUM MONTH                     # monthDay
    | NUM WEEKDAY MONTH             # nthWeekday
    | 'LAST' WEEKDAY MONTH          # lastWeekday
    | 'EASTER'                      # easter
    ;

period
    : NUM UNIT
    ;

NUM  : [0-9]+ ;
UNIT : [DWMQYB] ;
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
ROLL    : 'FOLLOWING' | 'PRECEDING' | 'MODFOLLOWING' | 'BOM' | 'EOM' | 'BOY' | 'EOY' ;
// currency pair such as EURUSD; six-letter keywords ('EASTER', 'TISHRI', ...) have the same length and win, as they are defined first
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;
WS   : [ \t\r\n]+ -> skip ;
