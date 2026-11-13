package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionManagerTest {
    @Test
    void includesAllProfilesAndDefaultsToRabbitAndWildlife() {
        assertEquals(28, VersionManager.getSupportedVersions().size());
        assertEquals("1.9.1", VersionManager.getDefaultVersion().id());
        assertEquals("Rabbit & Wildlife Update", VersionManager.find("1.8.1").displayName());
        assertEquals(144, VersionManager.find("1.8.1").worldRadius());
        assertTrue(VersionManager.find("1.8.1").survival());
        assertEquals("Living World & Companions", VersionManager.find("1.9.0").displayName());
        assertEquals(144, VersionManager.find("1.9.0").worldRadius());
        assertTrue(VersionManager.find("1.9.0").survival());
        assertEquals("World Creator & Seeds", VersionManager.find("1.9.1").displayName());
        assertEquals(144, VersionManager.find("1.9.1").worldRadius());
        assertTrue(VersionManager.find("1.9.1").survival());
        assertEquals("Village Defense & Combat Update", VersionManager.find("1.8.0").displayName());
        assertEquals(144, VersionManager.find("1.8.0").worldRadius());
        assertTrue(VersionManager.find("1.8.0").survival());
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
        assertEquals("Visual Inventory Update", VersionManager.find("1.0.1").displayName());
        assertEquals(96, VersionManager.find("1.0.1").worldRadius());
        assertTrue(VersionManager.find("1.0.1").survival());
        assertEquals("Third-Person Camera Update", VersionManager.find("1.0.2").displayName());
        assertEquals(96, VersionManager.find("1.0.2").worldRadius());
        assertTrue(VersionManager.find("1.0.2").survival());
        assertEquals("Creature & Camera Polish", VersionManager.find("1.0.3").displayName());
        assertEquals(96, VersionManager.find("1.0.3").worldRadius());
        assertTrue(VersionManager.find("1.0.3").survival());
        assertEquals("Wildlands & Crystal Caves", VersionManager.find("1.1.0").displayName());
        assertEquals(112, VersionManager.find("1.1.0").worldRadius());
        assertTrue(VersionManager.find("1.1.0").survival());
        assertEquals("Exploration Update", VersionManager.find("1.2.0").displayName());
        assertEquals(128, VersionManager.find("1.2.0").worldRadius());
        assertTrue(VersionManager.find("1.2.0").survival());
    }
}
