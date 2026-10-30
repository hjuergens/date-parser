/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * The actions build a ScheduleSemantics.Rule, a function of base date and market that is evaluated later.
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
    : range EOF                     { $result = $range.result; }
    | e+=expr (',' e+=expr)* EOF
      {
          List<Expr> exprs = new ArrayList<>();
          for (ExprContext expr : $e) exprs.add(expr.value);
          $result = ScheduleSemantics.list(exprs);
      }
    ;

range returns [Rule result]
    : '(' first=expr ':' step=period ':' last=expr ')'
      { $result = ScheduleSemantics.range($first.value, $step.value, $last.value); }
    ;

// periods are applied from left to right
expr returns [Expr value]
    : date                          { $value = $date.value; }
      ( '+' period                  { $value = ScheduleSemantics.plus($value, $period.value); }
      | '-' period                  { $value = ScheduleSemantics.minus($value, $period.value); }
      | '>' 'IMM'                   { $value = ScheduleSemantics.nextImm($value); }
      | '>' spot                    { $value = ScheduleSemantics.spot($value, $spot.pair); }
      | '>' ROLL                    { $value = ScheduleSemantics.roll($value, $ROLL.text); }
      | '>' 'NEXT' anchor           { $value = ScheduleSemantics.next($value, $anchor.value); }
      | '>' 'PREV' anchor           { $value = ScheduleSemantics.previous($value, $anchor.value); }
      | observance                  { $value = ScheduleSemantics.observe($value, $observance.shifts); }
      )*
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

anchor returns [Anchor value]
    : WEEKDAY                       { $value = ScheduleSemantics.weekday($WEEKDAY.text); }
    | 'BD'                          { $value = ScheduleSemantics.BUSINESS_DAY; }
    | 'IMM'                         { $value = ScheduleSemantics.IMM; }
    | annual                        { $value = ScheduleSemantics.annual($annual.value); }
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
    | y=NUM '-' m=NUM '-' d=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // ISO
    | d=NUM '.' m=NUM '.' y=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // German
    | m=NUM '/' d=NUM '/' y=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // US
    ;

// dates that recur every year, evaluated in the year of the base date
annual returns [Annual value]
    : NUM MONTH                     { $value = ScheduleSemantics.monthDay($NUM.int, $MONTH.text); }
    | NUM WEEKDAY MONTH             { $value = ScheduleSemantics.nthWeekday($NUM.int, $WEEKDAY.text, $MONTH.text); }
    | 'LAST' WEEKDAY MONTH          { $value = ScheduleSemantics.lastWeekday($WEEKDAY.text, $MONTH.text); }
    | 'EASTER'                      { $value = ScheduleSemantics.EASTER; }
    ;

period returns [java.time.temporal.TemporalAmount value]
    : NUM UNIT                      { $value = ScheduleSemantics.period($NUM.int, $UNIT.text); }
    ;

NUM  : [0-9]+ ;
UNIT : [DWMQYB] ;
MONTH   : 'JAN' | 'FEB' | 'MAR' | 'APR' | 'MAY' | 'JUN' | 'JUL' | 'AUG' | 'SEP' | 'OCT' | 'NOV' | 'DEC' ;
WEEKDAY : 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN' ;
ROLL    : 'FOLLOWING' | 'PRECEDING' | 'MODFOLLOWING' | 'BOM' | 'EOM' | 'BOY' | 'EOY' ;
// currency pair such as EURUSD; the keyword 'EASTER' has the same length and wins, as it is defined first
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;
WS   : [ \t\r\n]+ -> skip ;
