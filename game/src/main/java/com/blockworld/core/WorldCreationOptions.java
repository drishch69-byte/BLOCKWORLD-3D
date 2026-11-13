package com.blockworld.core;

/** Options selected in the launcher before generating a new world. */
public record WorldCreationOptions(Long seed, WorldSize size) {
    public WorldCreationOptions {
        size = size == null ? WorldSize.NORMAL : size;
    }

    public int worldRadius() {
        return size.radius();
    }

    public enum WorldSize {
        SMALL("Маленький — 193×193 блоков", 96),
        NORMAL("Обычный — 289×289 блоков", 144),
        LARGE("Большой — 385×385 блоков", 192);

        private final String label;
        private final int radius;

        WorldSize(String label, int radius) {
            this.label = label;
            this.radius = radius;
        }

        public int radius() {
            return radius;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
