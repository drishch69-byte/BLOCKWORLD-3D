package com.blockworld.core;

import com.blockworld.api.BlockWorldMod;
import com.blockworld.api.ModContext;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Loads enabled mod JARs with the small ServiceLoader-based BLOCKWORLD API. */
public final class ModLoader implements AutoCloseable {
    private record Loaded(BlockWorldMod mod, URLClassLoader classLoader, String source) { }
    private final List<Loaded> loaded = new ArrayList<>();
    private final Path modsDirectory;

    public ModLoader(Path modsDirectory) { this.modsDirectory = modsDirectory; }

    public int load(ModContext context) {
        try {
            Files.createDirectories(modsDirectory);
            Files.createDirectories(context.configDirectory());
        } catch (IOException ex) { GameLog.error("Не удалось открыть папку модов/настроек", ex); return 0; }
        try (var stream = Files.list(modsDirectory)) {
            for (Path jar : stream.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase().endsWith(".jar")).sorted().toList()) {
                URLClassLoader loader = null;
                boolean retained = false;
                try {
                    loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, BlockWorldMod.class.getClassLoader());
                    int found = 0;
                    String modFileName = jar.getFileName().toString();
                    String configKey = modFileName.replaceFirst("(?i)\\.jar$", "")
                            .replaceAll("[^A-Za-z0-9._-]", "_");
                    if (configKey.isBlank()) configKey = "mod";
                    configKey += "-" + Integer.toHexString(modFileName.hashCode());
                    Path modConfig = context.configDirectory().resolve("mods").resolve(configKey);
                    Files.createDirectories(modConfig);
                    ModContext modContext = new ModContext(context.world(), context.version(), modConfig);
                    for (BlockWorldMod mod : ServiceLoader.load(BlockWorldMod.class, loader)) {
                        try {
                            mod.onInitialize(modContext);
                            loaded.add(new Loaded(mod, loader, modFileName));
                            found++;
                            retained = true;
                            GameLog.info("Мод загружен: " + jar.getFileName() + " / " + mod.getClass().getName());
                        } catch (Throwable error) {
                            GameLog.error("Ошибка инициализации мода " + jar.getFileName(), error);
                        }
                    }
                    if (found == 0) GameLog.info("JAR пропущен: нет BlockWorldMod provider: " + jar.getFileName());
                } catch (Throwable error) {
                    GameLog.error("Не удалось загрузить мод " + jar.getFileName(), error);
                } finally {
                    if (!retained && loader != null) try { loader.close(); } catch (IOException ex) {
                        GameLog.error("Не удалось закрыть загрузчик мода", ex);
                    }
                }
            }
        } catch (IOException ex) { GameLog.error("Ошибка чтения каталога модов", ex); }
        return loaded.size();
    }

    public void worldReady() { invoke("onWorldReady", BlockWorldMod::onWorldReady); }
    public void update(float tpf) { invoke("onUpdate", mod -> mod.onUpdate(tpf)); }
    public void beforeSave() { invoke("onWorldSave", BlockWorldMod::onWorldSave); }

    private void invoke(String label, ModCall call) {
        for (Loaded item : List.copyOf(loaded)) {
            try { call.run(item.mod()); }
            catch (Throwable error) { GameLog.error("Ошибка мода " + item.source() + " при " + label, error); }
        }
    }

    @FunctionalInterface private interface ModCall { void run(BlockWorldMod mod) throws Exception; }

    @Override public void close() {
        java.util.Set<URLClassLoader> classLoaders = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = loaded.size() - 1; i >= 0; i--) {
            Loaded item = loaded.get(i);
            try { item.mod().onShutdown(); }
            catch (Throwable error) { GameLog.error("Ошибка остановки мода " + item.source(), error); }
            classLoaders.add(item.classLoader());
        }
        for (URLClassLoader classLoader : classLoaders) {
            try { classLoader.close(); }
            catch (IOException error) { GameLog.error("Не удалось закрыть загрузчик мода", error); }
        }
        loaded.clear();
    }
}
