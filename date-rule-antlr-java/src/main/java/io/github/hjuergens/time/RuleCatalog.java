package io.github.hjuergens.time;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github.hjuergens.time.ScheduleSemantics.Anchor;
import io.github.hjuergens.time.ScheduleSemantics.Annual;
import io.github.hjuergens.time.ScheduleSemantics.Context;
import io.github.hjuergens.time.ScheduleSemantics.Expr;
import io.github.hjuergens.time.ScheduleSemantics.Market;
import io.github.hjuergens.time.ScheduleSemantics.Rule;

/**
 * The evaluator of rule files, without a parser: copied unchanged (but for the package) into every JVM module that
 * parses rule files; the Java port of catalog.py (date-rule-lark). The parser builds the declarations with the static
 * functions of this class and gives them to the constructor.
 *
 * <p>{@code holidays(scope, year)} evaluates the rules of a scope for a year in two phases: each rule on its own, then,
 * for the rules that read the holidays of their own scope (a business day, a roll or a holiday condition without ON, or
 * ON the own scope), passes in the order of the nominal dates until nothing changes; a rule sees the dates of the other
 * rules, never its own. {@code evaluate(key, base, scope)} evaluates a rule with a base date, for trade-date rules such
 * as spot; {@code schedule(text)} parses one expression for {@code context(base, scope)}.
 *
 * <p>Not computed here: HINDU (no Hindu calendar library) is an error at evaluation; the Hijri methods other than
 * UMMALQURA are projected with Umm al-Qura and marked as projections.
 *
 * <p>Rules only project. Announcements are data, not grammar: the dates an authority published for a key and year
 * replace the dates of the rule with that key for that year, and in the other years its dates are projections. A key
 * without a rule (an AGM, which no rule can project) holds only announced dates, and other rules can refer to it with
 * {@code @key}.
 *
 * <p>The context of a rule is a {@link Context} of ScheduleSemantics whose events are {@link References}: they carry
 * the catalog and the scope, which ON needs.
 */
public final class RuleCatalog {

    static final Set<String> CLOSED = Set.of("closed", "non_working");   // kinds that close a day; working_day opens one
    static final int MAX_PASSES = 8;
    private static final List<String> WEEKDAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final List<String> HIJRI = List.of("MUHARRAM", "SAFAR", "RABIALAWWAL", "RABIALTHANI", "JUMADAALULA",
            "JUMADAALAKHIRA", "RAJAB", "SHABAN", "RAMADAN", "SHAWWAL", "DHULQADAH", "DHULHIJJAH");

    private static String weekday(LocalDate date) {
        return WEEKDAYS.get(date.getDayOfWeek().getValue() - 1);
    }

    // ================= the building blocks =================

    /** a shift of the rule language: a function of date and context */
    public interface Move {
        LocalDate apply(LocalDate date, Context context);
    }

    /** a condition of IF */
    public interface Condition {
        boolean test(LocalDate date, Context context);
    }

    /** a calendar of ON or of a calendar declaration */
    public sealed interface CalendarSpec permits Scope, Named, Combined {
    }

    /** a scope's calendar; ignore &gt; 0: it does not count at T + ignore unless the pair has one of the currencies */
    public record Scope(String scope, int ignore, Set<String> unless) implements CalendarSpec {
    }

    /** a declared calendar */
    public record Named(String key) implements CalendarSpec {
    }

    /** A &amp; B: a business day in every term */
    public record Combined(List<CalendarSpec> terms) implements CalendarSpec {
    }

    public record IfClause(Condition condition, List<Move> moves) {
    }

    /**
     * &gt; POSTPONE FOLLOWING ... MAX 14D [CUMULATIVE]: rolled, but at most the period from the date (with CUMULATIVE
     * from the date before the first postponement of the expression)
     */
    public record Postpone(String convention, CalendarSpec on, TemporalAmount limit, boolean cumulative) implements Move {

        @Override
        public LocalDate apply(LocalDate date, Context context) {
            return apply(date, context, date);
        }

        LocalDate apply(LocalDate date, Context context, LocalDate origin) {
            LocalDate target = roll(date, convention, market(on, context, null));
            if (target.isAfter(date)) {
                LocalDate limited = ScheduleSemantics.plus(origin, limit, 1, context.market());
                return target.isBefore(limited) ? target : limited;
            }
            LocalDate limited = ScheduleSemantics.plus(origin, limit, -1, context.market());
            return target.isAfter(limited) ? target : limited;
        }
    }

    public record Citation(String source, String article, String url, String label) {
    }

    /**
     * ONE OF {item AS name, ...}: alternatives of which exactly one will happen; their dates are projections, each under
     * its scenario name, and together they are the rule's dates for the other rules (references, calendars)
     */
    public record OneOf(Map<String, Rule> alternatives) {

        public List<LocalDate> union(Context context) {
            List<LocalDate> dates = new ArrayList<>();
            alternatives.values().forEach(expr -> dates.addAll(expr.evaluate(context)));
            return dates;
        }
    }

    /** weekend SCOPE = {FRI, SAT} [from DATE] */
    public record WeekendDecl(String scope, Set<String> weekdays, LocalDate validFrom) {
    }

    /** calendar KEY = ... */
    public record CalendarDecl(String key, CalendarSpec spec) {
    }

    /** a rule declaration; equal only to itself, as two versions may look alike */
    public static final class RuleDecl {
        public final String key;
        public final String eventClass;
        public final List<String> include;
        public final List<String> exclude;
        public final LocalDate validFrom;
        public final LocalDate validTo;
        public final Rule expr;
        public final Map<String, Object> attributes;
        public final List<Citation> citations;
        public final Set<String> references;
        public final boolean estimated;
        public final Map<String, Rule> alternatives;   // (scenario name, exprs) of ONE OF

        RuleDecl(String key, String eventClass, List<String> include, List<String> exclude, LocalDate validFrom,
                 LocalDate validTo, Rule expr, Map<String, Object> attributes, List<Citation> citations,
                 Set<String> references, boolean estimated, Map<String, Rule> alternatives) {
            this.key = key;
            this.eventClass = eventClass;
            this.include = include;
            this.exclude = exclude;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.expr = expr;
            this.attributes = attributes;
            this.citations = citations;
            this.references = references;
            this.estimated = estimated;
            this.alternatives = alternatives;
        }

        public String kind() {
            return (String) attributes.getOrDefault("kind", "closed");
        }

        public boolean valid(LocalDate date) {
            return (validFrom == null || !date.isBefore(validFrom)) && (validTo == null || !date.isAfter(validTo));
        }

        boolean inYear(int year) {
            return valid(LocalDate.of(year, 12, 31)) || valid(LocalDate.of(year, 1, 1))
                    || validFrom != null && validFrom.getYear() == year;
        }

        @Override
        public String toString() {
            return "rule " + key;
        }
    }

    /**
     * a date with the rule that produced it: its kind, precision, session (start, end, time zone) and citations;
     * projected when it comes from a projection (a Hijri date by sighting, a rule with announced dates in a year without
     * them); scenario is the name of the ONE OF alternative the date belongs to
     */
    public record Occurrence(LocalDate date, String rule, String eventClass, String kind, String precision,
                             List<String> session, List<Citation> citations, boolean projected, String scenario) {
    }

    // ================= markets that answer more than business days =================

    /** a holiday of the market: closed although not a weekend day */
    static boolean isHoliday(Market market, LocalDate date) {
        if (market instanceof Open) {
            return false;
        }
        if (market instanceof ScopeCalendar calendar) {
            return calendar.isHoliday(date);
        }
        if (market instanceof CombinedCalendar calendar) {
            return calendar.isHoliday(date);
        }
        return date.getDayOfWeek().getValue() < 6 && !market.isBusinessDay(date);
    }

    /** every day a business day: the calendar for the nominal dates, which give the order of the passes */
    static final class Open implements Market {
        @Override
        public boolean isBusinessDay(LocalDate date) {
            return true;
        }
    }

    /**
     * the weekend regime of a scope and the dates of its rules, from the evaluations of the year of a date and of the
     * years before and after (an observed holiday can fall into the neighbouring year). During the evaluation of a year
     * (home) skip leaves out the rule being evaluated, the other years are read shallow (evaluated with their own year
     * only), and a shallow calendar reads its home year only, so no evaluation starts a chain of years
     */
    static final class ScopeCalendar implements Market {
        private final RuleCatalog catalog;
        private final String scope;
        private final RuleDecl skip;
        private final int skipYear;
        private final Integer home;
        private final boolean shallow;

        ScopeCalendar(RuleCatalog catalog, String scope, RuleDecl skip, int skipYear, Integer home, boolean shallow) {
            this.catalog = catalog;
            this.scope = scope;
            this.skip = skip;
            this.skipYear = skipYear;
            this.home = home;
            this.shallow = shallow;
        }

        /** {closed, opened} */
        private boolean[] days(LocalDate date) {
            boolean closed = false, opened = false;
            for (int year = date.getYear() - 1; year <= date.getYear() + 1; year++) {
                if (shallow && year != home) {
                    continue;
                }
                Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>> state =
                        catalog.state(scope, year, home != null && year != home);
                for (Map.Entry<RuleDecl, Map.Entry<List<LocalDate>, Boolean>> entry : state.entrySet()) {
                    RuleDecl rule = entry.getKey();
                    if (rule == skip && year == skipYear || !entry.getValue().getKey().contains(date)) {
                        continue;
                    }
                    if (CLOSED.contains(rule.kind())) {
                        closed = true;
                    } else if (rule.kind().equals("working_day")) {
                        opened = true;
                    }
                }
            }
            return new boolean[] {closed, opened};
        }

        boolean isHoliday(LocalDate date) {
            return days(date)[0];
        }

        @Override
        public boolean isBusinessDay(LocalDate date) {
            boolean[] days = days(date);
            if (days[1]) {
                return true;
            }
            return !catalog.weekend(scope, date).contains(weekday(date)) && !days[0];
        }
    }

    /**
     * A &amp; B: a business day in every term; a term with ignore does not count on the ignore-th business day after T
     * of the other terms, unless the currency pair of the evaluation contains one of its currencies
     */
    static final class CombinedCalendar implements Market {
        private final List<Market> markets;
        private final Set<String> ignored = new HashSet<>();   // "term date"

        /** ignores: per term the scope term with ignore, or null */
        CombinedCalendar(List<Market> markets, List<Scope> ignores, LocalDate base, String pair) {
            this.markets = markets;
            for (int i = 0; i < markets.size(); i++) {
                Scope ignore = ignores.get(i);
                if (ignore != null && base != null
                        && !(pair != null && ignore.unless().stream().anyMatch(pair::contains))) {
                    List<Market> others = new ArrayList<>(markets);
                    others.remove(i);
                    LocalDate day = base;
                    for (int k = 0; k < ignore.ignore(); k++) {
                        day = nextDay(day, x -> others.stream().allMatch(m -> m.isBusinessDay(x)), 1);
                    }
                    ignored.add(i + " " + day);
                }
            }
        }

        @Override
        public boolean isBusinessDay(LocalDate date) {
            for (int i = 0; i < markets.size(); i++) {
                if (!ignored.contains(i + " " + date) && !markets.get(i).isBusinessDay(date)) {
                    return false;
                }
            }
            return true;
        }

        boolean isHoliday(LocalDate date) {
            return markets.stream().anyMatch(m -> RuleCatalog.isHoliday(m, date));
        }
    }

    /** the first day strictly after (before, direction -1) the date that passes the test, within 3700 days */
    static LocalDate nextDay(LocalDate date, Predicate<LocalDate> test, int direction) {
        for (int i = 0; i < 3700; i++) {
            date = date.plusDays(direction);
            if (test.test(date)) {
                return date;
            }
        }
        throw new IllegalArgumentException("no such day within 3700 days of " + date);
    }

    // ================= the context of a rule =================

    /** the calendar of ON ...; without ON the market of the context (in a rule: its own scope) */
    static Market market(CalendarSpec on, Context context, String pair) {
        if (on == null) {
            return context.market();
        }
        if (!(context.events() instanceof References references)) {
            throw new IllegalArgumentException("ON needs a rule catalog");
        }
        return references.catalog.resolve(on, context, references.scope, pair);
    }

    /**
     * the events of a rule's context: @key, the dates of a rule (by key), of the rules of an event class or of a key
     * with announced dates only, in the year of the base date; during the evaluation of a year (home) other years are
     * read shallow, or not at all from a shallow evaluation. They carry the catalog and the scope of the context.
     */
    static final class References extends AbstractMap<String, Rule> {
        private final RuleCatalog catalog;
        private final String scope;
        private final Integer home;
        private final boolean shallow;

        References(RuleCatalog catalog, String scope, Integer home, boolean shallow) {
            this.catalog = catalog;
            this.scope = scope;
            this.home = home;
            this.shallow = shallow;
        }

        @Override
        public boolean containsKey(Object name) {
            return name instanceof String key
                    && (!catalog.referenced(key).isEmpty() || catalog.announcements.containsKey(key));
        }

        @Override
        public Rule get(Object name) {
            if (!containsKey(name)) {
                return null;
            }
            String key = (String) name;
            return context -> {
                int year = ScheduleSemantics.BASE.at(context).getYear();
                if (shallow && year != home) {
                    return List.of();
                }
                return catalog.referenceDates(key, scope, year, home != null && year != home);
            };
        }

        @Override
        public Set<Entry<String, Rule>> entrySet() {
            return Set.of();
        }
    }

    // ================= dates of the rule language =================

    /** the conventions of the schedule language and MODPRECEDING (preceding unless that leaves the month) */
    static LocalDate roll(LocalDate date, String convention, Market market) {
        if (convention.equals("MODPRECEDING")) {
            LocalDate preceding = ScheduleSemantics.roll(date, "PRECEDING", market);
            return preceding.getMonth() == date.getMonth() ? preceding : ScheduleSemantics.roll(date, "FOLLOWING", market);
        }
        return ScheduleSemantics.roll(date, convention, market);
    }

    /** Orthodox Easter Sunday: the Julian computus (Meeus), converted to the Gregorian calendar */
    public static final Annual ORTHODOX_EASTER = year -> {
        int a = year % 4, b = year % 7, c = year % 19;
        int d = (19 * c + 15) % 30;
        int e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31, day = (d + e + 114) % 31;
        int k = (14 - month) / 12;
        int y = year + 4800 - k, m = month + 12 * k - 3;
        long jdn = day + 1 + (153 * m + 2) / 5 + 365L * y + y / 4 - 32083;
        return LocalDate.ofEpochDay(jdn - 2440588);
    };

    // ================= building the rules: the parser calls these =================

    /** a date expression: the start, then the moves from left to right */
    public static Expr shifted(Expr start, List<Move> moves) {
        List<Move> copy = List.copyOf(moves);
        return context -> applyShifts(start.at(context), copy, context);
    }

    /** an item with its IF clause (or null): the date if it meets the condition, then the moves of the clause */
    public static Expr ruleItem(Expr expr, IfClause clause) {
        if (clause == null) {
            return expr;
        }
        return context -> {
            LocalDate date = expr.at(context);
            if (date == null || !clause.condition().test(date, context)) {
                return null;
            }
            return applyShifts(date, clause.moves(), context);
        };
    }

    /** a list of items: all their dates, in order */
    public static Rule exprs(List<Expr> items) {
        return ScheduleSemantics.list(items);
    }

    /** the date of an expression that must have exactly one */
    static LocalDate single(Expr expr, Context context) {
        List<LocalDate> dates = ScheduleSemantics.list(List.of(expr)).evaluate(context);
        if (dates.size() != 1) {
            throw new IllegalArgumentException(dates.size() + " dates where one is needed");
        }
        return dates.get(0);
    }

    // conditions

    public static Condition anyCondition(List<Condition> parts) {
        List<Condition> copy = List.copyOf(parts);
        return (date, context) -> copy.stream().anyMatch(p -> p.test(date, context));
    }

    public static Condition allConditions(List<Condition> parts) {
        List<Condition> copy = List.copyOf(parts);
        return (date, context) -> copy.stream().allMatch(p -> p.test(date, context));
    }

    public static Condition notCondition(Condition part) {
        return (date, context) -> !part.test(date, context);
    }

    public static Condition onWeekday(Set<String> weekdays) {
        return (date, context) -> weekdays.contains(weekday(date));
    }

    public static Condition onDay(int first, int last, String month) {
        List<String> days = ScheduleSemantics.days(first, last, month);
        return (date, context) -> ScheduleSemantics.onlyOn(ScheduleSemantics.literal(date), days).at(context) != null;
    }

    public static Condition isWeekday(Expr subject, Set<String> weekdays) {
        return (date, context) -> weekdays.contains(weekday(single(subject, context)));
    }

    public static Condition holidayCondition(CalendarSpec on) {
        return (date, context) -> isHoliday(market(on, context, null), date);
    }

    public static Condition dateIsHoliday(Expr subject, CalendarSpec on) {
        return (date, context) -> isHoliday(market(on, context, null), single(subject, context));
    }

    public static Condition betweenHolidays(CalendarSpec on) {
        return (date, context) -> {
            Market market = market(on, context, null);
            return isHoliday(market, date.minusDays(1)) && isHoliday(market, date.plusDays(1));
        };
    }

    public static Condition every(TemporalAmount period, int first) {
        if (!(period instanceof Period p) || p.getDays() != 0 || p.toTotalMonths() <= 0 || p.toTotalMonths() % 12 != 0) {
            throw new IllegalArgumentException("EVERY needs whole years: EVERY 4Y FROM 1965");
        }
        long years = p.toTotalMonths() / 12;
        return (date, context) -> date.getYear() >= first && (date.getYear() - first) % years == 0;
    }

    // dates

    public static Expr spotDate(String pair, CalendarSpec on) {
        return context -> market(on, context, pair).spot(ScheduleSemantics.BASE.at(context), pair);
    }

    /** 1 SHAWWAL BY SIGHTING: the method only for Hijri months; all are computed with Umm al-Qura */
    public static Annual monthDay(int day, String month, String method) {
        if (method != null && !HIJRI.contains(month)) {
            throw new IllegalArgumentException("BY " + method + " only for Hijri months, not " + month);
        }
        return ScheduleSemantics.monthDay(day, month);
    }

    public static Annual hindu(String key) {
        return year -> {
            throw new IllegalArgumentException("HINDU " + key + ": no Hindu calendar in this implementation");
        };
    }

    /** an astronomical annual date in UTC, a fixed offset (UTC+9) or an IANA time zone ("Asia/Tokyo") */
    public static Annual season(String kind, String month, String zone) {
        List<String> months = List.of("MAR", "JUN", "SEP", "DEC");
        if (!months.contains(month) || (months.indexOf(month) % 2 == 0) != kind.equals("EQUINOX")) {
            throw new IllegalArgumentException("no " + kind + " in " + month);
        }
        if (zone != null && !zone.startsWith("UTC")) {
            return ScheduleSemantics.season(kind, month, ZoneId.of(zone));
        }
        return ScheduleSemantics.season(kind, month, zone == null ? 0 : ScheduleSemantics.zone(zone));
    }

    public static Annual solarTerm(int term, String zone) {
        if (term < 1 || term > 24) {
            throw new IllegalArgumentException("no solar term " + term);
        }
        if (zone != null && !zone.startsWith("UTC")) {
            return ScheduleSemantics.solarTerm(term, ZoneId.of(zone));
        }
        return ScheduleSemantics.solarTerm(term, zone == null ? 0 : ScheduleSemantics.zone(zone));
    }

    // moves

    /** + 1M [ENDEND] [ON ...]: with ENDEND the last business day of a month goes to the last one of the target month */
    public static Move plus(TemporalAmount period, int sign, boolean endend, CalendarSpec on) {
        return (date, context) -> {
            Market market = market(on, context, null);
            LocalDate moved = ScheduleSemantics.plus(date, period, sign, market, context.fixing());
            if (endend && date.equals(lastBusinessDay(date, market))) {
                return lastBusinessDay(moved, market);
            }
            return moved;
        };
    }

    public static Move spot(String pair, CalendarSpec on) {
        return (date, context) -> market(on, context, pair).spot(date, pair);
    }

    public static Move rollShift(String convention, CalendarSpec on) {
        return (date, context) -> roll(date, convention, market(on, context, null));
    }

    public static Move nearestShift(Anchor anchor) {
        return (date, context) -> nearest(anchor, date, context);
    }

    /** {SAT - 1D, TUE WED &gt; PREV MON}: the first entry for the weekday of the date moves it */
    public static Move observance(List<Map.Entry<Set<String>, Move>> entries) {
        List<Map.Entry<Set<String>, Move>> copy = List.copyOf(entries);
        return (date, context) -> {
            for (Map.Entry<Set<String>, Move> entry : copy) {
                if (entry.getKey().contains(weekday(date))) {
                    return entry.getValue().apply(date, context);
                }
            }
            return date;
        };
    }

    public static Map.Entry<Set<String>, Move> observePlus(Set<String> weekdays, TemporalAmount period, int sign) {
        return Map.entry(Set.copyOf(weekdays),
                (date, context) -> ScheduleSemantics.plus(date, period, sign, context.market(), context.fixing()));
    }

    public static Map.Entry<Set<String>, Move> observeAnchor(Set<String> weekdays, Anchor anchor, int direction) {
        return Map.entry(Set.copyOf(weekdays), (date, context) -> anchor.from(date, direction, context));
    }

    public static Map.Entry<Set<String>, Move> observeNearest(Set<String> weekdays, Anchor anchor) {
        return Map.entry(Set.copyOf(weekdays), (date, context) -> nearest(anchor, date, context));
    }

    public static Map.Entry<Set<String>, Move> observeRoll(Set<String> weekdays, String convention, CalendarSpec on) {
        return Map.entry(Set.copyOf(weekdays), (date, context) -> roll(date, convention, market(on, context, null)));
    }

    // anchors

    public static Anchor businessDay(CalendarSpec on) {
        return (date, direction, context) -> ScheduleSemantics.plusBusinessDays(date, direction, market(on, context, null));
    }

    public static Anchor holiday(CalendarSpec on) {
        return (date, direction, context) -> {
            Market market = market(on, context, null);
            return nextDay(date, x -> isHoliday(market, x), direction);
        };
    }

    public static Anchor nonholiday(CalendarSpec on) {
        return (date, direction, context) -> {
            Market market = market(on, context, null);
            return nextDay(date, x -> !isHoliday(market, x), direction);
        };
    }

    // declarations

    public static CalendarSpec calendar(List<CalendarSpec> terms) {
        return terms.size() == 1 ? terms.get(0) : new Combined(List.copyOf(terms));
    }

    /** alternatives: (name, exprs) pairs; a name only once */
    public static OneOf oneOf(List<Map.Entry<String, Rule>> alternatives) {
        Map<String, Rule> named = new LinkedHashMap<>();
        for (Map.Entry<String, Rule> alternative : alternatives) {
            if (named.put(alternative.getKey(), alternative.getValue()) != null) {
                throw new IllegalArgumentException("ONE OF: name " + alternative.getKey() + " twice");
            }
        }
        return new OneOf(Collections.unmodifiableMap(named));
    }

    /** a rule declaration; body is a Rule or a OneOf, attributes (name, value) pairs, references the @keys */
    public static RuleDecl rule(String key, String eventClass, List<String> include, List<String> exclude,
                                LocalDate validFrom, LocalDate validTo, Object body, List<Map.Entry<String, Object>> attributes,
                                List<Citation> citations, Set<String> references, boolean estimated) {
        Map<String, Rule> alternatives = Map.of();
        Rule expr;
        if (body instanceof OneOf oneOf) {
            alternatives = oneOf.alternatives();
            expr = oneOf::union;
        } else {
            expr = (Rule) body;
        }
        Map<String, Object> named = new LinkedHashMap<>();
        attributes.forEach(attribute -> named.put(attribute.getKey(), attribute.getValue()));
        return new RuleDecl(key, eventClass, List.copyOf(include), List.copyOf(exclude), validFrom, validTo, expr, named,
                List.copyOf(citations), Set.copyOf(references), estimated || !alternatives.isEmpty(), alternatives);
    }

    public static WeekendDecl weekend(String scope, Set<String> weekdays, LocalDate validFrom) {
        return new WeekendDecl(scope, Set.copyOf(weekdays), validFrom == null ? LocalDate.MIN : validFrom);
    }

    public static CalendarDecl calendarDeclaration(String key, CalendarSpec spec) {
        return new CalendarDecl(key, spec);
    }

    /** session 09:30 - 13:00, or a single time such as a release or a decision: session 14:30 (start = end) */
    public static Map.Entry<String, Object> session(String start, String end, String tz) {
        return Map.entry("session", Arrays.asList(start, end == null ? start : end, tz));
    }

    public static String time(int hours, int minutes) {
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * the moves from left to right; a postponement is limited from the date before it (CUMULATIVE: before the first
     * postponement)
     */
    static LocalDate applyShifts(LocalDate date, List<Move> moves, Context context) {
        LocalDate origin = null;
        for (Move move : moves) {
            if (move instanceof Postpone postpone) {
                origin = origin == null ? date : origin;
                date = postpone.apply(date, context, postpone.cumulative() ? origin : date);
            } else {
                date = move.apply(date, context);
            }
        }
        return date;
    }

    /** the nearer of the next and the previous match; ties go forward */
    static LocalDate nearest(Anchor anchor, LocalDate date, Context context) {
        LocalDate after = anchor.from(date, 1, context), before = anchor.from(date, -1, context);
        return ChronoUnit.DAYS.between(date, after) <= ChronoUnit.DAYS.between(before, date) ? after : before;
    }

    /** the last business day of the month of the date */
    static LocalDate lastBusinessDay(LocalDate date, Market market) {
        return ScheduleSemantics.roll(ScheduleSemantics.roll(date, "EOM", market), "PRECEDING", market);
    }

    /** country:AU contains country:AU and region:AU-NSW */
    static boolean contains(String outer, String inner) {
        return outer.equals(inner) || inner != null
                && inner.substring(inner.indexOf(':') + 1).startsWith(outer.substring(outer.indexOf(':') + 1) + "-");
    }

    // ================= the rule catalog =================

    private final Function<String, Rule> parse;   // text -> expression, for schedule()
    public final List<RuleDecl> rules = new ArrayList<>();
    private final Map<String, List<WeekendDecl>> weekends = new HashMap<>();
    private final Map<String, CalendarSpec> calendars = new HashMap<>();
    private final Map<String, List<LocalDate>> announcements = new HashMap<>();
    private final Map<String, Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>>> states = new HashMap<>();
    private final Map<String, Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>>> shallowStates = new HashMap<>();
    private final Map<String, Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>>> running = new HashMap<>();

    /**
     * the declarations of a rule file (RuleDecl, WeekendDecl, CalendarDecl); announcements maps a key to the dates an
     * authority published for it: a rule's key, or a key without a rule that @key reads
     *
     * @throws IllegalArgumentException on overlapping versions, an unknown or a cyclic reference
     */
    public RuleCatalog(List<?> declarations, Map<String, List<LocalDate>> announcements, Function<String, Rule> parse) {
        this.parse = parse;
        for (Object declaration : declarations) {
            if (declaration instanceof RuleDecl rule) {
                rules.add(rule);
            } else if (declaration instanceof WeekendDecl weekend) {
                weekends.computeIfAbsent(weekend.scope(), s -> new ArrayList<>()).add(weekend);
            } else if (declaration instanceof CalendarDecl calendar) {
                if (calendars.putIfAbsent(calendar.key(), calendar.spec()) != null) {
                    throw new IllegalArgumentException("calendar " + calendar.key() + " declared twice");
                }
            }
        }
        weekends.values().forEach(regimes -> regimes.sort(Comparator.comparing(WeekendDecl::validFrom)));
        if (announcements != null) {
            announcements.forEach((key, dates) -> this.announcements.put(key, dates.stream().sorted().toList()));
        }
        checkVersions();
        checkReferences();
    }

    // ---------- checks at load ----------

    private void checkVersions() {
        for (RuleDecl a : rules) {
            for (RuleDecl b : rules) {
                if (a != b && a.key.equals(b.key)
                        && !Objects.requireNonNullElse(a.validFrom, LocalDate.MIN).isAfter(Objects.requireNonNullElse(b.validTo, LocalDate.MAX))
                        && !Objects.requireNonNullElse(b.validFrom, LocalDate.MIN).isAfter(Objects.requireNonNullElse(a.validTo, LocalDate.MAX))) {
                    throw new IllegalArgumentException("rule " + a.key + ": versions overlap in time");
                }
            }
        }
    }

    List<RuleDecl> referenced(String name) {
        return rules.stream().filter(r -> r.key.equals(name) || r.eventClass.equals(name)).toList();
    }

    private void checkReferences() {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (RuleDecl rule : rules) {
            Set<String> targets = graph.computeIfAbsent(rule.key, k -> new LinkedHashSet<>());
            for (String name : rule.references) {
                List<RuleDecl> found = referenced(name);
                if (found.isEmpty() && !announcements.containsKey(name)) {
                    throw new IllegalArgumentException("rule " + rule.key + ": no rule, event class or announced key " + name);
                }
                found.forEach(r -> targets.add(r.key));
            }
        }
        Set<String> done = new HashSet<>();
        for (String key : graph.keySet()) {
            visit(key, graph, done, new ArrayList<>());
        }
    }

    private static void visit(String key, Map<String, Set<String>> graph, Set<String> done, List<String> path) {
        if (path.contains(key)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(key), path.size()));
            cycle.add(key);
            throw new IllegalArgumentException("cyclic references: " + String.join(" -> ", cycle));
        }
        if (done.contains(key)) {
            return;
        }
        path.add(key);
        for (String target : graph.getOrDefault(key, Set.of())) {
            visit(target, graph, done, path);
        }
        path.remove(path.size() - 1);
        done.add(key);
    }

    // ---------- scopes and calendars ----------

    public boolean applies(RuleDecl rule, String scope) {
        return rule.include.stream().anyMatch(s -> contains(s, scope)) && rule.exclude.stream().noneMatch(s -> contains(s, scope));
    }

    /** the weekend days of the scope on the date: its own regime, else that of the scope containing it, else SAT SUN */
    public Set<String> weekend(String scope, LocalDate date) {
        List<String> candidates = new ArrayList<>(weekends.keySet());
        candidates.sort(Comparator.comparingInt(String::length).reversed());
        for (String candidate : candidates) {
            if (contains(candidate, scope)) {
                Set<String> found = null;
                for (WeekendDecl regime : weekends.get(candidate)) {
                    if (!regime.validFrom().isAfter(date)) {
                        found = regime.weekdays();
                    }
                }
                if (found != null) {
                    return found;
                }
            }
        }
        return Set.of("SAT", "SUN");
    }

    /** the calendar of a scope, for contexts of the schedule language */
    public Market calendar(String scope) {
        return new ScopeCalendar(this, scope, null, 0, null, false);
    }

    Market resolve(CalendarSpec spec, Context context, String scope, String pair) {
        if (spec instanceof Named named) {
            CalendarSpec declared = calendars.get(named.key());
            if (declared == null) {
                throw new IllegalArgumentException("no calendar " + named.key());
            }
            return resolve(declared, context, scope, pair);
        }
        if (spec instanceof Scope s && s.ignore() == 0) {
            return s.scope().equals(scope) ? context.market() : calendar(s.scope());
        }
        List<CalendarSpec> terms = spec instanceof Combined combined ? combined.terms() : List.of(spec);
        List<Market> markets = new ArrayList<>();
        List<Scope> ignores = new ArrayList<>();
        for (CalendarSpec term : terms) {
            if (term instanceof Scope s) {
                markets.add(resolve(new Scope(s.scope(), 0, Set.of()), context, scope, null));
                ignores.add(s.ignore() > 0 ? s : null);
            } else {
                markets.add(resolve(term, context, scope, pair));
                ignores.add(null);
            }
        }
        return new CombinedCalendar(markets, ignores, context.base(), pair);
    }

    List<LocalDate> announcement(String key, int year) {
        return announcements.getOrDefault(key, List.of()).stream().filter(d -> d.getYear() == year).toList();
    }

    // ---------- evaluation ----------

    private Context context(RuleDecl rule, String scope, int year, boolean shallow, Market market) {
        return new Context(LocalDate.of(year, 1, 1),
                market != null ? market : new ScopeCalendar(this, scope, rule, year, year, shallow),
                new References(this, scope, year, shallow));
    }

    /** (dates, projected) of the rule evaluated in the year; an announcement for the year always wins */
    private Map.Entry<List<LocalDate>, Boolean> evaluate(RuleDecl rule, String scope, int year, boolean shallow, Market market) {
        List<LocalDate> published = announcement(rule.key, year);
        if (!published.isEmpty()) {
            return Map.entry(published.stream().filter(rule::valid).toList(), false);
        }
        TreeSet<LocalDate> dates = new TreeSet<>();
        rule.expr.evaluate(context(rule, scope, year, shallow, market)).stream().filter(rule::valid).forEach(dates::add);
        return Map.entry(List.copyOf(dates), rule.estimated || announcements.containsKey(rule.key));
    }

    /** rule -&gt; (dates, projected) of the rules of the scope evaluated with the first of January of the year */
    Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>> state(String scope, int year, boolean shallow) {
        String key = scope + " " + year;
        Map<String, Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>>> cache = shallow ? shallowStates : states;
        if (states.containsKey(key)) {
            return states.get(key);
        }
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        if (running.containsKey(key)) {
            return running.get(key);
        }
        List<RuleDecl> found = rules.stream().filter(r -> applies(r, scope) && r.inYear(year)).toList();
        Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>> state = new LinkedHashMap<>();
        found.forEach(r -> state.put(r, Map.entry(List.of(), r.estimated || announcements.containsKey(r.key))));
        running.put(key, state);
        try {
            List<LocalDate> nominal = found.stream().map(r -> nominal(r, scope, year)).toList();
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < found.size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparing(nominal::get));   // stable: ties in the order written
            boolean settled = false;
            for (int pass = 0; pass < MAX_PASSES && !settled; pass++) {
                settled = true;
                for (int i : order) {
                    Map.Entry<List<LocalDate>, Boolean> result = evaluate(found.get(i), scope, year, shallow, null);
                    if (!result.equals(state.get(found.get(i)))) {
                        state.put(found.get(i), result);
                        settled = false;
                    }
                }
            }
            if (!settled) {
                throw new IllegalArgumentException("the rules of " + scope + " in " + year + " do not settle in "
                        + MAX_PASSES + " passes");
            }
        } finally {
            running.remove(key);
        }
        cache.put(key, state);
        return state;
    }

    /** the first date of the rule with every day open, which orders the passes; LocalDate.MAX if it needs more */
    private LocalDate nominal(RuleDecl rule, String scope, int year) {
        try {
            List<LocalDate> dates = evaluate(rule, scope, year, true, new Open()).getKey();
            return dates.isEmpty() ? LocalDate.MAX : dates.get(0);
        } catch (RuntimeException e) {
            return LocalDate.MAX;
        }
    }

    /**
     * the occurrences of the rules of the scope in the year (also those evaluated with a neighbouring year), sorted by
     * date and rule key; the alternatives of ONE OF in the order written
     */
    public List<Occurrence> holidays(String scope, int year) {
        Set<Occurrence> found = new LinkedHashSet<>();
        for (int y = year - 1; y <= year + 1; y++) {
            for (Map.Entry<RuleDecl, Map.Entry<List<LocalDate>, Boolean>> entry : state(scope, y).entrySet()) {
                RuleDecl rule = entry.getKey();
                boolean projected = entry.getValue().getValue();
                @SuppressWarnings("unchecked")
                List<String> session = (List<String>) rule.attributes.get("session");
                String precision = (String) rule.attributes.getOrDefault("precision", "day");
                if (rule.alternatives.isEmpty() || !announcement(rule.key, y).isEmpty()) {
                    for (LocalDate date : entry.getValue().getKey()) {
                        if (date.getYear() == year) {
                            found.add(new Occurrence(date, rule.key, rule.eventClass, rule.kind(), precision, session,
                                    rule.citations, projected, null));
                        }
                    }
                    continue;
                }
                Context context = context(rule, scope, y, false, null);   // per alternative, a date can be in several
                for (Map.Entry<String, Rule> alternative : rule.alternatives.entrySet()) {
                    for (LocalDate date : new TreeSet<>(alternative.getValue().evaluate(context))) {
                        if (rule.valid(date) && date.getYear() == year) {
                            found.add(new Occurrence(date, rule.key, rule.eventClass, rule.kind(), precision, session,
                                    rule.citations, projected, alternative.getKey()));
                        }
                    }
                }
            }
        }
        List<Occurrence> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::rule));
        return sorted;
    }

    private Map<RuleDecl, Map.Entry<List<LocalDate>, Boolean>> state(String scope, int year) {
        return state(scope, year, false);
    }

    List<LocalDate> referenceDates(String name, String scope, int year, boolean shallow) {
        List<RuleDecl> found = referenced(name);
        TreeSet<LocalDate> dates = new TreeSet<>(found.isEmpty() ? announcement(name, year) : List.of());
        for (RuleDecl rule : found) {
            String inScope = scope != null && applies(rule, scope) ? scope : rule.include.get(0);
            Map.Entry<List<LocalDate>, Boolean> result = state(inScope, year, shallow).get(rule);
            if (result != null) {
                dates.addAll(result.getKey());
            }
        }
        return List.copyOf(dates);
    }

    /** a context for schedule(...): the base date and the calendar of the scope (weekends only without one) */
    public Context context(LocalDate base, String scope) {
        return new Context(base, scope != null ? calendar(scope) : ScheduleSemantics.WEEKENDS, new References(this, scope, null, false));
    }

    /** the dates of a rule with a base date, such as a trade date for spot and fixing rules */
    public List<LocalDate> evaluate(String key, LocalDate base, String scope) {
        List<RuleDecl> versions = rules.stream().filter(r -> r.key.equals(key) && r.valid(base)).toList();
        if (versions.isEmpty()) {
            versions = rules.stream().filter(r -> r.key.equals(key)).toList();
        }
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("no rule " + key);
        }
        RuleDecl rule = versions.get(0);
        return rule.expr.evaluate(context(base, scope != null ? scope : rule.include.get(0))).stream().sorted().toList();
    }

    /** one expression of the rule language, evaluated with context(base, scope) */
    public Rule schedule(String text) {
        return parse.apply(text);
    }
}
