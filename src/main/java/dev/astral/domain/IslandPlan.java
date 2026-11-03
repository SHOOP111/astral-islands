package dev.astral.domain;

import java.util.*;
import dev.astral.domain.Model.Template;

/** Deterministic, dependency-free schematics. Coordinates are relative to the plot center. */
public final class IslandPlan {
    public record BlockSpec(int x, int y, int z, String material) {}
    private IslandPlan() {}
    public static List<BlockSpec> forTemplate(Template template) {
        Map<String, BlockSpec> blocks = new LinkedHashMap<>();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            int d = x * x + z * z;
            if (d > 49) continue;
            int depth = 2 + (49 - d) / 12;
            for (int y = 100 - depth; y <= 100; y++) {
                String material = y == 100 ? (template == Template.EMBER && d > 25 ? "BLACKSTONE" : "GRASS_BLOCK")
                    : y >= 99 ? "DIRT" : template == Template.EMBER ? "BASALT" : "STONE";
                put(blocks, x, y, z, material);
            }
        }
        String log = template == Template.CRYSTAL ? "SPRUCE_LOG" : "OAK_LOG";
        String leaves = template == Template.CRYSTAL ? "SPRUCE_LEAVES" : "OAK_LEAVES";
        for (int y = 104; y <= 106; y++) for (int x = -5; x <= -1; x++) for (int z = -5; z <= -1; z++) {
            if (Math.abs(x + 3) + Math.abs(z + 3) <= (y == 106 ? 1 : 3)) put(blocks, x, y, z, leaves);
        }
        for (int y = 101; y <= 105; y++) put(blocks, -3, y, -3, log);
        if (template == Template.CRYSTAL) {
            for (int x : new int[]{-5, 5}) for (int y = 101; y <= 103; y++) put(blocks, x, y, 3, "AMETHYST_BLOCK");
            put(blocks, 5, 104, 3, "SEA_LANTERN");
        }
        if (template == Template.EMBER) {
            for (int y = 101; y <= 103; y++) put(blocks, 5, y, -3, "POLISHED_BASALT");
            put(blocks, 5, 104, -3, "SHROOMLIGHT");
        }
        put(blocks, 3, 101, 0, "CHEST"); put(blocks, 4, 101, 0, "CRAFTING_TABLE");
        put(blocks, 2, 101, 3, "OAK_FENCE"); put(blocks, 2, 102, 3, "LANTERN");
        return List.copyOf(blocks.values());
    }
    private static void put(Map<String, BlockSpec> out, int x, int y, int z, String material) {
        out.put(x + ":" + y + ":" + z, new BlockSpec(x, y, z, material));
    }
}
