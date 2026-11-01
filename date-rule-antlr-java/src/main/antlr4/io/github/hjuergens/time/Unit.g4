grammar Unit;

unit: SECONDS
 | MINUTES
 | HOURS
 | DAYS
 | WEEKS
 | MONTHS
 | YEARS
 | DECADES
 | ERAS
 | CENTURIES
 | WEEKYEARS
 | HALFDAYS
 | SECONDS
 | MILLIS
  ;

SECONDS   : 'seconds';
MINUTES   : 'minutes';
HOURS     : 'hours';
DAYS      : 'days';
WEEKS     : 'weeks';
MONTHS    : 'months';
YEARS     : 'years';
DECADES   : 'decades';
NANOS     : 'nanos';
MICROS    : 'micros';
MILLIS    : 'millis';
WEEKYEARS : 'weekyears';
HALFDAYS  : 'halfdays';
CENTURIES : 'centuries';
MILLENNIA : 'millennia';
ERAS      : 'eras';
FOREVER   : 'forever';
