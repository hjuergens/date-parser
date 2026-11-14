/*
 * Benchmark of the C schedule library called from Java through a SWIG binding (JNI): see README.md for the protocol
 * shared with bench_c, bench_go, bench_antlr.py and BenchJava. run.sh generates the binding from the SWIG interface of
 * the Go binding (date-rule-flex-bison/go/internal/cschedule/cschedule.swig) into the package
 * io.github.hjuergens.cschedule and builds libcschedule_jni.so; nothing generated is committed.
 *
 *   java -cp <classes> -Djava.library.path=<dir> BenchJavaSwig ROUNDS FILE...   prints one JSON object
 *   java ... BenchJavaSwig 0      start-up only: parses and evaluates one schedule and prints its date
 *
 * Reads the spec files like test/spec.c: each case gets its C context once, when the file is read.
 */
import io.github.hjuergens.cschedule.Context;
import io.github.hjuergens.cschedule.DateList;
import io.github.hjuergens.cschedule.ErrorBuffer;
import io.github.hjuergens.cschedule.Events;
import io.github.hjuergens.cschedule.Market;
import io.github.hjuergens.cschedule.Schedule;
import io.github.hjuergens.cschedule.cschedule;

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

public final class BenchJavaSwig {

    static {
        System.loadLibrary("cschedule_jni");
    }

    private static final Pattern PREFIX = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2})(?: ([A-Z0-9_]+))?(?: ([A-Z0-9_]+))?] (.*)");

    /** an input and its C context, null if a market is missing */
    private record Case(String text, Context context) {
    }

    /** a read spec file; its markets, events and contexts are C memory, freed by close */
    private record Spec(List<Case> cases, List<Market> markets, Events events) {
        void close() {
            for (Case c : cases)
                if (c.context() != null) c.context().delete();
            for (Market market : markets) cschedule.market_free(market);
            cschedule.events_free(events);
        }
    }

    private static final class Checksum {
        long dates, sum, errors;
    }

    /** the cases of a spec file; declarations first */
    private static Spec load(String path, ErrorBuffer error) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        Map<String, Market> markets = new HashMap<>();
        Events events = cschedule.events_new();
        for (String line : lines) {
            if (!line.startsWith("market ") && !line.startsWith("event ")) continue;
            String[] declaration = line.substring(line.indexOf(' ') + 1).split("=", 2);
            if (declaration.length < 2) continue;
            Schedule rule = cschedule.parse_rule(declaration[1].trim(), error);
            if (rule == null) continue;
            if (line.startsWith("market ")) markets.put(declaration[0].trim(), cschedule.market_new(rule));
            else cschedule.events_add(events, declaration[0].trim(), rule);
        }
        List<Case> cases = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("market ") || line.startsWith("event ")
                    || !line.contains("=>")) continue;
            String text = line.substring(0, line.lastIndexOf("=>")).trim();
            Context context = new Context();
            context.setEvents(events);
            Matcher prefix = PREFIX.matcher(text);
            if (prefix.matches()) {
                Market market = prefix.group(2) == null ? null : markets.get(prefix.group(2));
                Market fixing = prefix.group(3) == null ? null : markets.get(prefix.group(3));
                if ((prefix.group(2) != null && market == null) || (prefix.group(3) != null && fixing == null)) {
                    context.delete();
                    context = null;
                } else {
                    LocalDate base = LocalDate.parse(prefix.group(1));
                    context.setHas_base(1);
                    context.setBase((int) base.toEpochDay());
                    context.setMarket(market);
                    context.setFixing(fixing);
                }
                text = prefix.group(4);
            }
            cases.add(new Case(text, context));
        }
        return new Spec(cases, new ArrayList<>(markets.values()), events);
    }

    private static long parseRound(List<Spec> specs, ErrorBuffer error) {
        long errors = 0;
        for (Spec spec : specs)
            for (Case c : spec.cases()) {
                Schedule rule = cschedule.parse_rule(c.text(), error);
                if (rule == null) errors++;
                else cschedule.schedule_free(rule);
            }
        return errors;
    }

    /** read the files (fresh markets and events), then parse and evaluate every case */
    private static Checksum evaluateRound(String[] files, ErrorBuffer error, DateList dates) throws IOException {
        Checksum checksum = new Checksum();
        for (String file : files) {
            Spec spec = load(file, error);
            for (Case c : spec.cases()) {
                Schedule rule = c.context() == null ? null : cschedule.parse_rule(c.text(), error);
                if (rule == null || cschedule.evaluate_rule(rule, c.context(), dates, error) != 0) {
                    checksum.errors++;
                } else {
                    int count = dates.getCount();
                    checksum.dates += count;
                    for (int i = 0; i < count; i++) checksum.sum += cschedule.datelist_at(dates, i);
                    cschedule.datelist_free(dates);
                }
                if (rule != null) cschedule.schedule_free(rule);
            }
            spec.close();
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
        ErrorBuffer error = new ErrorBuffer();
        DateList dates = new DateList();
        if (args.length == 1 && args[0].equals("0")) {
            Schedule rule = cschedule.parse_rule("2015-06-09 + 3M", error);
            Context context = new Context();
            if (rule == null || cschedule.evaluate_rule(rule, context, dates, error) != 0)
                throw new IllegalStateException(error.getMessage());
            System.out.println(LocalDate.ofEpochDay(cschedule.datelist_at(dates, 0)));
            return;
        }
        int rounds = Integer.parseInt(args[0]);
        String[] files = java.util.Arrays.copyOfRange(args, 1, args.length);
        List<Spec> specs = new ArrayList<>();
        for (String file : files) specs.add(load(file, error));
        int cases = specs.stream().mapToInt(s -> s.cases().size()).sum();

        long parseErrors = parseRound(specs, error);   // warm-up
        String parse = measure(rounds, () -> parseRound(specs, error));
        Checksum checksum = evaluateRound(files, error, dates);   // warm-up
        String evaluate = measure(rounds, () -> evaluateRound(files, error, dates));
        for (Spec spec : specs) spec.close();

        System.out.printf("{\"implementation\": \"java-swig\", \"cases\": %d, \"parse\": %s, \"evaluate\": %s, "
                        + "\"checksum\": {\"parse_errors\": %d, \"dates\": %d, \"sum\": %d, \"errors\": %d}}%n",
                cases, parse, evaluate, parseErrors, checksum.dates, checksum.sum, checksum.errors);
    }
}
