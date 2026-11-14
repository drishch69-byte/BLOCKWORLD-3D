package com.blockworld.core;

/** Crafted item IDs are stable because player sidecar files store these names. */
public enum ItemType {
    PLANKS("planks", "Доски"),
    STICKS("sticks", "Палки"),
    CRAFTING_TABLE("crafting_table", "Верстак"),
    TORCH("torch", "Факелы"),
    WOODEN_PICKAXE("wooden_pickaxe", "Деревянная кирка"),
    STONE_PICKAXE("stone_pickaxe", "Каменная кирка"),
    IRON_PICKAXE("iron_pickaxe", "Железная кирка"),
    WOODEN_SHOVEL("wooden_shovel", "Деревянная лопата"),
    IRON_HELMET("iron_helmet", "Железный шлем"),
    IRON_CHESTPLATE("iron_chestplate", "Железный нагрудник"),
    IRON_LEGGINGS("iron_leggings", "Железные поножи"),
    IRON_BOOTS("iron_boots", "Железные ботинки"),
    WOODEN_HOE("wooden_hoe", "Деревянная мотыга"),
    WHEAT_SEEDS("wheat_seeds", "Семена пшеницы"),
    WHEAT("wheat", "Пшеница"),
    BREAD("bread", "Хлеб"),
    FURNACE("furnace", "Печь"),
    IRON_INGOT("iron_ingot", "Железный слиток"),
    COPPER_INGOT("copper_ingot", "Медный слиток"),
    EMERALD("emerald", "Изумруд"),
    FISHING_ROD("fishing_rod", "Удочка"),
    RAW_FISH("raw_fish", "Сырая рыба"),
    COOKED_FISH("cooked_fish", "Жареная рыба"),
    ANCIENT_RELIC("ancient_relic", "Древняя реликвия"),
    SUN_SIGIL("sun_sigil", "Солнечная печать"),
    MOON_SIGIL("moon_sigil", "Лунная печать"),
    EMPTY_BUCKET("empty_bucket", "Пустое ведро"),
    SHEARS("shears", "Ножницы"),
    MILK_BUCKET("milk_bucket", "Ведро молока"),
    WOOL("wool", "Шерсть"),
    EGG("egg", "Яйцо");

    private final String id;
    private final String displayName;

    ItemType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }

    public static ItemType fromId(String id) {
        for (ItemType type : values()) if (type.id.equalsIgnoreCase(id)) return type;
        throw new IllegalArgumentException("Неизвестный предмет: " + id);
    }
}
