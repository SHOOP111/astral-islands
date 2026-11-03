package dev.astral.domain;

/** Stable square-spiral addressing. The plot index is never reused, even after a failed provision. */
public final class Grid {
    public static final int SPACING = 512;
    public static final long MAX_PLOTS = 200_000;
    private Grid() {}
    public record Cell(int x, int z) {
        public int centerX() { return x * SPACING; }
        public int centerZ() { return z * SPACING; }
        public long key() { return ((long) x << 32) ^ (z & 0xffff_ffffL); }
        public boolean contains(double blockX, double blockZ, int radius) {
            return blockX >= centerX() - radius && blockX < centerX() + radius + 1
                && blockZ >= centerZ() - radius && blockZ < centerZ() + radius + 1;
        }
    }
    public static Cell at(long index) {
        if (index < 0 || index >= MAX_PLOTS) throw new IllegalArgumentException("Plot limit exceeded");
        if (index == 0) return new Cell(0, 0);
        int ring = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
        long last = (2L * ring + 1) * (2L * ring + 1) - 1;
        int distance = (int) (last - index), edge = ring * 2;
        if (distance < edge) return new Cell(ring - distance, -ring);
        if (distance < edge * 2) return new Cell(-ring, -ring + distance - edge);
        if (distance < edge * 3) return new Cell(-ring + distance - edge * 2, ring);
        return new Cell(ring, ring - distance + edge * 3);
    }
    public static Cell nearest(double x, double z) {
        return new Cell((int) Math.floor((x + SPACING / 2.0) / SPACING), (int) Math.floor((z + SPACING / 2.0) / SPACING));
    }
}
