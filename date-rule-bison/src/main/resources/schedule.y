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
  import io.github.hjuergens.bison.ScheduleSemantics.Expr;
  import io.github.hjuergens.bison.ScheduleSemantics.Rule;
}

%code {
  /** the rule of the schedule, set by a successful parse() */
  public Rule rule;
}

%token <Integer> NUM "number"
%token <String> UNIT "unit"
%token IMM "IMM"
%token SPOT "SPOT"
%token BASE "T"
%token <String> PAIR "currency pair"
%type <Expr> expr date
%type <TemporalAmount> period
%type <ArrayList<Expr>> exprs

%%

schedule:
  '(' expr ':' period ':' expr ')'  { rule = ScheduleSemantics.range ($2, $4, $6); }
| exprs                             { rule = ScheduleSemantics.list ($1); }
;

exprs:
  expr                              { ArrayList<Expr> list = new ArrayList<> (); list.add ($1); $$ = list; }
| exprs ',' expr                    { $1.add ($3); $$ = $1; }   /* $$ is untyped in Java, $1 is not */
;

/* periods are applied from left to right */
expr:
  date                              { $$ = $1; }
| expr '+' period                   { $$ = ScheduleSemantics.plus ($1, $3); }
| expr '-' period                   { $$ = ScheduleSemantics.minus ($1, $3); }
| expr '>' IMM                      { $$ = ScheduleSemantics.nextImm ($1); }
| expr '>' SPOT                     { $$ = ScheduleSemantics.spot ($1, null); }
| expr '>' SPOT PAIR                { $$ = ScheduleSemantics.spot ($1, $4); }
;

date:
  BASE                              { $$ = ScheduleSemantics.BASE; }   /* T */
| NUM '-' NUM '-' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($1, $3, $5)); }   /* ISO */
| NUM '.' NUM '.' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($5, $3, $1)); }   /* German */
| NUM '/' NUM '/' NUM               { $$ = ScheduleSemantics.literal (LocalDate.of ($5, $1, $3)); }   /* US */
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
        if (word.length () == 6)
          return PAIR;   /* currency pair such as EURUSD */
        if (word.length () == 1 && "DWMQYB".contains (word))
          return UNIT;
        return YYUNDEF;
      }
    pos++;
    if ("+-./,:()>".indexOf (c) >= 0)
      return c;
    return YYUNDEF;   /* unknown character: a syntax error for the parser */
  }

  public void yyerror (String message)
  {
    error = message;
  }
}
