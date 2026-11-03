package dev.astral.service;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import dev.astral.storage.Store;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class EngineTest {
    static class MemoryStore implements Store {
        volatile Universe stored = Universe.empty(); volatile boolean fail, closed;
        final List<Audit> history = new ArrayList<>(); CountDownLatch entered, release;
        @Override public Universe load() { return stored; }
        @Override public void commit(Universe before, Universe after, Audit audit) throws Exception {
            if (entered != null) { entered.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new TimeoutException("Test latch timeout"); }
            if (fail) throw new IOException("Disk full"); stored = after; history.add(audit);
        }
        @Override public List<Audit> audit(int limit) { return List.copyOf(history); }
        @Override public void close() { closed = true; }
    }
    @Test void readersNeverSeeAnUncommittedRevision() throws Exception {
        MemoryStore store = new MemoryStore(); store.entered = new CountDownLatch(1); store.release = new CountDownLatch(1);
        try (Engine engine = new Engine(store, Clock.systemUTC())) {
            UUID id = UUID.randomUUID(); var pending = engine.register(id, "Nova");
            try {
                assertTrue(store.entered.await(5, TimeUnit.SECONDS)); assertFalse(pending.isDone()); assertNull(engine.view().profiles().get(id));
            } finally { store.release.countDown(); }
            pending.get(5, TimeUnit.SECONDS); assertEquals(1_000, engine.view().profiles().get(id).wallet());
        }
    }
    @Test void failedPersistenceDoesNotPublishAndTheNextCommandCanRecover() throws Exception {
        MemoryStore store = new MemoryStore(); UUID id = UUID.randomUUID();
        try (Engine engine = new Engine(store, Clock.systemUTC())) {
            engine.register(id, "Nova").get(); Universe before = engine.view(); store.fail = true;
            assertThrows(ExecutionException.class, () -> engine.act(id, Action.of(Type.DAILY)).get()); assertSame(before, engine.view());
            store.fail = false; engine.act(id, Action.of(Type.DAILY)).get(); assertEquals(1_300, engine.view().profiles().get(id).wallet());
        }
    }
    @Test void aThousandConcurrentTransfersAreSerializedAndConserveMoney() throws Exception {
        MemoryStore store = new MemoryStore(); UUID from = UUID.randomUUID(), to = UUID.randomUUID();
        try (Engine engine = new Engine(store, Clock.systemUTC())) {
            engine.register(from, "Nova").get(); engine.register(to, "Orbit").get();
            ExecutorService callers = Executors.newFixedThreadPool(8);
            try {
                List<Future<CompletableFuture<Change>>> queued = new ArrayList<>();
                for (int n = 0; n < 1_000; n++) queued.add(callers.submit(() -> engine.act(from, new Action(Type.PAY, to, 1, ""))));
                for (var future : queued) future.get().get(10, TimeUnit.SECONDS);
            } finally { callers.shutdown(); assertTrue(callers.awaitTermination(10, TimeUnit.SECONDS)); }
            assertEquals(0, engine.view().profiles().get(from).wallet()); assertEquals(2_000, engine.view().profiles().get(to).wallet());
            assertEquals(1_002, engine.audit(100).get().size());
            assertThrows(ExecutionException.class, () -> engine.act(from, new Action(Type.PAY, to, 1, "")).get());
        }
    }
    @Test void idempotentRegistrationDoesNotWriteAnAuditOrResetDaily() throws Exception {
        MemoryStore store = new MemoryStore(); UUID id = UUID.randomUUID();
        try (Engine engine = new Engine(store, Clock.fixed(Instant.ofEpochMilli(20_000 * Model.DAY), ZoneOffset.UTC))) {
            engine.register(id, "Nova").get(); engine.register(id, "Nova").get(); assertEquals(1, engine.audit(10).get().size());
            engine.act(id, Action.of(Type.DAILY)).get(); assertThrows(ExecutionException.class, () -> engine.act(id, Action.of(Type.DAILY)).get());
        }
    }
    @Test void homesAndActivityUseTheSameDurableWriter() throws Exception {
        MemoryStore store = new MemoryStore(); UUID id = UUID.randomUUID();
        try (Engine engine = new Engine(store, Clock.systemUTC())) {
            engine.register(id, "Nova").get(); Change created = engine.act(id, Action.text(Type.CREATE, "ember")).get();
            engine.act(id, Action.target(Type.ACTIVATE, created.islandId())).get(); engine.home(id, new Anchor(1, 101, 1, 0, 0)).get();
            engine.activity(Map.of(new ActivityKey(created.islandId(), id), new Counts(5, 0, 0, 0))).get();
            assertEquals(10, engine.view().islandOf(id).bank()); assertEquals(1, store.stored.islandOf(id).home().x());
        }
    }
    @Test void shutdownDrainsTheQueueClosesStorageAndRejectsNewWork() throws Exception {
        MemoryStore store = new MemoryStore(); Engine engine = new Engine(store, Clock.systemUTC()); UUID id = UUID.randomUUID();
        var queued = engine.register(id, "Nova"); engine.close(); assertTrue(queued.isDone()); assertTrue(store.closed);
        assertThrows(ExecutionException.class, () -> engine.register(UUID.randomUUID(), "Late").get()); engine.close();
    }
}
