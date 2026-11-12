package com.blockworld.core;

/** Persistent creature state; rendering and movement are handled by the game layer. */
public record WorldCreature(long id, CreatureType type, float x, float y, float z, float yaw) {
    public WorldCreature {
        if (id < 0) throw new IllegalArgumentException("ID существа не может быть отрицательным");
        if (type == null) throw new IllegalArgumentException("Тип существа обязателен");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Координаты существа должны быть конечными числами");
        }
        if (Math.abs(x) > 100_000f || Math.abs(y) > 100_000f || Math.abs(z) > 100_000f) {
            throw new IllegalArgumentException("Координаты существа выходят за допустимые пределы");
        }
    }
}
