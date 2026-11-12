package com.blockworld.core;

/** A playable ruleset/profile for the BlockWorld game. */
public record GameVersion(
        String id,
        String displayName,
        String description,
        int worldRadius,
        boolean trees,
        boolean caves,
        boolean ores,
        boolean extendedHotbar,
        boolean survival) {

    @Override
    public String toString() {
        return id + "  |  " + displayName;
    }
}
