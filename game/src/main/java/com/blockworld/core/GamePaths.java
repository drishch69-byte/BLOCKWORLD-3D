package com.blockworld.core;

import java.nio.file.Path;
import java.util.Objects;

public final class GamePaths {
    private GamePaths() { }

    public static Path gameDirectory() {
        return Path.of(System.getProperty("user.home"), ".blockworld3d");
    }

    public static Path serversDirectory() { return gameDirectory().resolve("servers"); }
    public static Path multiplayerServerWorld() { return serversDirectory().resolve("hosted-world.bwserver"); }
    public static Path multiplayerServerWorld(Path worldFile) {
        String name = worldFile == null || worldFile.getFileName() == null
                ? "default" : worldFile.getFileName().toString();
        String safeName = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return serversDirectory().resolve(safeName + ".bwserver");
    }

    public static Path worldsDirectory() { return gameDirectory().resolve("worlds"); }
    public static Path defaultWorld() { return worldsDirectory().resolve("My_World.bwworld"); }
    public static Path configDirectory() { return gameDirectory().resolve("config"); }
    public static Path settingsFile() { return configDirectory().resolve("settings.properties"); }
    public static Path favoritesFile() { return configDirectory().resolve("world-favorites.txt"); }
    public static Path playerProfileFile() { return configDirectory().resolve("player-profile.properties"); }
    public static Path worldPreviewsDirectory() { return gameDirectory().resolve("previews"); }
    public static Path worldPreviewFile(Path worldFile) {
        if (worldFile == null || worldFile.getFileName() == null) {
            throw new IllegalArgumentException("World file path is required");
        }
        return worldPreviewsDirectory().resolve(worldFile.getFileName().toString() + ".png");
    }

    public static Path profileDirectory(String versionId) {
        return profileDirectory(gameDirectory(), versionId);
    }

    /** Root-folder overload keeps profile path handling testable without touching the user's home directory. */
    public static Path profileDirectory(Path root, String versionId) {
        Objects.requireNonNull(root, "root");
        return root.toAbsolutePath().normalize().resolve("profiles")
                .resolve(requireProfileId(versionId)).normalize();
    }

    public static Path profileConfigDirectory(String versionId) {
        return profileDirectory(versionId).resolve("config");
    }

    public static Path profileSettingsFile(String versionId) {
        return profileConfigDirectory(versionId).resolve("settings.properties");
    }

    public static Path profileModsDirectory(String versionId) {
        return profileDirectory(versionId).resolve("mods");
    }

    public static Path profileDisabledModsDirectory(String versionId) {
        return profileModsDirectory(versionId).resolve("disabled");
    }

    private static String requireProfileId(String versionId) {
        if (VersionManager.getSupportedVersions().stream()
                .noneMatch(version -> version.id().equals(versionId))) {
            throw new IllegalArgumentException("Unknown game profile: " + versionId);
        }
        return versionId;
    }

    /** Legacy shared mods folder; it remains available to every version profile. */
    public static Path modsDirectory() { return gameDirectory().resolve("mods"); }
    public static Path disabledModsDirectory() { return modsDirectory().resolve("disabled"); }
    public static Path resourcePacksDirectory() { return gameDirectory().resolve("resourcepacks"); }
}
