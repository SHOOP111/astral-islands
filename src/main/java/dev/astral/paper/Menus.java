package dev.astral.paper;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

/** Server-owned inventory sessions; no item names or client NBT are interpreted as commands. */
public final class Menus implements Listener {
    private final AstralPlugin plugin;
    private static final class Session implements InventoryHolder {
        final UUID owner; final Map<Integer, Consumer<Player>> actions = new HashMap<>(); final Inventory inventory;
        Session(Player player, String title) {
            owner = player.getUniqueId(); inventory = Bukkit.createInventory(this, 54, Component.text("✦ " + title, NamedTextColor.DARK_PURPLE));
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    public Menus(AstralPlugin plugin) { this.plugin = plugin; }
    private Session page(Player p, String title) {
        Session s = new Session(p, title);
        for (int slot = 0; slot < 54; slot++) if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8)
            s.inventory.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        button(s, 49, Material.COMPASS, "Dashboard", List.of("Return to your island overview"), this::dashboard);
        return s;
    }
    private void button(Session s, int slot, Material icon, String name, List<String> lore, Consumer<Player> action) {
        s.inventory.setItem(slot, item(icon, name, lore)); if (action != null) s.actions.put(slot, action);
    }
    private ItemStack item(Material type, String name, List<String> lore) {
        ItemStack item = new ItemStack(type); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta); return item;
    }
    private Consumer<Player> command(String args) { return p -> { p.closeInventory(); p.performCommand("is " + args); }; }
    public void dashboard(Player p) {
        Island i = plugin.engine().view().islandOf(p.getUniqueId());
        if (i == null) { templates(p); return; }
        Profile profile = plugin.engine().view().profiles().get(p.getUniqueId()); if (profile == null) return;
        Session s = page(p, "Astral Islands");
        button(s, 4, Material.NETHER_STAR, i.name(), List.of("Level " + i.level() + " · XP " + plugin.number(i.xp()),
            "Constellation " + i.constellation() + "/5 · " + i.focus(), "Plot radius " + i.radius() + " · " + i.status()), null);
        button(s, 20, Material.ENDER_PEARL, "Return Home", List.of("Safe, asynchronous travel"), command("home"));
        button(s, 22, Material.GOLD_INGOT, "Treasury", List.of("Wallet: " + plugin.number(profile.wallet()), "Island bank: " + plugin.number(i.bank()), "Deposits, withdrawals and payments"), this::bank);
        button(s, 24, Material.ENCHANTING_TABLE, "Island Upgrades", List.of("Horizon · Generator · Expedition docks"), this::upgrades);
        button(s, 29, Material.WRITABLE_BOOK, "Daily Contracts", List.of("Three rotating objectives", "Shared island progress · resets 00:00 UTC"), this::contracts);
        button(s, 31, Material.END_CRYSTAL, "Star Expeditions", List.of("Explore while you are offline", "Discovery relics: " + i.discoveries()), this::expeditions);
        button(s, 33, Material.PLAYER_HEAD, "Crew & Settings", List.of(i.members().size() + "/" + i.memberLimit() + " members", "Visits: " + (i.publicVisits() ? "Public" : "Private")), this::settings);
        button(s, 40, Material.CLOCK, "Daily Reward", List.of("Claim wallet coins", "Streak: " + profile.streak() + "/30"), command("daily"));
        button(s, 45, Material.SPYGLASS, "Explore the Sky", List.of("Visit public islands and view rankings"), player -> browse(player, 0));
        p.openInventory(s.inventory);
    }
    public void templates(Player p) {
        Session s = page(p, "Choose Your Origin");
        button(s, 20, Material.OAK_SAPLING, "Verdant Grove", List.of("A suspended garden with oak roots", "Classic resources · sheltered starter tree"), command("create grove"));
        button(s, 22, Material.AMETHYST_CLUSTER, "Crystal Sanctuary", List.of("Amethyst spires and a spruce canopy", "Cool light above an endless sky"), command("create crystal"));
        button(s, 24, Material.SHROOMLIGHT, "Ember Bastion", List.of("Basalt roots, blackstone and warm light", "Fertile core with all starter resources"), command("create ember"));
        button(s, 31, Material.PAPER, "Co-op Instead?", List.of("An owner can /is invite " + p.getName(), "Then accept with /is accept"), command("accept"));
        button(s, 40, Material.SPYGLASS, "Explore Public Islands", List.of("Discover the community before creating"), player -> browse(player, 0));
        p.openInventory(s.inventory);
    }
    public void bank(Player p) {
        Island i = island(p); if (i == null) return; Session s = page(p, "Treasury");
        button(s, 4, Material.GOLD_BLOCK, "Bank: " + plugin.number(i.bank()), List.of("Money commits atomically before confirmation", "Withdrawals require owner or steward"), null);
        button(s, 20, Material.EMERALD, "Deposit 250", List.of("From your wallet to the island"), command("bank deposit 250"));
        button(s, 21, Material.EMERALD_BLOCK, "Deposit 1,000", List.of("/is bank deposit <amount>"), command("bank deposit 1000"));
        button(s, 23, Material.GOLD_INGOT, "Withdraw 250", List.of("Into your wallet"), command("bank withdraw 250"));
        button(s, 24, Material.GOLD_BLOCK, "Withdraw 1,000", List.of("/is bank withdraw <amount>"), command("bank withdraw 1000"));
        button(s, 31, Material.PAPER, "Pay a Friend", List.of("/is pay <player> <amount>", "Both wallets update in one transaction"), null);
        p.openInventory(s.inventory);
    }
    public void upgrades(Player p) {
        Island i = island(p); if (i == null) return; Session s = page(p, "Celestial Engineering");
        int slot = 20;
        for (Upgrade u : Upgrade.values()) {
            int rank = i.rank(u); String detail = switch (u) { case HORIZON -> "Radius: " + i.radius() + " blocks"; case GENERATOR -> "Better natural cobble-generator ores"; case EXPEDITION -> "Docks: " + (1 + rank); };
            Material icon = switch (u) { case HORIZON -> Material.GRASS_BLOCK; case GENERATOR -> Material.DIAMOND_ORE; case EXPEDITION -> Material.END_CRYSTAL; };
            button(s, slot, icon, u + " · " + rank + "/" + u.max, List.of(detail, rank == u.max ? "Fully upgraded" : "Cost: " + plugin.number(u.cost(rank)) + " bank coins"), command("upgrade " + u.name())); slot += 2;
        }
        button(s, 31, Material.NETHER_STAR, "Constellations", List.of("Every 10 levels unlocks a star", "More crew slots, contract coins and discoveries", "Current: " + i.constellation() + "/5"), null);
        p.openInventory(s.inventory);
    }
    public void contracts(Player p) {
        Island i = island(p); if (i == null) return; Session s = page(p, "Daily Contracts");
        long day = Math.floorDiv(System.currentTimeMillis(), Model.DAY); int slot = 20;
        for (Contract c : Contract.values()) if (c.active(day)) {
            long progress = i.contractDay() == day ? i.progress().getOrDefault(c.activity, 0L) : 0;
            boolean claimed = i.contractDay() == day && i.claimed().contains(c);
            Material icon = switch (c.activity) { case MINE -> Material.IRON_PICKAXE; case FARM -> Material.WHEAT; case FISH -> Material.FISHING_ROD; case HUNT -> Material.IRON_SWORD; };
            button(s, slot, claimed ? Material.LIME_DYE : icon, c.name(), List.of(Math.min(progress, c.goal) + "/" + c.goal + " " + c.activity,
                "+" + (c.coins + i.constellation() * 100L) + " bank coins · +" + c.xp + " XP", claimed ? "Already claimed" : "Click to claim when complete"), command("claim " + c.name())); slot += 2;
        }
        button(s, 31, Material.CLOCK, "UTC Rotation", List.of("Mining counts only tracked generator blocks", "Harvest mature crops · catch fish · hunt natural mobs", "Owners and stewards claim shared rewards"), null);
        p.openInventory(s.inventory);
    }
    public void expeditions(Player p) {
        Island i = island(p); if (i == null) return; Session s = page(p, "The Star Docks"); int slot = 20;
        for (Voyage v : Voyage.values()) {
            button(s, slot, Material.FIREWORK_STAR, v.name(), List.of("Cost: " + v.cost + " · Return: " + v.duration / 60_000 + " min",
                "Base reward: " + v.reward + " coins + " + v.xp + " XP", "Discoveries can increase the return by 50%"), command("expedition launch " + v.name())); slot += 2;
        }
        int tripSlot = 28;
        for (Expedition e : i.expeditions()) {
            long left = Math.max(0, e.readyAt() - System.currentTimeMillis());
            button(s, tripSlot++, left == 0 ? Material.CHEST : Material.CLOCK, e.voyage().name(), List.of(left == 0 ? "Returned! Click to collect all returns" : "Returns in " + (left + 59_999) / 60_000 + " min"), command("expedition claim"));
        }
        button(s, 40, Material.ENDER_CHEST, "Collect All Returns", List.of(i.expeditions().size() + "/" + (1 + i.rank(Upgrade.EXPEDITION)) + " occupied docks", "Rewards and discovery results survive restarts"), command("expedition claim"));
        p.openInventory(s.inventory);
    }
    public void settings(Player p) {
        Island i = island(p); if (i == null) return; Session s = page(p, "Crew & Attunement");
        button(s, 20, Material.OAK_DOOR, "Visits: " + (i.publicVisits() ? "PUBLIC" : "PRIVATE"), List.of("Visitors cannot build, loot or hurt your mobs"), command("visitors"));
        button(s, 22, Material.RESPAWN_ANCHOR, "Set Home", List.of("Stand on a safe floor in your island"), command("sethome"));
        button(s, 24, Material.MAP, "Toggle HUD", List.of("A sidebar only inside the sky world", "Respects existing third-party sidebars"), command("hud"));
        int slot = 29;
        for (Focus f : Focus.values()) {
            String effect = switch (f) { case SOLAR -> "2 coins per tracked generator block"; case LUNAR -> "Better diamonds and expedition discoveries"; case VERDANT -> "3 XP per activity instead of 2"; };
            button(s, slot++, Material.AMETHYST_SHARD, f + (f == i.focus() ? " · ACTIVE" : ""), List.of(effect, "Switch: 500 bank coins · 6h cooldown"), command("attune " + f.name()));
        }
        List<String> crew = new ArrayList<>(); i.members().forEach((id, role) -> {
            Profile member = plugin.engine().view().profiles().get(id); crew.add((member == null ? id.toString() : member.name()) + " · " + role);
        });
        crew.add("/is invite, /is kick, /is role, /is transfer");
        button(s, 40, Material.PLAYER_HEAD, "Crew " + i.members().size() + "/" + i.memberLimit(), crew, null);
        p.openInventory(s.inventory);
    }
    public void browse(Player p, int page) {
        List<Island> all = plugin.engine().view().islands().values().stream().filter(i -> i.status() == Status.READY)
            .sorted(Comparator.comparingLong(Island::xp).reversed().thenComparing(Island::id)).toList();
        int last = Math.max(0, (all.size() - 1) / 21); int current = Math.max(0, Math.min(last, page));
        Session s = page(p, "Sky Atlas · " + (current + 1) + "/" + (last + 1));
        for (int n = current * 21; n < Math.min(all.size(), current * 21 + 21); n++) {
            Island i = all.get(n); int local = n - current * 21; int slot = 10 + (local / 7) * 9 + local % 7;
            button(s, slot, i.publicVisits() ? Material.GRASS_BLOCK : Material.BARRIER, "#" + (n + 1) + " " + i.name(),
                List.of("Level " + i.level() + " · " + i.discoveries() + " discoveries", i.publicVisits() ? "Click to visit" : "Private island"), player -> { player.closeInventory(); plugin.sky().teleport(player, i); });
        }
        if (current > 0) button(s, 45, Material.ARROW, "Previous", List.of(), player -> browse(player, current - 1));
        if (current < last) button(s, 53, Material.ARROW, "Next", List.of(), player -> browse(player, current + 1));
        p.openInventory(s.inventory);
    }
    private Island island(Player p) { Island i = plugin.engine().view().islandOf(p.getUniqueId()); if (i == null) templates(p); return i; }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Session s)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !s.owner.equals(p.getUniqueId()) || e.getRawSlot() < 0 || e.getRawSlot() >= 54 || !(e.isLeftClick() || e.isRightClick())) return;
        Consumer<Player> action = s.actions.get(e.getRawSlot());
        if (action != null) Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == s) action.accept(p); });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) { if (e.getView().getTopInventory().getHolder() instanceof Session) e.setCancelled(true); }
}
