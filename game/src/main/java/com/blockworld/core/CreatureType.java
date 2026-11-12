package com.blockworld.core;

/** Stable creature IDs stored in world files. Do not rename existing IDs. */
public enum CreatureType {
    COW("cow", "Корова", 0.55f),
    SHEEP("sheep", "Овца", 0.48f),
    PIG("pig", "Свинья", 0.62f),
    CHICKEN("chicken", "Курица", 0.78f),
    VILLAGER("villager", "Житель", 0.30f),
    SLIME("slime", "Слайм", 0.45f);

    private final String id;
    private final String displayName;
    private final float moveSpeed;

    CreatureType(String id, String displayName, float moveSpeed) {
        this.id = id;
        this.displayName = displayName;
        this.moveSpeed = moveSpeed;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public float moveSpeed() { return moveSpeed; }

    public static CreatureType fromId(String id) {
        for (CreatureType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        throw new IllegalArgumentException("Неизвестное существо: " + id);
    }
}
