package dev.astral.service;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import dev.astral.storage.Store;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Serializes domain transactions. A successful future always means the database commit succeeded. */
public final class Engine implements AutoCloseable {
    private final Store store;
    private final Clock clock;
    private final ExecutorService writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(4096), task -> new Thread(task, "astral-ledger"), new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile Universe state;
    public Engine(Store store, Clock clock) throws Exception { this.store = store; this.clock = clock; state = store.load(); }
    public Universe view() { return state; }
    public CompletableFuture<Change> register(UUID id, String name) { return mutate(id, "REGISTER", name, s -> Rules.register(s, id, name)); }
    public CompletableFuture<Change> act(UUID actor, Action a) {
        return mutate(actor, a.type().name(), "target=" + a.target() + " amount=" + a.amount() + " value=" + a.value(), s -> Rules.apply(s, actor, a, clock.millis()));
    }
    public CompletableFuture<Change> home(UUID actor, Anchor anchor) { return mutate(actor, "SET_HOME", anchor.toString(), s -> Rules.setHome(s, actor, anchor)); }
    public CompletableFuture<Change> activity(Map<ActivityKey, Counts> batch) {
        Map<ActivityKey, Counts> immutable = Map.copyOf(batch);
        return mutate(null, "ACTIVITY", "actors=" + batch.size(), s -> Rules.activity(s, immutable, clock.millis()));
    }
    private CompletableFuture<Change> mutate(UUID actor, String action, String detail, Function<Universe, Change> operation) {
        return enqueue(() -> {
            Universe before = state; Change result = operation.apply(before);
            if (result.state() != before) {
                store.commit(before, result.state(), new Audit(clock.millis(), actor, action, detail.substring(0, Math.min(1024, detail.length()))));
                state = result.state();
            }
            return result;
        });
    }
    public CompletableFuture<List<Audit>> audit(int limit) { return enqueue(() -> store.audit(limit)); }
    private <T> CompletableFuture<T> enqueue(Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closing.get()) { future.completeExceptionally(new RejectedExecutionException("Astral Islands is stopping.")); return future; }
        try {
            writer.execute(() -> {
                try { future.complete(operation.call()); } catch (Exception ex) { future.completeExceptionally(ex); }
            });
        } catch (RejectedExecutionException ex) { future.completeExceptionally(ex); }
        return future;
    }
    @Override public void close() throws Exception {
        if (!closing.compareAndSet(false, true)) return;
        writer.shutdown();
        if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
            // Do not close a JDBC connection underneath a live transaction.
            throw new TimeoutException("Ledger still draining after 30 seconds; database connection left intact.");
        }
        store.close();
    }
}
