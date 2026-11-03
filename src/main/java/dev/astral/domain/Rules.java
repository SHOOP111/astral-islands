package dev.astral.domain;

import java.util.*;
import dev.astral.domain.Model.*;

/** All money, authorization, cooldowns and progression are enforced here, not in menus. */
public final class Rules {
    private Rules() {}
    public enum Type { CREATE, ACTIVATE, INVITE, ACCEPT, LEAVE, KICK, ROLE, TRANSFER, DEPOSIT, WITHDRAW,
        PAY, DAILY, UPGRADE, CLAIM, LAUNCH, COLLECT, VISITORS, RENAME, ATTUNE, HUD, GRANT }
    public record Action(Type type, UUID target, long amount, String value) {
        public static Action of(Type type) { return new Action(type, null, 0, ""); }
        public static Action text(Type type, String text) { return new Action(type, null, 0, text); }
        public static Action amount(Type type, long amount) { return new Action(type, null, amount, ""); }
        public static Action target(Type type, UUID target) { return new Action(type, target, 0, ""); }
    }
    public record Change(Universe state, String message, UUID islandId) {}
    public static Change register(Universe s, UUID id, String name) {
        Profile p = s.profiles().get(id);
        if (p == null) return reply(s.update(Profile.fresh(id, name)), "Welcome to the stars.", null);
        if (p.name().equals(name)) return reply(s, "Welcome back.", null);
        return reply(s.update(new Profile(id, name, p.wallet(), p.dailyDay(), p.streak(), p.invitedIsland(), p.inviteExpires(), p.hud())), "Welcome back.", null);
    }
    public static Change apply(Universe s, UUID actor, Action a, long now) {
        Profile p = profile(s, actor);
        long day = Math.floorDiv(now, DAY);
        if (a.type == Type.CREATE) {
            require(s.islandOf(actor) == null, "You already belong to an island.");
            require(s.nextPlot() < Grid.MAX_PLOTS, "The island grid is full. Contact an administrator.");
            Island fresh = Island.fresh(actor, p.name() + "'s Island", parse(Template.class, a.value), s.nextPlot(), day);
            return reply(s.update(fresh), "Your island is being woven into the sky...", fresh.id());
        }
        if (a.type == Type.ACTIVATE) {
            Island i = s.islands().get(a.target);
            require(i != null, "Island not found.");
            require(i.owner().equals(actor), "Only the owner can activate this island.");
            if (i.status() == Status.READY) return reply(s, "Your island is already ready.", i.id());
            return reply(s.update(i.edit(b -> b.status = Status.READY)), "Your island is ready. Welcome home!", i.id());
        }
        if (a.type == Type.DAILY) {
            require(day > p.dailyDay(), "Your daily reward is already claimed. Resets at 00:00 UTC.");
            int streak = p.dailyDay() == day - 1 ? Math.min(30, p.streak() + 1) : 1;
            long reward = 250L + streak * 50L;
            Profile next = new Profile(actor, p.name(), credit(p.wallet(), reward), day, streak, p.invitedIsland(), p.inviteExpires(), p.hud());
            return reply(s.update(next), "Daily streak " + streak + ": +" + reward + " wallet coins.", null);
        }
        if (a.type == Type.HUD) {
            return reply(s.update(new Profile(actor, p.name(), p.wallet(), p.dailyDay(), p.streak(), p.invitedIsland(), p.inviteExpires(), !p.hud())), "HUD " + (p.hud() ? "disabled." : "enabled."), null);
        }
        if (a.type == Type.GRANT) {
            // Only the server adapter exposes this operation, behind astral.admin.
            positive(a.amount);
            Profile target = profile(s, a.target);
            return reply(s.update(target.wallet(credit(target.wallet(), a.amount))), "Granted " + a.amount + " coins to " + target.name() + ".", null);
        }
        if (a.type == Type.PAY) {
            positive(a.amount); require(!actor.equals(a.target), "You cannot pay yourself.");
            Profile target = profile(s, a.target);
            require(p.wallet() >= a.amount, "Not enough wallet coins.");
            return reply(s.update(p.wallet(p.wallet() - a.amount), target.wallet(credit(target.wallet(), a.amount))), "Paid " + target.name() + " " + a.amount + " coins.", null);
        }
        if (a.type == Type.ACCEPT) {
            require(s.islandOf(actor) == null, "Leave your current island first.");
            require(p.invitedIsland() != null && now < p.inviteExpires(), "You have no valid invitation.");
            Island invited = s.islands().get(p.invitedIsland());
            require(invited != null && invited.status() == Status.READY, "This island is not ready.");
            require(invited.members().size() < invited.memberLimit(), "The island is full.");
            Universe next = s.update(invited.edit(b -> b.members.put(actor, Role.BUILDER))).update(p.invite(null, 0));
            return reply(next, "Joined " + invited.name() + ".", invited.id());
        }
        Island i = member(s, actor);
        require(i.status() == Status.READY, "Your island is still being built.");
        Role role = i.members().get(actor);
        return switch (a.type) {
            case INVITE -> {
                manager(role); Profile target = profile(s, a.target);
                require(s.islandOf(a.target) == null, "That player already belongs to an island.");
                require(i.members().size() < i.memberLimit(), "Unlock constellations to increase the member limit.");
                yield reply(s.update(target.invite(i.id(), now + 10 * 60_000L)), "Invited " + target.name() + " for 10 minutes.", i.id());
            }
            case LEAVE -> {
                require(role != Role.OWNER, "Transfer ownership before leaving. Islands are never automatically deleted.");
                yield changed(s, i.edit(b -> b.members.remove(actor)), "You left the island.");
            }
            case KICK -> {
                manager(role); Role victim = i.members().get(a.target);
                require(victim != null && victim != Role.OWNER && !actor.equals(a.target), "You cannot kick that member.");
                require(role == Role.OWNER || victim == Role.BUILDER, "Only the owner can kick a steward.");
                yield changed(s, i.edit(b -> b.members.remove(a.target)), "Member removed.");
            }
            case ROLE -> {
                owner(role); Role next = parse(Role.class, a.value);
                require(next != Role.OWNER && i.members().containsKey(a.target) && !actor.equals(a.target), "Use /is transfer for ownership.");
                yield changed(s, i.edit(b -> b.members.put(a.target, next)), "Member role updated to " + next + ".");
            }
            case TRANSFER -> {
                owner(role); require(i.members().containsKey(a.target) && !actor.equals(a.target), "Choose another island member.");
                yield changed(s, i.edit(b -> { b.owner = a.target; b.members.put(actor, Role.STEWARD); b.members.put(a.target, Role.OWNER); }), "Ownership transferred.");
            }
            case DEPOSIT -> {
                positive(a.amount); require(p.wallet() >= a.amount, "Not enough wallet coins.");
                Island next = i.edit(b -> b.bank = credit(b.bank, a.amount));
                yield reply(s.update(next).update(p.wallet(p.wallet() - a.amount)), "Deposited " + a.amount + " coins.", i.id());
            }
            case WITHDRAW -> {
                manager(role); positive(a.amount); require(i.bank() >= a.amount, "Not enough island bank coins.");
                Profile next = p.wallet(credit(p.wallet(), a.amount));
                yield reply(s.update(i.edit(b -> b.bank -= a.amount)).update(next), "Withdrew " + a.amount + " coins.", i.id());
            }
            case UPGRADE -> {
                manager(role); Upgrade u = parse(Upgrade.class, a.value); int rank = i.rank(u);
                require(rank < u.max, "This upgrade is already maxed."); long cost = u.cost(rank);
                require(i.bank() >= cost, "You need " + cost + " island bank coins.");
                yield changed(s, i.edit(b -> { b.bank -= cost; b.upgrades.put(u, rank + 1); }), u + " upgraded to tier " + (rank + 1) + ".");
            }
            case CLAIM -> {
                manager(role); Contract c = parse(Contract.class, a.value); Island current = rotate(i, day);
                require(c.active(day), "That contract is not active today.");
                require(!current.claimed().contains(c), "That contract is already claimed.");
                require(current.progress().getOrDefault(c.activity, 0L) >= c.goal, "Contract objective not complete.");
                long reward = c.coins + current.constellation() * 100L;
                yield changed(s, current.edit(b -> { b.claimed.add(c); b.bank = credit(b.bank, reward); b.xp = capped(b.xp, c.xp); }), "Contract claimed: +" + reward + " bank coins.");
            }
            case LAUNCH -> {
                manager(role); Voyage v = parse(Voyage.class, a.value);
                require(i.expeditions().size() < 1 + i.rank(Upgrade.EXPEDITION), "All expedition docks are occupied. Claim returns or upgrade docks.");
                require(v != Voyage.NEBULA || i.level() >= 5, "Nebula expeditions unlock at level 5.");
                require(v != Voyage.ECLIPSE || i.level() >= 15, "Eclipse expeditions unlock at level 15.");
                require(i.bank() >= v.cost, "You need " + v.cost + " bank coins.");
                UUID nonce = UUID.randomUUID();
                int roll = Math.floorMod(nonce.hashCode() ^ i.id().hashCode(), 100);
                boolean discovery = roll < 20 + i.constellation() * 3 + (i.focus() == Focus.LUNAR ? 10 : 0);
                long coins = v.reward + (discovery ? v.reward / 2 : 0) + i.rank(Upgrade.EXPEDITION) * v.reward / 10;
                Expedition trip = new Expedition(nonce, v, now + v.duration, coins, v.xp + (discovery ? v.xp / 2 : 0), discovery);
                yield changed(s, i.edit(b -> { b.bank -= v.cost; b.expeditions.add(trip); }), v + " launched. Returns in " + v.duration / 60_000 + " minutes, even while offline.");
            }
            case COLLECT -> {
                manager(role); List<Expedition> ready = i.expeditions().stream().filter(e -> e.readyAt() <= now).toList();
                require(!ready.isEmpty(), "No expedition has returned yet.");
                long reward = ready.stream().mapToLong(Expedition::coins).sum(), xp = ready.stream().mapToLong(Expedition::xp).sum();
                yield changed(s, i.edit(b -> { b.bank = credit(b.bank, reward); b.xp = capped(b.xp, xp); b.expeditions.removeAll(ready); b.discoveries += (int) ready.stream().filter(Expedition::discovery).count(); }), "Collected " + ready.size() + " expedition(s): +" + reward + " bank coins.");
            }
            case VISITORS -> {
                manager(role); yield changed(s, i.edit(b -> b.publicVisits = !b.publicVisits), "Public visits " + (i.publicVisits() ? "disabled." : "enabled."));
            }
            case RENAME -> {
                manager(role); String name = a.value.trim();
                require(name.matches("[\\p{L}\\p{N} _'\\-]{3,32}"), "Name must be 3–32 letters, numbers, spaces, apostrophes or hyphens.");
                yield changed(s, i.edit(b -> b.name = name), "Island renamed to " + name + ".");
            }
            case ATTUNE -> {
                manager(role); Focus focus = parse(Focus.class, a.value); require(focus != i.focus(), "Already attuned to " + focus + ".");
                require(i.attunedAt() == 0 || now - i.attunedAt() >= 6 * 60 * 60_000L, "Attunement can change once every six hours.");
                require(i.bank() >= 500, "Attunement costs 500 bank coins.");
                yield changed(s, i.edit(b -> { b.focus = focus; b.attunedAt = now; b.bank -= 500; }), "Attuned to " + focus + ".");
            }
            default -> throw new IllegalArgumentException("Unhandled action " + a.type);
        };
    }
    public static Change setHome(Universe s, UUID actor, Anchor home) {
        Island i = member(s, actor); manager(i.members().get(actor)); require(i.status() == Status.READY, "Island not ready.");
        require(Math.abs(home.x()) <= i.radius() && Math.abs(home.z()) <= i.radius() && home.y() > -32 && home.y() < 320, "Home is outside your island.");
        return changed(s, i.edit(b -> b.home = home), "Island home updated.");
    }
    public static Change activity(Universe s, Map<ActivityKey, Counts> batch, long now) {
        Universe next = s; long day = Math.floorDiv(now, DAY);
        for (var entry : batch.entrySet()) {
            Island i = next.islands().get(entry.getKey().island());
            if (i == null || i.status() != Status.READY || !i.members().containsKey(entry.getKey().actor())) continue;
            Counts counts = entry.getValue(); Island current = rotate(i, day);
            next = next.update(current.edit(b -> {
                long total = 0;
                for (Activity a : Activity.values()) {
                    // A backstop, not a substitute for provenance checks in the Paper adapter.
                    long n = Math.min(200, counts.get(a)); total += n;
                    b.progress.merge(a, n, (x, y) -> Math.min(100_000, x + y));
                }
                long multiplier = i.focus() == Focus.VERDANT ? 3 : 2;
                b.xp = capped(b.xp, total * multiplier);
                b.bank = capped(b.bank, Math.min(200, counts.mine()) * (i.focus() == Focus.SOLAR ? 2 : 1));
            }));
        }
        return reply(next, "Activity synchronized.", null);
    }
    private static Island rotate(Island i, long day) {
        return i.contractDay() == day ? i : i.edit(b -> { b.contractDay = day; b.progress.clear(); b.claimed.clear(); });
    }
    private static Profile profile(Universe s, UUID id) { Profile p = s.profiles().get(id); require(p != null, "That player has not joined this server yet."); return p; }
    private static Island member(Universe s, UUID actor) { Island i = s.islandOf(actor); require(i != null, "Create an island with /is create, or accept an invitation."); return i; }
    private static void manager(Role role) { require(role.manages(), "Only the owner or a steward may do that."); }
    private static void owner(Role role) { require(role == Role.OWNER, "Only the island owner may do that."); }
    private static void positive(long amount) { require(amount > 0 && amount <= MAX_COINS, "Use a positive whole-number coin amount."); }
    private static long credit(long balance, long amount) { require(amount >= 0 && amount <= MAX_COINS - balance, "Balance limit reached."); return balance + amount; }
    private static long capped(long balance, long amount) { return Math.min(MAX_COINS, balance + amount); }
    public static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException | NullPointerException ex) { throw new RuleViolation("Choose one of: " + Arrays.toString(type.getEnumConstants())); }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new RuleViolation(message); }
    private static Change changed(Universe s, Island i, String text) { return reply(s.update(i), text, i.id()); }
    private static Change reply(Universe s, String text, UUID island) { return new Change(s, text, island); }
}
