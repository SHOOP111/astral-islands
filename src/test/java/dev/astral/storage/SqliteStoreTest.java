package dev.astral.storage;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStoreTest {
    @TempDir Path directory;
    private final UUID owner = UUID.randomUUID();
    private Universe fixture() {
        Universe s = Rules.register(Universe.empty(), owner, "Nova").state();
        s = Rules.apply(s, owner, Action.text(Type.CREATE, "crystal"), 20_000 * Model.DAY).state();
        Island i = s.islandOf(owner);
        return s.update(i.edit(b -> {
            b.status = Status.READY; b.name = "Nova's 星 Garden"; b.bank = 12_345; b.xp = 98_765;
            b.upgrades.put(Upgrade.GENERATOR, 4); b.progress.put(Activity.MINE, 65L); b.claimed.add(Contract.MINER);
            b.home = new Anchor(2.5, 102, -3.5, 90, 12); b.focus = Focus.LUNAR; b.attunedAt = 17; b.discoveries = 3;
            b.expeditions.add(new Expedition(UUID.randomUUID(), Voyage.ECLIPSE, Long.MAX_VALUE, 9_000, 2_000, true));
        }));
    }
    @Test void completeAggregateRoundTripsAndReopens() throws Exception {
        Path file = directory.resolve("astral.db"); Universe expected = fixture();
        try (SqliteStore store = new SqliteStore(file)) {
            assertTrue(store.load().islands().isEmpty()); store.commit(Universe.empty(), expected, new Audit(1, owner, "CREATE", "Unicode and apostrophes are data"));
            assertEquals(expected.islands(), store.load().islands()); assertEquals(expected.profiles(), store.load().profiles());
        }
        try (SqliteStore store = new SqliteStore(file)) {
            Universe loaded = store.load(); assertEquals(expected.islands(), loaded.islands()); assertEquals(1, loaded.nextPlot()); assertEquals(owner, loaded.islandOf(owner).owner());
            assertEquals(1, store.audit(10).size());
        }
    }
    @Test void failedAuditInsertRollsBackEveryTableAndAllocation() throws Exception {
        Path file = directory.resolve("rollback.db");
        try (SqliteStore store = new SqliteStore(file); Connection sabotage = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            try (Statement sql = sabotage.createStatement()) {
                sql.execute("CREATE TRIGGER fail_audit BEFORE INSERT ON audit BEGIN SELECT RAISE(ABORT,'injected failure'); END");
            }
            assertThrows(SQLException.class, () -> store.commit(Universe.empty(), fixture(), new Audit(1, owner, "CREATE", "failure")));
            assertTrue(store.load().islands().isEmpty()); assertTrue(store.load().profiles().isEmpty()); assertEquals(0, store.load().nextPlot());
            try (Statement sql = sabotage.createStatement()) { sql.execute("DROP TRIGGER fail_audit"); }
            store.commit(Universe.empty(), fixture(), new Audit(2, owner, "CREATE", "retry")); assertEquals(1, store.load().islands().size());
        }
    }
    @Test void upsertsDeletionsAndAuditBoundsWork() throws Exception {
        try (SqliteStore store = new SqliteStore(directory.resolve("audit.db"))) {
            Universe first = fixture(); store.commit(Universe.empty(), first, new Audit(1, owner, "FIRST", "one"));
            Universe second = first.update(first.profiles().get(owner).wallet(123)); store.commit(first, second, new Audit(2, null, "SECOND", "two"));
            assertEquals(123, store.load().profiles().get(owner).wallet()); assertEquals("SECOND", store.audit(0).getFirst().action()); assertEquals(1, store.audit(0).size());
            assertEquals(2, store.audit(1_000).size()); assertNull(store.audit(2).getFirst().actor());
            Universe empty = new Universe(Map.of(), Map.of(), 1); store.commit(second, empty, new Audit(3, owner, "DELETE", "operator"));
            assertTrue(store.load().profiles().isEmpty()); assertTrue(store.load().islands().isEmpty()); assertEquals(1, store.load().nextPlot());
        }
    }
    @Test void newerSchemaAndCorruptDataFailClosed() throws Exception {
        Path newer = directory.resolve("future.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + newer); Statement s = c.createStatement()) { s.execute("PRAGMA user_version=99"); }
        assertThrows(SQLException.class, () -> new SqliteStore(newer));
        Path corrupt = directory.resolve("corrupt.db");
        try (SqliteStore store = new SqliteStore(corrupt)) {
            store.commit(Universe.empty(), fixture(), new Audit(1, owner, "CREATE", "one"));
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + corrupt); Statement sql = c.createStatement()) { sql.executeUpdate("DELETE FROM profiles"); }
            assertThrows(IllegalArgumentException.class, store::load);
        }
    }
}
