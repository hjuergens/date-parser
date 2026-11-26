/*
 * Rule files: the schedule language of DateSchedule.g4 inside declarations with scope, validity, kind and provenance,
 * the same language as date-rule-lark's rules.lark (shared cases: examples/rules.txt). The extensions of the schedule
 * language are marked with "+". The same file, without actions, in date-rule-antlr-python and date-rule-antlr-java:
 * there the visitor of src/antlr_schedule/rules.py reuses the methods of the schedule visitor (labels as in
 * DateSchedule.g4) and catalog.py evaluates the declarations, here RuleVisitor.java and RuleCatalog.java.
 * Rules only project: announced dates are data given to the evaluator, never part of a rule.
 */
grammar RuleFile;

// ================= start rules =================

ruleFile
    : declaration* EOF
    ;

schedule
    : item (',' item)* EOF
    ;

// ================= declarations =================

declaration
    : 'rule' key=name 'for' eventClass=name 'in' scopes ('except' excluded=scopes)? validity? '=' body attributes? provenance? ';'  # ruleDecl
    | 'weekend' SCOPE '=' weekdaySet ('from' isoDate)? ';'      # weekendDecl
    | 'calendar' KEY '=' calendar ';'                           # calendarDecl
    ;

body
    : exprs                                 # exprsBody
    | 'ONE' 'OF' '{' alternative (',' alternative)* '}'   # oneOf  // + exactly one of the alternatives will happen
    ;

exprs
    : item (',' item)*
    ;

alternative
    : item 'AS' name
    ;

// a key or an event class; the words of kinds, precisions and labels are literals, so they are names here as well
// (an event class short_day, a rule kind = short_day)
name
    : KEY | 'closed' | 'non_working' | 'short_day' | 'working_day' | 'paid_no_rest' | 'tourist' | 'event'
    | 'special_session' | 'changed_hours' | 'day' | 'month' | 'official' | 'benchmark' | 'practice' | 'secondary'
    ;

scopes
    : SCOPE (',' SCOPE)*
    ;

validity
    : 'valid' ('from' from_=isoDate)? ('to' to=isoDate)?
    ;

attributes
    : 'with' attribute (',' attribute)*
    ;

attribute
    : 'kind' '=' kind=('closed' | 'non_working' | 'short_day' | 'working_day' | 'paid_no_rest' | 'tourist' | 'event'
                       | 'special_session' | 'changed_hours')         # kindAttribute
    | 'session' opens=time ('-' closes=time)? ('tz' STRING)?             # session
    | 'precision' '=' precision=('day' | 'month')                     # precisionAttribute
    | 'note' STRING                                                    # note
    ;

time
    : NUM ':' NUM
    ;

provenance
    : 'cites' citation (',' citation)*
    ;

citation
    : source=STRING ('art' article=STRING)? ('url' url=STRING)? 'label' label=('official' | 'benchmark' | 'practice' | 'secondary')
    ;

isoDate
    : y=NUM '-' m=NUM '-' d=NUM
    ;

// ================= calendars =================

// a business day in every term of "&"
calendar
    : calTerm ('&' calTerm)*
    ;

calTerm
    : SCOPE ignore?                 # scopeCalendar
    | KEY                           # namedCalendar
    | '(' calendar ')'              # nestedCalendar
    ;

// USD on T+1 does not count, unless for ARS, BRL, CLP, MXN
ignore
    : 'ignore' 'at' 'T' '+' NUM ('unless' '{' CCY (',' CCY)* '}')?
    ;

// + in an expression one term only, as "&" there is include: ON country:BR, ON usd_eur, ON (a & b)
on
    : 'ON' (SCOPE | KEY | '(' calendar ')')
    ;

// ================= expressions =================

// the condition applies to every date of the item; + the shifts after it to the dates it keeps
item
    : (ORDINAL fromEnd='LAST'? (series | EVENT) shift* | expr) combine* ifClause?
    ;

ifClause
    : 'IF' condition shift*
    ;

combine
    : op=('&' | '\\') operand shift*
    ;

operand
    : date                          # singleOperand
    | '{' expr (',' expr)* '}'      # setOperand
    ;

series
    : left=('[' | ']') first=endpoint ':' (step=period ':' last=endpoint? | last=endpoint) right=(']' | '[')
    ;

endpoint
    : singleDate shift*
    ;

expr
    : date shift*
    ;

shift
    : '+' period endend='ENDEND'? on?           # plus      // + ENDEND, on
    | '-' period endend='ENDEND'? on?           # minus
    | '>' 'IMM'                                 # imm
    | '>' 'SPOT' PAIR? on?                      # spot
    | '>' 'FIX' PAIR?                           # fix
    | '>' ROLL on?                              # roll
    | '>' 'NEXT' ORDINAL? anchor                # next
    | '>' 'PREV' ORDINAL? anchor                # prev
    | '>' 'NEAREST' anchor                      # nearest   // + ties go forward
    | '>' 'NEAREST' on                          # nearestRoll   // + the FpML convention, ON required
    | '>' 'CUTOFF' time zone                    # cutoff    // + the trade date of a base with a time of day
    | '>' count select 'IN' PERIOD              # selectIn
    | '>' '{' annual (',' annual)* '}'          # datesInYear
    | '>' 'POSTPONE' ROLL on? 'MAX' period cumulative='CUMULATIVE'?   # postpone  // +
    | '{' observe (',' observe)* '}'            # observance
    ;

// + several weekdays per entry and moves as targets: {TUE WED > PREV MON, THU FRI > NEXT MON}
observe
    : WEEKDAY+ sign=('+' | '-') period          # observePlus
    | WEEKDAY+ '>' 'NEXT' anchor                # observeNext
    | WEEKDAY+ '>' 'PREV' anchor                # observePrev
    | WEEKDAY+ '>' 'NEAREST' anchor             # observeNearest
    | WEEKDAY+ '>' ROLL on?                     # observeRoll
    ;

count
    : ORDINAL last='LAST'?
    | last='LAST'
    ;

select
    : chosen                        # singleSelect
    | '{' target (',' target)* '}'  # anySelect
    ;

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
    | 'BD' on?                      # businessDayAnchor   // + on
    | 'FD'                          # fixingDayAnchor
    | 'IMM'                         # immAnchor
    | fixedAnnual                   # annualAnchor
    | EVENT                         # eventAnchor
    | 'HOLIDAY' on?                 # holidayAnchor       // +
    | 'NONHOLIDAY' on?              # nonholidayAnchor    // +
    ;

date
    : singleDate                    # single
    | series                        # seriesDate
    ;

singleDate
    : 'T'                           # base
    | 'TOM'                         # tom
    | 'SPOT' PAIR? on?              # spotDate
    | y=NUM '-' m=NUM '-' d=NUM     # iso
    | d=NUM '.' m=NUM '.' y=NUM     # german
    | m=NUM '/' d=NUM '/' y=NUM     # us
    | annual                        # inYear
    | EVENT                         # event
    ;

annual
    : fixedAnnual                   # fixed
    | 'LAST' WEEKDAY MONTH          # lastWeekday
    ;

fixedAnnual
    : NUM MONTH hijriMethod?        # monthDay            // + method
    | NUM WEEKDAY MONTH             # nthWeekday
    | 'EASTER'                      # easter
    | 'ORTHODOX' 'EASTER'           # orthodoxEaster      // +
    | k=('EQUINOX' | 'SOLSTICE') MONTH zone?   # season   // + IANA zone
    | 'SOLARTERM' NUM zone?         # solarTerm
    | 'HINDU' KEY                   # hindu               // +
    ;

hijriMethod
    : 'BY' (method='UMMALQURA' | method='SIGHTING' | method='DIYANET' | method='FOLLOW' SCOPE)
    ;

zone
    : ZONE | STRING
    ;

period
    : NUM UNIT
    ;

// ================= conditions =================

condition
    : condAnd ('OR' condAnd)*
    ;

condAnd
    : condNot ('AND' condNot)*
    ;

condNot
    : negate='NOT'? condAtom
    ;

condAtom
    : weekdaySet                                # onWeekday         // the weekday of the date itself
    | 'DAY' first=NUM '..' last=NUM MONTH?      # onDay             // DAY 8..14 APR
    | subject 'IS' weekdaySet                   # isWeekday         // + another date
    | 'HOLIDAY' on?                             # isHoliday         // +
    | subject 'IS' 'HOLIDAY' on?                # dateIsHoliday
    | 'BETWEEN' 'HOLIDAYS' on?                  # betweenHolidays   // + JP art. 3(3)
    | 'EVERY' period 'FROM' NUM                 # every             // + EVERY 4Y FROM 1965
    | '(' condition ')'                         # nestedCondition
    ;

// 18 SEP, EASTER + 1D
subject
    : singleDate (sign+=('+' | '-') period)*
    ;

weekdaySet
    : WEEKDAY+
    | '{' WEEKDAY (',' WEEKDAY)* '}'
    ;

// ================= lexical =================

NUM  : [0-9]+ ;
ORDINAL : [0-9]+ ('ST' | 'ND' | 'RD' | 'TH') ;
ZONE : 'UTC' [+-] [0-9]+ ;
UNIT : [DWMQHYBF] ;
MONTH   : 'JAN' | 'FEB' | 'MAR' | 'APR' | 'MAY' | 'JUN' | 'JUL' | 'AUG' | 'SEP' | 'OCT' | 'NOV' | 'DEC'
        | 'MUHARRAM' | 'SAFAR' | 'RABIALAWWAL' | 'RABIALTHANI' | 'JUMADAALULA' | 'JUMADAALAKHIRA' | 'RAJAB' | 'SHABAN'
        | 'RAMADAN' | 'SHAWWAL' | 'DHULQADAH' | 'DHULHIJJAH'
        | 'NISAN' | 'IYAR' | 'SIVAN' | 'TAMMUZ' | 'AV' | 'ELUL' | 'TISHRI' | 'HESHVAN' | 'KISLEV' | 'TEVET' | 'SHEVAT'
        | 'ADAR' | 'ADARII'
        | 'ZHENGYUE' | 'ERYUE' | 'SANYUE' | 'SIYUE' | 'WUYUE' | 'LIUYUE' | 'QIYUE' | 'BAYUE' | 'JIUYUE' | 'SHIYUE'
        | 'DONGYUE' | 'LAYUE' ;
// a reference to a rule, an event class or an event: @za.freedom_day, @national_holiday, @FOMC
EVENT   : '@' [A-Za-z] [A-Za-z0-9_]* ('.' [A-Za-z0-9_]+)* ;
WEEKDAY : 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN' ;
ROLL    : 'FOLLOWING' | 'PRECEDING' | 'MODFOLLOWING' | 'MODPRECEDING'
        | 'BOW' | 'EOW' | 'BOM' | 'EOM' | 'BOQ' | 'EOQ' | 'BOH' | 'EOH' | 'BOY' | 'EOY' ;
PERIOD  : 'WEEK' | 'MONTH' | 'QUARTER' | 'HALFYEAR' | 'YEAR' ;
// a scope: country:JP, region:AU-NSW, settlement_system:TARGET2
SCOPE   : [a-z] [a-z_]* ':' [A-Z0-9] [A-Z0-9_]* ('-' [A-Z0-9] [A-Z0-9_]*)* ;
// keys, event classes and named calendars; the declaration words ('rule', 'for', ...) are literals and win at the same
// length, as ANTLR defines them first
KEY     : [a-z] [a-z0-9_]* ('.' [a-z] [a-z0-9_]*)* ;
STRING  : '"' ~["]* '"' ;
// keywords of six letters ('EASTER', 'TISHRI', 'ENDEND', 'FOLLOW') have the same length and win, as they are defined first
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;
// a currency, only inside "unless {…}"; three-letter keywords win
CCY  : [A-Z] [A-Z] [A-Z] ;
COMMENT : '#' ~[\r\n]* -> skip ;
WS   : [ \t\r\n]+ -> skip ;
