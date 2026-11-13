package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldBackupManagerTest {
    @TempDir Path tempDir;

    private static String worldState(String seed) {
        return "BLOCKWORLD_WORLD\nformat=1\ngameVersion=0.1.0\nseed=" + seed
                + "\n--blocks--\n0,0,0,grass\n";
    }

    @Test
    void restoreCreatesSafetyBackupAndRestoresSelectedCopy() throws IOException {
        Path world = tempDir.resolve("World.bwworld");
        String oldState = worldState("1");
        String newState = worldState("2");
        Files.writeString(world, oldState);
        Path originalBackup = WorldBackupManager.create(world);
        Files.writeString(world, newState);

        Path safetyBackup = WorldBackupManager.restore(world, originalBackup);

        assertEquals(oldState, Files.readString(world));
        assertEquals(newState, Files.readString(safetyBackup));
        assertEquals(2, WorldBackupManager.list(world).size());
    }

    @Test
    void restoreRestoresPlayerFarmAndAchievementSidecars() throws IOException {
        Path world = tempDir.resolve("World.bwworld");
        Files.writeString(world, worldState("old"));
        Path player = PlayerDataStore.sidecar(world);
        Path farm = FarmDataStore.sidecar(world);
        Path achievements = AchievementDataStore.sidecar(world);
        Path pets = PetDataStore.sidecar(world);
        Files.writeString(player, "inventory=old\n");
        Files.writeString(farm, "farm=old\n");
        Files.writeString(achievements, "achievement=old\n");
        Files.writeString(pets, "pet.17=stay\n");

        Path backup = WorldBackupManager.create(world);
        Files.writeString(world, worldState("new"));
        Files.writeString(player, "inventory=new\n");
        Files.writeString(farm, "farm=new\n");
        Files.writeString(achievements, "achievement=new\n");
        Files.writeString(pets, "pet.17=follow\n");

        WorldBackupManager.restore(world, backup);

        assertEquals("inventory=old\n", Files.readString(player));
        assertEquals("farm=old\n", Files.readString(farm));
        assertEquals("achievement=old\n", Files.readString(achievements));
        assertEquals("pet.17=stay\n", Files.readString(pets));
        assertEquals(2, WorldBackupManager.list(world).size());
    }

    @Test
    void restoreRemovesSidecarsThatDidNotExistWhenBackupWasCreated() throws IOException {
        Path world = tempDir.resolve("World.bwworld");
        Files.writeString(world, worldState("old"));
        Path player = PlayerDataStore.sidecar(world);
        Path farm = FarmDataStore.sidecar(world);
        Files.writeString(player, "inventory=old\n");

        Path backup = WorldBackupManager.create(world);
        Files.writeString(world, worldState("new"));
        Files.writeString(player, "inventory=new\n");
        Files.writeString(farm, "farm=new\n");

        WorldBackupManager.restore(world, backup);

        assertEquals("inventory=old\n", Files.readString(player));
        assertFalse(Files.exists(farm));
    }

    @Test
    void backupListExcludesManifestAndSidecarFilesAndDeleteRemovesThem() throws IOException {
        Path world = tempDir.resolve("World.bwworld");
        Files.writeString(world, worldState("old"));
        Files.writeString(PlayerDataStore.sidecar(world), "inventory=old\n");
        Files.writeString(PetDataStore.sidecar(world), "pet.3=stay\n");
        Path backup = WorldBackupManager.create(world);

        assertEquals(List.of(backup), WorldBackupManager.list(world));
        Path backupSidecar = backup.resolveSibling(backup.getFileName() + ".player.properties");
        Path manifest = backup.resolveSibling(backup.getFileName() + ".manifest");
        Path backupPets = backup.resolveSibling(backup.getFileName() + ".pets.properties");
        assertTrue(Files.exists(backupSidecar));
        assertTrue(Files.exists(backupPets));
        assertTrue(Files.exists(manifest));

        WorldBackupManager.delete(world, backup);

        assertFalse(Files.exists(backup));
        assertFalse(Files.exists(backupSidecar));
        assertFalse(Files.exists(backupPets));
        assertFalse(Files.exists(manifest));
        assertTrue(WorldBackupManager.list(world).isEmpty());
    }

    @Test
    void refusesBackupFromAnotherWorld() throws IOException {
        Path worldA = tempDir.resolve("A.bwworld");
        Path worldB = tempDir.resolve("B.bwworld");
        Files.writeString(worldA, worldState("A"));
        Files.writeString(worldB, worldState("B"));
        Path backupB = WorldBackupManager.create(worldB);

        assertThrows(IOException.class, () -> WorldBackupManager.restore(worldA, backupB));
        assertTrue(Files.readString(worldA).contains("seed=A"));
    }

    @Test
    void refusesMalformedBackupWithoutChangingCurrentWorld() throws IOException {
        Path world = tempDir.resolve("Safe.bwworld");
        Files.writeString(world, worldState("current"));
        Path malformed = tempDir.resolve("Safe.bwworld.backup-manual");
        Files.writeString(malformed, "not-a-save");

        assertThrows(IOException.class, () -> WorldBackupManager.restore(world, malformed));
        assertTrue(Files.readString(world).contains("seed=current"));
    }

    @Test
    void refusesIncompleteSidecarManifestBeforeChangingWorld() throws IOException {
        Path world = tempDir.resolve("Safe.bwworld");
        Files.writeString(world, worldState("current"));
        Path malformed = tempDir.resolve("Safe.bwworld.backup-manual");
        Files.writeString(malformed, worldState("backup"));
        Files.writeString(malformed.resolveSibling(malformed.getFileName() + ".manifest"),
                "format=1\nsidecar.player.properties=true\n");

        assertThrows(IOException.class, () -> WorldBackupManager.restore(world, malformed));
        assertTrue(Files.readString(world).contains("seed=current"));
    }

    @Test
    void restoresLegacyBackupWithoutManifestAndKeepsCurrentSidecars() throws IOException {
        Path world = tempDir.resolve("World.bwworld");
        Files.writeString(world, worldState("old"));
        Path legacyBackup = tempDir.resolve("World.bwworld.backup-legacy");
        Files.writeString(legacyBackup, worldState("legacy"));
        Path player = PlayerDataStore.sidecar(world);
        Files.writeString(player, "inventory=current\n");

        WorldBackupManager.restore(world, legacyBackup);

        assertTrue(Files.readString(world).contains("seed=legacy"));
        assertEquals("inventory=current\n", Files.readString(player));
    }

    @Test
    void restoresOlderBackupManifestThatPredatesPetSidecars() throws IOException {
        Path world = tempDir.resolve("Legacy.bwworld");
        Files.writeString(world, worldState("current"));
        Path pets = PetDataStore.sidecar(world);
        Files.writeString(pets, "pet.7=stay\n");
        Path legacyBackup = tempDir.resolve("Legacy.bwworld.backup-old-format");
        Files.writeString(legacyBackup, worldState("backup"));
        Files.writeString(legacyBackup.resolveSibling(legacyBackup.getFileName() + ".manifest"),
                "format=1\nsidecar.player.properties=false\nsidecar.farm.properties=false\nsidecar.achievements.properties=false\n");
        WorldBackupManager.restore(world, legacyBackup);
        assertTrue(Files.readString(world).contains("seed=backup"));
        assertFalse(Files.exists(pets));
    }

    @Test
    void listsOnlyBackupsBelongingToTheRequestedWorld() throws IOException {
        Path worldA = tempDir.resolve("A.bwworld");
        Path worldB = tempDir.resolve("B.bwworld");
        Files.writeString(worldA, "A");
        Files.writeString(worldB, "B");
        Path backupA = WorldBackupManager.create(worldA);
        WorldBackupManager.create(worldB);

        List<Path> backups = WorldBackupManager.list(worldA);
        assertEquals(1, backups.size());
        assertEquals(backupA, backups.get(0));
    }
}
