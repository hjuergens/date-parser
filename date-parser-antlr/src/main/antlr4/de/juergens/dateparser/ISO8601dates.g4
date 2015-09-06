grammar ISO8601dates;

options {
    language = Java;
}

@header { }

input           : date+ EOF;

date    : FOUR_DIGIT ('-')? TWO_DIGIT ('-')? TWO_DIGIT ;

FOUR_DIGIT
    : TWO_DIGIT TWO_DIGIT ;

TWO_DIGIT
    : DIGIT DIGIT ;

DIGIT   : ('0'..'9') ;
