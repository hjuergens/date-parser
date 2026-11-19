package io.github.hjuergens.coco.schedule;

import io.github.hjuergens.coco.schedule.rules.RuleFiles;

import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.hjuergens.coco.schedule.RuleCatalog.Citation;
import io.github.hjuergens.coco.schedule.RuleCatalog.Occurrence;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

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
        assertEquals(zahav.kind(), "short_day");
        assertEquals(zahav.session(), List.of("09:00", "14:00", "Asia/Jerusalem"));
        assertEquals(catalog.holidays("country:XX", 2026).get(0).precision(), "month");
        assertEquals(catalog.holidays("country:CO", 2026).get(0).citations(),
                List.of(new Citation("Ley 51 de 1983", "1", null, "official")));
    }

    @Test
    public void announcementsAndProjections() {
        RuleCatalog catalog = RuleFiles.parseRules(RULES, Map.of(
                "ae.eid", List.of(date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)),
                "sa.eid", List.of(date(2028, 2, 27))));
        List<Occurrence> announced = catalog.holidays("country:AE", 2026);
        assertEquals(announced.stream().map(Occurrence::date).toList(),
                List.of(date(2026, 3, 20), date(2026, 3, 21), date(2026, 3, 22)));
        assertTrue(announced.stream().noneMatch(Occurrence::projected));
        LocalDate shawwal = ScheduleSemantics.monthDay(1, "SHAWWAL").in(2027);
        List<Occurrence> projected = catalog.holidays("country:AE", 2027);
        assertEquals(projected.get(0).date(), shawwal);
        assertEquals(projected.size(), 3);
        assertTrue(projected.stream().allMatch(Occurrence::projected));
        Occurrence sighting = catalog.holidays("country:SA", 2027).get(0);
        assertEquals(sighting.date(), shawwal);
        assertTrue(sighting.projected());
        Occurrence published = catalog.holidays("country:SA", 2028).get(0);
        assertEquals(published.date(), date(2028, 2, 27));
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
        assertTrue(got2026.contains("fed.fomc 2026-03-18 false"), got2026.toString());   // the announcement wins
        assertTrue(got2027.contains("fed.fomc 2027-03-18 true"), got2027.toString());    // else the projection, marked
        assertTrue(got2027.contains("fed.after 2027-03-18 false"), got2027.toString());  // the event closes nothing
        // a key without a rule holds announced dates only; a rule can refer to it
        assertEquals(catalog.holidays("exchange:XETR", 2026).stream().map(Occurrence::date).toList(),
                List.of(date(2026, 5, 8)));
        assertEquals(catalog.holidays("exchange:XETR", 2027), List.of());
    }

    @Test
    public void oneOfDatesAreProjectionsUnlessAnnounced() {
        RuleCatalog catalog = RuleFiles.parseRules("""
                rule sa.eid for eid_al_fitr in country:SA = ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 };
                rule xsau.eid for eid_al_fitr in exchange:XSAU = ONE OF { 1 SHAWWAL AS uq, 1 SHAWWAL + 1D AS late };
                """, Map.of("xsau.eid", List.of(date(2026, 3, 22))));
        assertTrue(catalog.holidays("country:SA", 2026).stream().allMatch(Occurrence::projected));
        List<Occurrence> xsau = catalog.holidays("exchange:XSAU", 2026);
        assertEquals(xsau.size(), 1);
        assertEquals(xsau.get(0).date(), date(2026, 3, 22));
        assertEquals(xsau.get(0).scenario(), null);
    }

    @Test
    public void singleSessionTimeAndNewKinds() {
        RuleCatalog catalog = RuleFiles.parseRules("""
                rule rba.decision for rba_decision in central_bank:RBA = 3 TUE MAR with kind = event, session 14:30 tz "Australia/Sydney";
                rule xnse.muhurat for muhurat_session in exchange:XNSE = 1 NOV with kind = special_session;
                rule dsmd.ramadan for ramadan_session in exchange:DSMD = 1 MAR with kind = changed_hours, session 09:30 - 13:15;
                """);
        List<Occurrence> rba = catalog.holidays("central_bank:RBA", 2026);
        assertEquals(rba.size(), 1);
        assertEquals(rba.get(0).session(), Arrays.asList("14:30", "14:30", "Australia/Sydney"));
        assertEquals(catalog.holidays("exchange:XNSE", 2026).get(0).kind(), "special_session");
        assertEquals(catalog.holidays("exchange:DSMD", 2026).get(0).kind(), "changed_hours");
    }
}
