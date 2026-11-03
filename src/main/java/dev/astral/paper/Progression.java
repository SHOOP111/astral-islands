package dev.astral.paper;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;

public final class Progression implements Listener {
    private final AstralPlugin plugin;
    private final Map<ActivityKey, Counts> pending = new HashMap<>();
    private final NamespacedKey naturalMob;
    private static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
        Material.BEETROOTS, Material.NETHER_WART, Material.COCOA);
    public Progression(AstralPlugin plugin) {
        this.plugin = plugin; naturalMob = new NamespacedKey(plugin, "natural_hunt");
        long period = Math.max(1, Math.min(10, plugin.getConfig().getInt("activity-flush-seconds", 3))) * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, period, period);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void form(BlockFormEvent e) {
        if (!plugin.sky().isSky(e.getBlock().getLocation())) return;
        Island i = plugin.sky().at(e.getBlock().getLocation());
        if (i == null || i.status() != Status.READY) { e.setCancelled(true); return; }
        if (e.getNewState().getType() == Material.COBBLESTONE || e.getNewState().getType() == Material.STONE) {
            OreTable.Ore ore = OreTable.roll(i.rank(Upgrade.GENERATOR), i.focus(), i.constellation(), ThreadLocalRandom.current().nextInt(10_000));
            e.getNewState().setType(Material.valueOf(ore.name()));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void provenance(BlockFormEvent e) {
        if (!plugin.sky().isSky(e.getBlock().getLocation()) || plugin.sky().at(e.getBlock().getLocation()) == null) return;
        if (isOre(e.getNewState().getType())) plugin.nodes().mark(e.getBlock());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) { if (plugin.sky().isSky(e.getBlock().getLocation())) plugin.nodes().remove(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (!plugin.sky().isSky(e.getBlock().getLocation())) return;
        boolean node = plugin.nodes().remove(e.getBlock());
        if (node && isOre(e.getBlock().getType())) record(e.getPlayer(), e.getBlock().getLocation(), Activity.MINE);
        if (CROPS.contains(e.getBlock().getType()) && e.getBlock().getBlockData() instanceof Ageable age && age.getAge() == age.getMaximumAge()) record(e.getPlayer(), e.getBlock().getLocation(), Activity.FARM);
    }
    private boolean isOre(Material material) { for (var ore : OreTable.Ore.values()) if (material.name().equals(ore.name())) return true; return false; }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (!plugin.sky().isSky(e.getBlock().getLocation())) return;
        e.getBlocks().forEach(b -> { plugin.nodes().remove(b); plugin.nodes().remove(b.getRelative(e.getDirection())); });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) {
        if (!plugin.sky().isSky(e.getBlock().getLocation())) return;
        e.getBlocks().forEach(b -> { plugin.nodes().remove(b); plugin.nodes().remove(b.getRelative(e.getDirection())); });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent e) { if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && e.getCaught() instanceof Item) record(e.getPlayer(), e.getCaught().getLocation(), Activity.FISH); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent e) {
        if (plugin.sky().isSky(e.getLocation()) && (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS))
            e.getEntity().getPersistentDataContainer().set(naturalMob, PersistentDataType.BYTE, (byte) 1);
    }
    @EventHandler public void hunt(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && e.getEntity() instanceof Monster && e.getEntity().getPersistentDataContainer().has(naturalMob, PersistentDataType.BYTE)) record(killer, e.getEntity().getLocation(), Activity.HUNT);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void berries(PlayerHarvestBlockEvent e) { record(e.getPlayer(), e.getHarvestedBlock().getLocation(), Activity.FARM); }
    private void record(Player p, Location location, Activity activity) {
        if (p.getGameMode() != GameMode.SURVIVAL || !plugin.sky().isSky(location)) return;
        Island i = plugin.sky().at(location);
        if (i == null || i.status() != Status.READY || !i.members().containsKey(p.getUniqueId())) return;
        ActivityKey key = new ActivityKey(i.id(), p.getUniqueId());
        Counts old = pending.getOrDefault(key, new Counts(0, 0, 0, 0));
        if (old.get(activity) < 200) pending.put(key, old.add(activity));
    }
    public void flush() {
        if (pending.isEmpty()) return;
        Map<ActivityKey, Counts> batch = Map.copyOf(pending); pending.clear();
        Universe before = plugin.engine().view();
        plugin.engine().activity(batch).whenComplete((change, error) -> {
            if (error != null) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Activity commit failed; no coins were published", error); return; }
            plugin.onMain(() -> {
                for (Island i : change.state().islands().values()) {
                    Island old = before.islands().get(i.id());
                    if (old != null && i.level() > old.level()) {
                        for (UUID member : i.members().keySet()) {
                            Player p = Bukkit.getPlayer(member); if (p == null) continue;
                            plugin.tell(p, "Island level " + i.level() + "! Constellation " + i.constellation() + "/5.");
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.1f);
                        }
                        Bukkit.getPluginManager().callEvent(new dev.astral.api.IslandLevelUpEvent(i, old.level()));
                    }
                }
            });
        });
    }
}
