package com.blockworld.game;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

/** Camera orbit calculations for third-person view. */
public final class PlayerCamera {
    public static final float FOLLOW_DISTANCE = 4.5f;
    private PlayerCamera() { }

    public static Vector3f forwardDirection(float yaw) {
        return new Vector3f(-FastMath.sin(yaw), 0f, -FastMath.cos(yaw)).normalizeLocal();
    }

    public static Vector3f orbitPosition(Vector3f player, float yaw, float pitch) {
        if (player == null) throw new IllegalArgumentException("Player position is required");
        float horizontal = FastMath.cos(pitch) * FOLLOW_DISTANCE;
        float vertical = FastMath.sin(-pitch) * FOLLOW_DISTANCE;
        return new Vector3f(player.x + FastMath.sin(yaw) * horizontal,
                player.y + vertical, player.z + FastMath.cos(yaw) * horizontal);
    }

    public static Vector3f lookTarget(Vector3f player) {
        if (player == null) throw new IllegalArgumentException("Player position is required");
        return player.add(0f, -0.45f, 0f);
    }
}