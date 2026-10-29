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
    import io.github.hjuergens.time.ScheduleSemantics.Expr;
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
      )*
    ;

// a rule of its own: a PAIR label in the loop above would keep the pair of an earlier iteration
spot returns [String pair]
    : 'SPOT' (PAIR                  { $pair = $PAIR.text; }
             )?
    ;

date returns [Expr value]
    : 'T'                           { $value = ScheduleSemantics.BASE; }                                       // base date
    | y=NUM '-' m=NUM '-' d=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // ISO
    | d=NUM '.' m=NUM '.' y=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // German
    | m=NUM '/' d=NUM '/' y=NUM     { $value = ScheduleSemantics.literal(LocalDate.of($y.int, $m.int, $d.int)); }  // US
    ;

period returns [java.time.temporal.TemporalAmount value]
    : NUM UNIT                      { $value = ScheduleSemantics.period($NUM.int, $UNIT.text); }
    ;

NUM  : [0-9]+ ;
UNIT : [DWMQYB] ;
PAIR : [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] [A-Z] ;   // currency pair such as EURUSD
WS   : [ \t\r\n]+ -> skip ;
