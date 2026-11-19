package io.github.hjuergens.parser;

import io.github.hjuergens.parser.rules.RuleFiles;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.hjuergens.parser.RuleCatalog.Citation;
import io.github.hjuergens.parser.RuleCatalog.Occurrence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Rule files where the result is more than dates: attributes and announcements. The dates are checked by the shared
 * cases of examples/rules.txt (RuleCasesTest).
 */
public class RuleCatalogTest {

    private static final String RULES = """
            rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON}
              cites "Ley 51 de 1983" art "1" label official;
            weekend country:IL = {FRI, SAT};
            rule il.zahav for short_day in country:IL = [1 JAN : 31 DEC] IF THU
              with kind = short_day, session 09:00 - 14:00 tz "Asia/Jerusalem";
            rule fed.meeting for central_bank_meeting in country:XX = 15 MAR > PRECEDING with precision = month;
            weekend country:AE = {FRI, SAT};
            weekend country:AE = {SAT, SUN} from 2022-01-01;
            rule ae.eid for eid_al_fitr in country:AE = [1 SHAWWAL : 3 SHAWWAL];
            rule sa.eid for eid_al_fitr in country:SA = 1 SHAWWAL BY SIGHTING;
            """;

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    public void occurrenceCarriesKindSessionPrecisionAndCitations() {
        RuleCatalog catalog = RuleFiles.parseRules(RULES);
        Occurrence zahav = catalog.holidays("country:IL", 2026).get(0);
        assertEquals("short_day", zahav.kind());
        assertEquals(List.of("09:00", "14:00", "Asia/Jerusalem"), zahav.session());
        assertEquals("month", catalog.holidays("country:XX", 2026).get(0).precision());
        assertEquals(List.of(new Citation("Ley 51 de 1983", "1", null, "official")), catalog.holidays("country:CO", 2026).get(0).citations());
    }

    @Test
    public void announcementsAndProjections() {
        RuleCatalog catalog = RuleFiles.parseRules(RULES, Map.of(
                "ae.eid", List.of(date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)),
                "sa.eid", List.of(date(2028, 2, 27))));
        List<Occurrence> announced = catalog.holidays("country:AE", 2026);
        assertEquals(List.of(date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)), announced.stream().map(Occurrence::date).toList());
        assertTrue(announced.stream().noneMatch(Occurrence::projected));
        LocalDate shawwal = ScheduleSemantics.monthDay(1, "SHAWWAL").in(2027);
        List<Occurrence> projected = catalog.holidays("country:AE", 2027);
        assertEquals(shawwal, projected.get(0).date());
        assertEquals(3, projected.size());
        assertTrue(projected.stream().allMatch(Occurrence::projected));
        Occurrence sighting = catalog.holidays("country:SA", 2027).get(0);
        assertEquals(shawwal, sighting.date());
        assertTrue(sighting.projected());
        Occurrence published = catalog.holidays("country:SA", 2028).get(0);
        assertEquals(date(2028, 2, 27), published.date());
        assertFalse(published.projected());
    }

    @Test
    public void announcementsAreDataAndKindEvent() {
        RuleCatalog catalog = RuleFiles.parseRules("""
                rule fed.christmas for christmas_day in central_bank:FED = 25 DEC;
                rule fed.fomc for fomc_decision in central_bank:FED = 3 THU MAR with kind = event;
                rule fed.after for test_day in central_bank:FED = 18 MAR > FOLLOWING;
                rule xetr.ex_dividend for ex_dividend in exchange:XETR = @issuer.agm > NEXT BD;
                """, Map.of("fed.fomc", List.of(date(2026, 3, 18)), "issuer.agm", List.of(date(2026, 5, 7))));
        List<String> got2026 = catalog.holidays("central_bank:FED", 2026).stream()
                .map(o -> o.rule() + " " + o.date() + " " + o.projected()).toList();
        List<String> got2027 = catalog.holidays("central_bank:FED", 2027).stream()
                .map(o -> o.rule() + " " + o.date() + " " + o.projected()).toList();
        assertTrue(got2026.toString(), got2026.contains("fed.fomc 2026-03-18 false"));   // the announcement wins
        assertTrue(got2027.toString(), got2027.contains("fed.fomc 2027-03-18 true"));    // else the projection, marked
        assertTrue(got2027.toString(), got2027.contains("fed.after 2027-03-18 false"));  // the event closes nothing
        // a key without a rule holds announced dates only; a rule can refer to it
        assertEquals(List.of(date(2026, 5, 8)), catalog.holidays("exchange:XETR", 2026).stream().map(Occurrence::date).toList());
        assertEquals(List.of(), catalog.holidays("exchange:XETR", 2027));
    }

    @Test
    public void oneOfDatesAreProjectionsUnlessAnnounced() {
        RuleCatalog catalog = RuleFiles.parseRules("""
                rule sa.eid for eid_al_fitr in country:SA = ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 };
                rule xsau.eid for eid_al_fitr in exchange:XSAU = ONE OF { 1 SHAWWAL AS uq, 1 SHAWWAL + 1D AS late };
                """, Map.of("xsau.eid", List.of(date(2026, 3, 22))));
        assertTrue(catalog.holidays("country:SA", 2026).stream().allMatch(Occurrence::projected));
        List<Occurrence> xsau = catalog.holidays("exchange:XSAU", 2026);
        assertEquals(1, xsau.size());
        assertEquals(date(2026, 3, 22), xsau.get(0).date());
        assertEquals(null, xsau.get(0).scenario());
    }

    @Test
    public void singleSessionTimeAndNewKinds() {
        RuleCatalog catalog = RuleFiles.parseRules("""
                rule rba.decision for rba_decision in central_bank:RBA = 3 TUE MAR with kind = event, session 14:30 tz "Australia/Sydney";
                rule xnse.muhurat for muhurat_session in exchange:XNSE = 1 NOV with kind = special_session;
                rule dsmd.ramadan for ramadan_session in exchange:DSMD = 1 MAR with kind = changed_hours, session 09:30 - 13:15;
                """);
        List<Occurrence> rba = catalog.holidays("central_bank:RBA", 2026);
        assertEquals(1, rba.size());
        assertEquals(Arrays.asList("14:30", "14:30", "Australia/Sydney"), rba.get(0).session());
        assertEquals("special_session", catalog.holidays("exchange:XNSE", 2026).get(0).kind());
        assertEquals("changed_hours", catalog.holidays("exchange:DSMD", 2026).get(0).kind());
    }
}
