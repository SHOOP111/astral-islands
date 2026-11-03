package dev.astral.domain;

import java.util.*;
import java.util.function.Consumer;

/** Immutable, versioned persistence values. No Bukkit types cross the domain boundary. */
public final class Model {
    private Model() {}
    public static final long MAX_COINS = 9_000_000_000_000_000L;
    public static final long DAY = 86_400_000L;
    public enum Role { OWNER, STEWARD, BUILDER;
        public boolean manages() { return this != BUILDER; }
    }
    public enum Template { GROVE, CRYSTAL, EMBER }
    public enum Status { PROVISIONING, READY }
    public enum Focus { SOLAR, LUNAR, VERDANT }
    public enum Upgrade {
        HORIZON(3, 2_000), GENERATOR(4, 1_500), EXPEDITION(3, 2_500);
        public final int max;
        public final long base;
        Upgrade(int max, long base) { this.max = max; this.base = base; }
        public long cost(int current) { return Math.multiplyExact(base, 1L << current); }
    }
    public enum Activity { MINE, FARM, FISH, HUNT }
    public enum Contract {
        MINER(Activity.MINE, 64, 800, 320), GARDENER(Activity.FARM, 32, 600, 260),
        ANGLER(Activity.FISH, 8, 750, 300), SENTINEL(Activity.HUNT, 24, 700, 280);
        public final Activity activity;
        public final long goal, coins, xp;
        Contract(Activity activity, long goal, long coins, long xp) {
            this.activity = activity; this.goal = goal; this.coins = coins; this.xp = xp;
        }
        public boolean active(long day) { return Math.floorMod(day + ordinal(), 4) != 0; }
    }
    public enum Voyage {
        DRIFT(600, 5 * 60_000L, 900, 240), NEBULA(2_000, 20 * 60_000L, 3_000, 700),
        ECLIPSE(6_000, 60 * 60_000L, 9_000, 1_800);
        public final long cost, duration, reward, xp;
        Voyage(long cost, long duration, long reward, long xp) {
            this.cost = cost; this.duration = duration; this.reward = reward; this.xp = xp;
        }
    }
    public record Anchor(double x, double y, double z, float yaw, float pitch) {
        public Anchor {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid home");
        }
        public static Anchor starter() { return new Anchor(0.5, 101, 0.5, 0, 0); }
    }
    public record Expedition(UUID id, Voyage voyage, long readyAt, long coins, long xp, boolean discovery) {}
    public record Profile(UUID id, String name, long wallet, long dailyDay, int streak,
                          UUID invitedIsland, long inviteExpires, boolean hud) {
        public Profile {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            if (wallet < 0 || wallet > MAX_COINS || streak < 0) throw new IllegalArgumentException("Invalid profile");
        }
        public static Profile fresh(UUID id, String name) { return new Profile(id, name, 1_000, -1, 0, null, 0, true); }
        public Profile wallet(long value) { return new Profile(id, name, value, dailyDay, streak, invitedIsland, inviteExpires, hud); }
        public Profile invite(UUID island, long expires) { return new Profile(id, name, wallet, dailyDay, streak, island, expires, hud); }
    }
    public record Island(UUID id, UUID owner, String name, Template template, long plot, Status status,
                         Map<UUID, Role> members, boolean publicVisits, long bank, long xp,
                         Map<Upgrade, Integer> upgrades, long contractDay, Map<Activity, Long> progress,
                         Set<Contract> claimed, List<Expedition> expeditions, Anchor home,
                         Focus focus, long attunedAt, int discoveries) {
        public Island {
            Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(name);
            Objects.requireNonNull(template); Objects.requireNonNull(status); Objects.requireNonNull(home); Objects.requireNonNull(focus);
            members = Map.copyOf(members); upgrades = Map.copyOf(upgrades);
            progress = Map.copyOf(progress); claimed = Set.copyOf(claimed); expeditions = List.copyOf(expeditions);
            if (members.get(owner) != Role.OWNER || members.values().stream().filter(r -> r == Role.OWNER).count() != 1)
                throw new IllegalArgumentException("Exactly one owner required");
            if (plot < 0 || plot >= Grid.MAX_PLOTS || bank < 0 || bank > MAX_COINS || xp < 0 || xp > MAX_COINS || discoveries < 0)
                throw new IllegalArgumentException("Invalid island counters");
            upgrades.forEach((u, rank) -> { if (rank < 0 || rank > u.max) throw new IllegalArgumentException("Invalid upgrade"); });
            if (progress.values().stream().anyMatch(n -> n < 0)) throw new IllegalArgumentException("Invalid progress");
        }
        public int rank(Upgrade upgrade) { return upgrades.getOrDefault(upgrade, 0); }
        public int radius() { return 32 + rank(Upgrade.HORIZON) * 32; }
        public int level() { return 1 + (int) Math.sqrt(xp / 100.0); }
        public int constellation() { return Math.min(5, (level() - 1) / 10); }
        public int memberLimit() { return 4 + constellation() * 2; }
        public Island edit(Consumer<Builder> change) { Builder b = new Builder(this); change.accept(b); return b.build(); }
        public static Island fresh(UUID owner, String name, Template template, long plot, long day) {
            return new Island(UUID.randomUUID(), owner, name, template, plot, Status.PROVISIONING, Map.of(owner, Role.OWNER),
                false, 0, 0, Map.of(), day, Map.of(), Set.of(), List.of(), Anchor.starter(), Focus.SOLAR, 0, 0);
        }
    }
    /** A builder is private to a single serialized transaction; immutable values are published afterward. */
    public static final class Builder {
        private final Island source;
        public UUID owner;
        public String name;
        public Status status;
        public final Map<UUID, Role> members;
        public boolean publicVisits;
        public long bank, xp, contractDay, attunedAt;
        public final Map<Upgrade, Integer> upgrades;
        public final Map<Activity, Long> progress;
        public final Set<Contract> claimed;
        public final List<Expedition> expeditions;
        public Anchor home;
        public Focus focus;
        public int discoveries;
        Builder(Island s) {
            source = s; owner = s.owner; name = s.name; status = s.status; members = new HashMap<>(s.members);
            publicVisits = s.publicVisits; bank = s.bank; xp = s.xp; contractDay = s.contractDay;
            upgrades = new EnumMap<>(Upgrade.class); upgrades.putAll(s.upgrades);
            progress = new EnumMap<>(Activity.class); progress.putAll(s.progress);
            claimed = new HashSet<>(s.claimed); expeditions = new ArrayList<>(s.expeditions);
            home = s.home; focus = s.focus; attunedAt = s.attunedAt; discoveries = s.discoveries;
        }
        public Island build() {
            return new Island(source.id, owner, name, source.template, source.plot, status, members, publicVisits,
                bank, xp, upgrades, contractDay, progress, claimed, expeditions, home, focus, attunedAt, discoveries);
        }
    }
    public record ActivityKey(UUID island, UUID actor) {}
    public record Counts(long mine, long farm, long fish, long hunt) {
        public Counts { if (mine < 0 || farm < 0 || fish < 0 || hunt < 0) throw new IllegalArgumentException("Negative activity"); }
        public long get(Activity a) { return switch (a) { case MINE -> mine; case FARM -> farm; case FISH -> fish; case HUNT -> hunt; }; }
        public Counts add(Activity a) {
            return new Counts(mine + (a == Activity.MINE ? 1 : 0), farm + (a == Activity.FARM ? 1 : 0),
                fish + (a == Activity.FISH ? 1 : 0), hunt + (a == Activity.HUNT ? 1 : 0));
        }
    }
    public record Audit(long time, UUID actor, String action, String detail) {}
}
