package com.blockworld.core;

import com.jme3.math.ColorRGBA;

/** All block IDs ever introduced by BlockWorld. Keep IDs stable for saved worlds. */
public enum BlockType {
    GRASS("grass", "Трава", 0.30f, 0.68f, 0.24f),
    DIRT("dirt", "Земля", 0.48f, 0.31f, 0.17f),
    STONE("stone", "Камень", 0.48f, 0.50f, 0.53f),
    WOOD("wood", "Дерево", 0.48f, 0.29f, 0.12f),
    LEAVES("leaves", "Листва", 0.16f, 0.48f, 0.18f),
    SAND("sand", "Песок", 0.83f, 0.76f, 0.52f),
    COAL_ORE("coal_ore", "Угольная руда", 0.22f, 0.23f, 0.24f),
    IRON_ORE("iron_ore", "Железная руда", 0.70f, 0.48f, 0.36f),
    GOLD_ORE("gold_ore", "Золотая руда", 0.93f, 0.72f, 0.16f),
    WATER("water", "Вода", 0.12f, 0.42f, 0.88f),
    LAVA("lava", "Лава", 1.00f, 0.27f, 0.035f),
    SNOW("snow", "Снег", 0.88f, 0.93f, 0.98f),
    SANDSTONE("sandstone", "Песчаник", 0.76f, 0.66f, 0.43f),
    FARMLAND("farmland", "Пахотная земля", 0.38f, 0.25f, 0.14f),
    WHEAT_CROP("wheat_crop", "Пшеница", 0.58f, 0.72f, 0.19f),
    FURNACE("furnace", "Печь", 0.31f, 0.32f, 0.34f),
    COPPER_ORE("copper_ore", "Медная руда", 0.72f, 0.39f, 0.22f),
    EMERALD_ORE("emerald_ore", "Изумрудная руда", 0.12f, 0.64f, 0.34f),
    AMETHYST_ORE("amethyst_ore", "Аметистовая жила", 0.58f, 0.34f, 0.82f),
    PODZOL("podzol", "Подзол", 0.39f, 0.27f, 0.17f),
    MUD("mud", "Болотная почва", 0.30f, 0.34f, 0.25f),
    TREASURE_CHEST("treasure_chest", "Сундук с находками", 0.48f, 0.29f, 0.12f),
    RUBY_ORE("ruby_ore", "Рубиновая руда", 0.72f, 0.14f, 0.25f),
    SAPPHIRE_ORE("sapphire_ore", "Сапфировая руда", 0.14f, 0.42f, 0.78f),
    GLOWING_CRYSTAL("glowing_crystal", "Светящийся кристалл", 0.12f, 0.92f, 0.88f),
    CAVE_MOSS("cave_moss", "Пещерный мох", 0.18f, 0.42f, 0.22f),
    CAVE_FERN("cave_fern", "Пещерный папоротник", 0.22f, 0.64f, 0.31f),
    CRYSTAL_CACHE("crystal_cache", "Кристальный тайник", 0.55f, 0.36f, 0.82f),
    ANCIENT_CACHE("ancient_cache", "Древний тайник", 0.72f, 0.58f, 0.31f),
    WOODEN_FENCE("wooden_fence", "Деревянное ограждение", 0.48f, 0.30f, 0.13f),
    FEED_TROUGH("feed_trough", "Кормушка", 0.56f, 0.36f, 0.17f);

    private final String id;
    private final String displayName;
    private final float red;
    private final float green;
    private final float blue;

    BlockType(String id, String displayName, float red, float green, float blue) {
        this.id = id;
        this.displayName = displayName;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Stable texture-atlas slot. Do not renumber existing entries when adding blocks. */
    public int textureSlot() {
        return switch (this) {
            case GRASS -> 0;
            case DIRT -> 1;
            case STONE -> 2;
            case WOOD -> 3;
            case LEAVES -> 4;
            case SAND -> 5;
            case COAL_ORE -> 6;
            case IRON_ORE -> 7;
            case GOLD_ORE -> 8;
            case WATER -> 9;
            case LAVA -> 10;
            case SNOW -> 11;
            case SANDSTONE -> 12;
            case FARMLAND -> 13;
            case WHEAT_CROP -> 14;
            case FURNACE -> 15;
            case COPPER_ORE -> 16;
            case EMERALD_ORE -> 17;
            case AMETHYST_ORE -> 18;
            case PODZOL -> 19;
            case MUD -> 20;
            case TREASURE_CHEST -> 21;
            case RUBY_ORE -> 22;
            case SAPPHIRE_ORE -> 23;
            case GLOWING_CRYSTAL -> 24;
            case CAVE_MOSS -> 25;
            case CAVE_FERN -> 26;
            case CRYSTAL_CACHE -> 27;
            case ANCIENT_CACHE -> 28;
            case WOODEN_FENCE -> 29;
            case FEED_TROUGH -> 30;
        };
    }

    public ColorRGBA color() {
        return new ColorRGBA(red, green, blue, 1f);
    }

    public static BlockType fromId(String id) {
        for (BlockType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный блок: " + id);
    }
}
