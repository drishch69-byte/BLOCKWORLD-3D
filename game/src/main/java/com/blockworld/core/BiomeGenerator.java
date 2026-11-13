package com.blockworld.core;

/** Seeded value-noise biome sampler. It does not depend on generation order. */
public final class BiomeGenerator {
    private BiomeGenerator() { }

    public static BiomeType biomeAt(long seed, int x, int z) {
        double temperature = valueNoise(seed, x, z, 72, 0x51A7L) + 0.13 * Math.sin(z * 0.012);
        double moisture = valueNoise(seed, x, z, 61, 0xC0FFEE12L);
        double elevation = valueNoise(seed, x, z, 43, 0xBADC0DEL);
        if (elevation > 0.73) return BiomeType.MOUNTAINS;
        if (temperature < 0.30) return BiomeType.SNOWY_TUNDRA;
        if (temperature > 0.68 && moisture < 0.48) return BiomeType.DESERT;
        if (moisture > 0.59) return BiomeType.FOREST;
        return BiomeType.PLAINS;
    }

    /** Expanded biome table used by the 1.1.0 profile; the legacy sampler stays unchanged. */
    public static BiomeType biomeAtV110(long seed, int x, int z) {
        double temperature = valueNoise(seed, x, z, 72, 0x51A7L) + 0.13 * Math.sin(z * 0.012);
        double moisture = valueNoise(seed, x, z, 61, 0xC0FFEE12L);
        double elevation = valueNoise(seed, x, z, 43, 0xBADC0DEL);
        if (elevation > 0.73) return BiomeType.MOUNTAINS;
        if (temperature < 0.30) return BiomeType.SNOWY_TUNDRA;
        if (temperature < 0.45 && moisture > 0.43) return BiomeType.TAIGA;
        if (temperature > 0.68 && moisture < 0.48) return BiomeType.DESERT;
        if (moisture > 0.72) return BiomeType.SWAMP;
        if (moisture > 0.59) return BiomeType.FOREST;
        return BiomeType.PLAINS;
    }

    public static int mountainHeightBonus(long seed, int x, int z) {
        if (biomeAt(seed, x, z) != BiomeType.MOUNTAINS) return 0;
        double elevation = valueNoise(seed, x, z, 43, 0xBADC0DEL);
        return 2 + (int) Math.round((elevation - 0.73) * 18.0);
    }

    private static double valueNoise(long seed, int x, int z, int scale, long salt) {
        int gx = Math.floorDiv(x, scale);
        int gz = Math.floorDiv(z, scale);
        double fx = smooth(Math.floorMod(x, scale) / (double) scale);
        double fz = smooth(Math.floorMod(z, scale) / (double) scale);
        double a = hash(seed, gx, gz, salt);
        double b = hash(seed, gx + 1, gz, salt);
        double c = hash(seed, gx, gz + 1, salt);
        double d = hash(seed, gx + 1, gz + 1, salt);
        double top = lerp(a, b, fx);
        double bottom = lerp(c, d, fx);
        return lerp(top, bottom, fz);
    }

    private static double hash(long seed, int x, int z, long salt) {
        long value = seed ^ salt;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double value) { return value * value * (3.0 - 2.0 * value); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
