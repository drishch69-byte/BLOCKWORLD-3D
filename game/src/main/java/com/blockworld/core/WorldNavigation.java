package com.blockworld.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic structure markers and a compact player-centred compass map. */
public final class WorldNavigation {
    public enum LandmarkType {
        VILLAGE('V', "Деревня"),
        WATCHTOWER('T', "Сторожевая башня"),
        RUINS('R', "Заброшенные руины"),
        CAMP('C', "Лагерь"),
        MINESHAFT('M', "Старая шахта");

        private final char marker;
        private final String displayName;

        LandmarkType(char marker, String displayName) {
            this.marker = marker;
            this.displayName = displayName;
        }

        public char marker() { return marker; }
        public String displayName() { return displayName; }
    }

    public record Landmark(String id, String name, int x, int z, LandmarkType type) {
        public Landmark {
            if (id == null || id.isBlank() || name == null || name.isBlank() || type == null) {
                throw new IllegalArgumentException("Метка карты должна иметь ID, название и тип");
            }
        }
    }

    private static final int MAP_SIZE = 9;
    private static final int MAP_BLOCKS_PER_CELL = 32;

    private WorldNavigation() { }

    public static boolean supportsExploration(String versionId) {
        if (versionId == null) return false;
        String[] parts = versionId.split("\\.");
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || (major == 1 && minor >= 2);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Returns exactly four deterministic exploration sites for a generated world. */
    public static List<Landmark> generateSites(long seed, int radius) {
        if (radius < 48) return List.of();
        Random random = new Random(seed ^ 0x574F524C444D4150L);
        LandmarkType[] types = {
                LandmarkType.WATCHTOWER, LandmarkType.RUINS,
                LandmarkType.CAMP, LandmarkType.MINESHAFT
        };
        List<Landmark> result = new ArrayList<>(types.length);
        for (int index = 0; index < types.length; index++) {
            boolean placed = false;
            for (int attempt = 0; attempt < 500; attempt++) {
                int x = random.nextInt(radius * 2 - 28) - radius + 14;
                int z = random.nextInt(radius * 2 - 28) - radius + 14;
                if (x * x + z * z < 48 * 48) continue;
                if (distanceSquared(x, z, -32, 27) < 24 * 24
                        || distanceSquared(x, z, 31, 27) < 24 * 24) continue;
                boolean crowded = result.stream().anyMatch(site ->
                        distanceSquared(x, z, site.x(), site.z()) < 34 * 34);
                if (crowded) continue;
                result.add(new Landmark(types[index].name().toLowerCase(java.util.Locale.ROOT),
                        types[index].displayName(), x, z, types[index]));
                placed = true;
                break;
            }
            if (!placed) {
                int[][] fallback = {
                        {radius - 20, -radius + 20},
                        {-radius + 20, -radius + 20},
                        {radius - 20, radius - 20},
                        {-radius + 20, radius - 20}
                };
                int x = fallback[index][0];
                int z = fallback[index][1];
                result.add(new Landmark(types[index].name().toLowerCase(java.util.Locale.ROOT),
                        types[index].displayName(), x, z, types[index]));
            }
        }
        return List.copyOf(result);
    }

    public static List<Landmark> allMarkers(long seed, int radius) {
        List<Landmark> all = new ArrayList<>();
        all.add(new Landmark("village-west", "Западная деревня", -32, 27, LandmarkType.VILLAGE));
        all.add(new Landmark("village-east", "Восточная деревня", 31, 27, LandmarkType.VILLAGE));
        all.addAll(generateSites(seed, radius));
        return List.copyOf(all);
    }

    public static String compassHeading(float yaw) {
        if (!Float.isFinite(yaw)) throw new IllegalArgumentException("Направление должно быть конечным числом");
        float degrees = normalizeDegrees((float) Math.toDegrees(yaw));
        // Positive camera yaw points west because forwardDirection uses -sin(yaw).
        String[] headings = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.floorMod(Math.round(-degrees / 45f), headings.length);
        return headings[index];
    }

    public static String directionTo(float playerX, float playerZ, float targetX, float targetZ) {
        float dx = targetX - playerX;
        float dz = targetZ - playerZ;
        if (Math.abs(dx) < 0.001f && Math.abs(dz) < 0.001f) return "здесь";
        float degrees = normalizeDegrees((float) Math.toDegrees((float) Math.atan2(dx, -dz)));
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return directions[Math.floorMod(Math.round(degrees / 45f), directions.length)];
    }

    public static double distance(float x1, float z1, float x2, float z2) {
        return Math.hypot(x2 - x1, z2 - z1);
    }

    public static String renderMap(float playerX, float playerZ, float yaw, List<Landmark> landmarks) {
        if (!Float.isFinite(playerX) || !Float.isFinite(playerZ) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Координаты карты должны быть конечными числами");
        }
        char[][] grid = new char[MAP_SIZE][MAP_SIZE];
        for (int row = 0; row < MAP_SIZE; row++) {
            java.util.Arrays.fill(grid[row], '.');
        }
        int center = MAP_SIZE / 2;
        for (Landmark landmark : landmarks == null ? List.<Landmark>of() : landmarks) {
            int column = center + Math.round((landmark.x() - playerX) / (float) MAP_BLOCKS_PER_CELL);
            int row = center + Math.round((landmark.z() - playerZ) / (float) MAP_BLOCKS_PER_CELL);
            if (row >= 0 && row < MAP_SIZE && column >= 0 && column < MAP_SIZE) {
                grid[row][column] = landmark.type().marker();
            }
        }
        grid[center][center] = 'P';

        StringBuilder map = new StringBuilder();
        map.append("КОМПАС: ").append(compassHeading(yaw)).append("     P — ты\n");
        map.append("+-----------+\n");
        for (char[] row : grid) {
            map.append('|');
            for (int column = 0; column < MAP_SIZE; column++) {
                map.append(row[column]).append(' ');
            }
            map.append("|\n");
        }
        map.append("+-----------+\n");
        map.append("Метки: T башня  R руины  C лагерь  M шахта  V деревня\n");
        Landmark nearest = nearest(playerX, playerZ, landmarks);
        if (nearest != null) {
            long blocks = Math.round(distance(playerX, playerZ, nearest.x(), nearest.z()));
            map.append("Ближайшая: ").append(nearest.name()).append(" — ")
                    .append(blocks).append(" блоков, ")
                    .append(directionTo(playerX, playerZ, nearest.x(), nearest.z()));
        } else {
            map.append("Рядом нет отмеченных мест.");
        }
        return map.toString();
    }

    public static Landmark nearest(float playerX, float playerZ, List<Landmark> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) return null;
        Landmark nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Landmark landmark : landmarks) {
            double distance = distance(playerX, playerZ, landmark.x(), landmark.z());
            if (distance < best) {
                best = distance;
                nearest = landmark;
            }
        }
        return nearest;
    }

    private static int distanceSquared(int x1, int z1, int x2, int z2) {
        int dx = x1 - x2;
        int dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private static float normalizeDegrees(float degrees) {
        return (degrees % 360f + 360f) % 360f;
    }
}
