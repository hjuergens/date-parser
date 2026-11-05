package io.github.hjuergens.runtime;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RuleFileWatcherTest {

    private static final LocalDate BASE = LocalDate.of(2026, 11, 2);

    private static void await(CountDownLatch latch, String what) throws InterruptedException {
        assertTrue(latch.await(10, TimeUnit.SECONDS), "no " + what + " within 10 s");
    }

    @Test
    public void reloadsOnSaveAndKeepsTheRulesOnABrokenSave() throws Exception {
        Path dir = Files.createTempDirectory("rules");
        Path file = dir.resolve("app.rules");
        Files.writeString(file, "rule @A = T + 1D\n");
        RuleRegistry registry = new RuleRegistry();
        AtomicReference<CountDownLatch> changed = new AtomicReference<>(new CountDownLatch(1));
        AtomicReference<CountDownLatch> rejected = new AtomicReference<>(new CountDownLatch(1));
        registry.onChange(book -> changed.get().countDown());
        registry.onRejected(result -> rejected.get().countDown());
        try (RuleFileWatcher watcher = new RuleFileWatcher(file, registry)) {
            assertTrue(watcher.initial().accepted());
            assertEquals(registry.current().evaluate("A", BASE, null), List.of(BASE.plusDays(1)));

            // written in place
            changed.set(new CountDownLatch(1));
            Files.writeString(file, "rule @A = T + 2D\n");
            await(changed.get(), "reload after writing in place");
            assertEquals(registry.current().evaluate("A", BASE, null), List.of(BASE.plusDays(2)));

            // written next to it and renamed, as many editors save
            changed.set(new CountDownLatch(1));
            Path temp = dir.resolve("app.rules.tmp");
            Files.writeString(temp, "rule @A = T + 3D\n");
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            await(changed.get(), "reload after a rename");
            assertEquals(registry.current().evaluate("A", BASE, null), List.of(BASE.plusDays(3)));

            // a broken save is rejected, the rules stay
            Files.writeString(file, "rule @A = T + 3X\n");
            await(rejected.get(), "rejection of a broken save");
            assertEquals(registry.current().evaluate("A", BASE, null), List.of(BASE.plusDays(3)));
        }
    }
}
