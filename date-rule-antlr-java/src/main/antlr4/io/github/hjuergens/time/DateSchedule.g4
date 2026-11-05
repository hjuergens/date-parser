/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, [2015-01-15 : 1Q : 2015-12-31]
 * The actions build a ScheduleSemantics.Rule, a function of base date and market that is evaluated later.
 * A second start rule, ruleLine, reads one line of a rules file of date-rule-runtime.
 */
grammar DateSchedule;

@header {
    import java.time.LocalDate;
    import java.util.ArrayList;
    import java.util.List;
    import io.github.hjuergens.time.ScheduleSemantics.Annual;
    import io.github.hjuergens.time.ScheduleSemantics.Anchor;
    import io.github.hjuergens.time.ScheduleSemantics.Expr;
    import io.github.hjuergens.time.ScheduleSemantics.Shift;
    import io.github.hjuergens.time.ScheduleSemantics.Rule;
}

schedule returns [Rule result]
    : items EOF                     { $result = $items.result; }
    ;

items returns [Rule result]
    : e+=item (',' e+=item)*
      {
          List<Expr> exprs = new ArrayList<>();
          for (ItemContext item : $e) exprs.add(item.value);
          $result = ScheduleSemantics.list(exprs);
      }
    ;

// one line of a rules file (date-rule-runtime): a declaration, or an assertion of the dates a rule gives.
// Names carry the sigil, as a bare IMMH or XNYS would clash with the keywords, months and currency pairs:
//   rule @IMMH = T > NEXT 3 WED MAR        market @XNYS = 1 JAN, 25 DEC        event @FOMC = 2026-01-28, ...
//   assert @IMMH [2026-11-02 @XNYS] => 2027-03-17
ruleLine returns [String kind, String name, Rule rule, LocalDate base, String market, List<LocalDate> dates]
    : k=('rule' | 'market' | 'event') n=EVENT '=' items EOF
                                    { $kind = $k.text; $name = $n.text.substring(1); $rule = $items.result; }
    | 'assert' n=EVENT '[' b=isoDate (m=EVENT)? ']' '=>' e=expected EOF
      {
          $kind = "assert";
          $name = $n.text.substring(1);
          $base = $b.value;
          $market = $m == null ? null : $m.text.substring(1);
          $dates = $e.dates;
      }
    ;

// the expected dates of an assertion: a list, none, or error (null)
expected returns [List<LocalDate> dates = new ArrayList<>()]
    : 'none'
    | 'error'                       { $dates = null; }
    | d=isoDate                     { $dates.add($d.value); }
      (',' d=isoDate                { $dates.add($d.value); }
      )*
    ;

isoDate returns [LocalDate value]
    : y=NUM '-' m=NUM '-' d=NUM     { $value = LocalDate.of($y.int, $m.int, $d.int); }
    ;

// 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays
item returns [Expr value]
    : expr                          { $value = $expr.value; }
      // A & B includes the dates of B, A \ B excludes them; the moves after it apply to the result
      ( op=('&' | '\\') operand      { $value = ScheduleSemantics.combined($value, $op.text.equals("&"), $operand.exprs); }
        ( move[$value]              { $value = $move.value; }
        )*
      )*
      ( 'IF' ((w+=WEEKDAY)+ | '{' w+=WEEKDAY (',' w+=WEEKDAY)* '}')
        {
            List<String> weekdays = new ArrayList<>();
            for (Token weekday : $w) weekdays.add(weekday.getText());
            $value = ScheduleSemantics.onlyOn($value, weekdays);
        }
      )?
    ;

// a date, a series or a set of dates
operand returns [List<Expr> exprs = new ArrayList<>()]
    : date                          { $exprs.add($date.value); }
    | '{' e=expr                    { $exprs.add($e.value); }
      (',' e=expr                   { $exprs.add($e.value); }
      )* '}'
    ;

// [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out (ISO 80000-2).
// A series is a date with several values, like an event: the shifts after it apply to each element
series returns [Expr value]
locals [boolean startOut, boolean endOut]
    : ('[' | ']'                    { $startOut = true; }
      ) first=expr ':' (step=period ':')? last=expr
      (']' | '['                    { $endOut = true; }
      )                             { $value = ScheduleSemantics.series($first.value, $step.ctx == null ? null : $step.value,
                                                                        $last.value, $startOut, $endOut); }
    ;

// periods are applied from left to right
expr returns [Expr value]
    : date                          { $value = $date.value; }
      ( move[$value]                { $value = $move.value; }
      )*
    ;

// one step, applied to everything on its left
move[Expr before] returns [Expr value]
locals [List<Annual> annuals = new ArrayList<>()]
    : '+' period                    { $value = ScheduleSemantics.plus($before, $period.value); }
    | '-' period                    { $value = ScheduleSemantics.minus($before, $period.value); }
    | '>' 'IMM'                     { $value = ScheduleSemantics.nextImm($before); }
    | '>' spot                      { $value = ScheduleSemantics.spot($before, $spot.pair); }
    | '>' ROLL                      { $value = ScheduleSemantics.roll($before, $ROLL.text); }
    | '>' 'NEXT' ordinal anchor     { $value = ScheduleSemantics.next($before, $ordinal.n, $anchor.value); }
    | '>' 'PREV' ordinal anchor     { $value = ScheduleSemantics.previous($before, $ordinal.n, $anchor.value); }
    | '>' count select 'IN' PERIOD
                                    { $value = ScheduleSemantics.select($before, $count.n, $count.fromEnd, $select.value, $PERIOD.text); }
      // > {1 FEB, 1 MAY}: the dates of the set in the year of the date
    | '>' '{' a=annual              { $annuals.add($a.value); }
      (',' a=annual                 { $annuals.add($a.value); }
      )* '}'                        { $value = ScheduleSemantics.datesInYear($before, $annuals); }
    | observance                    { $value = ScheduleSemantics.observe($before, $observance.shifts); }
    ;

// {SAT - 1D, SUN + 1D}
observance returns [List<Shift> shifts = new ArrayList<>()]
    : '{' s=shift                   { $shifts.add($s.value); }
      (',' s=shift                  { $shifts.add($s.value); }
      )* '}'
    ;

shift returns [Shift value]
    : WEEKDAY '+' period            { $value = ScheduleSemantics.shift($WEEKDAY.text, $period.value, 1); }
    | WEEKDAY '-' period            { $value = ScheduleSemantics.shift($WEEKDAY.text, $period.value, -1); }
    ;

// NEXT 3RD FRI: the third Friday; a rule of its own for the same reason as spot below
ordinal returns [int n = 1]
    : (ORDINAL                      { $n = ScheduleSemantics.ordinal($ORDINAL.text); }
      )?
    ;

// > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH: the n-th match in the period of the date, from its start or its end
count returns [int n = 1, boolean fromEnd]
    : ORDINAL                       { $n = ScheduleSemantics.ordinal($ORDINAL.text); }
      ('LAST'                       { $fromEnd = true; }
      )?
    | 'LAST'                        { $fromEnd = true; }
    ;

// the targets of NEXT; an annual date starting with LAST only in braces, as LAST also starts the count
select returns [Anchor value]
locals [List<Anchor> anchors = new ArrayList<>()]
    : chosen                        { $value = $chosen.value; }
    | '{' t=target                  { $anchors.add($t.value); }
      (',' t=target                 { $anchors.add($t.value); }
      )* '}'                        { $value = ScheduleSemantics.anyOf($anchors); }
    ;

// NEXT {MON, FRI}: the nearest of a set
anchor returns [Anchor value]
locals [List<Anchor> anchors = new ArrayList<>()]
    : target                        { $value = $target.value; }
    | '{' t=target                  { $anchors.add($t.value); }
      (',' t=target                 { $anchors.add($t.value); }
      )* '}'                        { $value = ScheduleSemantics.anyOf($anchors); }
    ;

target returns [Anchor value]
    : chosen                        { $value = $chosen.value; }
    | 'LAST' WEEKDAY MONTH          { $value = ScheduleSemantics.annual(ScheduleSemantics.lastWeekday($WEEKDAY.text, $MONTH.text)); }
    ;

chosen returns [Anchor value]
    : WEEKDAY                       { $value = ScheduleSemantics.weekday($WEEKDAY.text); }
    | 'BD'                          { $value = ScheduleSemantics.BUSINESS_DAY; }
    | 'IMM'                         { $value = ScheduleSemantics.IMM; }
    | fixedAnnual                   { $value = ScheduleSemantics.annual($fixedAnnual.value); }
    | EVENT                         { $value = ScheduleSemantics.eventAnchor($EVENT.text.substring(1)); }
    ;

// a rule of its own: a PAIR label in the loop above would keep the pair of an earlier iteration
spot returns [String pair]
    : 'SPOT' (PAIR                  { $pair = $PAIR.text; }
             )?
    ;

date returns [Expr value]
    : 'T'                           { $value = ScheduleSemantics.BASE; }                                       // base date
    | 'TOM'                         { $value = ScheduleSemantics.plus(ScheduleSemantics.BASE, ScheduleSemantics.period(1, "B")); }
    | spot                          { $value = ScheduleSemantics.spot(ScheduleSemantics.BASE, $spot.pair); }
    | annual                        { $value = ScheduleSemantics.inYear($annual.value); }
    | EVENT                         { $value = ScheduleSemantics.event($EVENT.text.substring(1)); }
    | series                        { $value = $series.value; }
    | y=NUM '-' m=NUM '-' d=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // ISO
    | d=NUM '.' m=NUM '.' y=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // German
    | m=NUM '/' d=NUM '/' y=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // US
    ;

// dates that recur every year, evaluated in the year of the base date
annual returns [Annual value]
    : fixedAnnual                   { $value = $fixedAnnual.value; }
    | 'LAST' WEEKDAY MONTH          { $value = ScheduleSemantics.lastWeekday($WEEKDAY.text, $MONTH.text); }
    ;

fixedAnnual returns [Annual value]
    : NUM MONTH                     { $value = ScheduleSemantics.monthDay($NUM.int, $MONTH.text); }
    | NUM WEEKDAY MONTH             { $value = ScheduleSemantics.nthWeekday($NUM.int, $WEEKDAY.text, $MONTH.text); }
    | 'EASTER'                      { $value = ScheduleSemantics.EASTER; }
    | k=('EQUINOX' | 'SOLSTICE') MONTH zone
                                    { $value = ScheduleSemantics.season($k.text, $MONTH.text, $zone.offset); }
    | 'SOLARTERM' NUM zone          { $value = ScheduleSemantics.solarTerm($NUM.int, $zone.offset); }
    ;

// the time zone of an equinox, solstice or solar term: UTC unless an offset follows
zone returns [int offset]
    : (ZONE                         { $offset = ScheduleSemantics.zone($ZONE.text); }
      )?
    ;

period returns [java.time.temporal.TemporalAmount value]
    : NUM UNIT                      { $value = ScheduleSemantics.period($NUM.int, $UNIT.text); }
    ;

NUM  : [0-9]+ ;
ZONE : 'UTC' [+-] [0-9]+ ;
// 1ST, 2ND, 3RD, 4TH: the longer match wins over NUM
ORDINAL : [0-9]+ ('ST' | 'ND' | 'RD' | 'TH') ;
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
