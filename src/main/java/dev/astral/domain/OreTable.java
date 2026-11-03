package dev.astral.domain;

import java.util.*;
import dev.astral.domain.Model.Focus;

/** A pure weighted sampler; no client-provided randomness, block values or material names. */
public final class OreTable {
    private OreTable() {}
    public enum Ore { COBBLESTONE, COAL_ORE, COPPER_ORE, IRON_ORE, GOLD_ORE, REDSTONE_ORE, LAPIS_ORE, DIAMOND_ORE, EMERALD_ORE }
    public static Ore roll(int tier, Focus focus, int constellation, int random) {
        if (tier < 0 || tier > 4 || constellation < 0 || constellation > 5 || random < 0 || random >= 10_000)
            throw new IllegalArgumentException("Invalid ore roll parameters");
        LinkedHashMap<Ore, Integer> weights = weights(tier, focus, constellation);
        for (var e : weights.entrySet()) { if (random < e.getValue()) return e.getKey(); random -= e.getValue(); }
        return Ore.COBBLESTONE;
    }
    public static LinkedHashMap<Ore, Integer> weights(int tier, Focus focus, int constellation) {
        LinkedHashMap<Ore, Integer> w = new LinkedHashMap<>();
        w.put(Ore.EMERALD_ORE, tier >= 4 ? 45 + constellation * 5 : 0);
        w.put(Ore.DIAMOND_ORE, tier >= 3 ? 80 + constellation * 8 + (focus == Focus.LUNAR ? 40 : 0) : 0);
        w.put(Ore.LAPIS_ORE, tier >= 2 ? 180 : 0); w.put(Ore.REDSTONE_ORE, tier >= 2 ? 250 : 0);
        w.put(Ore.GOLD_ORE, tier >= 2 ? 200 : 0); w.put(Ore.IRON_ORE, tier >= 1 ? 700 + tier * 120 : 0);
        w.put(Ore.COPPER_ORE, tier >= 1 ? 800 : 0); w.put(Ore.COAL_ORE, 1_000 + tier * 100);
        w.put(Ore.COBBLESTONE, 10_000 - w.values().stream().mapToInt(Integer::intValue).sum());
        return w;
    }
}
