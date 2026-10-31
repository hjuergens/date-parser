"""Generates examples/catalog.txt: test cases from the holiday rule catalog of the holidays-events project.

Each catalog rule that the schedule language can express is translated into a schedule, and the expected dates
come from the catalog's own rule engine (engine.py), so all ten parsers are checked against an independent
implementation. Run it with the environment of holidays-events:

    uv run --project ../holidays-events python scripts/catalog_cases.py ../holidays-events
"""
import sys
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

from holidays_events.catalog.loader import read_rules_dir
from holidays_events.rules import calendars as cal
from holidays_events.rules.engine import DEFAULT_WEEKEND, RuleEngine

YEARS = range(2020, 2031)
OUT = Path(__file__).resolve().parents[1] / "examples" / "catalog.txt"

MONTHS = "JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split()
WEEKDAYS = "MON TUE WED THU FRI SAT SUN".split()  # ISO 1..7
HIJRI = ("MUHARRAM SAFAR RABIALAWWAL RABIALTHANI JUMADAALULA JUMADAALAKHIRA RAJAB SHABAN RAMADAN SHAWWAL "
         "DHULQADAH DHULHIJJAH").split()
HEBREW = "NISAN IYAR SIVAN TAMMUZ AV ELUL TISHRI HESHVAN KISLEV TEVET SHEVAT ADAR ADARII".split()  # pyluach 1..13
CHINESE = "ZHENGYUE ERYUE SANYUE SIYUE WUYUE LIUYUE QIYUE BAYUE JIUYUE SHIYUE DONGYUE LAYUE".split()


class Skip(Exception):
    """the rule can't be written in the language (yet); the reason goes into the generated file"""


def days(n):
    return f" + {n}D" if n > 0 else f" - {-n}D" if n < 0 else ""


def span(expr, n):
    """span_days: the first day and the n - 1 days after it"""
    return expr if n == 1 else f"({expr} : 1D : {expr} + {n - 1}D)"


def translate(rule_type, p, engine, rule):
    """(schedule, market scope or None) for one rule version"""
    n = p.get("span_days", 1)
    if p.get("include_prev_day_30"):
        raise Skip("the day before counts only if the previous month has 30 days")
    match rule_type:
        case "fixed_gregorian":
            return span(f"{p['day']} {MONTHS[p['month'] - 1]}", n), None
        case "nth_weekday":
            return span(f"{p['n']} {WEEKDAYS[p['weekday'] - 1]} {MONTHS[p['month'] - 1]}", n), None
        case "last_weekday":
            return span(f"LAST {WEEKDAYS[p['weekday'] - 1]} {MONTHS[p['month'] - 1]}", n), None
        case "easter_offset":
            return span(f"EASTER{days(p['offset_days'])}", n), None
        case "observance_shift":
            shifts = ", ".join(f"{WEEKDAYS[w - 1]}{days(k)}" for w, k in sorted(p["shifts"].items()) if k)
            return span(f"{p['day']} {MONTHS[p['month'] - 1]} {{{shifts}}}", n), None
        case "hijri_fixed":
            if p["method"] == "follow_country":
                raise Skip("follows another country's sighting")
            # sighting: the most likely scenario is the Umm al-Qura date
            return span(f"{p['hijri_day']} {HIJRI[p['hijri_month'] - 1]}", n), None
        case "hebrew":
            d = f"{p['hebrew_day']} {HEBREW[p['hebrew_month'] - 1]}"
            return (f"({d} - 1D : 1D : {d})" if p.get("eve_closed") else span(d, n)), None
        case "chinese_lunisolar":
            return span(f"{p['lunar_day']} {CHINESE[p['lunar_month'] - 1]}{days(p.get('offset_days', 0))}", n), None
        case "duration_span":
            start, _ = translate(p["start"]["rule_type"], p["start"]["params"], engine, rule)
            end, _ = translate(p["end"]["rule_type"], p["end"]["params"], engine, rule)
            return f"({start} : 1D : {end})", None
        case "conditional":
            probe = p["condition"]["weekday_of"]
            then, _ = translate(p["then"]["rule_type"], p["then"]["params"], engine, rule)
            if p.get("else") or then != f"{probe['day']} {MONTHS[probe['month'] - 1]}":
                raise Skip("the condition tests another date than the result")
            return f"{then} IF {' '.join(WEEKDAYS[w - 1] for w in p['condition']['in'])}", None
        case "nth_weekday_prev_business":
            return ", ".join(f"{p['n']} {WEEKDAYS[p['weekday'] - 1]} {MONTHS[m - 1]} > PRECEDING"
                             for m in p["months"]), rule.scope
        case "last_business_day":
            return ", ".join(f"1 {MONTHS[m - 1]} > EOM > PRECEDING" for m in p["months"]), rule.scope
        case "monthly_day_next_business":
            if p["day"] > 28:
                raise Skip("the day doesn't exist in every month")
            return ", ".join(f"{p['day']} {m} > FOLLOWING" for m in MONTHS), rule.scope
        case "relative_to_event":
            anchor = engine.rules[p["rule"]]
            versions = engine._current_versions(anchor)
            if len(versions) != 1 or p.get("start") is not None:
                raise Skip("anchored to a rule with several versions, or a span before the anchor")
            if versions[0].rule_type == "announcement_only":
                raise Skip(f"anchored to {p['rule']}, whose dates are announced, not computed")
            expr, scope = translate(versions[0].rule_type, versions[0].params, engine, anchor)
            if "," in expr or "(" in expr:
                raise Skip("anchored to a rule with several dates")
            if p.get("offset_business_days") is not None:
                return f"{expr} + {p['offset_business_days']}B", rule.scope
            return f"{expr}{days(p.get('offset_days') or 0)}", scope
        case "announcement_only" | "hindu_lunisolar":
            raise Skip("announced by an authority, no rule")
    raise Skip(f"rule type {rule_type} not supported")


def market_name(scope):
    return "CAT_" + scope.split(":", 1)[1].replace("-", "_").upper()


def hijri_or_lunar_twice(expr, year):
    """a lunar date that occurs twice in the Gregorian year (Hijri months: about every 33 years) is an error"""
    words = expr.replace("(", " ").replace(")", " ").split()
    for i, w in enumerate(words):
        if w in HIJRI:
            day, month = int(words[i - 1]), HIJRI.index(w) + 1
            hits = [d for hy in cal.hijri_years_in(year) if (d := cal.hijri_to_gregorian(hy, month, day)) and d.year == year]
            if len(hits) != 1:
                return True
    return False


def main(project):
    engine = RuleEngine(read_rules_dir(Path(project) / "rules" / "catalog"))
    drafts, _ = engine.project(YEARS.start - 1, YEARS.stop)
    by_rule = defaultdict(list)
    for d in drafts:
        if d.scenario_prob is None or d.scenario_prob >= 0.5:  # the most likely scenario
            by_rule[(d.rule_key, d.local_start_date.year)].append(d)

    lines, skipped, markets = [], [], {}
    for rule in engine.catalog.rules:
        versions = engine._current_versions(rule)
        translated = {}
        try:
            for v in versions:
                translated[v.version] = translate(v.rule_type, v.params, engine, rule)
        except Skip as reason:
            skipped.append(f"# {rule.key}: {reason}")
            continue
        cases = []
        for year in YEARS:
            found = by_rule.get((rule.key, year), [])
            valid = [v for v in versions if (v.valid_from is None or v.valid_from <= date(year, 12, 31))
                     and (v.valid_to is None or v.valid_to > date(year, 1, 1))]
            if len(valid) != 1 or (valid[0].valid_from and valid[0].valid_from > date(year, 1, 1)
                                   and not found):
                continue  # no version or a change of version within the year
            expr, scope = translated[valid[0].version]
            if any(d.local_start_date.year != d.local_end_date.year for d in found):
                continue  # a span across the turn of the year
            prefix = f"{year}-01-01"
            if scope:
                cal_ = engine.calendar(scope)
                if any(r[0] != DEFAULT_WEEKEND for r in cal_.regimes):
                    continue
                if cal_.closures:  # otherwise Saturdays and Sundays only, as without a market
                    markets[market_name(scope)] = cal_.closures
                    prefix += f" {market_name(scope)}"
            if hijri_or_lunar_twice(expr, year):
                expected = "error"
            else:
                dates = sorted({d.local_start_date + timedelta(days=i) for d in found
                                for i in range((d.local_end_date - d.local_start_date).days + 1)})
                expected = ", ".join(map(str, dates)) or "none"
            cases.append(f"[{prefix}] {expr} => {expected}")
        if cases:
            lines += [f"# {rule.key} ({', '.join(v.rule_type for v in versions)})", *cases, ""]

    header = [
        "# Generated by scripts/catalog_cases.py from the rule catalog of holidays-events; don't edit.",
        "# Expected dates come from the catalog's rule engine; a schedule is evaluated with the first of January",
        f"# of each year from {YEARS.start} to {YEARS.stop - 1} as base date. Lunar dates take the most likely scenario",
        "# (Umm al-Qura for a sighted Hijri month).",
        "",
    ]
    market_lines = [f"market {name} = {', '.join(str(d) for d in sorted(c) if YEARS.start - 1 <= d.year <= YEARS.stop)}"
                    for name, c in sorted(markets.items())]
    text = "\n".join(header + market_lines + [""] + lines
                     + ["# rules that the language can't express (yet):", *skipped]) + "\n"
    OUT.write_text(text, encoding="utf-8")
    print(f"{sum(1 for l in lines if l.startswith('['))} cases from {sum(1 for l in lines if l.startswith('# '))} rules, "
          f"{len(skipped)} rules skipped -> {OUT}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "../holidays-events")
