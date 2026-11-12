package com.blockworld.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Manages trusted Java mod JARs. Enabling a JAR allows its code to execute in-game. */
public final class ModManager {
    public record ModInfo(String fileName, String title, String version, String description,
                          Path file, boolean enabled, boolean apiProvider) {
        @Override public String toString() {
            return (enabled ? "[ВКЛ] " : "[ВЫКЛ] ") + title + " " + version
                    + (apiProvider ? "" : " — нет BlockWorld API provider");
        }
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(GamePaths.modsDirectory());
        Files.createDirectories(GamePaths.disabledModsDirectory());
    }

    public List<ModInfo> listAll() throws IOException {
        ensureDirectories();
        List<ModInfo> result = new ArrayList<>();
        scan(GamePaths.modsDirectory(), true, result);
        scan(GamePaths.disabledModsDirectory(), false, result);
        result.sort(java.util.Comparator.comparing(ModInfo::title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void scan(Path folder, boolean enabled, List<ModInfo> out) throws IOException {
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase().endsWith(".jar")).toList()) {
                String title = file.getFileName().toString();
                String version = "";
                String description = "Без описания";
                boolean provider = false;
                try (JarFile jar = new JarFile(file.toFile())) {
                    var entry = jar.getEntry("META-INF/blockworld-mod.properties");
                    if (entry != null) {
                        java.util.Properties p = new java.util.Properties();
                        try (var in = jar.getInputStream(entry)) { p.load(in); }
                        title = p.getProperty("name", title);
                        version = p.getProperty("version", "");
                        description = p.getProperty("description", description);
                    }
                    provider = jar.getEntry("META-INF/services/com.blockworld.api.BlockWorldMod") != null;
                } catch (IOException invalid) {
                    description = "Не удалось прочитать JAR: " + invalid.getMessage();
                }
                out.add(new ModInfo(file.getFileName().toString(), title, version, description, file, enabled, provider));
            }
        }
    }

    public Path install(Path jarFile) throws IOException {
        ensureDirectories();
        if (!Files.isRegularFile(jarFile) || !jarFile.getFileName().toString().toLowerCase().endsWith(".jar"))
            throw new IOException("Выбери Java-файл .jar");
        try (JarFile ignored = new JarFile(jarFile.toFile())) { /* Validate the archive. */ }
        Path target = GamePaths.modsDirectory().resolve(jarFile.getFileName()).normalize();
        if (!target.getParent().equals(GamePaths.modsDirectory().normalize())) throw new IOException("Недопустимое имя файла");
        if (Files.exists(target)) throw new IOException("Мод с таким именем уже установлен");
        Files.copy(jarFile, target);
        return target;
    }

    public void setEnabled(ModInfo mod, boolean enabled) throws IOException {
        ensureDirectories();
        if (mod.enabled() == enabled) return;
        Path target = (enabled ? GamePaths.modsDirectory() : GamePaths.disabledModsDirectory())
                .resolve(mod.fileName()).normalize();
        if (!target.getParent().equals((enabled ? GamePaths.modsDirectory() : GamePaths.disabledModsDirectory()).normalize()))
            throw new IOException("Недопустимое имя мода");
        if (Files.exists(target)) throw new IOException("Файл с таким именем уже существует");
        Files.move(mod.file(), target);
    }

    public void remove(ModInfo mod) throws IOException { Files.deleteIfExists(mod.file()); }
}
