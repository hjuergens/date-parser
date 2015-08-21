package de.juergens

/**
  */
object App {

  def main(args: Array[String]) {
    println("Hello World!")
    println("concat arguments = " + foo(args))
  }

  def foo(x: Array[String]) = x.foldLeft("")((a, b) => a + b)

}
