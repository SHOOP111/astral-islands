package dev.astral.paper;

import java.util.Random;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

public final class VoidGenerator extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 0.5, 101, 0.5); }
}
