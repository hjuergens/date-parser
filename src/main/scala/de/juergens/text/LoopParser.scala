/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.juergens.text

import scala.language.postfixOps
import scala.util.parsing.combinator.RegexParsers

class LoopParser extends RegexParsers {
  override type Elem = Char  
  def identifier  = """[_\p{L}][_\p{L}\p{Nd}]*""".r  
  def integer     = """(0|[1-9]\d*)""".r ^^ { _.toInt }  
  def loop =    "for"~identifier~"in"~integer~"to"~integer~statement ^^    {
    case f~variable~i~lBound~t~uBound~statement => ForLoop(variable, lBound, uBound,statement)
  }  
  def statements = statement*  
  def block = "{"~>statements<~"}"  ^^ { l => Block(l) }  
  def statement : Parser[Statement] = loop | block
} 

abstract trait Statement  
case class Block(statements : List[Statement]) extends Statement 
case class ForLoop(variable: String, lowerBound:Int, upperBound: Int, statement:Statement) extends Statement