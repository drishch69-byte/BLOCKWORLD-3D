package com.blockworld.core;

/**
 * Central version gates for gameplay systems introduced by earlier profiles.
 * Newer profiles inherit features from older releases instead of disabling them.
 */
public final class GameFeatureSupport {
    private GameFeatureSupport() {
    }

    public static boolean versionAtLeast(GameVersion version, int major, int minor, int patch) {
        if (version == null || version.id() == null || major < 0 || minor < 0 || patch < 0) return false;
        String[] parts = version.id().split("\\.", -1);
        if (parts.length < 2 || parts.length > 3) return false;
        int[] actual = {0, 0, 0};
        try {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isBlank()) return false;
                actual[i] = Integer.parseInt(parts[i]);
                if (actual[i] < 0) return false;
            }
        } catch (NumberFormatException error) {
            return false;
        }
        if (actual[0] != major) return actual[0] > major;
        if (actual[1] != minor) return actual[1] > minor;
        return actual[2] >= patch;
    }

    public static boolean supportsPets(GameVersion version) {
        return versionAtLeast(version, 1, 9, 0);
    }

    public static boolean supportsFoxCompanions(GameVersion version) {
        return versionAtLeast(version, 1, 9, 10);
    }

    public static boolean supportsAnimalBreeding(GameVersion version) {
        return versionAtLeast(version, 1, 9, 11);
    }

    public static boolean supportsAnimalProducts(GameVersion version) {
        return versionAtLeast(version, 1, 9, 12);
    }

    public static boolean supportsAnimalFeeders(GameVersion version) {
        return versionAtLeast(version, 1, 9, 13);
    }

    public static boolean supportsVillagerWorkplaces(GameVersion version) {
        return versionAtLeast(version, 1, 9, 25);
    }

    public static boolean supportsVillageHomes(GameVersion version) {
        return versionAtLeast(version, 1, 9, 26);
    }

    public static boolean supportsProfessionTrading(GameVersion version) {
        return versionAtLeast(version, 1, 9, 27);
    }

    public static boolean supportsVillageEconomy(GameVersion version) {
        return versionAtLeast(version, 1, 9, 28);
    }
}
