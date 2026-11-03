package dev.astral.domain;

import dev.astral.domain.Model.*;
import static dev.astral.domain.Model.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ModelTest {
    private final UUID owner = UUID.randomUUID();
    private Island fresh() { return Island.fresh(owner, "Test Island", Template.GROVE, 0, 20_000); }
    @Test void collectionsAreDeeplyImmutable() {
        Island i = fresh(); assertThrows(UnsupportedOperationException.class, () -> i.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> i.upgrades().put(Upgrade.HORIZON, 3));
        assertThrows(UnsupportedOperationException.class, () -> i.progress().put(Activity.MINE, 1L));
        assertThrows(UnsupportedOperationException.class, () -> i.claimed().add(Contract.MINER));
        assertThrows(UnsupportedOperationException.class, () -> i.expeditions().clear());
        Island edited = i.edit(b -> { b.bank = 10; b.upgrades.put(Upgrade.GENERATOR, 1); });
        assertEquals(0, i.bank()); assertEquals(0, i.rank(Upgrade.GENERATOR)); assertEquals(10, edited.bank());
    }
    @Test void invalidOwnersAndCountersAreRejected() {
        Island i = fresh(); assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.members.clear()));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.members.put(UUID.randomUUID(), Role.OWNER)));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.bank = -1));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.bank = MAX_COINS + 1));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.xp = -1));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.discoveries = -1));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.upgrades.put(Upgrade.HORIZON, 4)));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.upgrades.put(Upgrade.GENERATOR, -1)));
        assertThrows(IllegalArgumentException.class, () -> i.edit(b -> b.progress.put(Activity.MINE, -1L)));
    }
    @Test void profilesAndAnchorsValidateEdgeCases() {
        Profile p = Profile.fresh(owner, "Nova"); assertEquals(1_000, p.wallet()); assertEquals(-1, p.dailyDay());
        assertThrows(IllegalArgumentException.class, () -> p.wallet(-1)); assertThrows(IllegalArgumentException.class, () -> p.wallet(MAX_COINS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Profile(owner, "Nova", 0, 0, -1, null, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new Anchor(Double.NaN, 101, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Anchor(0, Double.POSITIVE_INFINITY, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Anchor(0, 101, 0, Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Counts(-1, 0, 0, 0));
    }
    @Test void countsAccumulateAllActivityTypes() {
        Counts c = new Counts(0, 0, 0, 0); for (Activity a : Activity.values()) c = c.add(a);
        for (Activity a : Activity.values()) assertEquals(1, c.get(a));
    }
    @Test void levelsAndConstellationsHaveExactThresholds() {
        Island i = fresh(); assertEquals(1, i.level()); assertEquals(0, i.constellation());
        assertEquals(1, i.edit(b -> b.xp = 99).level()); assertEquals(2, i.edit(b -> b.xp = 100).level());
        assertEquals(0, i.edit(b -> b.xp = 9_999).constellation()); assertEquals(1, i.edit(b -> b.xp = 10_000).constellation());
        assertEquals(5, i.edit(b -> b.xp = MAX_COINS).constellation()); assertEquals(14, i.edit(b -> b.xp = MAX_COINS).memberLimit());
    }
    @Test void readModelIndexesNamesMembersAndExactBoundaries() {
        Island i = fresh(); Profile p = Profile.fresh(owner, "Nova"); Universe s = new Universe(Map.of(i.id(), i), Map.of(owner, p), 1);
        assertSame(i, s.islandOf(owner)); assertEquals(p, s.named("nOvA")); assertNull(s.named("unknown"));
        assertSame(i, s.at(-32, -32)); assertSame(i, s.at(32.99, 32.99)); assertNull(s.at(33, 0)); assertNull(s.at(-32.01, 0)); assertNull(s.at(512, 0));
        assertThrows(UnsupportedOperationException.class, () -> s.islands().clear());
    }
    @Test void readModelRejectsCorruptIndexesRatherThanResettingData() {
        Island i = fresh(); Profile p = Profile.fresh(owner, "Nova");
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(i.id(), i), Map.of(owner, p), 0));
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(i.id(), i), Map.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(UUID.randomUUID(), i), Map.of(owner, p), 1));
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(), Map.of(UUID.randomUUID(), p), 0));
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(), Map.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(), Map.of(), Grid.MAX_PLOTS + 1));
        UUID other = UUID.randomUUID(); Island duplicatePlot = Island.fresh(other, "Other", Template.GROVE, 0, 20_000);
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(i.id(), i, duplicatePlot.id(), duplicatePlot), Map.of(owner, p, other, Profile.fresh(other, "Other")), 1));
        Island duplicateMember = Island.fresh(other, "Other", Template.GROVE, 1, 20_000).edit(b -> b.members.put(owner, Role.BUILDER));
        assertThrows(IllegalArgumentException.class, () -> new Universe(Map.of(i.id(), i, duplicateMember.id(), duplicateMember), Map.of(owner, p, other, Profile.fresh(other, "Other")), 2));
    }
    @Test void spiralUsesUniqueStableCellsForEverySupportedPlot() {
        Set<Long> seen = new HashSet<>();
        for (long n = 0; n < Grid.MAX_PLOTS; n++) {
            Grid.Cell c = Grid.at(n); assertTrue(seen.add(c.key()), "duplicate " + n);
            assertEquals(c, Grid.nearest(c.centerX(), c.centerZ())); assertTrue(c.contains(c.centerX(), c.centerZ(), 128));
        }
        assertEquals(new Grid.Cell(0, 0), Grid.at(0)); assertEquals(new Grid.Cell(1, 0), Grid.at(1)); assertEquals(new Grid.Cell(1, -1), Grid.at(8));
        assertThrows(IllegalArgumentException.class, () -> Grid.at(-1)); assertThrows(IllegalArgumentException.class, () -> Grid.at(Grid.MAX_PLOTS));
    }
    @Test void nearestCellHandlesNegativeCoordinatesAndHalfOpenSeams() {
        assertEquals(new Grid.Cell(0, 0), Grid.nearest(-256, -256)); assertEquals(new Grid.Cell(-1, -1), Grid.nearest(-256.01, -256.01));
        assertEquals(new Grid.Cell(1, 1), Grid.nearest(256, 256));
    }
    @ParameterizedTest @EnumSource(Template.class) void schematicsAreBoundedUniqueAndSafe(Template template) {
        var plan = IslandPlan.forTemplate(template); assertTrue(plan.size() > 500); Set<String> cells = new HashSet<>();
        for (var block : plan) {
            assertTrue(cells.add(block.x() + ":" + block.y() + ":" + block.z()));
            assertTrue(Math.abs(block.x()) <= 7 && Math.abs(block.z()) <= 7 && block.y() >= 94 && block.y() <= 106);
        }
        assertTrue(plan.stream().anyMatch(b -> b.x() == 0 && b.y() == 100 && b.z() == 0 && b.material().equals("GRASS_BLOCK")));
        assertTrue(plan.stream().anyMatch(b -> b.x() == 3 && b.y() == 101 && b.z() == 0 && b.material().equals("CHEST")));
        assertFalse(plan.stream().anyMatch(b -> b.x() == 0 && b.z() == 0 && b.y() >= 101));
    }
    @Test void oreWeightsSumToTenThousandAndSamplingMatchesExactly() {
        for (int tier = 0; tier <= 4; tier++) for (Focus f : Focus.values()) for (int stars = 0; stars <= 5; stars++) {
            var weights = OreTable.weights(tier, f, stars); assertEquals(10_000, weights.values().stream().mapToInt(Integer::intValue).sum());
            Map<OreTable.Ore, Integer> sampled = new EnumMap<>(OreTable.Ore.class);
            for (int roll = 0; roll < 10_000; roll++) sampled.merge(OreTable.roll(tier, f, stars, roll), 1, Integer::sum);
            weights.forEach((ore, weight) -> assertEquals(weight, sampled.getOrDefault(ore, 0)));
        }
        assertThrows(IllegalArgumentException.class, () -> OreTable.roll(-1, Focus.SOLAR, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> OreTable.roll(5, Focus.SOLAR, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> OreTable.roll(0, Focus.SOLAR, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> OreTable.roll(0, Focus.SOLAR, 6, 0));
        assertThrows(IllegalArgumentException.class, () -> OreTable.roll(0, Focus.SOLAR, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> OreTable.roll(0, Focus.SOLAR, 0, 10_000));
    }
}
