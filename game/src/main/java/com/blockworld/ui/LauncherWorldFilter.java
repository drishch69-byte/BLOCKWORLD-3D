package com.blockworld.ui;

import com.blockworld.core.WorldEntry;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Search and favorites filtering for the launcher's saved-world list. */
final class LauncherWorldFilter {
    private LauncherWorldFilter() { }

    static List<WorldEntry> filter(List<WorldEntry> worlds, String query,
                                   Set<String> favorites, boolean favoritesOnly) {
        if (worlds == null || worlds.isEmpty()) return List.of();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        Set<String> favoriteNames = favorites == null ? Set.of() : favorites;

        return worlds.stream()
                .filter(world -> world != null)
                .filter(world -> !favoritesOnly
                        || (world.file() != null
                            && favoriteNames.contains(world.file().getFileName().toString())))
                .filter(world -> matches(world, terms))
                .toList();
    }

    private static boolean matches(WorldEntry world, String[] terms) {
        if (terms.length == 0) return true;
        String haystack = String.join(" ",
                safe(world.name()), safe(world.lastVersion()),
                safe(world.integrityStatus()),
                world.file() == null ? "" : world.file().getFileName().toString())
                .toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!haystack.contains(term)) return false;
        }
        return true;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
