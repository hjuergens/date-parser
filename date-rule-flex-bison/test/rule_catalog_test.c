/* Rule files where the result is more than dates: attributes (kind, session, precision, citations) and announcements.
 * The dates are checked by the shared cases of examples/rules.txt (run_rule_cases.c). Prints every failed check and a
 * summary; the exit status is 1 if a check fails. */
#ifndef _DEFAULT_SOURCE
#define _DEFAULT_SOURCE
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "catalog.h"

static int checks, failed;

#define CHECK(condition) do { checks++; if (!(condition)) { failed++; printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); } } while (0)

static const char *const RULES =
    "rule co.ascension for ascension_day in country:CO = EASTER + 39D {TUE WED THU FRI SAT SUN > NEXT MON}\n"
    "  cites \"Ley 51 de 1983\" art \"1\" label official;\n"
    "weekend country:IL = {FRI, SAT};\n"
    "rule il.zahav for short_day in country:IL = [1 JAN : 31 DEC] IF THU\n"
    "  with kind = short_day, session 09:00 - 14:00 tz \"Asia/Jerusalem\";\n"
    "rule fed.meeting for central_bank_meeting in country:XX = 15 MAR > PRECEDING with precision = month;\n"
    "weekend country:AE = {FRI, SAT};\n"
    "weekend country:AE = {SAT, SUN} from 2022-01-01;\n"
    "rule ae.eid for eid_al_fitr in country:AE = [1 SHAWWAL : 3 SHAWWAL];\n"
    "rule sa.eid for eid_al_fitr in country:SA = 1 SHAWWAL BY SIGHTING;\n";

static RuleCatalog *parse(const char *text, const Announcement *announcements, int count) {
    char error[512];
    RuleCatalog *catalog = catalog_parse(text, announcements, count, error, sizeof error);
    if (!catalog) {
        printf("FAIL: %s\n", error);
        exit(1);
    }
    return catalog;
}

/* the occurrences of the scope in the year; a failed evaluation ends the test */
static OccurrenceList holidays(RuleCatalog *catalog, const char *scope, int year) {
    char error[512];
    OccurrenceList list;
    if (catalog_holidays(catalog, scope, year, &list, error, sizeof error)) {
        printf("FAIL: %s %d: %s\n", scope, year, error);
        exit(1);
    }
    return list;
}

static int has(const OccurrenceList *list, const char *rule, int date, int projected) {
    for (int i = 0; i < list->count; i++)
        if (!strcmp(list->items[i].rule, rule) && list->items[i].date == date && list->items[i].projected == projected) return 1;
    return 0;
}

static void occurrence_carries_kind_session_precision_and_citations(void) {
    RuleCatalog *catalog = parse(RULES, NULL, 0);
    OccurrenceList il = holidays(catalog, "country:IL", 2026), xx = holidays(catalog, "country:XX", 2026),
        co = holidays(catalog, "country:CO", 2026);
    CHECK(il.count && !strcmp(il.items[0].kind, "short_day"));
    CHECK(il.count && !strcmp(il.items[0].session[0], "09:00") && !strcmp(il.items[0].session[1], "14:00")
          && !strcmp(il.items[0].session[2], "Asia/Jerusalem"));
    CHECK(xx.count && !strcmp(xx.items[0].precision, "month"));
    const Citation *c = co.count ? co.items[0].citations : NULL;
    CHECK(c && !strcmp(c->source, "Ley 51 de 1983") && !strcmp(c->article, "1") && !c->url && !strcmp(c->label, "official")
          && !c->next);
    occurrencelist_free(&il), occurrencelist_free(&xx), occurrencelist_free(&co);
    catalog_free(catalog);
}

static void announcements_and_projections(void) {
    int ae[] = { date_of(2026, 3, 20), date_of(2026, 3, 21), date_of(2026, 3, 22) }, sa[] = { date_of(2028, 2, 27) };
    Announcement announced[] = { { "ae.eid", ae, 3 }, { "sa.eid", sa, 1 } };
    RuleCatalog *catalog = parse(RULES, announced, 2);
    OccurrenceList list = holidays(catalog, "country:AE", 2026);
    CHECK(list.count == 3 && list.items[0].date == ae[0] && list.items[1].date == ae[1] && list.items[2].date == ae[2]);
    for (int i = 0; i < list.count; i++) CHECK(!list.items[i].projected);
    occurrencelist_free(&list);
    char error[512];
    DateList shawwal;
    CHECK(!catalog_schedule(catalog, "1 SHAWWAL", date_of(2027, 1, 1), NULL, &shawwal, error, sizeof error) && shawwal.count == 1);
    list = holidays(catalog, "country:AE", 2027);
    CHECK(list.count == 3 && list.items[0].date == shawwal.dates[0]);
    for (int i = 0; i < list.count; i++) CHECK(list.items[i].projected);
    occurrencelist_free(&list);
    list = holidays(catalog, "country:SA", 2027);
    CHECK(list.count == 1 && list.items[0].date == shawwal.dates[0] && list.items[0].projected);
    occurrencelist_free(&list);
    list = holidays(catalog, "country:SA", 2028);
    CHECK(list.count == 1 && list.items[0].date == sa[0] && !list.items[0].projected);
    occurrencelist_free(&list);
    datelist_free(&shawwal);
    catalog_free(catalog);
}

static void announcements_are_data_and_kind_event(void) {
    int fomc[] = { date_of(2026, 3, 18) }, agm[] = { date_of(2026, 5, 7) };
    Announcement announced[] = { { "fed.fomc", fomc, 1 }, { "issuer.agm", agm, 1 } };
    RuleCatalog *catalog = parse(
        "rule fed.christmas for christmas_day in central_bank:FED = 25 DEC;\n"
        "rule fed.fomc for fomc_decision in central_bank:FED = 3 THU MAR with kind = event;\n"
        "rule fed.after for test_day in central_bank:FED = 18 MAR > FOLLOWING;\n"
        "rule xetr.ex_dividend for ex_dividend in exchange:XETR = @issuer.agm > NEXT BD;\n", announced, 2);
    OccurrenceList y2026 = holidays(catalog, "central_bank:FED", 2026), y2027 = holidays(catalog, "central_bank:FED", 2027);
    CHECK(has(&y2026, "fed.fomc", date_of(2026, 3, 18), 0));    /* the announcement wins */
    CHECK(has(&y2027, "fed.fomc", date_of(2027, 3, 18), 1));    /* else the projection, marked */
    CHECK(has(&y2027, "fed.after", date_of(2027, 3, 18), 0));   /* the event closes nothing */
    occurrencelist_free(&y2026), occurrencelist_free(&y2027);
    /* a key without a rule holds announced dates only; a rule can refer to it */
    OccurrenceList xetr = holidays(catalog, "exchange:XETR", 2026), none = holidays(catalog, "exchange:XETR", 2027);
    CHECK(xetr.count == 1 && xetr.items[0].date == date_of(2026, 5, 8));
    CHECK(none.count == 0);
    occurrencelist_free(&xetr), occurrencelist_free(&none);
    catalog_free(catalog);
}

static void one_of_dates_are_projections_unless_announced(void) {
    int xsau[] = { date_of(2026, 3, 22) };
    Announcement announced[] = { { "xsau.eid", xsau, 1 } };
    RuleCatalog *catalog = parse(
        "rule sa.eid for eid_al_fitr in country:SA = ONE OF { 1 RAMADAN + 29D AS ramadan_29, 1 RAMADAN + 30D AS ramadan_30 };\n"
        "rule xsau.eid for eid_al_fitr in exchange:XSAU = ONE OF { 1 SHAWWAL AS uq, 1 SHAWWAL + 1D AS late };\n", announced, 1);
    OccurrenceList sa = holidays(catalog, "country:SA", 2026), list = holidays(catalog, "exchange:XSAU", 2026);
    CHECK(sa.count == 2);
    for (int i = 0; i < sa.count; i++) CHECK(sa.items[i].projected);
    CHECK(list.count == 1 && list.items[0].date == xsau[0] && !list.items[0].scenario);
    occurrencelist_free(&sa), occurrencelist_free(&list);
    catalog_free(catalog);
}

static void single_session_time_and_new_kinds(void) {
    RuleCatalog *catalog = parse(
        "rule rba.decision for rba_decision in central_bank:RBA = 3 TUE MAR with kind = event, session 14:30 tz \"Australia/Sydney\";\n"
        "rule xnse.muhurat for muhurat_session in exchange:XNSE = 1 NOV with kind = special_session;\n"
        "rule dsmd.ramadan for ramadan_session in exchange:DSMD = 1 MAR with kind = changed_hours, session 09:30 - 13:15;\n", NULL, 0);
    OccurrenceList rba = holidays(catalog, "central_bank:RBA", 2026), xnse = holidays(catalog, "exchange:XNSE", 2026),
        dsmd = holidays(catalog, "exchange:DSMD", 2026);
    CHECK(rba.count == 1 && !strcmp(rba.items[0].session[0], "14:30") && !strcmp(rba.items[0].session[1], "14:30")
          && !strcmp(rba.items[0].session[2], "Australia/Sydney"));
    CHECK(xnse.count && !strcmp(xnse.items[0].kind, "special_session"));
    CHECK(dsmd.count && !strcmp(dsmd.items[0].kind, "changed_hours") && !dsmd.items[0].session[2]);
    occurrencelist_free(&rba), occurrencelist_free(&xnse), occurrencelist_free(&dsmd);
    catalog_free(catalog);
}

int main(void) {
    occurrence_carries_kind_session_precision_and_citations();
    announcements_and_projections();
    announcements_are_data_and_kind_event();
    one_of_dates_are_projections_unless_announced();
    single_session_time_and_new_kinds();
    printf("%d rule catalog checks, %d passed, %d failed\n", checks, checks - failed, failed);
    return failed ? 1 : 0;
}
