package io.github.hjuergens.runtime;

import io.github.hjuergens.runtime.RuleRegistry.ReloadResult;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class RuleRegistryTest {

    private static final LocalDate BASE = LocalDate.of(2026, 11, 2);

    /** two rules with the same date in each version, so a mixed snapshot would show */
    private static String version(int day) {
        return "rule @A = T + " + day + "D\nrule @B = T + " + day + "D\nassert @A [2026-11-02] => " + BASE.plusDays(day);
    }

    @Test
    public void acceptedReloadSwaps() throws Exception {
        RuleRegistry registry = new RuleRegistry();
        assertSame(registry.current(), RuleBook.EMPTY);
        ReloadResult first = registry.reload(RuleBookTest.example());
        assertTrue(first.accepted(), first.problems().toString());
        assertEquals(registry.current().version(), 1);
        ReloadResult second = registry.reload(version(1));
        assertTrue(second.accepted(), second.problems().toString());
        assertEquals(registry.current().version(), 2);
        assertEquals(registry.current().evaluate("A", BASE, null), List.of(BASE.plusDays(1)));
    }

    @Test
    public void rejectedReloadKeepsTheCurrentRules() {
        RuleRegistry registry = new RuleRegistry();
        registry.reload(version(1));
        RuleBook before = registry.current();
        List<ReloadResult> rejected = new ArrayList<>();
        registry.onRejected(rejected::add);
        ReloadResult syntax = registry.reload("rule @A = T + 1X");
        ReloadResult assertion = registry.reload("rule @A = T + 2D\nassert @A [2026-11-02] => 2026-11-03");
        assertFalse(syntax.accepted());
        assertFalse(assertion.accepted());
        assertEquals(assertion.problems().get(0).line(), 2);
        assertSame(registry.current(), before);
        assertEquals(rejected.size(), 2);
    }

    @Test
    public void rollback() {
        RuleRegistry registry = new RuleRegistry();
        assertFalse(registry.rollback());
        registry.reload(version(1));
        registry.reload(version(2));
        List<RuleBook> changes = new ArrayList<>();
        registry.onChange(changes::add);
        assertTrue(registry.rollback());
        assertEquals(registry.current().evaluate("A", BASE, null), List.of(BASE.plusDays(1)));
        assertFalse(registry.rollback(), "only one step back");
        assertEquals(changes.size(), 1);
    }

    /** readers never see a mix of two versions while reloads alternate between them */
    @Test
    public void readersSeeOneVersionAtATime() throws Exception {
        RuleRegistry registry = new RuleRegistry();
        registry.reload(version(1));
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService readers = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(readers.submit(() -> {
                int reads = 0;
                while (running.get()) {
                    RuleBook book = registry.current();
                    LocalDate a = book.evaluate("A", BASE, null).get(0), b = book.evaluate("B", BASE, null).get(0);
                    if (!a.equals(b)) {
                        throw new AssertionError("mixed versions: " + a + " and " + b);
                    }
                    reads++;
                }
                return reads;
            }));
        }
        for (int i = 0; i < 50; i++) {
            ReloadResult result = registry.reload(version(i % 2 + 1));
            assertTrue(result.accepted(), result.problems().toString());
        }
        running.set(false);
        int reads = 0;
        for (Future<Integer> result : results) {
            reads += result.get(10, TimeUnit.SECONDS);
        }
        readers.shutdown();
        assertTrue(reads > 0);
        assertEquals(registry.current().version(), 51);
    }
}
