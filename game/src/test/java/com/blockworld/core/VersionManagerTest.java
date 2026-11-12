package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionManagerTest {
    @Test
    void includesAllProfilesAndBiomesFarmingFurnaceUpdateAsDefault() {
        assertEquals(19, VersionManager.getSupportedVersions().size());
        assertEquals("1.0.0", VersionManager.getDefaultVersion().id());
        assertTrue(VersionManager.find("0.3.0").caves());
        assertTrue(VersionManager.find("0.3.0").ores());
        assertTrue(VersionManager.find("0.4.0").survival());
        assertEquals(64, VersionManager.find("0.5.0").worldRadius());
        assertTrue(VersionManager.find("0.5.0").survival());
        assertEquals(64, VersionManager.find("0.5.1").worldRadius());
        assertTrue(VersionManager.find("0.5.1").survival());
        assertEquals(64, VersionManager.find("0.5.2").worldRadius());
        assertTrue(VersionManager.find("0.5.2").survival());
        assertEquals(64, VersionManager.find("0.5.3").worldRadius());
        assertTrue(VersionManager.find("0.5.3").survival());
        assertEquals(64, VersionManager.find("0.6.0").worldRadius());
        assertTrue(VersionManager.find("0.6.0").survival());
        assertEquals(64, VersionManager.find("0.6.1").worldRadius());
        assertTrue(VersionManager.find("0.6.1").survival());
        assertEquals(64, VersionManager.find("0.6.2").worldRadius());
        assertTrue(VersionManager.find("0.6.2").survival());
        assertEquals(64, VersionManager.find("0.6.3").worldRadius());
        assertTrue(VersionManager.find("0.6.3").survival());
        assertEquals("Customization Update", VersionManager.find("0.7.0").displayName());
        assertEquals(64, VersionManager.find("0.7.0").worldRadius());
        assertTrue(VersionManager.find("0.7.0").survival());
        assertEquals("Texture Pack Engine", VersionManager.find("0.7.5").displayName());
        assertEquals(64, VersionManager.find("0.7.5").worldRadius());
        assertTrue(VersionManager.find("0.7.5").survival());
        assertEquals("Creatures Update", VersionManager.find("0.8.0").displayName());
        assertEquals(64, VersionManager.find("0.8.0").worldRadius());
        assertTrue(VersionManager.find("0.8.0").survival());
        assertEquals("Inventory & Crafting Update", VersionManager.find("0.8.5").displayName());
        assertEquals(64, VersionManager.find("0.8.5").worldRadius());
        assertTrue(VersionManager.find("0.8.5").survival());
        assertEquals("Tools & Equipment Update", VersionManager.find("0.9.0").displayName());
        assertEquals(64, VersionManager.find("0.9.0").worldRadius());
        assertTrue(VersionManager.find("0.9.0").survival());
        assertEquals("Biomes, Farming & Furnace Update", VersionManager.find("0.9.5").displayName());
        assertEquals(88, VersionManager.find("0.9.5").worldRadius());
        assertTrue(VersionManager.find("0.9.5").survival());
        assertEquals("Trading, Fishing & Achievements", VersionManager.find("1.0.0").displayName());
        assertEquals(96, VersionManager.find("1.0.0").worldRadius());
        assertTrue(VersionManager.find("1.0.0").survival());
    }
}
