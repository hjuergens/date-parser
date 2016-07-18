grammar Schedule;

import List,Period;

@header
{
    import org.joda.time.Period;
    import java.util.List;
    import java.util.LinkedList;
}

vector :  list '*' period
    ;
