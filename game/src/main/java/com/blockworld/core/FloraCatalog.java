package com.blockworld.core;

/** Seeded decorative meadow palette. */
public final class FloraCatalog {
    private FloraCatalog() { }

    public enum Grass {
        SHORT_GRASS("Низкая трава"),
        TALL_GRASS("Высокая трава"),
        FERN("Папоротник");

        private final String displayName;
        Grass(String name) { displayName = name; }
        public String displayName() { return displayName; }
    }

    public enum Flower {
        DANDELION("Одуванчик", .98f, .82f, .12f),
        POPPY("Мак", .88f, .16f, .18f),
        BLUE_ORCHID("Голубая орхидея", .24f, .62f, .95f),
        ALLIUM("Лук", .72f, .40f, .86f),
        AZURE_BLUET("Хьюстон", .78f, .84f, .92f),
        RED_TULIP("Красный тюльпан", .88f, .18f, .20f),
        ORANGE_TULIP("Оранжевый тюльпан", .98f, .48f, .10f),
        WHITE_TULIP("Белый тюльпан", .92f, .91f, .84f),
        PINK_TULIP("Розовый тюльпан", .96f, .56f, .72f),
        CORNFLOWER("Василёк", .20f, .38f, .92f),
        LILY_OF_THE_VALLEY("Ландыш", .92f, .92f, .84f),
        SUNFLOWER("Подсолнух", .98f, .76f, .08f),
        LILAC("Сирень", .65f, .42f, .82f),
        ROSE_BUSH("Розовый куст", .86f, .18f, .34f),
        PEONY("Пион", .96f, .54f, .72f);

        private final String displayName;
        private final float red, green, blue;

        Flower(String name, float r, float g, float b) {
            displayName = name;
            red = r;
            green = g;
            blue = b;
        }

        public String displayName() { return displayName; }
        public float red() { return red; }
        public float green() { return green; }
        public float blue() { return blue; }
    }

    public static Grass grassAt(long seed, int x, int z) {
        return Grass.values()[Math.floorMod((int) mix(seed, x, z), 3)];
    }

    public static Flower flowerAt(long seed, int x, int z, BiomeType biome) {
        Flower[] palette = switch (biome == null ? BiomeType.PLAINS : biome) {
            case FOREST, TAIGA -> new Flower[]{Flower.ALLIUM, Flower.LILY_OF_THE_VALLEY,
                    Flower.BLUE_ORCHID, Flower.PEONY, Flower.PINK_TULIP};
            case SWAMP -> new Flower[]{Flower.BLUE_ORCHID, Flower.LILY_OF_THE_VALLEY,
                    Flower.AZURE_BLUET, Flower.POPPY};
            case MOUNTAINS, SNOWY_TUNDRA -> new Flower[]{Flower.CORNFLOWER, Flower.WHITE_TULIP,
                    Flower.AZURE_BLUET, Flower.POPPY};
            case DESERT -> new Flower[]{Flower.DANDELION, Flower.ORANGE_TULIP, Flower.RED_TULIP};
            default -> Flower.values();
        };
        return palette[Math.floorMod((int) mix(seed ^ 0x6A09E667F3BCC909L, x, z), palette.length)];
    }

    private static long mix(long seed, int x, int z) {
        long value = seed ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
