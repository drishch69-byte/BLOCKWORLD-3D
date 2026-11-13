package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogTailReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsWholeShortLogWithoutTruncationMarker() throws IOException {
        Path log = tempDir.resolve("latest-run.log");
        String content = "[INFO] Начало\n[INFO] Готово\n";
        Files.writeString(log, content);

        assertEquals(content, LogTailReader.readTail(log, 100));
    }

    @Test
    void boundsLargeLogAndKeepsItsLastEntries() throws IOException {
        Path log = tempDir.resolve("large.log");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 2000; i++) content.append("line-").append(i).append("-длинная запись диагностики\n");
        Files.writeString(log, content);

        String tail = LogTailReader.readTail(log, 512);

        assertTrue(tail.startsWith("[Показан конец журнала; начало опущено]"));
        assertTrue(tail.contains("line-1999-"));
        assertTrue(!tail.contains("line-0-"));
        assertTrue(tail.length() <= 512 + 60);
    }

    @Test
    void rejectsInvalidCharacterLimit() {
        assertThrows(IllegalArgumentException.class, () -> LogTailReader.readTail(Path.of("unused.log"), 0));
    }
}
