package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBackupManagerTest {
    @TempDir Path tempDir;

    @Test
    void restoreCreatesSafetyBackupAndRestoresSelectedCopy() throws IOException {
        Path world = tempDir.resolve("World.bwworld");
        String oldState = "BLOCKWORLD_WORLD\\nformat=1\\ngameVersion=0.1.0\\nseed=1\\n--blocks--\\n0,0,0,grass\\n";
        String newState = "BLOCKWORLD_WORLD\\nformat=1\\ngameVersion=0.1.0\\nseed=2\\n--blocks--\\n1,0,0,dirt\\n";
        Files.writeString(world, oldState);
        Path originalBackup = WorldBackupManager.create(world);
        Files.writeString(world, newState);

        Path safetyBackup = WorldBackupManager.restore(world, originalBackup);

        assertEquals(oldState, Files.readString(world));
        assertEquals(newState, Files.readString(safetyBackup));
        assertEquals(2, WorldBackupManager.list(world).size());
    }

    @Test
    void refusesBackupFromAnotherWorld() throws IOException {
        Path worldA = tempDir.resolve("A.bwworld");
        Path worldB = tempDir.resolve("B.bwworld");
        Files.writeString(worldA, "BLOCKWORLD_WORLD\\nformat=1\\ngameVersion=0.1.0\\n--blocks--\\nA");
        Files.writeString(worldB, "BLOCKWORLD_WORLD\\nformat=1\\ngameVersion=0.1.0\\n--blocks--\\nB");
        Path backupB = WorldBackupManager.create(worldB);

        assertThrows(IOException.class, () -> WorldBackupManager.restore(worldA, backupB));
        assertTrue(Files.readString(worldA).endsWith("A"));
    }

    @Test
    void refusesMalformedBackupWithoutChangingCurrentWorld() throws IOException {
        Path world = tempDir.resolve("Safe.bwworld");
        Files.writeString(world, "BLOCKWORLD_WORLD\\nformat=1\\ngameVersion=0.1.0\\n--blocks--\\ncurrent");
        Path malformed = tempDir.resolve("Safe.bwworld.backup-manual");
        Files.writeString(malformed, "not-a-save");

        assertThrows(IOException.class, () -> WorldBackupManager.restore(world, malformed));
        assertTrue(Files.readString(world).endsWith("current"));
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
        assertTrue(backups.get(0).equals(backupA));
    }
}
