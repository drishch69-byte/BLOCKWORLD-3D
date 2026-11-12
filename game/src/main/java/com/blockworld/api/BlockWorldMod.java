package com.blockworld.api;

/** Entry point for BLOCKWORLD Java mods. Register it in META-INF/services/com.blockworld.api.BlockWorldMod. */
public interface BlockWorldMod {
    default void onInitialize(ModContext context) throws Exception { }
    default void onWorldReady() throws Exception { }
    default void onUpdate(float timePerFrame) throws Exception { }
    default void onWorldSave() throws Exception { }
    default void onShutdown() throws Exception { }
}
