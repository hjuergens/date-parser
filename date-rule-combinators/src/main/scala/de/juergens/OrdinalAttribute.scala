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



