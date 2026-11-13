package com.blockworld.core;

import java.nio.file.Path;
import java.time.Instant;

/** One saved world shown in the launcher, independent of the selected game version. */
public record WorldEntry(String name, Path file, String lastVersion, Instant modified,
                         long sizeBytes, String integrityStatus) {
    public WorldEntry(String name, Path file, String lastVersion, Instant modified) {
        this(name, file, lastVersion, modified, 0L, "НЕ ПРОВЕРЕНО");
    }

    @Override
    public String toString() {
        return name + "  |  " + lastVersion + "  |  " + integrityStatus;
    }
}
