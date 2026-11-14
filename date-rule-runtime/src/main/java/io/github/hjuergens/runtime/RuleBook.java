package io.github.hjuergens.runtime;

import io.github.hjuergens.time.DateScheduleParser.RuleLineContext;
import io.github.hjuergens.time.DateSchedules;
import io.github.hjuergens.time.ScheduleSemantics;
import io.github.hjuergens.time.ScheduleSemantics.Context;
import io.github.hjuergens.time.ScheduleSemantics.Market;
import io.github.hjuergens.time.ScheduleSemantics.Rule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One version of a rules file, fully parsed and immutable:
 *
 * <pre>
 * market @XNYS = 1 JAN {SUN + 1D}, 3 MON JAN, ...     holidays, as in examples/schedule.txt
 * event @FOMC = 2026-01-28, 2026-03-18, ...            usable as @FOMC in rules
 * rule @IMMH = T > NEXT 3 WED MAR                      any schedule of the language
 * assert @IMMH [2026-11-02 @XNYS] => 2027-03-17         a assertion: rule, [base] or [base market], expected dates
 * </pre>
 *
 * Each line is parsed by the grammar of the language, start rule {@code ruleLine} of DateSchedule.g4; expected dates
 * are ISO dates separated by commas, {@code none} or {@code error}. Lines starting with {@code #} are comments.
 */
public final class RuleBook {

    /** a problem of a rules file: a line that doesn't parse, or an assertion that fails */
    public record Problem(int line, String message) {
        @Override
        public String toString() {
            return "line " + line + ": " + message;
        }
    }

    /** a assertion of a rule; expected is null for an error */
    public record Assertion(int line, String rule, LocalDate base, String market, List<LocalDate> expected) {
    }

    /** thrown by {@link #parse} with every problem of the file */
    public static final class RuleBookException extends Exception {
        private final List<Problem> problems;

        RuleBookException(List<Problem> problems) {
            super(problems.size() + " problem(s), first " + problems.get(0));
            this.problems = List.copyOf(problems);
        }

        public List<Problem> problems() {
            return problems;
        }
    }

    /** a book without rules, the start of every registry */
    public static final RuleBook EMPTY = new RuleBook(0, Map.of(), Map.of(), Map.of(), List.of());

    private final long version;
    private final Instant loadedAt = Instant.now();
    private final Map<String, Rule> rules;
    private final Map<String, Market> markets;
    private final Map<String, Rule> events;
    private final List<Assertion> assertions;

    private RuleBook(long version, Map<String, Rule> rules, Map<String, Market> markets, Map<String, Rule> events,
                     List<Assertion> assertions) {
        this.version = version;
        this.rules = Collections.unmodifiableMap(rules);
        this.markets = Collections.unmodifiableMap(markets);
        this.events = Collections.unmodifiableMap(events);
        this.assertions = List.copyOf(assertions);
    }

    public static RuleBook parse(String text) throws RuleBookException {
        return parse(text, 0);
    }

    /** every line is read, so the exception lists all problems of the file, not only the first */
    static RuleBook parse(String text, long version) throws RuleBookException {
        Map<String, Rule> rules = new LinkedHashMap<>();
        Map<String, Market> markets = new LinkedHashMap<>();
        Map<String, Rule> events = new LinkedHashMap<>();
        List<Assertion> assertions = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int line = i + 1;
            String content = lines[i].strip();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            RuleLineContext parsed;
            try {
                parsed = DateSchedules.parseRuleLine(content);
            } catch (RuntimeException e) {   // a syntax error ("1:12 mismatched input ..."), an invalid date
                problems.add(new Problem(line, describe(e)));
                continue;
            }
            String kind = parsed.kind, name = parsed.name;
            if (kind.equals("assert")) {
                assertions.add(new Assertion(line, name, parsed.base, parsed.market, parsed.dates));
                continue;
            }
            Map<String, ?> names = kind.equals("market") ? markets : kind.equals("event") ? events : rules;
            if (names.containsKey(name)) {
                problems.add(new Problem(line, kind + " @" + name + " is declared twice"));
            } else if (kind.equals("market")) {
                markets.put(name, ScheduleSemantics.withHolidayRules(parsed.rule_));
            } else if (kind.equals("event")) {
                events.put(name, parsed.rule_);
            } else {
                rules.put(name, parsed.rule_);
            }
        }
        for (Assertion assertion : assertions) {
            if (!rules.containsKey(assertion.rule())) {
                problems.add(new Problem(assertion.line(), "assertion of an unknown rule @" + assertion.rule()));
            }
            if (assertion.market() != null && !markets.containsKey(assertion.market())) {
                problems.add(new Problem(assertion.line(), "assertion with an unknown market @" + assertion.market()));
            }
        }
        if (!problems.isEmpty()) {
            problems.sort((a, b) -> Integer.compare(a.line(), b.line()));
            throw new RuleBookException(problems);
        }
        return new RuleBook(version, rules, markets, events, assertions);
    }

    /** the message of a parse error; the bail strategy throws without one, the offending token is in its cause */
    private static String describe(RuntimeException e) {
        if (e.getMessage() != null) {
            return e.getMessage().replaceFirst("^1:", "column ");
        }
        if (e.getCause() instanceof org.antlr.v4.runtime.RecognitionException cause && cause.getOffendingToken() != null) {
            org.antlr.v4.runtime.Token token = cause.getOffendingToken();
            return "column " + token.getCharPositionInLine() + " unexpected '" + token.getText() + "'";
        }
        return e.toString();
    }

    /** runs every assertion; an empty list if all hold */
    public List<Problem> verify() {
        List<Problem> failures = new ArrayList<>();
        for (Assertion assertion : assertions) {
            List<LocalDate> got;
            try {
                got = evaluate(assertion.rule(), assertion.base(), assertion.market());
            } catch (RuntimeException e) {
                got = null;
            }
            if (!Objects.equals(got, assertion.expected())) {
                failures.add(new Problem(assertion.line(),
                        "@" + assertion.rule() + " gives " + format(got) + ", expected " + format(assertion.expected())));
            }
        }
        return failures;
    }

    /** the dates of a rule for a base date, in a market (null: Monday to Friday) */
    public List<LocalDate> evaluate(String rule, LocalDate base, String market) {
        Rule found = rules.get(rule);
        if (found == null) {
            throw new IllegalArgumentException("no rule " + rule + " in version " + version);
        }
        if (market != null && !markets.containsKey(market)) {
            throw new IllegalArgumentException("no market " + market + " in version " + version);
        }
        return found.evaluate(new Context(base, market == null ? null : markets.get(market), events));
    }

    /** as in an assertion: the dates, none or error */
    private static String format(List<LocalDate> dates) {
        return dates == null ? "error"
                : dates.isEmpty() ? "none" : String.join(", ", dates.stream().map(LocalDate::toString).toList());
    }

    public long version() {
        return version;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public Map<String, Rule> rules() {
        return rules;
    }

    public Map<String, Market> markets() {
        return markets;
    }

    public Map<String, Rule> events() {
        return events;
    }

    public List<Assertion> assertions() {
        return assertions;
    }
}
