package com.blockworld.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GamePathsWorldPreviewTest {
    @Test
    void previewPathIsSeparateAndKeepsUnicodeWorldName() {
        Path world = Path.of("worlds", "Мой_лес.bwworld");
        Path preview = GamePaths.worldPreviewFile(world);
        assertEquals("Мой_лес.bwworld.png", preview.getFileName().toString());
        assertEquals("previews", preview.getParent().getFileName().toString());
    }

    @Test
    void previewPathRejectsMissingWorldPath() {
        assertThrows(IllegalArgumentException.class, () -> GamePaths.worldPreviewFile(null));
    }
}
