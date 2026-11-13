package com.blockworld.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureManagerAnimationTest {
    @Test
    void idleCreaturesKeepTheirLimbsStill() {
        assertEquals(0f, CreatureManager.gaitSwing(1.2f, false), 0.0001f);
    }

    @Test
    void walkingCreaturesSwingTheirLimbsWithinExpectedRange() {
        float swing = CreatureManager.gaitSwing((float) (Math.PI / 2), true);
        assertEquals(0.42f, swing, 0.001f);
        assertTrue(Math.abs(swing) <= 0.42f);
    }

    @Test
    void slimeBounceStaysInsideSmallAnimationRange() {
        for (int i = 0; i < 100; i++) {
            float bounce = CreatureManager.slimeBounce(i * 0.1f);
            assertTrue(bounce >= 0f);
            assertTrue(bounce <= 0.13f);
        }
    }
}