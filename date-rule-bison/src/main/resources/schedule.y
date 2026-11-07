/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * LALR(1): lists and period chains are written left recursive.
 * The actions build a ScheduleSemantics.Rule, a function of base date and market that is evaluated later.
 * No 'error' rules: the first syntax error ends the parse (parse() returns false).
 */
%language "Java"
%define api.package {io.github.hjuergens.bison}
%define api.parser.class {ScheduleParser}
%define api.parser.public

%code imports {
  import java.time.LocalDate;
  import java.time.temporal.TemporalAmount;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.function.UnaryOperator;
  import io.github.hjuergens.bison.ScheduleSemantics.Anchor;
  import io.github.hjuergens.bison.ScheduleSemantics.Annual;
  import io.github.hjuergens.bison.ScheduleSemantics.Expr;
  import io.github.hjuergens.bison.ScheduleSemantics.Shift;
  import io.github.hjuergens.bison.ScheduleSemantics.Rule;
}

%code {
  /** the rule of the schedule, set by a successful parse() */
  public Rule rule;

  /** the moves and combines of an item applied to its start, then IF (null: none) */
  static Expr item (Expr start, List<UnaryOperator<Expr>> moves, List<UnaryOperator<Expr>> combines, List<String> weekdays)
  {
    Expr d = start;
    for (UnaryOperator<Expr> m : moves)
      d = m.apply (d);
    for (UnaryOperator<Expr> c : combines)
      d = c.apply (d);
    return weekdays == null ? d : ScheduleSemantics.onlyOn (d, weekdays);
  }
}

%token <Integer> NUM "number"
%token <String> ORDINAL "ordinal"
%token <String> UNIT "unit"
%token IMM "IMM"
%token SPOT "SPOT"
%token BASE "T"
%token TOM "TOM"
%token NEXT "NEXT"
%token PREV "PREV"
%token BD "BD"
%token FD "FD"
%token FIX "FIX"
%token LAST "LAST"
%token EASTER "EASTER"
%token IF "IF"
%token IN "IN"
%token <String> PERIOD "period"
%token <String> PAIR "currency pair"
%token <String> MONTH "month"
%token <String> WEEKDAY "weekday"
%token <String> ROLL "roll convention"
%token <String> EVENT "event"
%token <String> SEASON "EQUINOX or SOLSTICE"
%token SOLARTERM "SOLARTERM"
%token <Integer> ZONE "time zone"
%type <Integer> zone count
%type <Expr> item picked_item expr combined date series endpoint single_date
%type <Integer> pick
%type <ArrayList<String>> condition
%type <UnaryOperator<Expr>> move combine
%type <ArrayList<UnaryOperator<Expr>>> moves combines
%type <ArrayList<Expr>> operand operand_exprs
%type <ArrayList<Annual>> annuals
%type <Boolean> start_out end_out
%type <ArrayList<String>> weekdays weekday_set
%type <Annual> annual fixed_annual
%type <Anchor> counted anchor target select chosen
%type <ArrayList<Anchor>> targets
%type <Shift> shift
%type <ArrayList<Shift>> shifts
%type <TemporalAmount> period
%type <ArrayList<Expr>> exprs

%%

schedule:
  exprs                             { rule = ScheduleSemantics.list ($1); }
;

exprs:
  item                              { ArrayList<Expr> list = new ArrayList<> (); list.add ($1); $$ = list; }
| exprs ',' item                    { $1.add ($3); $$ = $1; }   /* $$ is untyped in Java, $1 is not */
;

/* 24 DEC IF MON TUE WED THU, IF {MON, TUE}: the date only on these weekdays */
item:
  combined condition                { $$ = $2 == null ? $1 : ScheduleSemantics.onlyOn ($1, $2); }
| picked_item                       { $$ = $1; }
;

/* 5TH [SPOT : 1Q : ] > EOQ > 3RD WED IN MONTH: the 5th date after T of the item; its item starts with a series or an
   event, where several dates come from */
picked_item:
  pick series moves combines condition { $$ = ScheduleSemantics.pick (Math.abs ($1), $1 < 0, item ($2, $3, $4, $5)); }
| pick EVENT moves combines condition  { $$ = ScheduleSemantics.pick (Math.abs ($1), $1 < 0, item (ScheduleSemantics.event ($2), $3, $4, $5)); }
;

/* 2ND LAST from the last one: a negative count (0 is an error anyway) */
pick:
  ORDINAL                           { $$ = ScheduleSemantics.ordinal ($1); }
| ORDINAL LAST                      { $$ = -ScheduleSemantics.ordinal ($1); }
;

condition:
  %empty                            { $$ = null; }
| IF weekdays                       { $$ = $2; }
| IF '{' weekday_set '}'            { $$ = $3; }
;

weekday_set:
  WEEKDAY                           { ArrayList<String> list = new ArrayList<> (); list.add ($1); $$ = list; }
| weekday_set ',' WEEKDAY           { $1.add ($3); $$ = $1; }
;

/* [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out. A series is
   a date with several values, like an event: the shifts after it apply to each element */
series:
  start_out endpoint ':' period ':' endpoint end_out   { $$ = ScheduleSemantics.series ($2, $4, $6, $1, $7); }
| start_out endpoint ':' period ':' end_out            { $$ = ScheduleSemantics.series ($2, $4, null, $1, $6); }   /* open */
| start_out endpoint ':' endpoint end_out              { $$ = ScheduleSemantics.series ($2, null, $4, $1, $5); }
;

/* an endpoint is one date, so it can't be a series: after the last ":" a "]" closes an open series */
endpoint:
  single_date                       { $$ = $1; }
| endpoint move                     { $$ = $2.apply ($1); }
;

start_out:
  '['                               { $$ = false; }
| ']'                               { $$ = true; }
;

end_out:
  ']'                               { $$ = false; }
| '['                               { $$ = true; }
;

weekdays:
  WEEKDAY                           { ArrayList<String> list = new ArrayList<> (); list.add ($1); $$ = list; }
| weekdays WEEKDAY                  { $1.add ($2); $$ = $1; }
;

/* periods are applied from left to right */
expr:
  date                              { $$ = $1; }
| expr move                         { $$ = $2.apply ($1); }
;

/* one step, applied to everything on its left: a function of the expression so far ($$ is an Object in the Java
   skeleton, so each lambda is cast to its type) */
move:
  '+' period                        { TemporalAmount p = $2; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.plus (e, p); }
| '-' period                        { TemporalAmount p = $2; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.minus (e, p); }
| '>' IMM                           { $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.nextImm (e); }
| '>' SPOT                          { $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.spot (e, null); }
| '>' SPOT PAIR                     { String pair = $3; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.spot (e, pair); }
| '>' FIX                           { $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.fix (e, null); }   /* the fixing date */
| '>' FIX PAIR                      { String pair = $3; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.fix (e, pair); }
| '>' ROLL                          { String roll = $2; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.roll (e, roll); }
| '>' NEXT counted                  { Anchor a = $3; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.next (e, a); }
| '>' PREV counted                  { Anchor a = $3; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.previous (e, a); }
| '>' count select IN PERIOD        { int n = $2; Anchor a = $3; String period = $5;
                                      $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.select (e, Math.abs (n), n < 0, a, period); }
| '>' '{' annuals '}'               { ArrayList<Annual> list = $3; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.datesInYear (e, list); }
| '{' shifts '}'                    { ArrayList<Shift> s = $2; $$ = (UnaryOperator<Expr>) e -> ScheduleSemantics.observe (e, s); }   /* {SAT - 1D, SUN + 1D} */
;

annuals:
  annual                            { ArrayList<Annual> list = new ArrayList<> (); list.add ($1); $$ = list; }
| annuals ',' annual                { $1.add ($3); $$ = $1; }
;

/* A & B includes the dates of B, A \\ B excludes them; the moves after it apply to the result */
combined:
  expr combines                     { Expr d = $1; for (UnaryOperator<Expr> c : $2) d = c.apply (d); $$ = d; }
;

combines:
  %empty                            { $$ = new ArrayList<> (); }
| combines combine                  { $1.add ($2); $$ = $1; }
;

combine:
  '&' operand moves                 { ArrayList<Expr> o = $2; ArrayList<UnaryOperator<Expr>> ms = $3;
                                      $$ = (UnaryOperator<Expr>) e -> { Expr d = ScheduleSemantics.combined (e, true, o); for (UnaryOperator<Expr> m : ms) d = m.apply (d); return d; }; }
| '\\' operand moves                { ArrayList<Expr> o = $2; ArrayList<UnaryOperator<Expr>> ms = $3;
                                      $$ = (UnaryOperator<Expr>) e -> { Expr d = ScheduleSemantics.combined (e, false, o); for (UnaryOperator<Expr> m : ms) d = m.apply (d); return d; }; }
;

moves:
  %empty                            { $$ = new ArrayList<> (); }
| moves move                        { $1.add ($2); $$ = $1; }
;

/* a date, a series or a set of dates */
operand:
  date                              { ArrayList<Expr> list = new ArrayList<> (); list.add ($1); $$ = list; }
| '{' operand_exprs '}'             { $$ = $2; }
;

operand_exprs:
  expr                              { ArrayList<Expr> list = new ArrayList<> (); list.add ($1); $$ = list; }
| operand_exprs ',' expr            { $1.add ($3); $$ = $1; }
;

shifts:
  shift                             { ArrayList<Shift> list = new ArrayList<> (); list.add ($1); $$ = list; }
| shifts ',' shift                  { $1.add ($3); $$ = $1; }
;

shift:
  WEEKDAY '+' period                { $$ = ScheduleSemantics.shift ($1, $3, 1); }
| WEEKDAY '-' period                { $$ = ScheduleSemantics.shift ($1, $3, -1); }
;

/* NEXT 3RD FRI: the third Friday; the scanner tells the ordinal from the number of 3 FRI JAN */
counted:
  anchor                            { $$ = $1; }
| ORDINAL anchor                    { $$ = ScheduleSemantics.times (ScheduleSemantics.ordinal ($1), $2); }
;

/* > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH: the n-th match in the period of the date, from its start or its end;
   counting from the end is a negative count (0 is an error anyway) */
count:
  ORDINAL                           { $$ = ScheduleSemantics.ordinal ($1); }
| ORDINAL LAST                      { $$ = -ScheduleSemantics.ordinal ($1); }
| LAST                              { $$ = -1; }
;

/* the targets of NEXT; an annual date starting with LAST only in braces, as LAST also starts the count */
select:
  chosen                            { $$ = $1; }
| '{' targets '}'                   { $$ = ScheduleSemantics.anyOf ($2); }
;

/* NEXT {MON, FRI}: the nearest of a set */
anchor:
  target                            { $$ = $1; }
| '{' targets '}'                   { $$ = ScheduleSemantics.anyOf ($2); }
;

targets:
  target                            { ArrayList<Anchor> list = new ArrayList<> (); list.add ($1); $$ = list; }
| targets ',' target                { $1.add ($3); $$ = $1; }
;

target:
  chosen                            { $$ = $1; }
| LAST WEEKDAY MONTH                { $$ = ScheduleSemantics.annual (ScheduleSemantics.lastWeekday ($2, $3)); }
;

chosen:
  WEEKDAY                           { $$ = ScheduleSemantics.weekday ($1); }
| BD                                { $$ = ScheduleSemantics.BUSINESS_DAY; }
| FD                                { $$ = ScheduleSemantics.FIXING_DAY; }
| IMM                               { $$ = ScheduleSemantics.IMM; }
| fixed_annual                      { $$ = ScheduleSemantics.annual ($1); }
| EVENT                             { $$ = ScheduleSemantics.eventAnchor ($1); }
;

date:
  single_date                       { $$ = $1; }
| series                            { $$ = $1; }
;

/* a date that is not a series */
single_date:
  BASE                              { $$ = ScheduleSemantics.BASE; }   /* T */
| TOM                               { $$ = ScheduleSemantics.plus (ScheduleSemantics.BASE, ScheduleSemantics.period (1, "B")); }
| SPOT                              { $$ = ScheduleSemantics.spot (ScheduleSemantics.BASE, null); }
| SPOT PAIR                         { $$ = ScheduleSemantics.spot (ScheduleSemantics.BASE, $2); }
| annual                            { $$ = ScheduleSemantics.inYear ($1); }
| EVENT                             { $$ = ScheduleSemantics.event ($1); }   /* @FOMC */
| NUM '-' NUM '-' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($1, $3, $5)); }   /* ISO */
| NUM '.' NUM '.' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($5, $3, $1)); }   /* German */
| NUM '/' NUM '/' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($5, $1, $3)); }   /* US */
;

/* dates that recur every year, evaluated in the year of the base date */
annual:
  fixed_annual                      { $$ = $1; }
| LAST WEEKDAY MONTH                { $$ = ScheduleSemantics.lastWeekday ($2, $3); }
;

fixed_annual:
  NUM MONTH                         { $$ = ScheduleSemantics.monthDay ($1, $2); }
| NUM WEEKDAY MONTH                 { $$ = ScheduleSemantics.nthWeekday ($1, $2, $3); }
| EASTER                            { $$ = ScheduleSemantics.EASTER; }
| SEASON MONTH zone                 { $$ = ScheduleSemantics.season ($1, $2, $3); }
| SOLARTERM NUM zone                { $$ = ScheduleSemantics.solarTerm ($2, $3); }
;

/* the time zone of an equinox, solstice or solar term: UTC unless an offset follows */
zone:
  %empty                            { $$ = 0; }
| ZONE                              { $$ = $1; }
;

period:
  NUM UNIT                          { $$ = ScheduleSemantics.period ($1, $2); }
;

%%

/** Hand-written scanner: numbers, words of capital letters, single-character tokens. */
class ScheduleLexer implements ScheduleParser.Lexer {

  private final String text;
  private int pos;
  private Object value;
  String error;

  public ScheduleLexer (String text)
  {
    this.text = text;
  }

  public Object getLVal ()
  {
    return value;
  }

  public int yylex ()
  {
    while (pos < text.length () && Character.isWhitespace (text.charAt (pos)))
      pos++;
    if (pos == text.length ())
      return EOF;

    char c = text.charAt (pos);
    if (c >= '0' && c <= '9')
      {
        int start = pos;
        while (pos < text.length () && text.charAt (pos) >= '0' && text.charAt (pos) <= '9')
          pos++;
        /* 1ST, 2ND, 3RD, 4TH: an ordinal, not a number */
        if (pos + 2 <= text.length () && text.substring (pos, pos + 2).matches ("ST|ND|RD|TH"))
          {
            pos += 2;
            value = text.substring (start, pos);
            return ORDINAL;
          }
        value = Integer.valueOf (text.substring (start, pos));
        return NUM;
      }
    if (c >= 'A' && c <= 'Z')
      {
        int start = pos;
        while (pos < text.length () && text.charAt (pos) >= 'A' && text.charAt (pos) <= 'Z')
          pos++;
        String word = text.substring (start, pos);
        value = word;
        if (word.equals ("IMM"))
          return IMM;
        if (word.equals ("SPOT"))
          return SPOT;
        if (word.equals ("T"))
          return BASE;
        if (word.equals ("TOM"))
          return TOM;
        if (word.equals ("NEXT"))
          return NEXT;
        if (word.equals ("PREV"))
          return PREV;
        if (word.equals ("BD"))
          return BD;
        if (word.equals ("FD"))
          return FD;
        if (word.equals ("FIX"))
          return FIX;
        if (word.equals ("LAST"))
          return LAST;
        if (word.equals ("IF"))
          return IF;
        if (word.equals ("IN"))
          return IN;
        if (word.matches ("WEEK|MONTH|QUARTER|HALFYEAR|YEAR"))   /* the periods of a selection */
          return PERIOD;
        /* Gregorian, then Hijri (Umm al-Qura), Hebrew and Chinese months */
        if (word.matches ("JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC"
                          + "|MUHARRAM|SAFAR|RABIALAWWAL|RABIALTHANI|JUMADAALULA|JUMADAALAKHIRA|RAJAB|SHABAN"
                          + "|RAMADAN|SHAWWAL|DHULQADAH|DHULHIJJAH"
                          + "|NISAN|IYAR|SIVAN|TAMMUZ|AV|ELUL|TISHRI|HESHVAN|KISLEV|TEVET|SHEVAT|ADAR|ADARII"
                          + "|ZHENGYUE|ERYUE|SANYUE|SIYUE|WUYUE|LIUYUE|QIYUE|BAYUE|JIUYUE|SHIYUE|DONGYUE|LAYUE"))
          return MONTH;
        if (word.matches ("MON|TUE|WED|THU|FRI|SAT|SUN"))
          return WEEKDAY;
        if (word.matches ("FOLLOWING|PRECEDING|MODFOLLOWING|BOW|EOW|BOM|EOM|BOQ|EOQ|BOH|EOH|BOY|EOY"))
          return ROLL;
        if (word.equals ("EASTER"))   /* keywords (the months above too) before a word can be a pair */
          return EASTER;
        if (word.equals ("EQUINOX") || word.equals ("SOLSTICE"))
          return SEASON;
        if (word.equals ("SOLARTERM"))
          return SOLARTERM;
        if (word.equals ("UTC") && pos + 1 < text.length () && "+-".indexOf (text.charAt (pos)) >= 0
            && Character.isDigit (text.charAt (pos + 1)))
          {   /* the time zone of an equinox, solstice or solar term: UTC+9 */
            start = pos++;
            while (pos < text.length () && Character.isDigit (text.charAt (pos)))
              pos++;
            value = ScheduleSemantics.zone ("UTC" + text.substring (start, pos));
            return ZONE;
          }
        if (word.length () == 6)
          return PAIR;   /* currency pair such as EURUSD */
        if (word.length () == 1 && "DWMQHYBF".contains (word))
          return UNIT;
        return YYUNDEF;
      }
    if (c == '@' && pos + 1 < text.length () && text.charAt (pos + 1) >= 'A' && text.charAt (pos + 1) <= 'Z')
      {   /* a named event such as @FOMC; the value is the name without the sigil */
        int start = ++pos;
        while (pos < text.length () && (text.charAt (pos) >= 'A' && text.charAt (pos) <= 'Z'
                                         || text.charAt (pos) >= '0' && text.charAt (pos) <= '9' || text.charAt (pos) == '_'))
          pos++;
        value = text.substring (start, pos);
        return EVENT;
      }
    pos++;
    if ("+-./,:[]>{}&\\".indexOf (c) >= 0)
      return c;
    return YYUNDEF;   /* unknown character: a syntax error for the parser */
  }

  public void yyerror (String message)
  {
    error = message;
  }
}
