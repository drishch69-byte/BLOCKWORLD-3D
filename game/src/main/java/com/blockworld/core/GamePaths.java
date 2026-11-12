package com.blockworld.core;

import java.nio.file.Path;

public final class GamePaths {
    private GamePaths() {
    }

    public static Path gameDirectory() {
        return Path.of(System.getProperty("user.home"), ".blockworld3d");
    }

    public static Path worldsDirectory() {
        return gameDirectory().resolve("worlds");
    }

    public static Path defaultWorld() {
        return worldsDirectory().resolve("My_World.bwworld");
    }

    public static Path configDirectory() { return gameDirectory().resolve("config"); }
    public static Path settingsFile() { return configDirectory().resolve("settings.properties"); }
    public static Path modsDirectory() { return gameDirectory().resolve("mods"); }
    public static Path disabledModsDirectory() { return modsDirectory().resolve("disabled"); }
    public static Path resourcePacksDirectory() { return gameDirectory().resolve("resourcepacks"); }
}
