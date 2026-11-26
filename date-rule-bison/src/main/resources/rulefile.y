/*
 * Rule files: the schedule language of schedule.y inside declarations with scope, validity, kind and provenance, the
 * same language as date-rule-antlr-java's RuleFile.g4 and date-rule-lark's rules.lark (shared cases:
 * examples/rules.txt). LALR(1), lists left recursive, no conflicts (the pom passes -Werror=conflicts-sr/rr). The
 * actions build the declarations with RuleCatalog and ScheduleSemantics; shifts are RuleCatalog.Move, functions of a
 * date. One start symbol: rule_file reads declarations or one schedule (the first token tells them apart). The parser
 * class is RuleFiles itself, the entry point (parseRules, schedule), as the generated sources exist only in the bison
 * profile. Rules only project: announced dates are data given to the evaluator.
 */
%language "Java"
%define api.package {io.github.hjuergens.bison.rules}
%define api.parser.class {RuleFiles}
%define api.parser.public

%code imports {
  import java.time.LocalDate;
  import java.time.temporal.TemporalAmount;
  import java.util.ArrayList;
  import java.util.LinkedHashSet;
  import java.util.List;
  import java.util.Map;
  import java.util.Set;
  import java.util.function.UnaryOperator;
  import io.github.hjuergens.bison.RuleCatalog;
  import io.github.hjuergens.bison.RuleCatalog.CalendarSpec;
  import io.github.hjuergens.bison.RuleCatalog.Citation;
  import io.github.hjuergens.bison.RuleCatalog.Condition;
  import io.github.hjuergens.bison.RuleCatalog.IfClause;
  import io.github.hjuergens.bison.RuleCatalog.Move;
  import io.github.hjuergens.bison.ScheduleSemantics;
  import io.github.hjuergens.bison.ScheduleSemantics.Anchor;
  import io.github.hjuergens.bison.ScheduleSemantics.Annual;
  import io.github.hjuergens.bison.ScheduleSemantics.Expr;
  import io.github.hjuergens.bison.ScheduleSemantics.Rule;
}

%code {
  /** the result of a successful parse(): the declarations of a rule file (a List) or a schedule (a Rule) */
  Object result;

  /** the @references of the rule being read; reset when its declaration is reduced */
  private Set<String> references = new LinkedHashSet<> ();
  /** whether the rule being read has a Hijri method other than UMMALQURA */
  private boolean estimated;

  private String event (String name)
  {
    references.add (name);
    return name;
  }

  /** the combines of an item applied to its expression, then IF and the pick (0: none, negative: from the end) */
  static Expr item (Expr expr, List<UnaryOperator<Expr>> combines, IfClause clause, int pick)
  {
    Expr d = expr;
    for (UnaryOperator<Expr> c : combines)
      d = c.apply (d);
    d = RuleCatalog.ruleItem (d, clause);
    return pick == 0 ? d : ScheduleSemantics.pick (Math.abs (pick), pick < 0, d);
  }

  @SuppressWarnings ("unchecked")
  static <T> List<T> cast (List<Object> list)
  {
    return (List<T>) (List<?>) list;
  }

  /**
   * the rule catalog of a rule file
   *
   * @throws IllegalArgumentException on a syntax error, overlapping versions, an unknown or a cyclic reference
   * @throws java.time.DateTimeException on an invalid date
   */
  public static RuleCatalog parseRules (String text)
  {
    return parseRules (text, null);
  }

  /** the same with announced dates: key -> the dates an authority published */
  public static RuleCatalog parseRules (String text, Map<String, List<LocalDate>> announcements)
  {
    if (!(parse (text) instanceof List<?> declarations))
      throw new IllegalArgumentException ("a schedule, not a rule file");
    return new RuleCatalog (declarations, announcements, RuleFiles::schedule);
  }

  /** one expression of the rule language */
  public static Rule schedule (String text)
  {
    if (!(parse (text) instanceof Rule rule))
      throw new IllegalArgumentException ("not a schedule: " + text);
    return rule;
  }

  private static Object parse (String text)
  {
    RuleFileLexer lexer = new RuleFileLexer (text);
    RuleFiles parser = new RuleFiles (lexer);
    try
      {
        if (!parser.parse ())
          throw new IllegalArgumentException (lexer.error);
      }
    catch (java.io.IOException e)   /* declared by Lexer.yylex, the string scanner never throws it */
      {
        throw new java.io.UncheckedIOException (e);
      }
    return parser.result;
  }
}

/* the words of declarations */
%token L_RULE "rule" L_FOR "for" L_IN "in" L_EXCEPT "except" L_VALID "valid" L_FROM "from" L_TO "to" L_WITH "with"
%token L_KIND "kind" L_SESSION "session" L_TZ "tz" L_PRECISION "precision" L_NOTE "note" L_CITES "cites" L_ART "art"
%token L_URL "url" L_LABEL "label" L_WEEKEND "weekend" L_CALENDAR "calendar" L_IGNORE "ignore" L_AT "at"
%token L_UNLESS "unless"
%token <String> KIND_WORD "kind of day" PRECISION_WORD "day or month" LABEL_WORD "kind of source"
/* the words of the schedule language */
%token ONE "ONE" OF "OF" AS "AS" BASE "T" TOM "TOM" SPOT "SPOT" FIX "FIX" IMM "IMM" NEXT "NEXT" PREV "PREV"
%token NEAREST "NEAREST" CUTOFF "CUTOFF" IN "IN" POSTPONE "POSTPONE" MAX "MAX" CUMULATIVE "CUMULATIVE" ENDEND "ENDEND" ON "ON"
%token LAST "LAST" BD "BD" FD "FD" HOLIDAY "HOLIDAY" NONHOLIDAY "NONHOLIDAY" HOLIDAYS "HOLIDAYS" EASTER "EASTER"
%token ORTHODOX "ORTHODOX" SOLARTERM "SOLARTERM" HINDU "HINDU" BY "BY" FOLLOW "FOLLOW" IF "IF" OR "OR" AND "AND"
%token NOT "NOT" DAY "DAY" IS "IS" BETWEEN "BETWEEN" EVERY "EVERY" FROM "FROM" DOTDOT ".."
%token <Integer> NUM "number"
%token <String> ORDINAL "ordinal" UNIT "unit" MONTH "month" WEEKDAY "weekday" ROLL "roll convention"
%token <String> PERIOD "period" ZONE "time zone" PAIR "currency pair" CCY "currency" EVENT "event" SCOPE "scope"
%token <String> KEY "key" STRING "string" SEASON "EQUINOX or SOLSTICE" METHOD "Hijri method"

%type <Object> declaration body alternative attribute observe
%type <ArrayList<Object>> declarations alternatives attributes attributes_opt observes
%type <String> name time closes_opt tz_opt article_opt url_opt method_opt zone_opt pair_opt month_opt
%type <ArrayList<String>> scopes except_opt
%type <ArrayList<LocalDate>> validity
%type <LocalDate> iso_date from_opt to_opt
%type <ArrayList<Citation>> citations citations_opt
%type <Citation> citation
%type <CalendarSpec> calendar cal_term on on_opt
%type <LinkedHashSet<String>> ccys weekday_set weekday_list weekday_commas
%type <ArrayList<Expr>> exprs operand operand_exprs
%type <Expr> item expr date series endpoint single_date subject
%type <Integer> pick count
%type <ArrayList<Move>> moves
%type <Move> move
%type <UnaryOperator<Expr>> combine
%type <ArrayList<UnaryOperator<Expr>>> combines
%type <IfClause> if_opt
%type <Condition> condition cond_and cond_not cond_atom
%type <Anchor> anchor target select chosen
%type <ArrayList<Anchor>> targets
%type <Annual> annual fixed_annual
%type <ArrayList<Annual>> annuals
%type <TemporalAmount> period
%type <Boolean> endend_opt cumulative_opt start_out end_out

%%

/* a rule file, or one schedule (for schedule()) */
rule_file:
  declarations                      { result = $1; }
| exprs                             { result = RuleCatalog.exprs ($1); }
;

declarations:
  %empty                            { $$ = new ArrayList<Object> (); }
| declarations declaration          { $1.add ($2); $$ = $1; }
;

/* ================= declarations ================= */

declaration:
  "rule" name "for" name "in" scopes except_opt validity '=' body attributes_opt citations_opt ';'
    {
      $$ = RuleCatalog.rule ($2, $4, $6, $7, $8.get (0), $8.get (1), $10, cast ($11), $12, references, estimated);
      references = new LinkedHashSet<> ();
      estimated = false;
    }
| "weekend" SCOPE '=' weekday_set from_opt ';'
                                    { $$ = RuleCatalog.weekend ($2, $4, $5); }
| "calendar" KEY '=' calendar ';'   { $$ = RuleCatalog.calendarDeclaration ($2, $4); }
;

/* exactly one of the alternatives will happen: ONE OF {item AS name, ...} */
body:
  "ONE" "OF" '{' alternatives '}'   { $$ = RuleCatalog.oneOf (cast ($4)); }
| exprs                             { $$ = RuleCatalog.exprs ($1); }
;

alternatives:
  alternative                       { ArrayList<Object> list = new ArrayList<> (); list.add ($1); $$ = list; }
| alternatives ',' alternative      { $1.add ($3); $$ = $1; }
;

alternative:
  item "AS" name                    { $$ = Map.entry ($3, RuleCatalog.exprs (List.of ($1))); }
;

/* a key or an event class; the words of kinds, precisions and labels are tokens, so they are names here as well */
name:
  KEY                               { $$ = $1; }
| KIND_WORD                         { $$ = $1; }
| PRECISION_WORD                    { $$ = $1; }
| LABEL_WORD                        { $$ = $1; }
;

scopes:
  SCOPE                             { ArrayList<String> list = new ArrayList<> (); list.add ($1); $$ = list; }
| scopes ',' SCOPE                  { $1.add ($3); $$ = $1; }
;

except_opt:
  %empty                            { $$ = new ArrayList<String> (); }
| "except" scopes                   { $$ = $2; }
;

validity:
  %empty                            { ArrayList<LocalDate> v = new ArrayList<> (); v.add (null); v.add (null); $$ = v; }
| "valid" from_opt to_opt           { ArrayList<LocalDate> v = new ArrayList<> (); v.add ($2); v.add ($3); $$ = v; }
;

from_opt:
  %empty                            { $$ = null; }
| "from" iso_date                   { $$ = $2; }
;

to_opt:
  %empty                            { $$ = null; }
| "to" iso_date                     { $$ = $2; }
;

iso_date:
  NUM '-' NUM '-' NUM               { $$ = LocalDate.of ($1, $3, $5); }
;

attributes_opt:
  %empty                            { $$ = new ArrayList<Object> (); }
| "with" attributes                 { $$ = $2; }
;

attributes:
  attribute                         { ArrayList<Object> list = new ArrayList<> (); list.add ($1); $$ = list; }
| attributes ',' attribute          { $1.add ($3); $$ = $1; }
;

attribute:
  "kind" '=' KIND_WORD              { $$ = Map.entry ("kind", (Object) $3); }
| "session" time closes_opt tz_opt  { $$ = RuleCatalog.session ($2, $3, $4); }
| "precision" '=' PRECISION_WORD    { $$ = Map.entry ("precision", (Object) $3); }
| "note" STRING                     { $$ = Map.entry ("note", (Object) $2); }
;

time:
  NUM ':' NUM                       { $$ = RuleCatalog.time ($1, $3); }
;

closes_opt:
  %empty                            { $$ = null; }
| '-' time                          { $$ = $2; }
;

tz_opt:
  %empty                            { $$ = null; }
| "tz" STRING                       { $$ = $2; }
;

citations_opt:
  %empty                            { $$ = new ArrayList<Citation> (); }
| "cites" citations                 { $$ = $2; }
;

citations:
  citation                          { ArrayList<Citation> list = new ArrayList<> (); list.add ($1); $$ = list; }
| citations ',' citation            { $1.add ($3); $$ = $1; }
;

citation:
  STRING article_opt url_opt "label" LABEL_WORD { $$ = new Citation ($1, $2, $3, $5); }
;

article_opt:
  %empty                            { $$ = null; }
| "art" STRING                      { $$ = $2; }
;

url_opt:
  %empty                            { $$ = null; }
| "url" STRING                      { $$ = $2; }
;

/* ================= calendars ================= */

/* a business day in every term of "&" */
calendar:
  cal_term                          { $$ = $1; }
| calendar '&' cal_term
    {
      List<CalendarSpec> terms = new ArrayList<> ($1 instanceof RuleCatalog.Combined combined ? combined.terms () : List.of ($1));
      terms.add ($3);
      $$ = RuleCatalog.calendar (terms);
    }
;

/* USD on T+1 does not count, unless for MXN */
cal_term:
  SCOPE                             { $$ = new RuleCatalog.Scope ($1, 0, Set.of ()); }
| SCOPE "ignore" "at" "T" '+' NUM   { $$ = new RuleCatalog.Scope ($1, $6, Set.of ()); }
| SCOPE "ignore" "at" "T" '+' NUM "unless" '{' ccys '}' { $$ = new RuleCatalog.Scope ($1, $6, $9); }
| KEY                               { $$ = new RuleCatalog.Named ($1); }
| '(' calendar ')'                  { $$ = $2; }
;

ccys:
  CCY                               { LinkedHashSet<String> set = new LinkedHashSet<> (); set.add ($1); $$ = set; }
| ccys ',' CCY                      { $1.add ($3); $$ = $1; }
;

/* in an expression one term only, as "&" there is include: ON country:BR, ON usd_eur, ON (a & b) */
on:
  "ON" SCOPE                        { $$ = new RuleCatalog.Scope ($2, 0, Set.of ()); }
| "ON" KEY                          { $$ = new RuleCatalog.Named ($2); }
| "ON" '(' calendar ')'             { $$ = $3; }
;

on_opt:
  %empty                            { $$ = null; }
| on                                { $$ = $1; }
;

/* ================= expressions ================= */

exprs:
  item                              { ArrayList<Expr> list = new ArrayList<> (); list.add ($1); $$ = list; }
| exprs ',' item                    { $1.add ($3); $$ = $1; }
;

/* the condition applies to every date of the item; the shifts after it to the dates it keeps. A pick starts with a
   series or an event, where several dates come from */
item:
  expr combines if_opt              { $$ = item ($1, $2, $3, 0); }
| pick series moves combines if_opt { $$ = item (RuleCatalog.shifted ($2, $3), $4, $5, $1); }
| pick EVENT moves combines if_opt  { $$ = item (RuleCatalog.shifted (ScheduleSemantics.event (event ($2)), $3), $4, $5, $1); }
;

/* 5TH ...: the 5th date after T of the item; 2ND LAST from the last one, a negative count */
pick:
  ORDINAL                           { $$ = ScheduleSemantics.ordinal ($1); }
| ORDINAL "LAST"                    { $$ = -ScheduleSemantics.ordinal ($1); }
;

if_opt:
  %empty                            { $$ = null; }
| "IF" condition moves              { $$ = new IfClause ($2, $3); }
;

/* A & B includes the dates of B, A \ B excludes them; the shifts after it apply to the result */
combines:
  %empty                            { $$ = new ArrayList<UnaryOperator<Expr>> (); }
| combines combine                  { $1.add ($2); $$ = $1; }
;

combine:
  '&' operand moves                 { ArrayList<Expr> o = $2; ArrayList<Move> ms = $3; $$ = (UnaryOperator<Expr>) e -> RuleCatalog.shifted (ScheduleSemantics.combined (e, true, o), ms); }
| '\\' operand moves                { ArrayList<Expr> o = $2; ArrayList<Move> ms = $3; $$ = (UnaryOperator<Expr>) e -> RuleCatalog.shifted (ScheduleSemantics.combined (e, false, o), ms); }
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

/* shifts are applied from left to right */
expr:
  date moves                        { $$ = RuleCatalog.shifted ($1, $2); }
;

moves:
  %empty                            { $$ = new ArrayList<Move> (); }
| moves move                        { $1.add ($2); $$ = $1; }
;

/* [a : step : b], every day without a step; "]" at the start or "[" at the end leaves that endpoint out */
series:
  start_out endpoint ':' period ':' endpoint end_out   { $$ = ScheduleSemantics.series ($2, $4, $6, $1, $7); }
| start_out endpoint ':' period ':' end_out            { $$ = ScheduleSemantics.series ($2, $4, null, $1, $6); }   /* open */
| start_out endpoint ':' endpoint end_out              { $$ = ScheduleSemantics.series ($2, null, $4, $1, $5); }
;

/* an endpoint is one date, so it can't be a series */
endpoint:
  single_date moves                 { $$ = RuleCatalog.shifted ($1, $2); }
;

start_out:
  '['                               { $$ = false; }
| ']'                               { $$ = true; }
;

end_out:
  ']'                               { $$ = false; }
| '['                               { $$ = true; }
;

move:
  '+' period endend_opt on_opt      { $$ = RuleCatalog.plus ($2, 1, $3, $4); }
| '-' period endend_opt on_opt      { $$ = RuleCatalog.plus ($2, -1, $3, $4); }
| '>' "IMM"                         { $$ = RuleCatalog.nextImm (); }
| '>' "SPOT" pair_opt on_opt        { $$ = RuleCatalog.spot ($3, $4); }
| '>' "FIX" pair_opt                { $$ = RuleCatalog.fix ($3); }
| '>' ROLL on_opt                   { $$ = RuleCatalog.rollShift ($2, $3); }
| '>' "NEXT" anchor                 { $$ = RuleCatalog.next (1, $3); }
| '>' "NEXT" ORDINAL anchor         { $$ = RuleCatalog.next (ScheduleSemantics.ordinal ($3), $4); }
| '>' "PREV" anchor                 { $$ = RuleCatalog.previous (1, $3); }
| '>' "PREV" ORDINAL anchor         { $$ = RuleCatalog.previous (ScheduleSemantics.ordinal ($3), $4); }
| '>' "NEAREST" anchor              { $$ = RuleCatalog.nearestShift ($3); }   /* ties go forward */
| '>' "NEAREST" on                  { $$ = RuleCatalog.rollShift ("NEAREST", $3); }   /* the FpML convention */
  /* the trade date of a base with a time of day: > CUTOFF 17:00 "America/New_York", > CUTOFF 10:00 UTC+0 */
| '>' "CUTOFF" time ZONE            { $$ = RuleCatalog.cutoff ($3, $4); }
| '>' "CUTOFF" time STRING          { $$ = RuleCatalog.cutoff ($3, $4); }
  /* > 3RD WED IN MONTH, > 2ND LAST BD IN MONTH */
| '>' count select "IN" PERIOD      { $$ = RuleCatalog.select (Math.abs ($2), $2 < 0, $3, $5); }
  /* > {1 FEB, 1 MAY}: the dates of the set in the year of the date */
| '>' '{' annuals '}'               { $$ = RuleCatalog.datesInYear ($3); }
| '>' "POSTPONE" ROLL on_opt "MAX" period cumulative_opt { $$ = new RuleCatalog.Postpone ($3, $4, $6, $7); }
  /* {SAT - 1D, TUE WED > PREV MON}: the first entry for the weekday of the date moves it */
| '{' observes '}'                  { $$ = RuleCatalog.observance (cast ($2)); }
;

endend_opt:
  %empty                            { $$ = false; }
| "ENDEND"                          { $$ = true; }
;

cumulative_opt:
  %empty                            { $$ = false; }
| "CUMULATIVE"                      { $$ = true; }
;

pair_opt:
  %empty                            { $$ = null; }
| PAIR                              { $$ = $1; }
;

observes:
  observe                           { ArrayList<Object> list = new ArrayList<> (); list.add ($1); $$ = list; }
| observes ',' observe              { $1.add ($3); $$ = $1; }
;

observe:
  weekday_list '+' period           { $$ = RuleCatalog.observePlus ($1, $3, 1); }
| weekday_list '-' period           { $$ = RuleCatalog.observePlus ($1, $3, -1); }
| weekday_list '>' "NEXT" anchor    { $$ = RuleCatalog.observeAnchor ($1, $4, 1); }
| weekday_list '>' "PREV" anchor    { $$ = RuleCatalog.observeAnchor ($1, $4, -1); }
| weekday_list '>' "NEAREST" anchor { $$ = RuleCatalog.observeNearest ($1, $4); }
| weekday_list '>' ROLL on_opt      { $$ = RuleCatalog.observeRoll ($1, $3, $4); }
;

annuals:
  annual                            { ArrayList<Annual> list = new ArrayList<> (); list.add ($1); $$ = list; }
| annuals ',' annual                { $1.add ($3); $$ = $1; }
;

/* counting from the end is a negative count */
count:
  ORDINAL                           { $$ = ScheduleSemantics.ordinal ($1); }
| ORDINAL "LAST"                    { $$ = -ScheduleSemantics.ordinal ($1); }
| "LAST"                            { $$ = -1; }
;

/* the targets of a selection; an annual date starting with LAST only in braces, as LAST also starts the count */
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
| "LAST" WEEKDAY MONTH              { $$ = ScheduleSemantics.annual (ScheduleSemantics.lastWeekday ($2, $3)); }
;

chosen:
  WEEKDAY                           { $$ = ScheduleSemantics.weekday ($1); }
| "BD" on_opt                       { $$ = RuleCatalog.businessDay ($2); }
| "FD"                              { $$ = ScheduleSemantics.FIXING_DAY; }
| "IMM"                             { $$ = ScheduleSemantics.IMM; }
| fixed_annual                      { $$ = ScheduleSemantics.annual ($1); }
| EVENT                             { $$ = ScheduleSemantics.eventAnchor (event ($1)); }
| "HOLIDAY" on_opt                  { $$ = RuleCatalog.holiday ($2); }
| "NONHOLIDAY" on_opt               { $$ = RuleCatalog.nonholiday ($2); }
;

date:
  single_date                       { $$ = $1; }
| series                            { $$ = $1; }
;

/* a date that is not a series */
single_date:
  "T"                               { $$ = ScheduleSemantics.BASE; }
| "TOM"                             { $$ = ScheduleSemantics.plus (ScheduleSemantics.BASE, ScheduleSemantics.period (1, "B")); }
| "SPOT" pair_opt on_opt            { $$ = RuleCatalog.spotDate ($2, $3); }
| annual                            { $$ = ScheduleSemantics.inYear ($1); }
| EVENT                             { $$ = ScheduleSemantics.event (event ($1)); }
| NUM '-' NUM '-' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($1, $3, $5)); }   /* ISO */
| NUM '.' NUM '.' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($5, $3, $1)); }   /* German */
| NUM '/' NUM '/' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($5, $1, $3)); }   /* US */
;

/* dates that recur every year */
annual:
  fixed_annual                      { $$ = $1; }
| "LAST" WEEKDAY MONTH              { $$ = ScheduleSemantics.lastWeekday ($2, $3); }
;

fixed_annual:
  NUM MONTH method_opt              { $$ = RuleCatalog.monthDay ($1, $2, $3); }
| NUM WEEKDAY MONTH                 { $$ = ScheduleSemantics.nthWeekday ($1, $2, $3); }
| "EASTER"                          { $$ = ScheduleSemantics.EASTER; }
| "ORTHODOX" "EASTER"               { $$ = RuleCatalog.ORTHODOX_EASTER; }
| SEASON MONTH zone_opt             { $$ = RuleCatalog.season ($1, $2, $3); }
| "SOLARTERM" NUM zone_opt          { $$ = RuleCatalog.solarTerm ($2, $3); }
| "HINDU" KEY                       { $$ = RuleCatalog.hindu ($2); }
;

/* 1 SHAWWAL BY SIGHTING: a method other than UMMALQURA marks the rule's dates as projections */
method_opt:
  %empty                            { $$ = null; }
| "BY" METHOD                       { $$ = $2; if (!$2.equals ("UMMALQURA")) estimated = true; }
| "BY" "FOLLOW" SCOPE               { $$ = "FOLLOW"; estimated = true; }
;

/* UTC unless a zone follows: UTC+9 or an IANA time zone such as "Asia/Tokyo" */
zone_opt:
  %empty                            { $$ = null; }
| ZONE                              { $$ = $1; }
| STRING                            { $$ = $1; }
;

period:
  NUM UNIT                          { $$ = ScheduleSemantics.period ($1, $2); }
;

/* ================= conditions ================= */

condition:
  cond_and                          { $$ = $1; }
| condition "OR" cond_and           { $$ = RuleCatalog.anyCondition (List.of ($1, $3)); }
;

cond_and:
  cond_not                          { $$ = $1; }
| cond_and "AND" cond_not           { $$ = RuleCatalog.allConditions (List.of ($1, $3)); }
;

cond_not:
  cond_atom                         { $$ = $1; }
| "NOT" cond_atom                   { $$ = RuleCatalog.notCondition ($2); }
;

cond_atom:
  weekday_set                       { $$ = RuleCatalog.onWeekday ($1); }   /* the weekday of the date itself */
| "DAY" NUM ".." NUM month_opt      { $$ = RuleCatalog.onDay ($2, $4, $5); }   /* DAY 8..14 APR */
| subject "IS" weekday_set          { $$ = RuleCatalog.isWeekday ($1, $3); }
| subject "IS" "HOLIDAY" on_opt     { $$ = RuleCatalog.dateIsHoliday ($1, $4); }
| "HOLIDAY" on_opt                  { $$ = RuleCatalog.holidayCondition ($2); }
| "BETWEEN" "HOLIDAYS" on_opt       { $$ = RuleCatalog.betweenHolidays ($3); }   /* JP art. 3(3) */
| "EVERY" period "FROM" NUM         { $$ = RuleCatalog.every ($2, $4); }   /* EVERY 4Y FROM 1965 */
| '(' condition ')'                 { $$ = $2; }
;

month_opt:
  %empty                            { $$ = null; }
| MONTH                             { $$ = $1; }
;

/* 18 SEP, EASTER + 1D */
subject:
  single_date                       { $$ = $1; }
| subject '+' period                { $$ = RuleCatalog.shifted ($1, List.of (RuleCatalog.plus ($3, 1, false, null))); }
| subject '-' period                { $$ = RuleCatalog.shifted ($1, List.of (RuleCatalog.plus ($3, -1, false, null))); }
;

weekday_set:
  weekday_list                      { $$ = $1; }
| '{' weekday_commas '}'            { $$ = $2; }
;

weekday_list:
  WEEKDAY                           { LinkedHashSet<String> set = new LinkedHashSet<> (); set.add ($1); $$ = set; }
| weekday_list WEEKDAY              { $1.add ($2); $$ = $1; }
;

weekday_commas:
  WEEKDAY                           { LinkedHashSet<String> set = new LinkedHashSet<> (); set.add ($1); $$ = set; }
| weekday_commas ',' WEEKDAY        { $1.add ($3); $$ = $1; }
;

%%

/**
 * Hand-written scanner: numbers, words of capital letters (keywords, months, pairs, currencies), lowercase words
 * (keywords, keys, scopes), strings, comments and single-character tokens.
 */
class RuleFileLexer implements RuleFiles.Lexer {

  private static final Map<String, Integer> LOWER = Map.ofEntries (
      Map.entry ("rule", L_RULE), Map.entry ("for", L_FOR), Map.entry ("in", L_IN), Map.entry ("except", L_EXCEPT),
      Map.entry ("valid", L_VALID), Map.entry ("from", L_FROM), Map.entry ("to", L_TO), Map.entry ("with", L_WITH),
      Map.entry ("kind", L_KIND), Map.entry ("session", L_SESSION), Map.entry ("tz", L_TZ),
      Map.entry ("precision", L_PRECISION), Map.entry ("note", L_NOTE), Map.entry ("cites", L_CITES),
      Map.entry ("art", L_ART), Map.entry ("url", L_URL), Map.entry ("label", L_LABEL), Map.entry ("weekend", L_WEEKEND),
      Map.entry ("calendar", L_CALENDAR), Map.entry ("ignore", L_IGNORE), Map.entry ("at", L_AT),
      Map.entry ("unless", L_UNLESS));

  private static final Map<String, Integer> UPPER = Map.ofEntries (
      Map.entry ("ONE", ONE), Map.entry ("OF", OF), Map.entry ("AS", AS), Map.entry ("T", BASE), Map.entry ("TOM", TOM),
      Map.entry ("SPOT", SPOT), Map.entry ("FIX", FIX), Map.entry ("IMM", IMM), Map.entry ("NEXT", NEXT),
      Map.entry ("PREV", PREV), Map.entry ("NEAREST", NEAREST), Map.entry ("CUTOFF", CUTOFF),
      Map.entry ("IN", IN), Map.entry ("POSTPONE", POSTPONE),
      Map.entry ("MAX", MAX), Map.entry ("CUMULATIVE", CUMULATIVE), Map.entry ("ENDEND", ENDEND), Map.entry ("ON", ON),
      Map.entry ("LAST", LAST), Map.entry ("BD", BD), Map.entry ("FD", FD), Map.entry ("HOLIDAY", HOLIDAY),
      Map.entry ("NONHOLIDAY", NONHOLIDAY), Map.entry ("HOLIDAYS", HOLIDAYS), Map.entry ("EASTER", EASTER),
      Map.entry ("ORTHODOX", ORTHODOX), Map.entry ("SOLARTERM", SOLARTERM), Map.entry ("HINDU", HINDU),
      Map.entry ("BY", BY), Map.entry ("FOLLOW", FOLLOW), Map.entry ("IF", IF), Map.entry ("OR", OR),
      Map.entry ("AND", AND), Map.entry ("NOT", NOT), Map.entry ("DAY", DAY), Map.entry ("IS", IS),
      Map.entry ("BETWEEN", BETWEEN), Map.entry ("EVERY", EVERY), Map.entry ("FROM", FROM));

  private final String text;
  private int pos;
  private Object value;
  String error;

  RuleFileLexer (String text)
  {
    this.text = text;
  }

  public Object getLVal ()
  {
    return value;
  }

  private boolean at (int i, String chars)
  {
    return i < text.length () && chars.indexOf (text.charAt (i)) >= 0;
  }

  private static final String DIGITS = "0123456789";
  private static final String UPPERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String LOWERS = "abcdefghijklmnopqrstuvwxyz";
  private static final String NAME = UPPERS + LOWERS + DIGITS + "_";

  public int yylex ()
  {
    while (true)
      {
        while (pos < text.length () && Character.isWhitespace (text.charAt (pos)))
          pos++;
        if (pos < text.length () && text.charAt (pos) == '#')   /* a comment up to the end of the line */
          {
            while (pos < text.length () && text.charAt (pos) != '\n')
              pos++;
            continue;
          }
        break;
      }
    if (pos == text.length ())
      return EOF;

    char c = text.charAt (pos);
    int start = pos;
    if (at (pos, DIGITS))
      {
        while (at (pos, DIGITS))
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
    if (at (pos, UPPERS))
      {
        while (at (pos, UPPERS))
          pos++;
        String word = text.substring (start, pos);
        value = word;
        if (UPPER.containsKey (word))
          return UPPER.get (word);
        if (word.matches ("WEEK|MONTH|QUARTER|HALFYEAR|YEAR"))   /* the periods of a selection */
          return PERIOD;
        if (word.matches ("JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC"
                          + "|MUHARRAM|SAFAR|RABIALAWWAL|RABIALTHANI|JUMADAALULA|JUMADAALAKHIRA|RAJAB|SHABAN"
                          + "|RAMADAN|SHAWWAL|DHULQADAH|DHULHIJJAH"
                          + "|NISAN|IYAR|SIVAN|TAMMUZ|AV|ELUL|TISHRI|HESHVAN|KISLEV|TEVET|SHEVAT|ADAR|ADARII"
                          + "|ZHENGYUE|ERYUE|SANYUE|SIYUE|WUYUE|LIUYUE|QIYUE|BAYUE|JIUYUE|SHIYUE|DONGYUE|LAYUE"))
          return MONTH;
        if (word.matches ("MON|TUE|WED|THU|FRI|SAT|SUN"))
          return WEEKDAY;
        if (word.matches ("FOLLOWING|PRECEDING|MODFOLLOWING|MODPRECEDING|BOW|EOW|BOM|EOM|BOQ|EOQ|BOH|EOH|BOY|EOY"))
          return ROLL;
        if (word.equals ("EQUINOX") || word.equals ("SOLSTICE"))
          return SEASON;
        if (word.matches ("UMMALQURA|SIGHTING|DIYANET"))
          return METHOD;
        if (word.equals ("UTC") && at (pos, "+-") && at (pos + 1, DIGITS))
          {   /* UTC+9 */
            pos++;
            while (at (pos, DIGITS))
              pos++;
            value = text.substring (start, pos);
            return ZONE;
          }
        if (word.length () == 6)
          return PAIR;   /* currency pair such as EURUSD */
        if (word.length () == 3)
          return CCY;    /* a currency, only inside "unless {...}" */
        if (word.length () == 1 && "DWMQHYBF".contains (word))
          return UNIT;
        return YYUNDEF;
      }
    if (at (pos, LOWERS))
      {
        while (at (pos, LOWERS + DIGITS + "_"))
          pos++;
        if (at (pos, ":") && at (pos + 1, UPPERS + DIGITS) && text.substring (start, pos).matches ("[a-z_]+"))
          {   /* a scope: country:JP, region:AU-NSW */
            pos++;
            while (at (pos, UPPERS + DIGITS + "_") || at (pos, "-") && at (pos + 1, UPPERS + DIGITS))
              pos++;
            value = text.substring (start, pos);
            return SCOPE;
          }
        while (at (pos, ".") && at (pos + 1, LOWERS))   /* fed.fomc */
          {
            pos++;
            while (at (pos, LOWERS + DIGITS + "_"))
              pos++;
          }
        String word = text.substring (start, pos);
        value = word;
        if (LOWER.containsKey (word))
          return LOWER.get (word);
        if (word.matches ("closed|non_working|short_day|working_day|paid_no_rest|tourist|event|special_session|changed_hours"))
          return KIND_WORD;
        if (word.matches ("day|month"))
          return PRECISION_WORD;
        if (word.matches ("official|benchmark|practice|secondary"))
          return LABEL_WORD;
        return KEY;
      }
    if (c == '@' && at (pos + 1, UPPERS + LOWERS))
      {   /* a reference such as @FOMC or @za.freedom_day; the value is the name without the sigil */
        pos++;
        while (at (pos, NAME) || at (pos, ".") && at (pos + 1, NAME))
          pos++;
        value = text.substring (start + 1, pos);
        return EVENT;
      }
    if (c == '"')
      {   /* the value is the text without the quotes */
        int end = text.indexOf ('"', pos + 1);
        if (end < 0)
          {
            pos = text.length ();
            return YYUNDEF;
          }
        value = text.substring (pos + 1, end);
        pos = end + 1;
        return STRING;
      }
    if (text.startsWith ("..", pos))
      {
        pos += 2;
        return DOTDOT;
      }
    pos++;
    if ("+-./,:;=()[]>{}&\\".indexOf (c) >= 0)
      return c;
    return YYUNDEF;   /* unknown character: a syntax error for the parser */
  }

  public void yyerror (String message)
  {
    error = message;
  }
}
