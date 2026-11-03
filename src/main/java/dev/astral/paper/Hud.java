package dev.astral.paper;

import dev.astral.domain.Model.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public final class Hud {
    private final AstralPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> previous = new HashMap<>();
    public Hud(AstralPlugin plugin) {
        this.plugin = plugin; Bukkit.getScheduler().runTaskTimer(plugin, this::update, 40, 40);
    }
    private void update() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile profile = plugin.engine().view().profiles().get(p.getUniqueId()); Island i = plugin.engine().view().islandOf(p.getUniqueId());
            if (!plugin.getConfig().getBoolean("ui.hud", true) || profile == null || !profile.hud() || i == null || !plugin.sky().isSky(p.getLocation())) { remove(p); continue; }
            ScoreboardManager manager = Bukkit.getScoreboardManager(); if (manager == null) continue;
            Scoreboard own = boards.get(p.getUniqueId());
            if (p.getScoreboard() != manager.getMainScoreboard() && p.getScoreboard() != own) continue;
            if (own == null) {
                own = manager.getNewScoreboard(); Objective objective = own.registerNewObjective("astral", Criteria.DUMMY,
                    Component.text("✦ ASTRAL ISLANDS", NamedTextColor.LIGHT_PURPLE)); objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                for (int n = 0; n < 12; n++) {
                    String entry = "§" + Integer.toHexString(n); own.registerNewTeam("line" + n).addEntry(entry); objective.getScore(entry).setScore(12 - n);
                }
                boards.put(p.getUniqueId(), own);
            }
            List<String> lines = List.of("", i.name(), "Level  " + i.level(), "Stars  " + "✦".repeat(i.constellation()) + "✧".repeat(5 - i.constellation()),
                "", "Wallet  " + plugin.number(profile.wallet()), "Bank  " + plugin.number(i.bank()), "", "Focus  " + i.focus(),
                "Crew  " + i.members().size() + "/" + i.memberLimit(), "Docks  " + i.expeditions().size() + "/" + (1 + i.rank(Upgrade.EXPEDITION)), "/is  ·  reach for the stars");
            if (!lines.equals(previous.get(p.getUniqueId()))) {
                for (int n = 0; n < lines.size(); n++) {
                    Team team = own.getTeam("line" + n); if (team != null) team.prefix(Component.text(lines.get(n), n == 1 ? NamedTextColor.AQUA : NamedTextColor.GRAY));
                }
                previous.put(p.getUniqueId(), lines);
            }
            if (p.getScoreboard() != own) p.setScoreboard(own);
        }
    }
    public void remove(Player p) {
        Scoreboard old = boards.remove(p.getUniqueId()); previous.remove(p.getUniqueId());
        ScoreboardManager manager = Bukkit.getScoreboardManager(); if (manager != null && old != null && p.getScoreboard() == old) p.setScoreboard(manager.getMainScoreboard());
    }
    public void close() { for (Player p : Bukkit.getOnlinePlayers()) remove(p); boards.clear(); previous.clear(); }
}
