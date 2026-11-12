package com.blockworld.core;

import java.nio.file.Path;
import java.time.Instant;

/** One saved world shown in the launcher, independent of the selected game version. */
public record WorldEntry(String name, Path file, String lastVersion, Instant modified) {
    @Override
    public String toString() {
        return name + "  |  последняя версия: " + lastVersion;
    }
}
