package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameFeatureSupportTest {
    @Test
    void laterProfilesKeepFeaturesIntroducedByEarlierUpdates() {
        GameVersion latest = VersionManager.find("1.9.28");

        assertTrue(GameFeatureSupport.supportsPets(latest));
        assertTrue(GameFeatureSupport.supportsFoxCompanions(latest));
        assertTrue(GameFeatureSupport.supportsAnimalBreeding(latest));
        assertTrue(GameFeatureSupport.supportsAnimalProducts(latest));
        assertTrue(GameFeatureSupport.supportsAnimalFeeders(latest));
        assertTrue(GameFeatureSupport.supportsVillagerWorkplaces(latest));
        assertTrue(GameFeatureSupport.supportsVillageHomes(latest));
        assertTrue(GameFeatureSupport.supportsProfessionTrading(latest));
        assertTrue(GameFeatureSupport.supportsVillageEconomy(latest));
    }

    @Test
    void featuresBecomeAvailableAtTheirOriginalReleaseThresholds() {
        assertFalse(GameFeatureSupport.supportsFoxCompanions(VersionManager.find("1.9.9")));
        assertTrue(GameFeatureSupport.supportsFoxCompanions(VersionManager.find("1.9.10")));
        assertFalse(GameFeatureSupport.supportsAnimalBreeding(VersionManager.find("1.9.10")));
        assertTrue(GameFeatureSupport.supportsAnimalBreeding(VersionManager.find("1.9.11")));
        assertFalse(GameFeatureSupport.supportsAnimalProducts(VersionManager.find("1.9.11")));
        assertTrue(GameFeatureSupport.supportsAnimalProducts(VersionManager.find("1.9.12")));
        assertFalse(GameFeatureSupport.supportsAnimalFeeders(VersionManager.find("1.9.12")));
        assertTrue(GameFeatureSupport.supportsAnimalFeeders(VersionManager.find("1.9.13")));
        assertFalse(GameFeatureSupport.supportsVillagerWorkplaces(VersionManager.find("1.9.24")));
        assertTrue(GameFeatureSupport.supportsVillagerWorkplaces(VersionManager.find("1.9.25")));
        assertFalse(GameFeatureSupport.supportsVillageHomes(VersionManager.find("1.9.25")));
        assertTrue(GameFeatureSupport.supportsVillageHomes(VersionManager.find("1.9.26")));
        assertFalse(GameFeatureSupport.supportsProfessionTrading(VersionManager.find("1.9.26")));
        assertTrue(GameFeatureSupport.supportsProfessionTrading(VersionManager.find("1.9.27")));
        assertFalse(GameFeatureSupport.supportsVillageEconomy(VersionManager.find("1.9.27")));
        assertTrue(GameFeatureSupport.supportsVillageEconomy(VersionManager.find("1.9.28")));
    }

    @Test
    void comparesNumericVersionPartsRatherThanLexicographicText() {
        GameVersion oneNineTwentyThree = VersionManager.find("1.9.23");
        GameVersion oneTen = new GameVersion("1.10.0", "Future", "", 144, true, true, true, true, true);

        assertTrue(GameFeatureSupport.versionAtLeast(oneNineTwentyThree, 1, 9, 10));
        assertTrue(GameFeatureSupport.versionAtLeast(oneTen, 1, 9, 23));
        assertFalse(GameFeatureSupport.versionAtLeast(oneNineTwentyThree, 1, 10, 0));
    }

    @Test
    void rejectsMalformedOrMissingVersions() {
        GameVersion malformed = new GameVersion("1.x.0", "Invalid", "", 64, false, false, false, false, false);
        assertFalse(GameFeatureSupport.versionAtLeast(null, 1, 9, 0));
        assertFalse(GameFeatureSupport.versionAtLeast(malformed, 1, 9, 0));
    }
}
