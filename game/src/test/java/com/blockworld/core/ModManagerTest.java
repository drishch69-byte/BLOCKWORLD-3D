package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModManagerTest {
    @TempDir
    Path tempDir;

    private Path createJar(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            jar.putNextEntry(new JarEntry("META-INF/blockworld-mod.properties"));
            jar.write("name=Test Mod\nversion=1.0\ndescription=Profile test\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("META-INF/services/com.blockworld.api.BlockWorldMod"));
            jar.write("example.TestMod\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return file;
    }

    @Test
    void modOperationsStayInsideTheSelectedProfile() throws IOException {
        Path profileA = tempDir.resolve("profiles/1.9.8/mods");
        Path profileB = tempDir.resolve("profiles/1.9.9/mods");
        ModManager managerA = new ModManager(profileA, profileA.resolve("disabled"));
        ModManager managerB = new ModManager(profileB, profileB.resolve("disabled"));

        Path installed = managerA.install(createJar("test-mod.jar"));
        assertTrue(Files.isRegularFile(installed));
        assertTrue(managerB.listAll().isEmpty());
        assertEquals("Test Mod", managerA.listAll().get(0).title());

        managerA.setEnabled(managerA.listAll().get(0), false);
        assertFalse(Files.exists(installed));
        assertEquals(1, managerA.listAll().size());
        assertFalse(managerA.listAll().get(0).enabled());

        managerA.setEnabled(managerA.listAll().get(0), true);
        assertTrue(managerA.listAll().get(0).enabled());
        assertTrue(managerB.listAll().isEmpty());

        managerA.remove(managerA.listAll().get(0));
        assertTrue(managerA.listAll().isEmpty());
    }

    @Test
    void rejectsNonJarArchives() throws IOException {
        ModManager manager = new ModManager(tempDir.resolve("mods"), tempDir.resolve("mods/disabled"));
        Path invalid = tempDir.resolve("not-a-mod.jar");
        Files.writeString(invalid, "not a zip archive");
        assertThrows(IOException.class, () -> manager.install(invalid));
    }
}
