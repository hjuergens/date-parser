package de.juergens.time

import java.time.chrono.{ChronoLocalDate, Era, IsoChronology}
import java.time.format.DateTimeFormatter
import java.time.temporal._
import java.time.{LocalDate => JLocalDate, Month => JMonth, Period => JPeriod, _}

import scala.language.implicitConversions


trait EnrichedDate extends Temporal with Ordered[Temporal] {
  protected def localDate: JLocalDate

  type D <: EnrichedDate

  def +(days: Int): D

  def -(days: Int): D

  def year: Int

  def month: Int

  def dayOfMonth: Int

  override final def equals(obj: scala.Any): Boolean = obj match {
    case date: EnrichedDate => year == date.year && month == date.month && dayOfMonth == date.dayOfMonth
  }
}

object Date {

  def apply(year: Int, month: Int, dayOfMonth: Int): JLocalDate = java.time.LocalDate.of(year, month, dayOfMonth)

  implicit class EnrichedLocalDate(localDate: JLocalDate) extends ChronoLocalDate {

    def getDayOfMonth: Int = localDate.getDayOfMonth

    override def minus(amountToSubtract: TemporalAmount): JLocalDate = localDate.minus(amountToSubtract)

    override def `with`(field: TemporalField, newValue: Long): JLocalDate = localDate.`with`(field, newValue)

    def minusDays(daysToSubtract: Long): JLocalDate = localDate.minusDays(daysToSubtract)

    def atTime(hour: Int, minute: Int): LocalDateTime = localDate.atTime(hour, minute)

    override def equals(obj: scala.Any): Boolean = localDate.equals(obj)

    override def atTime(time: LocalTime): LocalDateTime = localDate.atTime(time)

    override def isBefore(other: ChronoLocalDate): Boolean = localDate.isBefore(other)

    override def get(field: TemporalField): Int = localDate.get(field)

    override def range(field: TemporalField): ValueRange = localDate.range(field)

    override def adjustInto(temporal: Temporal): Temporal = localDate.adjustInto(temporal)

    def atTime(hour: Int, minute: Int, second: Int): LocalDateTime = localDate.atTime(hour, minute, second)

    override def compareTo(other: ChronoLocalDate): Int = localDate.compareTo(other)

    override def plus(amountToAdd: TemporalAmount): JLocalDate = localDate.plus(amountToAdd)

    def withMonth(month: Int): JLocalDate = localDate.withMonth(month)

    def plusWeeks(weeksToAdd: Long): JLocalDate = localDate.plusWeeks(weeksToAdd)

    def atStartOfDay(zone: ZoneId): ZonedDateTime = localDate.atStartOfDay(zone)

    override def getLong(field: TemporalField): Long = localDate.getLong(field)

    override def getChronology: IsoChronology = localDate.getChronology

    def minusYears(yearsToSubtract: Long): JLocalDate = localDate.minusYears(yearsToSubtract)

    override def isSupported(unit: TemporalUnit): Boolean = localDate.isSupported(unit)

    override def toEpochDay: Long = localDate.toEpochDay

    override def until(endDateExclusive: ChronoLocalDate): JPeriod = localDate.until(endDateExclusive)

    def atStartOfDay(): LocalDateTime = localDate.atStartOfDay()

    def atTime(time: OffsetTime): OffsetDateTime = localDate.atTime(time)

    override def hashCode(): Int = localDate.hashCode()

    override def until(endExclusive: Temporal, unit: TemporalUnit): Long = localDate.until(endExclusive, unit)

    override def format(formatter: DateTimeFormatter): String = localDate.format(formatter)

    override def query[R](query: TemporalQuery[R]): R = localDate.query(query)

    override def isAfter(other: ChronoLocalDate): Boolean = localDate.isAfter(other)

    override def minus(amountToSubtract: Long, unit: TemporalUnit): JLocalDate = localDate.minus(amountToSubtract, unit)

    def atTime(hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalDateTime = localDate.atTime(hour, minute, second, nanoOfSecond)

    def withYear(year: Int): JLocalDate = localDate.withYear(year)

    override def `with`(adjuster: TemporalAdjuster): JLocalDate = localDate.`with`(adjuster)

    override def plus(amountToAdd: Long, unit: TemporalUnit): JLocalDate = localDate.plus(amountToAdd, unit)

    def minusWeeks(weeksToSubtract: Long): JLocalDate = localDate.minusWeeks(weeksToSubtract)

    def plusDays(daysToAdd: Long): JLocalDate = localDate.plusDays(daysToAdd)

    def withDayOfMonth(dayOfMonth: Int): JLocalDate = localDate.withDayOfMonth(dayOfMonth)

    def getMonth: JMonth = localDate.getMonth

    def withDayOfYear(dayOfYear: Int): JLocalDate = localDate.withDayOfYear(dayOfYear)

    override def isEqual(other: ChronoLocalDate): Boolean = localDate.isEqual(other)

    def plusYears(yearsToAdd: Long): JLocalDate = localDate.plusYears(yearsToAdd)

    override def toString: String = localDate.toString

    def getDayOfWeek: DayOfWeek = localDate.getDayOfWeek

    override def getEra: Era = localDate.getEra

    def getYear: Int = localDate.getYear

    def plusMonths(monthsToAdd: Long): JLocalDate = localDate.plusMonths(monthsToAdd)

    override def lengthOfMonth(): Int = localDate.lengthOfMonth()

    def minusMonths(monthsToSubtract: Long): JLocalDate = localDate.minusMonths(monthsToSubtract)

    def getDayOfYear: Int = localDate.getDayOfYear

    override def lengthOfYear(): Int = localDate.lengthOfYear()

    override def isSupported(field: TemporalField): Boolean = localDate.isSupported(field)

    override def isLeapYear: Boolean = localDate.isLeapYear

    def getMonthValue: Int = localDate.getMonthValue

    def +(days: Int) = localDate.`with`(ChronoField.DAY_OF_YEAR, days)

    def -(days: Int) = localDate.`with`(ChronoField.DAY_OF_YEAR, -days)
  }

}

