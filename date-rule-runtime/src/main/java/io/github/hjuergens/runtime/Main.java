package io.github.hjuergens.runtime;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Evaluates a rule of a rules file, once or every time the file changes:
 *
 * <pre>
 * java -cp ... io.github.hjuergens.runtime.Main [--watch] FILE RULE BASE [MARKET]
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        boolean watch = args.length > 0 && args[0].equals("--watch");
        int i = watch ? 1 : 0;
        if (args.length - i < 3 || args.length - i > 4) {
            System.err.println("usage: Main [--watch] FILE RULE BASE [MARKET]");
            System.exit(2);
        }
        Path file = Path.of(args[i]);
        // names with or without the @ of the rules file
        String rule = args[i + 1].replaceFirst("^@", ""), market = args.length - i == 4 ? args[i + 3].replaceFirst("^@", "") : null;
        LocalDate base = LocalDate.parse(args[i + 2]);
        RuleRegistry registry = new RuleRegistry();
        registry.onChange(book -> print(book, rule, base, market));
        registry.onRejected(result -> {
            System.out.println("rejected, version " + result.current().version() + " stays:");
            result.problems().forEach(problem -> System.out.println("  " + problem));
        });
        try (RuleFileWatcher watcher = new RuleFileWatcher(file, registry)) {
            if (!watch) {
                System.exit(watcher.initial().accepted() ? 0 : 1);
            }
            Thread.currentThread().join();   // until interrupted
        }
    }

    private static void print(RuleBook book, String rule, LocalDate base, String market) {
        try {
            System.out.println("version " + book.version() + ": " + rule + " = " + book.evaluate(rule, base, market));
        } catch (RuntimeException e) {
            System.out.println("version " + book.version() + ": " + rule + ": " + e.getMessage());
        }
    }
}
