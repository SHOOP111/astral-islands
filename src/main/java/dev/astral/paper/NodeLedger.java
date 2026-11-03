package dev.astral.paper;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/** Chunk-persisted provenance, not transient metadata. Placed ores never grant generator rewards. */
public final class NodeLedger {
    private final NamespacedKey key;
    public NodeLedger(AstralPlugin plugin) { key = new NamespacedKey(plugin, "generated_nodes_v1"); }
    private int packed(Block block) { return ((block.getY() - block.getWorld().getMinHeight()) << 8) | ((block.getZ() & 15) << 4) | (block.getX() & 15); }
    private Set<Integer> nodes(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        Set<Integer> values = new LinkedHashSet<>(); if (stored != null) for (int n : stored) values.add(n); return values;
    }
    private void save(Chunk c, Set<Integer> values) {
        if (values.isEmpty()) c.getPersistentDataContainer().remove(key);
        else c.getPersistentDataContainer().set(key, PersistentDataType.INTEGER_ARRAY, values.stream().mapToInt(Integer::intValue).toArray());
    }
    public void mark(Block block) { Set<Integer> values = nodes(block.getChunk()); if (values.size() < 4096) { values.add(packed(block)); save(block.getChunk(), values); } }
    public boolean remove(Block block) { Set<Integer> values = nodes(block.getChunk()); boolean removed = values.remove(packed(block)); if (removed) save(block.getChunk(), values); return removed; }
    public boolean contains(Block block) { return nodes(block.getChunk()).contains(packed(block)); }
}
