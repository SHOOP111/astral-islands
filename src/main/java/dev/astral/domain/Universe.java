package dev.astral.domain;

import java.util.*;
import dev.astral.domain.Model.*;

/** A lock-free read model. The engine alone owns the next immutable revision. */
public final class Universe {
    private final Map<UUID, Island> islands;
    private final Map<UUID, Profile> profiles;
    private final Map<UUID, Island> membership;
    private final Map<Long, Island> plots;
    private final long nextPlot;
    public Universe(Map<UUID, Island> islands, Map<UUID, Profile> profiles, long nextPlot) {
        this.islands = Map.copyOf(islands); this.profiles = Map.copyOf(profiles); this.nextPlot = nextPlot;
        Map<UUID, Island> members = new HashMap<>(); Map<Long, Island> cells = new HashMap<>();
        islands.forEach((key, island) -> {
            if (!key.equals(island.id()) || island.plot() >= nextPlot) throw new IllegalArgumentException("Corrupt plot allocation");
            if (cells.put(Grid.at(island.plot()).key(), island) != null) throw new IllegalArgumentException("Duplicate plot");
            island.members().keySet().forEach(id -> {
                if (!profiles.containsKey(id) || members.put(id, island) != null) throw new IllegalArgumentException("Corrupt membership");
            });
        });
        if (nextPlot < 0 || nextPlot > Grid.MAX_PLOTS) throw new IllegalArgumentException("Invalid next plot");
        profiles.forEach((id, p) -> { if (!id.equals(p.id())) throw new IllegalArgumentException("Profile key mismatch"); });
        membership = Map.copyOf(members); plots = Map.copyOf(cells);
    }
    public static Universe empty() { return new Universe(Map.of(), Map.of(), 0); }
    public Map<UUID, Island> islands() { return islands; }
    public Map<UUID, Profile> profiles() { return profiles; }
    public long nextPlot() { return nextPlot; }
    public Island islandOf(UUID player) { return membership.get(player); }
    public Island at(double x, double z) {
        Island island = plots.get(Grid.nearest(x, z).key());
        return island != null && Grid.at(island.plot()).contains(x, z, island.radius()) ? island : null;
    }
    public Profile named(String name) {
        return profiles.values().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public Universe update(Island island) {
        Map<UUID, Island> changed = new HashMap<>(islands); changed.put(island.id(), island);
        return new Universe(changed, profiles, Math.max(nextPlot, island.plot() + 1));
    }
    public Universe update(Profile... changes) {
        Map<UUID, Profile> changed = new HashMap<>(profiles); for (Profile p : changes) changed.put(p.id(), p);
        return new Universe(islands, changed, nextPlot);
    }
}
