##Summary

Grammar to describe term structures. Producing date-time-adjusters according to a term structure.

##Using

Expressions:
```
(SP:3M:5Y) = [SP,3M,6M,...,57M,5Y]
[IMM_01,IMM_02,IMM_03]+3M
```

Java code example:
```
ANTLRInputStream inputStream = new ANTLRInputStream("[3M,4M,6M]x(4M:1M:9M)");
TermStructureLexer lexer = new TermStructureLexer(inputStream);
CommonTokenStream tokenStream = new CommonTokenStream(lexer);
TermStructureParser parser = new TermStructureParser(tokenStream);
List<Pair<DateTimeAdjuster, DateTimeAdjuster>> list = parser.forwards().listOut;
```

##Implementation & Testing

* [TestNG](http://testng.org/doc/index.html).
* [Joda-Time](http://www.joda.org/joda-time/).

##License
[Apache License, Version 2.0, January 2004](http://www.apache.org/licenses/).

