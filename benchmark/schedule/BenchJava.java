/*
 * Benchmark of ANTLR 4's Java target (date-rule-antlr-java) and of the same rules compiled to bytecode
 * (date-rule-compiled): see README.md for the protocol shared with bench_c, bench_go and bench_antlr.py. Reads the spec
 * files like DateSchedulesTest.
 *
 *   java -cp <date-rule-compiled and its dependencies>:<classes> BenchJava [compiled] ROUNDS FILE...   prints one JSON object
 *   java ... BenchJava [compiled] 0      start-up only: parses and evaluates one schedule and prints its date
 *
 * Besides "parse" and "evaluate" (parse and evaluate), it measures "evaluate_only": the rules parsed once, then only
 * evaluated in every round.
 */
import io.github.hjuergens.compiled.CompiledRules;
import io.github.hjuergens.time.DateSchedules;
import io.github.hjuergens.time.ScheduleSemantics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BenchJava {

    /** DateSchedules.parse (closures) or CompiledRules.parse (bytecode), chosen by the first argument */
    private static java.util.function.Function<String, ScheduleSemantics.Rule> parser = DateSchedules::parse;
    private static String implementation = "antlr-java";

    private static final Pattern PREFIX = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2})(?: ([A-Z0-9_]+))?(?: ([A-Z0-9_]+))?] (.*)");

    /** an input and its context, null if a market is missing */
    private record Case(String text, ScheduleSemantics.Context context) {
    }

    /** a case parsed once, rule null if it doesn't parse */
    private record Parsed(ScheduleSemantics.Rule rule, ScheduleSemantics.Context context) {
    }

    private static final class Checksum {
        long dates, sum, errors;

        void add(List<LocalDate> result) {
            dates += result.size();
            for (LocalDate date : result) sum += date.toEpochDay();
        }
    }

    /** the cases of a spec file; declarations first */
    private static List<Case> load(String path) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        Map<String, ScheduleSemantics.Market> markets = new HashMap<>();
        Map<String, ScheduleSemantics.Rule> events = new HashMap<>();
        for (String line : lines) {
            if (!line.startsWith("market ") && !line.startsWith("event ")) continue;
            String[] declaration = line.substring(line.indexOf(' ') + 1).split("=", 2);
            ScheduleSemantics.Rule rule;
            try {
                rule = parser.apply(declaration[1].trim());
            } catch (RuntimeException e) {
                continue;
            }
            if (line.startsWith("market ")) markets.put(declaration[0].trim(), ScheduleSemantics.withHolidayRules(rule));
            else events.put(declaration[0].trim(), rule);
        }
        List<Case> cases = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("market ") || line.startsWith("event ")
                    || !line.contains("=>")) continue;
            String text = line.substring(0, line.lastIndexOf("=>")).trim();
            ScheduleSemantics.Context context = new ScheduleSemantics.Context(null, null, events);
            Matcher prefix = PREFIX.matcher(text);
            if (prefix.matches()) {
                ScheduleSemantics.Market market = prefix.group(2) == null ? null : markets.get(prefix.group(2));
                ScheduleSemantics.Market fixing = prefix.group(3) == null ? null : markets.get(prefix.group(3));
                boolean missing = (prefix.group(2) != null && market == null) || (prefix.group(3) != null && fixing == null);
                context = missing ? null
                        : new ScheduleSemantics.Context(LocalDate.parse(prefix.group(1)), market, events, fixing);
                text = prefix.group(4);
            }
            cases.add(new Case(text, context));
        }
        return cases;
    }

    private static long parseRound(List<List<Case>> specs) {
        long errors = 0;
        for (List<Case> cases : specs)
            for (Case c : cases) {
                try {
                    parser.apply(c.text());
                } catch (RuntimeException e) {
                    errors++;
                }
            }
        return errors;
    }

    /** read the files (fresh markets and events), then parse and evaluate every case */
    private static Checksum evaluateRound(String[] files) throws IOException {
        Checksum checksum = new Checksum();
        for (String file : files)
            for (Case c : load(file)) {
                try {
                    if (c.context() == null) throw new IllegalArgumentException("no market");
                    checksum.add(parser.apply(c.text()).evaluate(c.context()));
                } catch (RuntimeException e) {
                    checksum.errors++;
                }
            }
        return checksum;
    }

    /** evaluate the rules parsed once; the markets keep their holiday caches between rounds */
    private static Checksum evaluateOnlyRound(List<Parsed> parsed) {
        Checksum checksum = new Checksum();
        for (Parsed p : parsed) {
            try {
                if (p.rule() == null || p.context() == null) throw new IllegalArgumentException("no rule");
                checksum.add(p.rule().evaluate(p.context()));
            } catch (RuntimeException e) {
                checksum.errors++;
            }
        }
        return checksum;
    }

    private interface Work {
        void run() throws IOException;
    }

    private static String measure(int rounds, Work work) throws IOException {
        StringJoiner times = new StringJoiner(", ", "[", "]");
        for (int r = 0; r < rounds; r++) {
            long start = System.nanoTime();
            work.run();
            times.add(String.format(java.util.Locale.ROOT, "%.6f", (System.nanoTime() - start) / 1e9));
        }
        return times.toString();
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("compiled")) {
            parser = CompiledRules::parse;
            implementation = "compiled";
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 1 && args[0].equals("0")) {
            System.out.println(parser.apply("2015-06-09 + 3M").evaluate(new ScheduleSemantics.Context(null, null)).get(0));
            return;
        }
        int rounds = Integer.parseInt(args[0]);
        String[] files = java.util.Arrays.copyOfRange(args, 1, args.length);
        List<List<Case>> specs = new ArrayList<>();
        for (String file : files) specs.add(load(file));
        int cases = specs.stream().mapToInt(List::size).sum();

        long parseErrors = parseRound(specs);   // warm-up
        String parse = measure(rounds, () -> parseRound(specs));
        Checksum checksum = evaluateRound(files);   // warm-up
        String evaluate = measure(rounds, () -> evaluateRound(files));

        List<Parsed> parsed = new ArrayList<>();
        for (List<Case> spec : specs)
            for (Case c : spec) {
                ScheduleSemantics.Rule rule;
                try {
                    rule = parser.apply(c.text());
                } catch (RuntimeException e) {
                    rule = null;
                }
                parsed.add(new Parsed(rule, c.context()));
            }
        Checksum only = evaluateOnlyRound(parsed);   // warm-up
        String evaluateOnly = measure(rounds, () -> evaluateOnlyRound(parsed));
        if (only.dates != checksum.dates || only.sum != checksum.sum || only.errors != checksum.errors)
            throw new IllegalStateException("evaluate_only computed other dates than evaluate");

        System.out.printf("{\"implementation\": \"%s\", \"cases\": %d, \"parse\": %s, \"evaluate\": %s, "
                        + "\"evaluate_only\": %s, \"checksum\": {\"parse_errors\": %d, \"dates\": %d, \"sum\": %d, \"errors\": %d}}%n",
                implementation, cases, parse, evaluate, evaluateOnly, parseErrors, checksum.dates, checksum.sum, checksum.errors);
    }
}
