package dev.astral.paper;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.event.block.*;

/** Opt-in CI-only real Paper integration probe. Never runs automatically on a production server. */
final class ServerSmoke {
    private static final UUID BOT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private final AstralPlugin plugin;
    ServerSmoke(AstralPlugin plugin) { this.plugin = plugin; }
    void run() {
        Island existing = plugin.engine().view().islandOf(BOT);
        if (existing != null) { plugin.onMain(() -> verify(existing, true)); return; }
        plugin.engine().register(BOT, "CI_Astronaut")
            .thenCompose(c -> plugin.engine().act(BOT, Action.text(Type.CREATE, "crystal")))
            .whenComplete((created, error) -> plugin.onMain(() -> {
                if (error != null) { fail(error); return; }
                plugin.sky().provision(created.islandId()).whenComplete((ready, failure) -> plugin.onMain(() -> {
                    if (failure != null) { fail(failure); return; }
                    plugin.engine().act(BOT, Action.amount(Type.DEPOSIT, 400)).whenComplete((result, depositError) -> plugin.onMain(() -> {
                        if (depositError != null) fail(depositError); else verify(result.state().islandOf(BOT), false);
                    }));
                }));
            }));
    }
    private void verify(Island island, boolean restart) {
        try {
            check(island.status() == Status.READY, "island activation");
            Grid.Cell c = Grid.at(island.plot()); World w = plugin.sky().world();
            check(w.getBlockAt(c.centerX(), 100, c.centerZ()).getType() == Material.GRASS_BLOCK, "terrain");
            check(plugin.sky().safe(plugin.sky().home(island)), "safe home");
            check(w.getBlockAt(c.centerX() + 3, 101, c.centerZ()).getState() instanceof Chest, "starter chest");
            Chest chest = (Chest) w.getBlockAt(c.centerX() + 3, 101, c.centerZ()).getState();
            check(chest.getBlockInventory().contains(Material.LAVA_BUCKET), "starter inventory");
            check(island.bank() == 400 && plugin.engine().view().profiles().get(BOT).wallet() == 600, "durable transfer");
            var node = w.getBlockAt(c.centerX() + 1, 101, c.centerZ());
            if (!restart) {
                var newState = node.getState(); newState.setType(Material.COBBLESTONE);
                BlockFormEvent form = new BlockFormEvent(node, newState); Bukkit.getPluginManager().callEvent(form);
                check(!form.isCancelled(), "generator form"); form.getNewState().update(true, false);
                check(plugin.nodes().contains(node), "node provenance created");
                check(plugin.nodes().remove(node) && !plugin.nodes().remove(node), "provenance consumed exactly once"); plugin.nodes().mark(node);
            } else check(plugin.nodes().contains(node), "node provenance survived restart");
            var edge = w.getBlockAt(c.centerX() + island.radius(), 100, c.centerZ());
            BlockFromToEvent flow = new BlockFromToEvent(edge, org.bukkit.block.BlockFace.EAST); Bukkit.getPluginManager().callEvent(flow);
            check(flow.isCancelled(), "fluid boundary protection");
            check(Bukkit.getServicesManager().load(dev.astral.api.SkyblockApi.class) != null, "public API registered");
            w.save(); plugin.getLogger().info("ASTRAL_CI_OK " + (restart ? "RESTART" : "FIRST_BOOT"));
        } catch (Exception ex) { fail(ex); }
    }
    private void check(boolean condition, String what) { if (!condition) throw new IllegalStateException("CI assertion failed: " + what); }
    private void fail(Throwable error) { plugin.getLogger().log(Level.SEVERE, "ASTRAL_CI_FAILED", error); }
}
