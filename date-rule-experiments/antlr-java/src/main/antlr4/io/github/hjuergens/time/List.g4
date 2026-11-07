grammar List;

import TimeLexer;

/*
http://stackoverflow.com/questions/27405893/using-visitors-in-antlr4-in-a-simple-integer-list-grammar
https://github.com/ericharley/matlab-parser/blob/master/MATLAB.g4
*/
/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

list : LPARAM s=TWODIGITNUMBER (DP p=TWODIGITNUMBER)? DP e=TWODIGITNUMBER RPARAM
    ;

