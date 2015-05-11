package de.juergens.time


trait Date

object Date {

  def apply(year:Int, month:Int, dayOfMonth:Int) : Date = {
    impl.SimpleDate(year,month,dayOfMonth)
  }

  def unapply(any :Any) : Option[(Int,Int,Int)] = PartialFunction.condOpt(any){
    case date : impl.SimpleDate => (date.year ,date.month ,date.dayOfMonth)
  }

//  def isValid(year :Int , month :Int, day :Int) : Boolean = true
//  def daysIn(year : Int, month : Int) : Int = 30
//  def isLeap(year:Int) : Boolean = false
//
//  implicit class ExtendedDate(date:Date) extends Date {
//    def +(days : Int) : Date = { date }
//    def -(days : Int) : Date = { date }
//    def year : Int = 0
//    def month : Int = 0
//    def day : Int = 0
//  }
}

