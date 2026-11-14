package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLogTest {
    @TempDir
    Path directory;

    @Test
    void rotatesPreviousRunWhenLogWouldExceedOneMiB() throws Exception {
        Path log = directory.resolve("latest-run.log");
        Files.writeString(log, "x".repeat((int) GameLog.MAX_LOG_BYTES));
        GameLog.append(log, "INFO", "fresh session", null);
        Path previous = directory.resolve("latest-run.log.1");
        assertTrue(Files.isRegularFile(previous));
        assertTrue(Files.size(previous) >= GameLog.MAX_LOG_BYTES);
        assertTrue(Files.readString(log).contains("fresh session"));
        assertTrue(Files.size(log) < GameLog.MAX_LOG_BYTES);
    }

    @Test
    void writesToRelativeFileWithoutParentDirectory() throws Exception {
        Path log = Path.of("blockworld-game-log-parentless-test.log");
        try {
            Files.deleteIfExists(log);
            GameLog.append(log, "INFO", "relative path works", null);
            assertTrue(Files.readString(log).contains("relative path works"));
        } finally {
            Files.deleteIfExists(log);
        }
    }

    @Test
    void keepsSmallLogsAndAppendsWithoutCreatingBackup() throws Exception {
        Path log = directory.resolve("latest-run.log");
        GameLog.append(log, "INFO", "first", null);
        GameLog.append(log, "ERROR", "second", new IllegalStateException("test"));
        assertTrue(Files.readString(log).contains("first"));
        assertTrue(Files.readString(log).contains("second"));
        assertFalse(Files.exists(directory.resolve("latest-run.log.1")));
    }
}
