package dev.astral.paper;

import dev.astral.domain.Model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Deny-by-default protection across player actions and cross-plot environmental propagation. */
public final class Protection implements Listener {
    private final AstralPlugin plugin;
    private final IslandWorld sky;
    private final Map<UUID, Long> notified = new HashMap<>();
    public Protection(AstralPlugin plugin) { this.plugin = plugin; sky = plugin.sky(); }
    private boolean deny(Player player, Location location) {
        if (sky.canBuild(player, location)) return false;
        long now = System.currentTimeMillis();
        if (now - notified.getOrDefault(player.getUniqueId(), 0L) > 2_000) {
            plugin.tell(player, "This area is protected. Only island members can build or use containers."); notified.put(player.getUniqueId(), now);
        }
        return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
        if (e instanceof BlockMultiPlaceEvent multi && multi.getReplacedBlockStates().stream().anyMatch(b -> !sky.canBuild(e.getPlayer(), b.getLocation()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && deny(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.DENY); e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation()) || deny(e.getPlayer(), e.getBlockClicked().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityInteract(PlayerInteractEntityEvent e) { if (deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void armorStand(PlayerArmorStandManipulateEvent e) { if (deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void shear(PlayerShearEntityEvent e) { if (deny(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void harvest(PlayerHarvestBlockEvent e) { if (deny(e.getPlayer(), e.getHarvestedBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bed(PlayerBedEnterEvent e) { if (deny(e.getPlayer(), e.getBed().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hang(HangingPlaceEvent e) { if (e.getPlayer() == null ? sky.isSky(e.getEntity().getLocation()) : deny(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void unhang(HangingBreakEvent e) {
        if (!sky.isSky(e.getEntity().getLocation())) return;
        if (e instanceof HangingBreakByEntityEvent by && by.getRemover() instanceof Player p) { if (deny(p, e.getEntity().getLocation())) e.setCancelled(true); }
        else e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void flow(BlockFromToEvent e) { if (sky.isSky(e.getBlock().getLocation()) && !sky.sameIsland(e.getBlock().getLocation(), e.getToBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spread(BlockSpreadEvent e) { if (sky.isSky(e.getBlock().getLocation()) && !sky.sameIsland(e.getSource().getLocation(), e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent e) { if (sky.isSky(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (!sky.isSky(e.getBlock().getLocation())) return;
        if (e.getPlayer() == null || deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (sky.isSky(e.getBlock().getLocation()) && e.getBlocks().stream().anyMatch(b -> !sky.sameIsland(e.getBlock().getLocation(), b.getLocation()) || !sky.sameIsland(e.getBlock().getLocation(), b.getRelative(e.getDirection()).getLocation()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pistonBack(BlockPistonRetractEvent e) {
        if (sky.isSky(e.getBlock().getLocation()) && e.getBlocks().stream().anyMatch(b -> !sky.sameIsland(e.getBlock().getLocation(), b.getLocation()) || !sky.sameIsland(e.getBlock().getLocation(), b.getRelative(e.getDirection()).getLocation()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void structure(StructureGrowEvent e) { if (sky.isSky(e.getLocation()) && e.getBlocks().stream().anyMatch(b -> !sky.sameIsland(e.getLocation(), b.getLocation()))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fertilize(BlockFertilizeEvent e) {
        if (!sky.isSky(e.getBlock().getLocation())) return;
        if (e.getPlayer() != null && deny(e.getPlayer(), e.getBlock().getLocation()) || e.getBlocks().stream().anyMatch(b -> !sky.sameIsland(e.getBlock().getLocation(), b.getLocation()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void sponge(SpongeAbsorbEvent e) { if (sky.isSky(e.getBlock().getLocation()) && e.getBlocks().stream().anyMatch(b -> !sky.sameIsland(e.getBlock().getLocation(), b.getLocation()))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) { if (sky.isSky(e.getLocation())) { e.blockList().clear(); e.setCancelled(true); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent e) { if (sky.isSky(e.getBlock().getLocation())) { e.blockList().clear(); e.setCancelled(true); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void change(EntityChangeBlockEvent e) { if (sky.isSky(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent e) {
        Location a = e.getSource().getLocation(), b = e.getDestination().getLocation();
        if ((sky.isSky(a) || sky.isSky(b)) && !sky.sameIsland(a, b)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && deny(p, e.getItem().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        if (!sky.isSky(e.getEntity().getLocation())) return;
        Player attacker = e.getDamager() instanceof Player p ? p : null;
        if (e.getDamager() instanceof Projectile shot) { ProjectileSource shooter = shot.getShooter(); if (shooter instanceof Player p) attacker = p; }
        if (attacker != null && (e.getEntity() instanceof Player || deny(attacker, e.getEntity().getLocation()))) e.setCancelled(true);
        if (e.getEntity() instanceof Player p && !sky.canBuild(p, p.getLocation())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) { if (e.getTarget() instanceof Player p && sky.isSky(p.getLocation()) && !sky.canBuild(p, p.getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent e) {
        if (!sky.isSky(e.getLocation())) return;
        Island i = sky.at(e.getLocation());
        if (i == null || i.status() != Status.READY) { e.setCancelled(true); return; }
        int cap = Math.max(1, plugin.getConfig().getInt("world.mob-cap-per-island", 40));
        long count = sky.world().getNearbyEntities(e.getLocation(), i.radius() * 2.0, 384, i.radius() * 2.0).stream()
            .filter(entity -> entity instanceof LivingEntity && !(entity instanceof Player) && sky.at(entity.getLocation()) != null && sky.at(entity.getLocation()).id().equals(i.id())).count();
        if (count >= cap) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) { if (sky.isSky(e.getFrom()) || sky.isSky(e.getTo())) { e.setCancelled(true); plugin.tell(e.getPlayer(), "Portals are disabled in Astral Islands. Use /is home."); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPortal(EntityPortalEvent e) { if (sky.isSky(e.getFrom()) || sky.isSky(e.getTo())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) { if (sky.isSky(e.getTo()) && !sky.canEnter(e.getPlayer(), sky.at(e.getTo()))) { e.setCancelled(true); plugin.tell(e.getPlayer(), "This island is private or outside a plot."); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        Location to = e.getTo(); if (to == null || !sky.isSky(to)) return;
        if (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY() && e.getFrom().getBlockZ() == to.getBlockZ()) return;
        Island i = sky.at(to);
        if (i != null && !sky.canEnter(e.getPlayer(), i)) e.setTo(e.getFrom());
    }
    @EventHandler public void death(PlayerDeathEvent e) {
        if (sky.isSky(e.getEntity().getLocation()) && plugin.getConfig().getBoolean("world.keep-inventory", true)) {
            e.setKeepInventory(true); e.getDrops().clear(); e.setKeepLevel(true); e.setDroppedExp(0);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { notified.remove(e.getPlayer().getUniqueId()); }
}
