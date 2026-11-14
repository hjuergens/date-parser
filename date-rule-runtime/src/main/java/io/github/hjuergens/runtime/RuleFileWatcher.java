package io.github.hjuergens.runtime;

import io.github.hjuergens.runtime.RuleRegistry.ReloadResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

/**
 * Reloads a rules file into a registry whenever it changes. The directory is watched, not the file, as many editors
 * save by writing a new file and renaming it; events of other files are ignored. Changes within {@code DEBOUNCE_MS}
 * are taken together, so one save is one reload.
 */
public final class RuleFileWatcher implements AutoCloseable {

    static final long DEBOUNCE_MS = 200;

    private final Path file;
    private final RuleRegistry registry;
    private final WatchService watcher;
    private final Thread thread;

    /** loads the file once now (the result is {@link #initial()}), then watches it */
    public RuleFileWatcher(Path file, RuleRegistry registry) throws IOException {
        this.file = file.toAbsolutePath();
        this.registry = registry;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.file.getParent().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        this.initial = load();
        this.thread = new Thread(this::watch, "rule-file-watcher " + this.file.getFileName());
        thread.setDaemon(true);
        thread.start();
    }

    private final ReloadResult initial;

    public ReloadResult initial() {
        return initial;
    }

    private ReloadResult load() {
        try {
            return registry.reload(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void watch() {
        try {
            while (true) {
                WatchKey key = watcher.take();
                boolean ours = concerns(key);
                // take the events of the same save together
                for (WatchKey more; (more = watcher.poll(DEBOUNCE_MS, TimeUnit.MILLISECONDS)) != null; ) {
                    ours |= concerns(more);
                }
                if (ours && Files.exists(file)) {
                    try {
                        load();
                    } catch (UncheckedIOException e) {
                        // the file is being replaced; the next event reloads it
                    }
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // closed
        }
    }

    private boolean concerns(WatchKey key) {
        boolean ours = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            ours |= event.context() instanceof Path name && name.equals(file.getFileName());
        }
        key.reset();
        return ours;
    }

    @Override
    public void close() throws IOException {
        watcher.close();
        thread.interrupt();
    }
}
