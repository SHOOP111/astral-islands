package dev.astral.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.astral.domain.Universe;
import dev.astral.domain.Model.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** One connection, owned by the engine's single writer after startup. WAL + FULL durability. */
public final class SqliteStore implements Store {
    private final Connection connection;
    private final ObjectMapper json = JsonMapper.builder().build();
    public SqliteStore(Path file) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try {
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA synchronous=FULL");
                s.execute("PRAGMA busy_timeout=5000"); s.execute("PRAGMA foreign_keys=ON");
                try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
                    if (!r.next() || r.getInt(1) > 1) throw new SQLException("Database is newer than this plugin. Refusing a destructive downgrade.");
                }
                s.execute("CREATE TABLE IF NOT EXISTS islands (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS profiles (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value INTEGER NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS audit (seq INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, actor TEXT, action TEXT NOT NULL, detail TEXT NOT NULL)");
                s.execute("PRAGMA user_version=1");
            }
        } catch (Exception ex) { connection.close(); throw ex; }
    }
    @Override public Universe load() throws Exception {
        Map<UUID, Island> islands = new HashMap<>(); Map<UUID, Profile> profiles = new HashMap<>(); long next = 0;
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT id,json FROM islands")) {
            while (r.next()) islands.put(UUID.fromString(r.getString(1)), json.readValue(r.getString(2), Island.class));
        }
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT id,json FROM profiles")) {
            while (r.next()) profiles.put(UUID.fromString(r.getString(1)), json.readValue(r.getString(2), Profile.class));
        }
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM metadata WHERE key='next_plot'")) {
            if (r.next()) next = r.getLong(1);
        }
        return new Universe(islands, profiles, next); // Reject corruption; never silently reset someone's island.
    }
    @Override public void commit(Universe before, Universe after, Audit audit) throws Exception {
        connection.setAutoCommit(false);
        try {
            upsert("islands", before.islands(), after.islands());
            upsert("profiles", before.profiles(), after.profiles());
            try (PreparedStatement s = connection.prepareStatement("INSERT INTO metadata(key,value) VALUES('next_plot',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
                s.setLong(1, after.nextPlot()); s.executeUpdate();
            }
            try (PreparedStatement s = connection.prepareStatement("INSERT INTO audit(time,actor,action,detail) VALUES(?,?,?,?)")) {
                s.setLong(1, audit.time()); s.setString(2, audit.actor() == null ? null : audit.actor().toString());
                s.setString(3, audit.action()); s.setString(4, audit.detail()); s.executeUpdate();
            }
            // Bounded storage while retaining the latest 50k administrative/financial entries.
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("DELETE FROM audit WHERE seq <= (SELECT COALESCE(MAX(seq),0)-50000 FROM audit)");
            }
            connection.commit();
        } catch (Exception ex) {
            try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        } finally { connection.setAutoCommit(true); }
    }
    private <T> void upsert(String table, Map<UUID, T> before, Map<UUID, T> after) throws Exception {
        try (PreparedStatement s = connection.prepareStatement("INSERT INTO " + table + "(id,json) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET json=excluded.json")) {
            for (var entry : after.entrySet()) {
                if (Objects.equals(before.get(entry.getKey()), entry.getValue())) continue;
                s.setString(1, entry.getKey().toString()); s.setString(2, json.writeValueAsString(entry.getValue())); s.addBatch();
            }
            s.executeBatch();
        }
        try (PreparedStatement s = connection.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
            for (UUID id : before.keySet()) if (!after.containsKey(id)) { s.setString(1, id.toString()); s.addBatch(); }
            s.executeBatch();
        }
    }
    @Override public List<Audit> audit(int limit) throws SQLException {
        List<Audit> result = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement("SELECT time,actor,action,detail FROM audit ORDER BY seq DESC LIMIT ?")) {
            s.setInt(1, Math.max(1, Math.min(100, limit)));
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) result.add(new Audit(r.getLong(1), r.getString(2) == null ? null : UUID.fromString(r.getString(2)), r.getString(3), r.getString(4)));
            }
        }
        return List.copyOf(result);
    }
    @Override public void close() throws SQLException { connection.close(); }
}
