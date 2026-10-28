/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * Values are computed in the actions, java.time is used throughout.
 */
grammar DateSchedule;

@header {
    import java.time.LocalDate;
    import java.util.ArrayList;
    import java.util.List;
}

schedule returns [List<LocalDate> dates]
    : range EOF                     { $dates = $range.dates; }
    | e+=expr (',' e+=expr)* EOF
      {
          $dates = new ArrayList<>();
          for (ExprContext expr : $e) $dates.add(expr.value);
      }
    ;

range returns [List<LocalDate> dates]
    : '(' first=expr ':' step=period ':' last=expr ')'
      { $dates = ScheduleSemantics.range($first.value, $step.value, $last.value); }
    ;

// periods are applied from left to right
expr returns [LocalDate value]
    : date                          { $value = $date.value; }
      ( '+' period                  { $value = $value.plus($period.value); }
      | '-' period                  { $value = $value.minus($period.value); }
      )*
    ;

date returns [LocalDate value]
    : y=NUM '-' m=NUM '-' d=NUM     { $value = LocalDate.of($y.int, $m.int, $d.int); }  // ISO
    | d=NUM '.' m=NUM '.' y=NUM     { $value = LocalDate.of($y.int, $m.int, $d.int); }  // German
    | m=NUM '/' d=NUM '/' y=NUM     { $value = LocalDate.of($y.int, $m.int, $d.int); }  // US
    ;

period returns [java.time.Period value]
    : NUM UNIT                      { $value = ScheduleSemantics.period($NUM.int, $UNIT.text); }
    ;

NUM  : [0-9]+ ;
UNIT : [DWMQY] ;
WS   : [ \t\r\n]+ -> skip ;
