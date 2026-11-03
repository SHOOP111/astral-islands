package dev.astral.domain;

import dev.astral.domain.Model.*;
import dev.astral.domain.Rules.*;
import static dev.astral.domain.Model.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RulesTest {
    UUID owner, friend, third;
    Universe s;
    long now = 20_000 * DAY + 30_000;
    @BeforeEach void setup() {
        owner = UUID.randomUUID(); friend = UUID.randomUUID(); third = UUID.randomUUID(); s = Universe.empty();
        s = Rules.register(s, owner, "Nova").state(); s = Rules.register(s, friend, "Orbit").state(); s = Rules.register(s, third, "Lumen").state();
        act(owner, Action.text(Type.CREATE, "grove")); act(owner, Action.target(Type.ACTIVATE, island().id()));
    }
    Island island() { return s.islandOf(owner); }
    Change act(UUID who, Action action) { Change c = Rules.apply(s, who, action, now); s = c.state(); return c; }
    void fund(long amount) { s = s.update(island().edit(b -> b.bank = amount)); }
    void xp(long amount) { s = s.update(island().edit(b -> b.xp = amount)); }
    void join(UUID who) { act(owner, Action.target(Type.INVITE, who)); act(who, Action.of(Type.ACCEPT)); }
    void rejects(UUID who, Action action) { Universe before = s; assertThrows(RuleViolation.class, () -> act(who, action)); assertSame(before, s); }

    @Test void registrationIsIdempotentAndUpdatesNameWithoutResettingMoney() {
        assertSame(s, Rules.register(s, owner, "Nova").state());
        s = Rules.register(s, owner, "NewNova").state(); assertEquals("NewNova", s.profiles().get(owner).name()); assertEquals(1_000, s.profiles().get(owner).wallet());
    }
    @ParameterizedTest @EnumSource(Template.class) void everyTemplateCanBeCreated(Template template) {
        act(friend, Action.text(Type.CREATE, template.name())); Island i = s.islandOf(friend);
        assertEquals(template, i.template()); assertEquals(Status.PROVISIONING, i.status()); assertEquals(1, i.plot()); assertEquals(2, s.nextPlot());
        rejects(friend, Action.text(Type.CREATE, "grove")); rejects(friend, Action.amount(Type.DEPOSIT, 1));
        act(friend, Action.target(Type.ACTIVATE, i.id())); assertEquals(Status.READY, s.islandOf(friend).status());
    }
    @Test void invalidCreationAndUnknownPlayersAreRejected() {
        rejects(friend, Action.text(Type.CREATE, "unknown")); rejects(UUID.randomUUID(), Action.of(Type.DAILY));
        rejects(owner, Action.target(Type.ACTIVATE, UUID.randomUUID()));
    }
    @Test void cannotCreateWhenGridIsFull() {
        s = new Universe(s.islands(), s.profiles(), Grid.MAX_PLOTS); rejects(friend, Action.text(Type.CREATE, "grove"));
    }
    @Test void inviteAcceptAndLeaveKeepMembershipConsistent() {
        join(friend); assertEquals(Role.BUILDER, s.islandOf(friend).members().get(friend)); assertNull(s.profiles().get(friend).invitedIsland());
        rejects(friend, Action.of(Type.ACCEPT)); rejects(owner, Action.target(Type.INVITE, friend));
        act(friend, Action.of(Type.LEAVE)); assertNull(s.islandOf(friend)); rejects(owner, Action.of(Type.LEAVE));
    }
    @Test void invitationExpiresAtExactDeadline() {
        rejects(friend, Action.of(Type.ACCEPT)); act(owner, Action.target(Type.INVITE, friend)); now += 600_000; rejects(friend, Action.of(Type.ACCEPT));
    }
    @Test void acceptingProvisioningOrMissingInvitationIsRejected() {
        Island i = island(); s = s.update(i.edit(b -> b.status = Status.PROVISIONING)).update(s.profiles().get(friend).invite(i.id(), now + 1_000));
        rejects(friend, Action.of(Type.ACCEPT)); s = s.update(s.profiles().get(friend).invite(UUID.randomUUID(), now + 1_000)); rejects(friend, Action.of(Type.ACCEPT));
    }
    @Test void crewLimitIsEnforcedAgainAtAcceptance() {
        for (int n = 0; n < 3; n++) { UUID id = UUID.randomUUID(); s = Rules.register(s, id, "Crew" + n).state(); join(id); }
        rejects(owner, Action.target(Type.INVITE, friend)); s = s.update(s.profiles().get(friend).invite(island().id(), now + 1_000)); rejects(friend, Action.of(Type.ACCEPT));
        xp(10_000); assertEquals(6, island().memberLimit()); act(friend, Action.of(Type.ACCEPT));
    }
    @Test void stewardsCannotKickOwnersOrOtherStewards() {
        join(friend); join(third); act(owner, new Action(Type.ROLE, friend, 0, "steward")); act(owner, new Action(Type.ROLE, third, 0, "steward"));
        rejects(friend, Action.target(Type.KICK, owner)); rejects(friend, Action.target(Type.KICK, third)); rejects(friend, Action.target(Type.KICK, friend));
        act(owner, new Action(Type.ROLE, third, 0, "builder")); act(friend, Action.target(Type.KICK, third)); assertNull(s.islandOf(third));
        act(owner, Action.target(Type.KICK, friend)); assertNull(s.islandOf(friend));
    }
    @Test void ownershipTransferIsAtomicAndOnlyOwnerMaySetRoles() {
        join(friend); rejects(friend, new Action(Type.ROLE, owner, 0, "builder")); rejects(owner, new Action(Type.ROLE, friend, 0, "owner"));
        rejects(owner, Action.target(Type.TRANSFER, third)); rejects(owner, Action.target(Type.TRANSFER, owner));
        act(owner, Action.target(Type.TRANSFER, friend)); Island i = s.islandOf(friend);
        assertEquals(friend, i.owner()); assertEquals(Role.STEWARD, i.members().get(owner)); assertEquals(Role.OWNER, i.members().get(friend));
        act(owner, Action.of(Type.LEAVE)); assertNull(s.islandOf(owner));
    }
    @Test void buildersCannotManageTreasuryOrSettings() {
        join(friend); fund(100_000);
        for (Action a : List.of(Action.amount(Type.WITHDRAW, 1), Action.target(Type.INVITE, third), Action.text(Type.UPGRADE, "generator"),
            Action.text(Type.CLAIM, "miner"), Action.text(Type.LAUNCH, "drift"), Action.of(Type.COLLECT), Action.of(Type.VISITORS),
            Action.text(Type.RENAME, "Other Island"), Action.text(Type.ATTUNE, "lunar"), Action.target(Type.KICK, third), Action.target(Type.TRANSFER, owner))) rejects(friend, a);
    }
    @Test void depositsWithdrawalsAndPaymentsConserveCoins() {
        join(friend); long original = total(); act(friend, Action.amount(Type.DEPOSIT, 600)); assertEquals(600, island().bank()); assertEquals(400, s.profiles().get(friend).wallet());
        act(owner, Action.amount(Type.WITHDRAW, 300)); assertEquals(original, total());
        act(owner, new Action(Type.PAY, friend, 100, "")); assertEquals(original, total());
        rejects(owner, new Action(Type.PAY, owner, 10, "")); rejects(owner, new Action(Type.PAY, third, MAX_COINS, ""));
    }
    long total() { return s.profiles().values().stream().mapToLong(Profile::wallet).sum() + s.islands().values().stream().mapToLong(Island::bank).sum(); }
    @Test void invalidCoinAmountsAndInsufficientFundsNeverMutate() {
        for (long n : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) rejects(owner, Action.amount(Type.DEPOSIT, n));
        rejects(owner, Action.amount(Type.DEPOSIT, 1_001)); rejects(owner, Action.amount(Type.WITHDRAW, 1));
        rejects(owner, new Action(Type.GRANT, friend, 0, "")); act(owner, new Action(Type.GRANT, friend, 125, "")); assertEquals(1_125, s.profiles().get(friend).wallet());
    }
    @Test void creditOverflowIsRejectedBeforeDebit() {
        s = s.update(s.profiles().get(friend).wallet(MAX_COINS)); rejects(owner, new Action(Type.PAY, friend, 1, ""));
        fund(MAX_COINS); rejects(owner, Action.amount(Type.DEPOSIT, 1)); s = s.update(s.profiles().get(owner).wallet(MAX_COINS));
        rejects(owner, Action.amount(Type.WITHDRAW, 1)); rejects(owner, new Action(Type.GRANT, friend, 1, "")); rejects(owner, Action.of(Type.DAILY));
    }
    @Test void dailyRewardsUseUtcAndRejectBackwardsTime() {
        act(owner, Action.of(Type.DAILY)); assertEquals(1_300, s.profiles().get(owner).wallet()); rejects(owner, Action.of(Type.DAILY));
        now += DAY; act(owner, Action.of(Type.DAILY)); assertEquals(2, s.profiles().get(owner).streak());
        now -= 2 * DAY; rejects(owner, Action.of(Type.DAILY)); now += 4 * DAY; act(owner, Action.of(Type.DAILY)); assertEquals(1, s.profiles().get(owner).streak());
    }
    @Test void streakIsCappedAtThirtyAndHudPersists() {
        for (int n = 0; n < 35; n++) { act(owner, Action.of(Type.DAILY)); now += DAY; }
        assertEquals(30, s.profiles().get(owner).streak()); act(owner, Action.of(Type.HUD)); assertFalse(s.profiles().get(owner).hud()); act(owner, Action.of(Type.HUD)); assertTrue(s.profiles().get(owner).hud());
    }
    @ParameterizedTest @EnumSource(Upgrade.class) void everyUpgradeHasBoundedExponentialPricing(Upgrade u) {
        rejects(owner, Action.text(Type.UPGRADE, u.name())); fund(1_000_000); long bank = island().bank(); long spent = 0;
        for (int n = 0; n < u.max; n++) { spent += u.cost(n); act(owner, Action.text(Type.UPGRADE, u.name())); assertEquals(n + 1, island().rank(u)); }
        assertEquals(bank - spent, island().bank()); rejects(owner, Action.text(Type.UPGRADE, u.name()));
        if (u == Upgrade.HORIZON) assertEquals(128, island().radius());
    }
    @Test void claimsRequireActiveCompletedAndUnclaimedContract() {
        long day = now / DAY; Contract active = Arrays.stream(Contract.values()).filter(c -> c.active(day)).findFirst().orElseThrow();
        rejects(owner, Action.text(Type.CLAIM, active.name()));
        s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(200, 200, 200, 200)), now).state();
        long previous = island().bank(); act(owner, Action.text(Type.CLAIM, active.name())); assertTrue(island().bank() > previous);
        rejects(owner, Action.text(Type.CLAIM, active.name()));
        Contract inactive = Arrays.stream(Contract.values()).filter(c -> !c.active(day)).findFirst().orElseThrow(); rejects(owner, Action.text(Type.CLAIM, inactive.name()));
    }
    @Test void everyContractCanRotateAndBeClaimed() {
        Set<Contract> seen = new HashSet<>();
        for (int n = 0; n < 4; n++) {
            s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(100, 100, 100, 100)), now).state();
            for (Contract c : Contract.values()) if (c.active(now / DAY)) { act(owner, Action.text(Type.CLAIM, c.name())); seen.add(c); }
            assertEquals(3, island().claimed().size()); now += DAY;
        }
        assertEquals(Set.of(Contract.values()), seen);
    }
    @Test void contractProgressResetsAcrossMidnight() {
        s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(100, 100, 100, 100)), now).state(); now += DAY;
        s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(1, 0, 0, 0)), now).state(); assertEquals(1, island().progress().get(Activity.MINE)); assertEquals(0, island().progress().get(Activity.FARM));
    }
    @Test void activityIgnoresVisitorsKickedMembersAndUnknownIslands() {
        join(friend); UUID island = island().id(); act(owner, Action.target(Type.KICK, friend)); long xp = island().xp();
        s = Rules.activity(s, Map.of(new ActivityKey(island, friend), new Counts(100, 100, 100, 100), new ActivityKey(UUID.randomUUID(), owner), new Counts(100, 0, 0, 0)), now).state();
        assertEquals(xp, island().xp());
    }
    @Test void activityCapsAndFocusBonusesWork() {
        s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(10_000, 0, 0, 0)), now).state();
        assertEquals(400, island().xp()); assertEquals(400, island().bank()); fund(5_000);
        act(owner, Action.text(Type.ATTUNE, "verdant")); long before = island().xp();
        s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(1, 1, 1, 1)), now).state(); assertEquals(before + 12, island().xp());
        fund(MAX_COINS); xp(MAX_COINS); s = Rules.activity(s, Map.of(new ActivityKey(island().id(), owner), new Counts(1, 1, 0, 0)), now).state(); assertEquals(MAX_COINS, island().bank()); assertEquals(MAX_COINS, island().xp());
    }
    @Test void attunementHasRealTimeCooldownAndCost() {
        rejects(owner, Action.text(Type.ATTUNE, "solar")); rejects(owner, Action.text(Type.ATTUNE, "lunar")); fund(10_000);
        act(owner, Action.text(Type.ATTUNE, "lunar")); assertEquals(9_500, island().bank()); rejects(owner, Action.text(Type.ATTUNE, "verdant"));
        now += 6 * 60 * 60_000L; act(owner, Action.text(Type.ATTUNE, "verdant")); assertEquals(Focus.VERDANT, island().focus());
    }
    @ParameterizedTest @EnumSource(Voyage.class) void voyagesPersistOutcomeAndClaimExactlyOnce(Voyage voyage) {
        fund(100_000); xp(100_000); act(owner, Action.text(Type.LAUNCH, voyage.name())); Expedition e = island().expeditions().getFirst();
        assertEquals(voyage, e.voyage()); assertEquals(now + voyage.duration, e.readyAt()); assertTrue(e.coins() >= voyage.reward); rejects(owner, Action.of(Type.COLLECT));
        rejects(owner, Action.text(Type.LAUNCH, "drift")); long before = island().bank(); now = e.readyAt(); act(owner, Action.of(Type.COLLECT));
        assertEquals(before + e.coins(), island().bank()); assertTrue(island().expeditions().isEmpty()); rejects(owner, Action.of(Type.COLLECT));
    }
    @Test void voyagesEnforceLevelAndBudgetAndDockLimits() {
        fund(100_000); rejects(owner, Action.text(Type.LAUNCH, "nebula")); rejects(owner, Action.text(Type.LAUNCH, "eclipse"));
        xp(1_600); act(owner, Action.text(Type.LAUNCH, "nebula")); act(owner, Action.text(Type.UPGRADE, "expedition")); act(owner, Action.text(Type.LAUNCH, "drift"));
        assertEquals(2, island().expeditions().size()); rejects(owner, Action.text(Type.LAUNCH, "drift"));
        fund(0); now += DAY; act(owner, Action.of(Type.COLLECT)); fund(0); rejects(owner, Action.text(Type.LAUNCH, "drift"));
    }
    @Test void renamingVisitorsAndHomeAreAuthorizedAndValidated() {
        act(owner, Action.of(Type.VISITORS)); assertTrue(island().publicVisits()); act(owner, Action.of(Type.VISITORS)); assertFalse(island().publicVisits());
        act(owner, Action.text(Type.RENAME, "  Nova's 星 Garden  ")); assertEquals("Nova's 星 Garden", island().name());
        rejects(owner, Action.text(Type.RENAME, "<red>bad")); rejects(owner, Action.text(Type.RENAME, "a"));
        s = Rules.setHome(s, owner, new Anchor(2.5, 101, 3.5, 90, 0)).state(); assertEquals(2.5, island().home().x());
        assertThrows(RuleViolation.class, () -> Rules.setHome(s, owner, new Anchor(100, 101, 0, 0, 0)));
        join(friend); assertThrows(RuleViolation.class, () -> Rules.setHome(s, friend, Anchor.starter()));
    }
    @Test void nonMembersCannotRunIslandOperationsAndEnumsAreSafe() {
        rejects(friend, Action.of(Type.VISITORS)); rejects(owner, Action.text(Type.UPGRADE, "fake")); rejects(owner, Action.text(Type.CLAIM, "fake"));
        assertEquals(Focus.LUNAR, Rules.parse(Focus.class, "LuNaR")); assertThrows(RuleViolation.class, () -> Rules.parse(Focus.class, null));
    }
}
