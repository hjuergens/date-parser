package de.juergens.text.rules

import java.time.LocalDate
import java.util.{Arrays, List => JList, Map => JMap}

import de.juergens.text.RuleCatalog.Citation
import de.juergens.text.ScheduleSemantics
import org.scalatest.funsuite.AnyFunSuite

import scala.jdk.CollectionConverters._

/**
 * Rule files where the result is more than dates: attributes and announcements. The dates are checked by the shared
 * cases of examples/rules.txt (RuleCasesSuite).
 */
class RuleCatalogSuite extends AnyFunSuite {

  private val Rules =
    """rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON}
      |  cites "Ley 51 de 1983" art "1" label official;
      |weekend country:IL = {FRI, SAT};
      |rule il.zahav for short_day in country:IL = [1 JAN : 31 DEC] IF THU
      |  with kind = short_day, session 09:00 - 14:00 tz "Asia/Jerusalem";
      |rule fed.meeting for central_bank_meeting in country:XX = 15 MAR > PRECEDING with precision = month;
      |weekend country:AE = {FRI, SAT};
      |weekend country:AE = {SAT, SUN} from 2022-01-01;
      |rule ae.eid for eid_al_fitr in country:AE = [1 SHAWWAL : 3 SHAWWAL];
      |rule sa.eid for eid_al_fitr in country:SA = 1 SHAWWAL BY SIGHTING;
      |""".stripMargin

  private def date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

  private def announced(entries: (String, Seq[LocalDate])*): JMap[String, JList[LocalDate]] =
    entries.map { case (k, v) => k -> v.asJava }.toMap.asJava

  test("an occurrence carries kind, session, precision and citations") {
    val catalog = RuleFiles.parseRules(Rules)
    val zahav = catalog.holidays("country:IL", 2026).get(0)
    assert(zahav.kind === "short_day")
    assert(zahav.session === JList.of("09:00", "14:00", "Asia/Jerusalem"))
    assert(catalog.holidays("country:XX", 2026).get(0).precision === "month")
    assert(catalog.holidays("country:CO", 2026).get(0).citations === JList.of(new Citation("Ley 51 de 1983", "1", null, "official")))
  }

  test("announcements and projections") {
    val catalog = RuleFiles.parseRules(Rules, announced(
      "ae.eid" -> Seq(date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)), "sa.eid" -> Seq(date(2028, 2, 27))))
    val announcedDates = catalog.holidays("country:AE", 2026).asScala
    assert(announcedDates.map(_.date) === Seq(date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)))
    assert(!announcedDates.exists(_.projected))
    val shawwal = ScheduleSemantics.monthDay(1, "SHAWWAL").in(2027)
    val projected = catalog.holidays("country:AE", 2027).asScala
    assert(projected.head.date === shawwal && projected.size === 3 && projected.forall(_.projected))
    val sighting = catalog.holidays("country:SA", 2027).get(0)
    assert(sighting.date === shawwal && sighting.projected)
    val published = catalog.holidays("country:SA", 2028).get(0)
    assert(published.date === date(2028, 2, 27) && !published.projected)
  }

  test("announcements are data, and kind = event closes nothing") {
    val catalog = RuleFiles.parseRules(
      """rule fed.christmas for christmas_day in central_bank:FED = 25 DEC;
        |rule fed.fomc for fomc_decision in central_bank:FED = 3 THU MAR with kind = event;
        |rule fed.after for test_day in central_bank:FED = 18 MAR > FOLLOWING;
        |rule xetr.ex_dividend for ex_dividend in exchange:XETR = @issuer.agm > NEXT BD;
        |""".stripMargin, announced("fed.fomc" -> Seq(date(2026, 3, 18)), "issuer.agm" -> Seq(date(2026, 5, 7))))
    def got(year: Int) = catalog.holidays("central_bank:FED", year).asScala.map(o => s"${o.rule} ${o.date} ${o.projected}")
    assert(got(2026).contains("fed.fomc 2026-03-18 false"))   // the announcement wins
    assert(got(2027).contains("fed.fomc 2027-03-18 true"))    // else the projection, marked
    assert(got(2027).contains("fed.after 2027-03-18 false"))  // the event closes nothing
    // a key without a rule holds announced dates only; a rule can refer to it
    assert(catalog.holidays("exchange:XETR", 2026).asScala.map(_.date) === Seq(date(2026, 5, 8)))
    assert(catalog.holidays("exchange:XETR", 2027).isEmpty)
  }

  test("ONE OF dates are projections unless announced") {
    val catalog = RuleFiles.parseRules(
      """rule sa.eid for eid_al_fitr in country:SA = ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 };
        |rule xsau.eid for eid_al_fitr in exchange:XSAU = ONE OF { 1 SHAWWAL AS uq, 1 SHAWWAL + 1D AS late };
        |""".stripMargin, announced("xsau.eid" -> Seq(date(2026, 3, 22))))
    assert(catalog.holidays("country:SA", 2026).asScala.forall(_.projected))
    val xsau = catalog.holidays("exchange:XSAU", 2026).asScala
    assert(xsau.map(o => (o.date, o.scenario)) === Seq((date(2026, 3, 22), null)))
  }

  test("a single session time and the new kinds") {
    val catalog = RuleFiles.parseRules(
      """rule rba.decision for rba_decision in central_bank:RBA = 3 TUE MAR with kind = event, session 14:30 tz "Australia/Sydney";
        |rule xnse.muhurat for muhurat_session in exchange:XNSE = 1 NOV with kind = special_session;
        |rule dsmd.ramadan for ramadan_session in exchange:DSMD = 1 MAR with kind = changed_hours, session 09:30 - 13:15;
        |""".stripMargin)
    val rba = catalog.holidays("central_bank:RBA", 2026)
    assert(rba.size === 1 && rba.get(0).session === Arrays.asList("14:30", "14:30", "Australia/Sydney"))
    assert(catalog.holidays("exchange:XNSE", 2026).get(0).kind === "special_session")
    assert(catalog.holidays("exchange:DSMD", 2026).get(0).kind === "changed_hours")
  }
}
