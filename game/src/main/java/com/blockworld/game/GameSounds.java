package com.blockworld.game;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.scene.Node;

import java.util.EnumMap;
import java.util.Map;

/** Optional short UI/gameplay sound effects; a missing audio device never blocks gameplay. */
public final class GameSounds {
    public enum Effect { MINE, TRADE, FISH, ACHIEVEMENT, SMELT }

    private final Map<Effect, AudioNode> sounds = new EnumMap<>(Effect.class);

    public GameSounds(AssetManager assets, Node parent) {
        for (Effect effect : Effect.values()) {
            try {
                AudioNode sound = new AudioNode(assets, "Sounds/" + effect.name().toLowerCase(java.util.Locale.ROOT) + ".wav",
                        AudioData.DataType.Buffer);
                sound.setPositional(false);
                sound.setLooping(false);
                sound.setVolume(effect == Effect.ACHIEVEMENT ? 0.45f : 0.30f);
                parent.attachChild(sound);
                sounds.put(effect, sound);
            } catch (RuntimeException ignored) {
                // Audio is optional; visual and gameplay systems remain available without it.
            }
        }
    }

    public void play(Effect effect) {
        AudioNode sound = sounds.get(effect);
        if (sound == null) return;
        try {
            sound.stop();
            sound.play();
        } catch (RuntimeException ignored) {
            // Some systems may not provide an audio device. Keep the game running silently.
        }
    }
}
