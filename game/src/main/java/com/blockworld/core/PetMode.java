package com.blockworld.core;

public enum PetMode {
    FOLLOW("follow"), STAY("stay");
    private final String id;
    PetMode(String id) { this.id = id; }
    public String id() { return id; }
    public PetMode toggle() { return this == FOLLOW ? STAY : FOLLOW; }
    public static PetMode fromId(String id) {
        if (id == null) return null;
        for (PetMode mode : values()) if (mode.id.equalsIgnoreCase(id.trim())) return mode;
        return null;
    }
}
