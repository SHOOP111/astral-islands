package dev.astral.paper;

import dev.astral.domain.*;
import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class IslandCommand implements CommandExecutor, TabCompleter {
    private final AstralPlugin plugin;
    private final Set<UUID> pending = new HashSet<>();
    public IslandCommand(AstralPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("help")) { help(sender); return true; }
            if (sub.equals("admin") || sub.equals("diagnostics") || sub.equals("ci-smoke")) { admin(sender, sub, args); return true; }
            if (!(sender instanceof Player p)) throw new RuleViolation("Player commands require an in-game player. Use /is admin or /is help.");
            if (plugin.engine().view().profiles().get(p.getUniqueId()) == null) throw new RuleViolation("Your profile is loading. Please try again in a moment.");
            switch (sub) {
                case "menu" -> plugin.menus().dashboard(p);
                case "create" -> run(p, () -> plugin.engine().act(p.getUniqueId(), Action.text(Type.CREATE, args.length > 1 ? args[1] : "grove")), change ->
                    plugin.sky().provision(change.islandId()).whenComplete((island, error) -> plugin.onMain(() -> {
                        if (error != null) plugin.failure(p, error); else if (p.isOnline()) plugin.sky().teleport(p, island);
                    })));
                case "home" -> plugin.sky().teleport(p, plugin.engine().view().islandOf(p.getUniqueId()));
                case "visit" -> { need(args, 2, "/is visit <player>"); Profile target = target(args[1]); plugin.sky().teleport(p, plugin.engine().view().islandOf(target.id())); }
                case "top", "browse" -> plugin.menus().browse(p, 0);
                case "bank" -> {
                    if (args.length == 1) plugin.menus().bank(p);
                    else {
                        need(args, 3, "/is bank <deposit|withdraw> <amount>");
                        Type type = switch (args[1].toLowerCase(Locale.ROOT)) { case "deposit" -> Type.DEPOSIT; case "withdraw" -> Type.WITHDRAW; default -> throw new RuleViolation("Use /is bank deposit or /is bank withdraw."); };
                        action(p, Action.amount(type, amount(args[2])));
                    }
                }
                case "pay" -> { need(args, 3, "/is pay <player> <amount>"); Profile target = target(args[1]); action(p, new Action(Type.PAY, target.id(), amount(args[2]), "")); }
                case "daily" -> action(p, Action.of(Type.DAILY));
                case "hud" -> action(p, Action.of(Type.HUD));
                case "upgrades" -> plugin.menus().upgrades(p);
                case "upgrade" -> { need(args, 2, "/is upgrade <horizon|generator|expedition>"); action(p, Action.text(Type.UPGRADE, args[1])); }
                case "contracts", "missions" -> plugin.menus().contracts(p);
                case "claim" -> { need(args, 2, "/is claim <contract>"); action(p, Action.text(Type.CLAIM, args[1])); }
                case "expedition", "expeditions" -> {
                    if (args.length == 1) plugin.menus().expeditions(p);
                    else if (args[1].equalsIgnoreCase("claim")) action(p, Action.of(Type.COLLECT));
                    else if (args[1].equalsIgnoreCase("launch")) { need(args, 3, "/is expedition launch <drift|nebula|eclipse>"); action(p, Action.text(Type.LAUNCH, args[2])); }
                    else throw new RuleViolation("Use /is expedition launch or /is expedition claim.");
                }
                case "settings", "members" -> plugin.menus().settings(p);
                case "visitors" -> action(p, Action.of(Type.VISITORS));
                case "rename" -> { need(args, 2, "/is rename <name>"); action(p, Action.text(Type.RENAME, String.join(" ", Arrays.copyOfRange(args, 1, args.length)))); }
                case "attune" -> { need(args, 2, "/is attune <solar|lunar|verdant>"); action(p, Action.text(Type.ATTUNE, args[1])); }
                case "invite" -> {
                    need(args, 2, "/is invite <player>"); Profile target = target(args[1]);
                    run(p, () -> plugin.engine().act(p.getUniqueId(), Action.target(Type.INVITE, target.id())), change -> {
                        Player online = Bukkit.getPlayer(target.id()); if (online != null) plugin.tell(online, p.getName() + " invited you to their island. /is accept (expires in 10 minutes).");
                    });
                }
                case "accept" -> run(p, () -> plugin.engine().act(p.getUniqueId(), Action.of(Type.ACCEPT)), change -> plugin.sky().teleport(p, change.state().islands().get(change.islandId())));
                case "leave" -> run(p, () -> plugin.engine().act(p.getUniqueId(), Action.of(Type.LEAVE)), change -> evict(p));
                case "kick" -> {
                    need(args, 2, "/is kick <member>"); Profile target = target(args[1]);
                    run(p, () -> plugin.engine().act(p.getUniqueId(), Action.target(Type.KICK, target.id())), change -> { Player online = Bukkit.getPlayer(target.id()); if (online != null) evict(online); });
                }
                case "role" -> { need(args, 3, "/is role <member> <builder|steward>"); action(p, new Action(Type.ROLE, target(args[1]).id(), 0, args[2])); }
                case "transfer" -> { need(args, 2, "/is transfer <member>"); action(p, Action.target(Type.TRANSFER, target(args[1]).id())); }
                case "sethome" -> {
                    Island i = plugin.engine().view().islandOf(p.getUniqueId()); Location l = p.getLocation();
                    if (i == null || !plugin.sky().isSky(l) || plugin.sky().at(l) == null || !plugin.sky().at(l).id().equals(i.id()) || !plugin.sky().safe(l)) throw new RuleViolation("Stand on a safe solid floor in your island.");
                    Grid.Cell c = Grid.at(i.plot()); Anchor home = new Anchor(l.getX() - c.centerX(), l.getY(), l.getZ() - c.centerZ(), l.getYaw(), l.getPitch());
                    run(p, () -> plugin.engine().home(p.getUniqueId(), home), change -> {});
                }
                case "info", "balance" -> info(p);
                default -> throw new RuleViolation("Unknown command. Use /is help.");
            }
        } catch (RuleViolation ex) { plugin.tell(sender, ex.getMessage()); }
        catch (Exception ex) { plugin.failure(sender, ex); }
        return true;
    }
    private void action(Player p, Action a) { run(p, () -> plugin.engine().act(p.getUniqueId(), a), change -> {}); }
    private void run(Player p, Supplier<CompletableFuture<Change>> operation, Consumer<Change> after) {
        if (!pending.add(p.getUniqueId())) { plugin.tell(p, "Your previous transaction is still committing."); return; }
        CompletableFuture<Change> future;
        try { future = operation.get(); } catch (Exception ex) { pending.remove(p.getUniqueId()); throw ex; }
        future.whenComplete((change, error) -> plugin.onMain(() -> {
            pending.remove(p.getUniqueId());
            if (error != null) { plugin.failure(p, error); return; }
            if (p.isOnline()) plugin.tell(p, change.message()); after.accept(change);
        }));
    }
    private void evict(Player p) {
        if (!plugin.sky().isSky(p.getLocation())) return;
        Island current = plugin.sky().at(p.getLocation());
        if (current != null && !plugin.sky().canEnter(p, current)) plugin.sky().rescue(p);
    }
    private void info(Player p) {
        Profile profile = plugin.engine().view().profiles().get(p.getUniqueId()); Island i = plugin.engine().view().islandOf(p.getUniqueId());
        plugin.tell(p, "Wallet: " + plugin.number(profile.wallet()) + " coins · Daily streak: " + profile.streak());
        if (i != null) {
            plugin.tell(p, i.name() + " · " + i.members().get(p.getUniqueId()) + " · Level " + i.level() + " · Constellation " + i.constellation() + "/5");
            plugin.tell(p, "Bank: " + plugin.number(i.bank()) + " · Radius: " + i.radius() + " · Focus: " + i.focus() + " · Discoveries: " + i.discoveries());
        }
    }
    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("astral.admin")) throw new RuleViolation("You do not have astral.admin.");
        if (sub.equals("ci-smoke")) {
            if (sender instanceof Player || !Boolean.getBoolean("astral.ci")) throw new RuleViolation("Only available to the CI console with -Dastral.ci=true.");
            new ServerSmoke(plugin).run(); return;
        }
        if (sub.equals("diagnostics") || args.length > 1 && args[1].equalsIgnoreCase("diagnostics")) {
            plugin.tell(sender, "Astral " + plugin.getDescription().getVersion() + " · world=" + plugin.sky().world().getName()
                + " · islands=" + plugin.engine().view().islands().size() + " · profiles=" + plugin.engine().view().profiles().size()
                + " · nextPlot=" + plugin.engine().view().nextPlot()); return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("audit")) {
            plugin.engine().audit(10).whenComplete((entries, error) -> plugin.onMain(() -> {
                if (error != null) { plugin.failure(sender, error); return; }
                for (Audit a : entries) plugin.tell(sender, java.time.Instant.ofEpochMilli(a.time()) + " · " + a.actor() + " · " + a.action() + " · " + a.detail());
            })); return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("grant")) {
            need(args, 4, "/is admin grant <player> <amount>"); Profile target = target(args[2]);
            plugin.engine().act(target.id(), new Action(Type.GRANT, target.id(), amount(args[3]), "admin=" + sender.getName()))
                .whenComplete((change, error) -> plugin.onMain(() -> { if (error != null) plugin.failure(sender, error); else plugin.tell(sender, change.message()); })); return;
        }
        plugin.tell(sender, "/is admin diagnostics | audit | grant <player> <amount>");
    }
    private Profile target(String name) { Profile p = plugin.engine().view().named(name); if (p == null) throw new RuleViolation("That player must have joined this server first."); return p; }
    private static long amount(String text) {
        try { return Long.parseLong(text); } catch (NumberFormatException ex) { throw new RuleViolation("Use a whole-number amount, not decimals or abbreviations."); }
    }
    private static void need(String[] args, int size, String usage) { if (args.length < size) throw new RuleViolation("Usage: " + usage); }
    private void help(CommandSender sender) {
        for (String line : List.of("/is — dashboard · /is create [grove|crystal|ember] · /is home", "/is visit <player> · /is top · /is info · /is hud",
            "/is invite <player> · /is accept · /is leave · /is kick <member>", "/is role <member> <builder|steward> · /is transfer <member>",
            "/is bank [deposit|withdraw <amount>] · /is pay <player> <amount> · /is daily", "/is upgrades · /is upgrade <horizon|generator|expedition>",
            "/is contracts · /is claim <contract> · /is expedition [launch <voyage>|claim]", "/is settings · /is visitors · /is sethome · /is rename <name> · /is attune <focus>")) plugin.tell(sender, line);
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("menu", "help", "create", "home", "visit", "top", "bank", "pay", "daily", "info", "hud", "upgrades", "upgrade", "contracts", "claim", "expedition", "invite", "accept", "leave", "kick", "role", "transfer", "settings", "visitors", "sethome", "rename", "attune"));
            if (sender.hasPermission("astral.admin")) options.add("admin");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> options.addAll(List.of("grove", "crystal", "ember"));
                case "bank" -> options.addAll(List.of("deposit", "withdraw"));
                case "upgrade" -> options.addAll(List.of("horizon", "generator", "expedition"));
                case "claim" -> options.addAll(List.of("miner", "gardener", "angler", "sentinel"));
                case "attune" -> options.addAll(List.of("solar", "lunar", "verdant"));
                case "expedition" -> options.addAll(List.of("launch", "claim"));
                case "admin" -> { if (sender.hasPermission("astral.admin")) options.addAll(List.of("grant", "audit", "diagnostics")); }
                case "invite", "visit", "kick", "role", "transfer", "pay" -> plugin.engine().view().profiles().values().forEach(p -> options.add(p.name()));
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("expedition") && args[1].equalsIgnoreCase("launch")) options.addAll(List.of("drift", "nebula", "eclipse"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("role")) options.addAll(List.of("builder", "steward"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("grant") && sender.hasPermission("astral.admin")) plugin.engine().view().profiles().values().forEach(p -> options.add(p.name()));
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().limit(50).toList();
    }
}
