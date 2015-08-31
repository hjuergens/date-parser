package de.juergens

import java.time.temporal._
import java.time.{LocalDate => Date, DayOfWeek}

import de.juergens.rule.WeekDayPredicate
import de.juergens.time._
import de.juergens.time.impl.{DayShifter, TimeUnitShifter, WeekShifter}
import de.juergens.util.{Cardinal, Direction, Ordinal, Sign}

import scala.util.parsing.combinator.JavaTokenParsers

abstract class OrdinalAttribute(ordinal:Ordinal, predicate:(Date)=>Boolean)
  extends TemporalAdjuster


case class OrdinalWeekDay(ordinal:Ordinal, weekDayPredicate: WeekDayPredicate)
  extends OrdinalAttribute(ordinal, weekDayPredicate.evaluate)
  with LocalDateAdjuster
{

  val Ordinal(number) = ordinal

  val shifter = time.impl.DateShifter(weekDayPredicate.test)

  def forward(count:Int) = new TemporalQuery[Temporal] {
    override def queryFrom(temporal: TemporalAccessor): Temporal =
    {
      TemporalQueries.localDate().queryFrom(temporal)
        .`with`(TemporalAdjusters.nextOrSame(weekDayPredicate.weekDay))
        .plusWeeks(count-1)
    }
  }
  def backward(count:Int) = new TemporalQuery[Temporal] {
    override def queryFrom(temporal: TemporalAccessor): Temporal =
    {
      TemporalQueries.localDate().queryFrom(temporal)
        .`with`(TemporalAdjusters.previousOrSame(weekDayPredicate.weekDay))
        .minusWeeks(count-1)
    }
  }
  override def adjustInto(anchor: Temporal): Temporal = {
    (number match {
      case i if(i > 0) => forward(i)
      case i if(i < 0) => backward(-i)
      case _ => TemporalQueries.localDate()
    }).queryFrom(anchor)

    /*
    var date = anchor
    val currentWeekday : DayOfWeek = DayOfWeek(anchor.get(ChronoField.DAY_OF_WEEK))
    val destinationWeekday : DayOfWeek  = weekDayPredicate.weekDay
    val dayShifter = DayShifter(destinationWeekday distance currentWeekday match {
      case days if days < 0 => days + 7
      case days if days > 0 => days
    })

    if(number > 0) date = dayShifter.adjustInto(date)
    if(number > 1)
      date = WeekShifter(number-1).adjustInto(date)

    date
    */
  }
}