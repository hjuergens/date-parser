module ExpressionGrammar where

import Control.Applicative((<*))
import Text.Parsec
import Text.Parsec.String
import Text.Parsec.Expr
import Text.Parsec.Token
import Text.Parsec.Language



expr  ::= var | const | ( expr ) | unop expr | expr duop expr
var   ::= letter { letter | digit }*
const ::= true | false
unop  ::= ~
duop  ::= & | =

