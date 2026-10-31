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
    | e+=item (',' e+=item)* EOF
      {
          List<Expr> exprs = new ArrayList<>();
          for (ItemContext item : $e) exprs.add(item.value);
          $result = ScheduleSemantics.list(exprs);
      }
    ;

// 24 DEC IF MON TUE WED THU: the date only on these weekdays
item returns [Expr value]
    : expr                          { $value = $expr.value; }
      ( 'IF' (w+=WEEKDAY)+
        {
            List<String> weekdays = new ArrayList<>();
            for (Token weekday : $w) weekdays.add(weekday.getText());
            $value = ScheduleSemantics.onlyOn($value, weekdays);
        }
      )?
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
// Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months
MONTH   : 'JAN' | 'FEB' | 'MAR' | 'APR' | 'MAY' | 'JUN' | 'JUL' | 'AUG' | 'SEP' | 'OCT' | 'NOV' | 'DEC'
        | 'MUHARRAM' | 'SAFAR' | 'RABIALAWWAL' | 'RABIALTHANI' | 'JUMADAALULA' | 'JUMADAALAKHIRA' | 'RAJAB' | 'SHABAN'
        | 'RAMADAN' | 'SHAWWAL' | 'DHULQADAH' | 'DHULHIJJAH'
        | 'NISAN' | 'IYAR' | 'SIVAN' | 'TAMMUZ' | 'AV' | 'ELUL' | 'TISHRI' | 'HESHVAN' | 'KISLEV' | 'TEVET' | 'SHEVAT'
        | 'ADAR' | 'ADARII'
        | 'ZHENGYUE' | 'ERYUE' | 'SANYUE' | 'SIYUE' | 'WUYUE' | 'LIUYUE' | 'QIYUE' | 'BAYUE' | 'JIUYUE' | 'SHIYUE'
        | 'DONGYUE' | 'LAYUE' ;
WEEKDAY : 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN' ;
ROLL    : 'FOLLOWING' | 'PRECEDING' | 'MODFOLLOWING' | 'BOM' | 'EOM' | 'BOY' | 'EOY' ;
// currency pair such as EURUSD; six-letter keywords ('EASTER', 'TISHRI', ...) have the same length and win, as they are defined first
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;
WS   : [ \t\r\n]+ -> skip ;
