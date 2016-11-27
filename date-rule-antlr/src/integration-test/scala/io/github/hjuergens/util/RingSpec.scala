package io.github.hjuergens.util


import org.specs2.mutable._

class RingSpec extends Specification { override def is = s2"""

  This is a specification for the 'Hello world' string

  The 'Hello world' string should
    contain 11 characters                             $e1
    start with 'Hello'                                $e2
    end with 'world'                                  $e3
                                                      """
        def e1 = "Hello world" must haveSize(11)
        def e2 = "Hello world" must startWith("Hello")
        def e3 = "Hello world" must endWith("world")

        "This is a specification for the 'Hello world' string".txt

        "The 'Hello world' string should" >> {
                "contain 11 characters" >> {
                        "Hello world" must haveSize(11)
                }
                "start with 'Hello'" >> {
                        "Hello world" must startWith("Hello")
                }
                "end with 'world'" >> {
                        "Hello world" must endWith("world")
                }
        }
}

/*
class PluralSpec extends Specification { def is = s2"""

  Names can be pluralized depending on a quantity
  ${ "apple".plural(1) === "apple"  }
  ${ "apple".plural(2) === "apples" }
  ${ "foot".plural(2)  === "feet"   }

  ${ 1.qty("apple") === "1 apple"  }
  ${ 2.qty("apple") === "2 apples" }
  ${ 2.qty("foot")  === "2 feet"   }
  """
}

import org.specs2.mutable._
object ArithmeticSpec2 extends Specification {
        "Arithmetic2" should {
                "add" in {
                        "two numbers" in {
                                1 + 1 mustEqual 2
                        }
                        "three numbers" in {
                                1 + 1 + 1 mustEqual 3
                        }
                }
        }
}
*/