package com.blockworld.game;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerCameraTest {
    @Test void forwardAtZeroYawFacesNegativeZ() {
        Vector3f forward = PlayerCamera.forwardDirection(0f);
        assertEquals(0f, forward.x, 0.0001f);
        assertEquals(-1f, forward.z, 0.0001f);
        assertEquals(1f, forward.length(), 0.0001f);
    }
    @Test void cameraStaysBehindAndAbovePlayer() {
        Vector3f player = new Vector3f(10f, 20f, 30f);
        Vector3f camera = PlayerCamera.orbitPosition(player, 0f, -0.35f);
        assertEquals(10f, camera.x, 0.0001f);
        assertTrue(camera.z > player.z);
        assertTrue(camera.y > player.y);
        assertEquals(PlayerCamera.FOLLOW_DISTANCE, player.distance(camera), 0.2f);
    }
    @Test void cameraOrbitsAsPlayerTurns() {
        Vector3f player = new Vector3f(0f, 5f, 0f);
        Vector3f camera = PlayerCamera.orbitPosition(player, (float)(Math.PI / 2), 0f);
        assertTrue(camera.x > player.x);
        assertEquals(player.z, camera.z, 0.0001f);
    }
    @Test void lookTargetIsNearUpperBody() {
        Vector3f target = PlayerCamera.lookTarget(new Vector3f(1f, 3f, 2f));
        assertEquals(2.55f, target.y, 0.0001f);
    }
}