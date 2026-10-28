/*
 * Shared "schedule" example (see examples/schedule.txt in the project root):
 *   2015-06-09 + 3M, 09.06.2015, (2015-01-15 : 1Q : 2015-12-31)
 * LALR(1): lists and period chains are written left recursive.
 * No 'error' rules: the first syntax error ends the parse (parse() returns false).
 */
%language "Java"
%define api.package {io.github.hjuergens.bison}
%define api.parser.class {ScheduleParser}
%define api.parser.public

%code imports {
  import java.time.LocalDate;
  import java.time.Period;
  import java.util.ArrayList;
  import java.util.List;
}

%code {
  /** the dates of the schedule, set by a successful parse() */
  public List<LocalDate> dates;
}

%token <Integer> NUM "number"
%token <String> UNIT "unit"
%token IMM "IMM"
%type <LocalDate> expr date
%type <Period> period
%type <ArrayList<LocalDate>> exprs

%%

schedule:
  '(' expr ':' period ':' expr ')'  { dates = ScheduleSemantics.range ($2, $4, $6); }
| exprs                             { dates = $1; }
;

exprs:
  expr                              { ArrayList<LocalDate> list = new ArrayList<> (); list.add ($1); $$ = list; }
| exprs ',' expr                    { $1.add ($3); $$ = $1; }   /* $$ is untyped in Java, $1 is not */
;

/* periods are applied from left to right */
expr:
  date                              { $$ = $1; }
| expr '+' period                   { $$ = $1.plus ($3); }
| expr '-' period                   { $$ = $1.minus ($3); }
| expr '>' IMM                      { $$ = ScheduleSemantics.nextImm ($1); }
;

date:
  NUM '-' NUM '-' NUM               { $$ = LocalDate.of ($1, $3, $5); }   /* ISO */
| NUM '.' NUM '.' NUM               { $$ = LocalDate.of ($5, $3, $1); }   /* German */
| NUM '/' NUM '/' NUM               { $$ = LocalDate.of ($5, $1, $3); }   /* US */
;

period:
  NUM UNIT                          { $$ = ScheduleSemantics.period ($1, $2); }
;

%%

/** Hand-written scanner: numbers, IMM, unit letters, single-character tokens. */
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
    if (text.startsWith ("IMM", pos))
      {
        pos += 3;
        return IMM;
      }
    pos++;
    if ("DWMQY".indexOf (c) >= 0)
      {
        value = String.valueOf (c);
        return UNIT;
      }
    if ("+-./,:()>".indexOf (c) >= 0)
      return c;
    return YYUNDEF;   /* unknown character: a syntax error for the parser */
  }

  public void yyerror (String message)
  {
    error = message;
  }
}
