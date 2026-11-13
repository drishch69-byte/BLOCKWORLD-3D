package com.blockworld.core;

/** Deterministic cave-biome sampler. Sampling is independent of generation order. */
public final class CaveBiomeGenerator {
    private static final int CELL_SIZE = 48;

    private CaveBiomeGenerator() {
    }

    public static CaveBiomeType biomeAt(long seed, int x, int y, int z) {
        if (y > -3) return CaveBiomeType.NONE;
        int cellX = Math.floorDiv(x, CELL_SIZE);
        int cellZ = Math.floorDiv(z, CELL_SIZE);
        long value = seed;
        value ^= (long) cellX * 0x9E3779B97F4A7C15L;
        value ^= (long) cellZ * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        int roll = (int) (value & 0xFF);
        if (roll < 26) return CaveBiomeType.CRYSTAL_GROTTO;
        if (roll < 52) return CaveBiomeType.MOSS_CAVERN;
        if (roll < 78) return CaveBiomeType.UNDERGROUND_LAKE;
        return CaveBiomeType.NONE;
    }

    public static boolean supports(String versionId) {
        if (versionId == null) return false;
        try {
            String[] parts = versionId.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 1 || (major == 1 && (minor > 9 || (minor == 9 && patch >= 6)));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static int cellSize() {
        return CELL_SIZE;
    }
}
