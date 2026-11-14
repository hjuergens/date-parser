package io.github.hjuergens.runtime;

import io.github.hjuergens.runtime.RuleBook.Problem;
import io.github.hjuergens.runtime.RuleBook.RuleBookException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The current version of the rules, changed at run time. Readers take one snapshot and evaluate on it without locks:
 * {@code registry.current().evaluate("IMMH", base, "XNYS")}; a reload builds a new {@link RuleBook} next to the current
 * one and swaps it in only if it parses and all its assertions pass, otherwise the current one stays.
 */
public final class RuleRegistry {

    /** the outcome of a reload: the book that is current afterwards, and the problems if it was rejected */
    public record ReloadResult(boolean accepted, RuleBook current, List<Problem> problems) {
    }

    private final AtomicReference<RuleBook> current = new AtomicReference<>(RuleBook.EMPTY);
    private final AtomicReference<RuleBook> previous = new AtomicReference<>();
    private final List<Consumer<RuleBook>> changeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ReloadResult>> rejectListeners = new CopyOnWriteArrayList<>();

    /** the current version; never null, {@link RuleBook#EMPTY} before the first accepted reload */
    public RuleBook current() {
        return current.get();
    }

    /** the version before the current one, if there was one */
    public Optional<RuleBook> previous() {
        return Optional.ofNullable(previous.get());
    }

    /** parse, verify, swap; reloads are serialised, readers are never blocked */
    public synchronized ReloadResult reload(String text) {
        RuleBook candidate;
        try {
            candidate = RuleBook.parse(text, current.get().version() + 1);
        } catch (RuleBookException e) {
            return rejected(e.problems());
        }
        List<Problem> failures = candidate.verify();
        if (!failures.isEmpty()) {
            return rejected(failures);
        }
        previous.set(current.getAndSet(candidate));
        changeListeners.forEach(listener -> listener.accept(candidate));
        return new ReloadResult(true, candidate, List.of());
    }

    /** swaps back to the previous version; false if there is none */
    public synchronized boolean rollback() {
        RuleBook before = previous.getAndSet(null);
        if (before == null) {
            return false;
        }
        current.set(before);
        changeListeners.forEach(listener -> listener.accept(before));
        return true;
    }

    public void onChange(Consumer<RuleBook> listener) {
        changeListeners.add(listener);
    }

    public void onRejected(Consumer<ReloadResult> listener) {
        rejectListeners.add(listener);
    }

    private ReloadResult rejected(List<Problem> problems) {
        ReloadResult result = new ReloadResult(false, current.get(), problems);
        rejectListeners.forEach(listener -> listener.accept(result));
        return result;
    }
}
