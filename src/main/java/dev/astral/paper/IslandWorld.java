package dev.astral.paper;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class IslandWorld {
    private final AstralPlugin plugin;
    private final World world;
    private final Queue<BuildJob> jobs = new ArrayDeque<>();
    private final Map<UUID, CompletableFuture<Island>> provisioning = new HashMap<>();
    private final int budget;
    private static final class BuildJob {
        final Island island; final List<IslandPlan.BlockSpec> blocks; final List<Chunk> chunks;
        final CompletableFuture<Island> future; int cursor;
        BuildJob(Island i, List<Chunk> chunks, CompletableFuture<Island> future) {
            island = i; blocks = IslandPlan.forTemplate(i.template()); this.chunks = chunks; this.future = future;
        }
    }
    public IslandWorld(AstralPlugin plugin) {
        this.plugin = plugin;
        String name = plugin.getConfig().getString("world-name", "astral_islands");
        if (Bukkit.getWorld(name) != null) {
            world = Bukkit.getWorld(name);
            if (!(world.getGenerator() instanceof VoidGenerator)) throw new IllegalStateException("Refusing non-Astral world: " + name);
        } else world = new WorldCreator(name).generator(new VoidGenerator()).environment(World.Environment.NORMAL).createWorld();
        if (world == null) throw new IllegalStateException("Could not create sky world");
        world.setGameRule(GameRule.DO_FIRE_TICK, false); world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false); world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setSpawnFlags(true, true); world.setSpawnLocation(0, 101, 0);
        budget = Math.max(50, Math.min(2_000, plugin.getConfig().getInt("world.build-blocks-per-tick", 350)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    public World world() { return world; }
    public boolean isSky(Location l) { return l != null && l.getWorld() == world; }
    public Island at(Location l) { return isSky(l) ? plugin.engine().view().at(l.getX(), l.getZ()) : null; }
    public boolean canEnter(Player p, Island i) {
        return p.hasPermission("astral.bypass") || i != null && i.status() == Status.READY && (i.publicVisits() || i.members().containsKey(p.getUniqueId()));
    }
    public boolean canBuild(Player p, Location l) {
        if (!isSky(l) || p.hasPermission("astral.bypass")) return true;
        Island i = at(l); return i != null && i.status() == Status.READY && i.members().containsKey(p.getUniqueId());
    }
    public boolean sameIsland(Location a, Location b) {
        Island first = at(a), second = at(b);
        return first != null && second != null && first.status() == Status.READY && first.id().equals(second.id());
    }
    public CompletableFuture<Island> provision(UUID id) {
        if (provisioning.containsKey(id)) return provisioning.get(id);
        Island island = plugin.engine().view().islands().get(id);
        if (island == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown island"));
        if (island.status() == Status.READY) return CompletableFuture.completedFuture(island);
        CompletableFuture<Island> result = new CompletableFuture<>(); provisioning.put(id, result);
        Grid.Cell cell = Grid.at(island.plot());
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int cx = (cell.centerX() - 8) >> 4; cx <= (cell.centerX() + 8) >> 4; cx++)
            for (int cz = (cell.centerZ() - 8) >> 4; cz <= (cell.centerZ() + 8) >> 4; cz++) loads.add(world.getChunkAtAsync(cx, cz, true));
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((unused, error) -> plugin.onMain(() -> {
            if (error != null) { provisioning.remove(id); result.completeExceptionally(error); return; }
            List<Chunk> chunks = loads.stream().map(CompletableFuture::join).toList();
            chunks.forEach(c -> c.addPluginChunkTicket(plugin));
            jobs.add(new BuildJob(island, chunks, result));
        }));
        return result;
    }
    private void tick() {
        int remaining = budget;
        while (!jobs.isEmpty() && remaining > 0) {
            BuildJob job = jobs.peek(); Grid.Cell c = Grid.at(job.island.plot());
            try {
                while (job.cursor < job.blocks.size() && remaining-- > 0) {
                    var spec = job.blocks.get(job.cursor++);
                    Block block = world.getBlockAt(c.centerX() + spec.x(), spec.y(), c.centerZ() + spec.z());
                    block.setType(Material.valueOf(spec.material()), false);
                    if (block.getBlockData() instanceof Leaves leaves) { leaves.setPersistent(true); block.setBlockData(leaves, false); }
                }
                if (job.cursor == job.blocks.size()) {
                    jobs.remove();
                    Chest chest = (Chest) world.getBlockAt(c.centerX() + 3, 101, c.centerZ()).getState();
                    chest.getBlockInventory().clear();
                    chest.getBlockInventory().addItem(new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.ICE, 2),
                        new ItemStack(Material.DIRT, 32), new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.OAK_SAPLING, 2),
                        new ItemStack(Material.WHEAT_SEEDS, 16), new ItemStack(Material.SUGAR_CANE, 2), new ItemStack(Material.CACTUS),
                        new ItemStack(Material.MELON_SEEDS, 2), new ItemStack(Material.PUMPKIN_SEEDS, 2), new ItemStack(Material.BONE_MEAL, 16),
                        new ItemStack(Material.BREAD, 16));
                    // Flush the world before committing READY; startup replays a partially built schematic.
                    world.save();
                    plugin.engine().act(job.island.owner(), Action.target(Type.ACTIVATE, job.island.id())).whenComplete((change, error) -> plugin.onMain(() -> {
                        job.chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin)); provisioning.remove(job.island.id());
                        if (error != null) job.future.completeExceptionally(error);
                        else {
                            Island ready = change.state().islands().get(job.island.id());
                            Bukkit.getPluginManager().callEvent(new dev.astral.api.IslandReadyEvent(ready));
                            job.future.complete(ready);
                        }
                    }));
                }
            } catch (Exception ex) {
                jobs.remove(job); job.chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin)); provisioning.remove(job.island.id()); job.future.completeExceptionally(ex);
            }
        }
    }
    public Location home(Island i) {
        Grid.Cell c = Grid.at(i.plot()); Anchor h = i.home();
        return new Location(world, c.centerX() + h.x(), h.y(), c.centerZ() + h.z(), h.yaw(), h.pitch());
    }
    public void teleport(Player player, Island i) {
        if (i == null || !canEnter(player, i)) { plugin.tell(player, "This island is private or not ready."); return; }
        Location target = home(i);
        world.getChunkAtAsync(target).whenComplete((chunk, error) -> plugin.onMain(() -> {
            if (!player.isOnline()) return;
            Island current = plugin.engine().view().islands().get(i.id());
            if (error != null || !canEnter(player, current)) { plugin.tell(player, "Travel failed or access changed. Try again."); return; }
            if (!safe(target)) { plugin.tell(player, "The island home is obstructed or has no safe floor. Ask a steward to /is sethome."); return; }
            player.teleportAsync(target).thenAccept(ok -> plugin.onMain(() -> {
                if (ok && player.isOnline()) { player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f); plugin.tell(player, "Welcome to " + current.name() + "."); }
            }));
        }));
    }
    public boolean safe(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable() && l.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }
    public void rescue(Player p) {
        if (p.hasPermission("astral.bypass") && p.getY() >= 0) return;
        Island i = plugin.engine().view().islandOf(p.getUniqueId());
        if (i != null && i.status() == Status.READY && safe(home(i))) teleport(p, i);
        else {
            World fallback = Bukkit.getWorlds().stream().filter(w -> w != world).findFirst().orElse(null);
            if (fallback != null) p.teleportAsync(fallback.getSpawnLocation());
        }
        p.setFallDistance(0);
    }
    public void close() {
        jobs.forEach(job -> { job.chunks.forEach(c -> c.removePluginChunkTicket(plugin)); job.future.completeExceptionally(new CancellationException("Server stopping")); });
        jobs.clear(); provisioning.values().forEach(f -> f.completeExceptionally(new CancellationException("Server stopping"))); provisioning.clear();
    }
}
