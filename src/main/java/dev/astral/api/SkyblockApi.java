package dev.astral.api;

import dev.astral.domain.Model.*;
import java.util.*;

/** Obtain via Bukkit's ServicesManager. Values are immutable snapshots; never call Bukkit off-thread. */
public interface SkyblockApi {
    Optional<Island> islandOf(UUID player);
    Optional<Island> island(UUID id);
    Optional<Profile> profile(UUID player);
    List<Island> leaderboard(int limit);
}
