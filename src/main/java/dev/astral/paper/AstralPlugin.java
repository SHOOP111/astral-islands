package dev.astral.paper;

import dev.astral.api.*;
import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.service.Engine;
import dev.astral.storage.SqliteStore;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class AstralPlugin extends JavaPlugin implements Listener {
    private Engine engine;
    private IslandWorld sky;
    private NodeLedger nodes;
    private Menus menus;
    private Progression progression;
    private Hud hud;
    private boolean stopping;
    private final Map<UUID, Long> rescued = new HashMap<>();
    public Engine engine() { return engine; }
    public IslandWorld sky() { return sky; }
    public NodeLedger nodes() { return nodes; }
    public Menus menus() { return menus; }
    @Override public void onEnable() {
        saveDefaultConfig(); SqliteStore opened = null;
        try {
            opened = new SqliteStore(getDataFolder().toPath().resolve("astral.db"));
            engine = new Engine(opened, Clock.systemUTC());
            sky = new IslandWorld(this); nodes = new NodeLedger(this); menus = new Menus(this);
            progression = new Progression(this); hud = new Hud(this);
            IslandCommand executor = new IslandCommand(this);
            Objects.requireNonNull(getCommand("island")).setExecutor(executor); getCommand("island").setTabCompleter(executor);
            Bukkit.getPluginManager().registerEvents(this, this);
            Bukkit.getPluginManager().registerEvents(new Protection(this), this);
            Bukkit.getPluginManager().registerEvents(progression, this);
            Bukkit.getPluginManager().registerEvents(menus, this);
            Bukkit.getServicesManager().register(SkyblockApi.class, new SkyblockApi() {
                @Override public Optional<Island> islandOf(UUID player) { return Optional.ofNullable(engine.view().islandOf(player)); }
                @Override public Optional<Island> island(UUID id) { return Optional.ofNullable(engine.view().islands().get(id)); }
                @Override public Optional<Profile> profile(UUID player) { return Optional.ofNullable(engine.view().profiles().get(player)); }
                @Override public List<Island> leaderboard(int limit) {
                    return engine.view().islands().values().stream().filter(i -> i.status() == Status.READY)
                        .sorted(Comparator.comparingLong(Island::xp).reversed().thenComparing(Island::id)).limit(Math.max(0, Math.min(100, limit))).toList();
                }
            }, this, ServicePriority.Normal);
            for (Island i : engine.view().islands().values()) if (i.status() == Status.PROVISIONING) sky.provision(i.id())
                .whenComplete((ready, error) -> { if (error != null) getLogger().log(Level.SEVERE, "Provision recovery failed for " + i.id(), error); });
            Bukkit.getOnlinePlayers().forEach(this::register);
            Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 10, 10);
            getLogger().info("ASTRAL_ENABLED — transactional ledger ready; " + engine.view().islands().size() + " islands loaded.");
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Astral failed to start. Existing data will NOT be reset.", ex);
            if (engine == null && opened != null) try { opened.close(); } catch (Exception close) { ex.addSuppressed(close); }
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        stopping = true;
        if (hud != null) hud.close();
        if (sky != null) sky.close();
        if (progression != null) progression.flush();
        if (engine != null) try { engine.close(); } catch (Exception ex) { getLogger().log(Level.SEVERE, "Ledger shutdown failed", ex); }
        Bukkit.getServicesManager().unregisterAll(this); rescued.clear();
    }
    @Override public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) { return new VoidGenerator(); }
    public void onMain(Runnable task) {
        if (stopping || !isEnabled()) return;
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(this, () -> { if (!stopping) task.run(); });
    }
    public String number(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    public void tell(CommandSender sender, String text) {
        sender.sendMessage(Component.text("✦ ", NamedTextColor.LIGHT_PURPLE).append(Component.text(text, NamedTextColor.GRAY)));
    }
    public void failure(CommandSender sender, Throwable error) {
        Throwable cause = error; while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof RuleViolation) tell(sender, cause.getMessage());
        else { tell(sender, "The operation could not be committed. No success was recorded. Contact an administrator."); getLogger().log(Level.SEVERE, "Astral operation failed", cause); }
    }
    private void register(Player p) {
        engine.register(p.getUniqueId(), p.getName()).whenComplete((change, error) -> onMain(() -> {
            if (error != null) failure(p, error);
            else if (p.isOnline() && change.state().profiles().get(p.getUniqueId()).invitedIsland() != null
                && change.state().profiles().get(p.getUniqueId()).inviteExpires() > System.currentTimeMillis()) tell(p, "You have an island invitation. /is accept");
        }));
    }
    @EventHandler public void join(PlayerJoinEvent e) { register(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) { hud.remove(e.getPlayer()); rescued.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void respawn(PlayerRespawnEvent e) {
        if (!sky.isSky(e.getPlayer().getLocation())) return;
        Island i = engine.view().islandOf(e.getPlayer().getUniqueId());
        if (i != null && i.status() == Status.READY && sky.safe(sky.home(i))) e.setRespawnLocation(sky.home(i));
        else Bukkit.getWorlds().stream().filter(w -> w != sky.world()).findFirst().ifPresent(w -> e.setRespawnLocation(w.getSpawnLocation()));
    }
    private void heartbeat() {
        long now = System.currentTimeMillis(); int voidY = Math.max(-60, Math.min(50, getConfig().getInt("world.void-rescue-y", -32)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!sky.isSky(p.getLocation())) continue; Island occupied = sky.at(p.getLocation());
            if (p.getY() < voidY || occupied != null && !sky.canEnter(p, occupied)) {
                if (now - rescued.getOrDefault(p.getUniqueId(), 0L) >= 3_000) { rescued.put(p.getUniqueId(), now); sky.rescue(p); }
            }
        }
    }
}
