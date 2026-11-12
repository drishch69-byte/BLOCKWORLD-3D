package com.blockworld.core;

/** Simple fictional equipment slots for the survival character. */
public enum ArmorSlot {
    HELMET("helmet", "Шлем", ItemType.IRON_HELMET, 2),
    CHESTPLATE("chestplate", "Нагрудник", ItemType.IRON_CHESTPLATE, 3),
    LEGGINGS("leggings", "Поножи", ItemType.IRON_LEGGINGS, 2),
    BOOTS("boots", "Ботинки", ItemType.IRON_BOOTS, 1);

    private final String id;
    private final String displayName;
    private final ItemType item;
    private final int defensePoints;

    ArmorSlot(String id, String displayName, ItemType item, int defensePoints) {
        this.id = id;
        this.displayName = displayName;
        this.item = item;
        this.defensePoints = defensePoints;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public ItemType item() { return item; }
    public int defensePoints() { return defensePoints; }

    public static ArmorSlot fromId(String id) {
        for (ArmorSlot slot : values()) if (slot.id.equalsIgnoreCase(id)) return slot;
        throw new IllegalArgumentException("Неизвестный слот экипировки: " + id);
    }
}
