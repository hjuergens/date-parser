grammar XPeriod;

options {
    language = Java;
}

@header
{
    import org.joda.time.Period;
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

// period, TODO consider The standard ISO format - PyYmMwWdDThHmMsS.
period returns [Period p]
    : yearssub { $p = $yearssub.p; }
    | monthssub { $p = $monthssub.p; }
    | weekssub { $p = $weekssub.p; }
    | dayssub { $p = $dayssub.p; }
    ;

yearssub returns [Period p]
    : years (monthssub | weekssub | dayssub)?
    {
        $p = $years.p;
        if($monthssub.ctx != null)
            $p = $p.plus($monthssub.p);
        else if($weekssub.ctx != null)
            $p = $p.plus($weekssub.p);
        else if($dayssub.ctx != null)
            $p = $p.plus($dayssub.p);
    }
    ;

monthssub returns [Period p]
    : months (weekssub | dayssub)?
    {
        $p = $months.p;
        if($weekssub.ctx != null)
            $p = $p.plus($weekssub.p);
        else if($dayssub.ctx != null)
            $p = $p.plus($dayssub.p);
    }
    ;

weekssub returns [Period p]
    : weeks dayssub?
    {
        $p = $weeks.p;
        if($dayssub.ctx != null)
            $p = $weeks.p.plus($dayssub.p);
    }
    ;

dayssub returns [Period p]
    : days { $p = $days.p; }
    ;

// units
years returns [Period p]
    // Labels become fields in the appropriate parse tree node class
    : cardinal=twodigit YEAR { $p = Period.years($twodigit.value); }
    ;

months returns [Period p]
    : twodigit MONTH { $p = Period.months($twodigit.value); }
    ;

weeks returns [Period p]
    : twodigit WEEK { $p = Period.weeks($twodigit.value); }
    ;

days returns [Period p]
    : twodigit DAY { $p = Period.days($twodigit.value); }
    ;

// cardinal
twodigit returns [int value]
    : (t2=TWODIGITNUMBER) { $value = Integer.parseInt($t2.text); }
    ;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

TWODIGITNUMBER  : NONNULLDIGIT?DIGIT ;

fragment DIGIT  : '0'..'9' ;
fragment NONNULLDIGIT  : '1'..'9' ;

YEAR : 'Y';
MONTH : 'M';
WEEK : 'W';
DAY : 'D';
QUARTER : 'Q';

NEWLINE   : '\r' '\n' | '\n' | '\r';

WS      : (' '|'\t'|'\f'|'\r'|'\n')+ {skip();};
SPACE : (' ' | '\t' | '\r' | '\n') {skip();};

