import de.juergens.text.DateRuleParsers
import java.time.LocalDate

object TestNattyDates extends App with DateRuleParsers {
  
  val testCases = Seq[
    // Formal dates
    ("1978-01-28", "date"),
    ("1984/04/02", "date"),
    ("1/02/1980", "date"),
    ("2/28/79", "date"),
    
    // Relaxed dates
    ("The 31st of April in the year 2008", "monthDay"),
    ("Fri, 21 Nov 1997", "adjuster"),
    ("Jan 21, '97", "monthDay"),
    ("Sun, Nov 21", "adjuster"),
    ("jan 1st", "monthDay"),
    ("february twenty-eighth", "monthDay"),
    
    // Relative dates
    ("next thursday", "adjuster"),
    ("last wednesday", "adjuster"),
    ("today", "adjuster"),
    ("tomorrow", "adjuster"),
    ("yesterday", "adjuster"),
    ("next week", "adjuster"),
    ("next month", "adjuster"),
    ("next year", "adjuster"),
    ("3 days from now", "adjuster"),
    ("three weeks ago", "adjuster"),
    
    // Date alternatives
    ("next wed or thurs", "adjuster"),
    ("oct 3rd or 4th", "monthDay"),
    
    // Prefixes
    ("day after", "adjuster"),
    ("the day before", "adjuster"),
    ("the monday after", "adjuster"),
    ("the monday before", "adjuster"),
    ("2 fridays before", "adjuster"),
    ("4 tuesdays after", "adjuster")
  ]
  
  testCases.foreach { case (input, parserName) =>
    try {
      val parser = this.getClass.getMethod(parserName).invoke(this).asInstanceOf[Parser[Any]]
      val result = parseAll(parser, input.toLowerCase)
      if (result.successful) {
        println(s"✓ '$input' parsed with $parserName -> ${result.get}")
      } else {
        println(s"✗ '$input' FAILED with $parserName: ${result.toString.take(100)}")
      }
    } catch {
      case e: Exception =>
        println(s"✗ '$input' ERROR with $parserName: ${e.getMessage}")
    }
  }
}
