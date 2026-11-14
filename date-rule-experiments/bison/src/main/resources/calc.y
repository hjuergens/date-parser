/* Infix notation calculator--calc */
%language "Java"
%define api.package {io.github.hjuergens.bison}
%define api.parser.class {Calc}
%define api.parser.public


%code {

  /** values of the expressions parsed so far, one per input line */
  public final java.util.List<Integer> results = new java.util.ArrayList<>();

  public static void main (String args[]) throws IOException
  {
    CalcLexer l = new CalcLexer (System.in);
    Calc p = new Calc (l);
    p.parse ();
    p.results.forEach (System.out::println);
  }

}

%code imports {
  import java.io.StreamTokenizer;
  import java.io.InputStream;
  import java.io.InputStreamReader;
  import java.io.Reader;
  import java.io.IOException;
}

/* Bison Declarations */
%token <Integer> NUM "number"
%type  <Integer> exp

%nonassoc '=' /* comparison            */
%left '-' '+'
%left '*' '/'
%left NEG     /* negation--unary minus */
%right '^'    /* exponentiation        */

/* Grammar follows */
%%
input:
  line
| input line
;

line:
  '\n'
| exp '\n'           { results.add ($1);                                    }
| error '\n'
;

exp:
  NUM                { $$ = $1;                                             }
| exp '=' exp
  {
    if ($1.intValue () != $3.intValue ())
      yyerror ( "calc: error: " + $1 + " != " + $3);
  }
| exp '+' exp        { $$ = Integer.valueOf ($1.intValue () + $3.intValue ());  }
| exp '-' exp        { $$ = Integer.valueOf ($1.intValue () - $3.intValue ());  }
| exp '*' exp        { $$ = Integer.valueOf ($1.intValue () * $3.intValue ());  }
| exp '/' exp        { $$ = Integer.valueOf ($1.intValue () / $3.intValue ());  }
| '-' exp  %prec NEG { $$ = Integer.valueOf (-$2.intValue ());                  }
| exp '^' exp        { $$ = Integer.valueOf ((int)
                                         Math.pow ($1.intValue (),
                                                   $3.intValue ()));        }
| '(' exp ')'        { $$ = $2;                                             }
| '(' error ')'      { $$ = Integer.valueOf (1111);                             }
| '!'                { $$ = Integer.valueOf (0); return YYERROR;            }
| '-' error          { $$ = Integer.valueOf (0); return YYERROR;            }
;


%%
class CalcLexer implements Calc.Lexer {

  StreamTokenizer st;

  public CalcLexer (InputStream is)
  {
    st = new StreamTokenizer (new InputStreamReader (is));
    st.resetSyntax ();
    st.eolIsSignificant (true);
    st.whitespaceChars (9, 9);
    st.whitespaceChars (32, 32);
    st.wordChars (48, 57);
  }


  /** number of syntax errors reported so far */
  int errors;

  public void yyerror (String s)
  {
    errors++;
    System.err.println (s);
  }


  Integer yylval;

  public Object getLVal() {
    return yylval;
  }

  public int yylex () throws IOException {
    int ttype = st.nextToken ();

    if (ttype == st.TT_EOF)
      return EOF;

    else if (ttype == st.TT_EOL)
      {

        return (int) '\n';
      }

    else if (ttype == st.TT_WORD)
      {
        yylval = Integer.valueOf (st.sval);
        return NUM;
      }

    else
      return st.ttype;
  }



}


class Position {
  public int line;
  public int token;

  public Position ()
  {
    line = 0;
    token = 0;
  }

  public Position (int l, int t)
  {
    line = l;
    token = t;
  }

  public boolean equals (Position l)
  {
    return l.line == line && l.token == token;
  }

  public String toString ()
  {
    return Integer.toString (line) + "." + Integer.toString(token);
  }

  public int lineno ()
  {
    return line;
  }

  public int token ()
  {
    return token;
  }
}