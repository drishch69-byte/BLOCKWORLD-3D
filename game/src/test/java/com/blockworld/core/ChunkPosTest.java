package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkPosTest {
    @Test
    void mapsPositiveAndNegativeBlockCoordinatesToSixteenBySixteenChunks() {
        assertEquals(new ChunkPos(0, 0), ChunkPos.fromBlock(0, 15));
        assertEquals(new ChunkPos(1, 1), ChunkPos.fromBlock(16, 31));
        assertEquals(new ChunkPos(-1, -1), ChunkPos.fromBlock(-1, -16));
        assertEquals(new ChunkPos(-2, 0), ChunkPos.fromBlock(-17, 15));
    }
}
