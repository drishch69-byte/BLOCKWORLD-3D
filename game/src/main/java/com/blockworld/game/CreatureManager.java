package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;
import com.blockworld.core.WorldCreature;
import com.blockworld.core.PetMode;
import com.blockworld.core.PetRules;
import com.blockworld.core.AnimalBreedingRules;
import com.blockworld.core.AnimalDataStore;
import com.blockworld.core.AnimalFeederRules;
import com.blockworld.core.AnimalProductRules;
import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Low-cost blocky creature visuals and simple deterministic wandering. */
public final class CreatureManager {
    private final AssetManager assets;
    private final Map<String, Texture> creatureTextures = new HashMap<>();
    private final Node node = new Node("WorldCreatures");
    private final Map<Long, Visual> visuals = new HashMap<>();
    private final Map<Long, PetMode> petModes = new HashMap<>();
    private final Map<Long, Float> babyGrowthSeconds = new HashMap<>();
    private final Map<Long, Float> breedingCooldowns = new HashMap<>();
    private final Map<Long, Float> productionCooldowns = new HashMap<>();
    private final Map<BlockPos, AnimalFeederRules.Stock> feederStocks = new HashMap<>();
    private boolean foxCompanionsEnabled;
    private boolean animalBreedingEnabled;
    private boolean animalProductsEnabled;
    private boolean animalFeedersEnabled;

    public CreatureManager(AssetManager assets) {
        this.assets = assets;
        loadCreatureTextures();
    }

    /** Loads nearest-filtered PNG skins; color-only materials remain the safe fallback. */
    private void loadCreatureTextures() {
        for (CreatureType type : CreatureType.values()) {
            try {
                Texture texture = assets.loadTexture("Textures/Creatures/" + type.id() + ".png");
                texture.setMagFilter(Texture.MagFilter.Nearest);
                texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
                texture.setWrap(Texture.WrapMode.Repeat);
                creatureTextures.put(type.id(), texture);
            } catch (RuntimeException ignored) {
                // Keep the existing color-only appearance when an optional texture is unavailable.
            }
        }
    }

    public Node getNode() { return node; }
    public int size() { return visuals.size(); }

    public void setAnimalData(AnimalDataStore.State state, boolean enabled) {
        setAnimalData(state, enabled, false, false);
    }

    public void setAnimalData(AnimalDataStore.State state, boolean enabled, boolean productsEnabled) {
        setAnimalData(state, enabled, productsEnabled, false);
    }

    public void setAnimalData(AnimalDataStore.State state, boolean enabled,
                              boolean productsEnabled, boolean feedersEnabled) {
        animalBreedingEnabled = enabled;
        animalProductsEnabled = productsEnabled;
        animalFeedersEnabled = feedersEnabled;
        babyGrowthSeconds.clear();
        breedingCooldowns.clear();
        productionCooldowns.clear();
        feederStocks.clear();
        if (!enabled) return;
        if (state != null) {
            for (Map.Entry<Long, Float> entry : state.babyAges().entrySet()) {
                Visual visual = visuals.get(entry.getKey());
                if (visual != null && AnimalBreedingRules.isBaby(visual.state.type())
                        && entry.getValue() != null && Float.isFinite(entry.getValue())) {
                    babyGrowthSeconds.put(entry.getKey(), Math.max(0f,
                            Math.min(AnimalBreedingRules.GROWTH_SECONDS, entry.getValue())));
                }
            }
            for (Map.Entry<Long, Float> entry : state.cooldowns().entrySet()) {
                Visual visual = visuals.get(entry.getKey());
                if (visual != null && AnimalBreedingRules.isBreedableAdult(visual.state.type())
                        && entry.getValue() != null && Float.isFinite(entry.getValue())
                        && entry.getValue() > 0f) {
                    breedingCooldowns.put(entry.getKey(), Math.min(
                            AnimalBreedingRules.BREEDING_COOLDOWN_SECONDS, entry.getValue()));
                }
            }
            // Keep newer production timers intact even when opening this world in profile 1.9.11.
            for (Map.Entry<Long, Float> entry : state.productionCooldowns().entrySet()) {
                Visual visual = visuals.get(entry.getKey());
                if (visual != null && AnimalProductRules.produces(visual.state.type())
                        && entry.getValue() != null && Float.isFinite(entry.getValue())
                        && entry.getValue() >= 0f) {
                    productionCooldowns.put(entry.getKey(), Math.min(
                            AnimalProductRules.MAX_COOLDOWN_SECONDS, entry.getValue()));
                }
            }
            feederStocks.putAll(state.feeders());
        }
        for (Visual visual : visuals.values()) {
            if (AnimalBreedingRules.isBaby(visual.state.type())) {
                babyGrowthSeconds.putIfAbsent(visual.state.id(), 0f);
            }
            if (productsEnabled && AnimalProductRules.produces(visual.state.type())) {
                productionCooldowns.putIfAbsent(visual.state.id(),
                        AnimalProductRules.initialCooldown(visual.state.type()));
            }
        }
    }

    public AnimalDataStore.State animalDataSnapshot() {
        Map<Long, Float> ages = new HashMap<>();
        Map<Long, Float> cooldowns = new HashMap<>();
        if (!animalBreedingEnabled) return AnimalDataStore.State.empty();
        babyGrowthSeconds.forEach((id, age) -> {
            Visual visual = visuals.get(id);
            if (visual != null && AnimalBreedingRules.isBaby(visual.state.type())) ages.put(id, age);
        });
        breedingCooldowns.forEach((id, time) -> {
            Visual visual = visuals.get(id);
            if (visual != null && AnimalBreedingRules.isBreedableAdult(visual.state.type())
                    && time > 0f) cooldowns.put(id, time);
        });
        Map<Long, Float> productTimes = new HashMap<>();
        productionCooldowns.forEach((id, time) -> {
            Visual visual = visuals.get(id);
            if (visual != null && AnimalProductRules.produces(visual.state.type())
                    && Float.isFinite(time) && time >= 0f) {
                productTimes.put(id, time);
            }
        });
        return new AnimalDataStore.State(ages, cooldowns, productTimes, feederStocks);
    }

    public AnimalFeederRules.Stock feederStockAt(BlockPos position) {
        return feederStocks.getOrDefault(position, AnimalFeederRules.Stock.empty());
    }

    public void setFeederStock(BlockPos position, AnimalFeederRules.Stock stock) {
        if (position == null || stock == null) return;
        if (stock.isEmpty()) feederStocks.remove(position);
        else feederStocks.put(position, stock);
    }

    public void removeFeeder(BlockPos position) {
        if (position != null) feederStocks.remove(position);
    }

    public float productionCooldownRemaining(long creatureId) {
        return Math.max(0f, productionCooldowns.getOrDefault(creatureId, 0f));
    }

    public void startProductionCooldown(long creatureId, float seconds) {
        if (!animalProductsEnabled || !Float.isFinite(seconds) || seconds < 0f
                || seconds > AnimalProductRules.MAX_COOLDOWN_SECONDS) return;
        Visual visual = visuals.get(creatureId);
        if (visual != null && AnimalProductRules.produces(visual.state.type())) {
            productionCooldowns.put(creatureId, seconds);
        }
    }

    public WorldCreature nearestBreedableMate(WorldCreature parent, float maxDistance) {
        if (!animalBreedingEnabled || parent == null
                || !AnimalBreedingRules.isBreedableAdult(parent.type())
                || !Float.isFinite(maxDistance) || maxDistance < 0f) return null;
        float bestDistance = maxDistance * maxDistance;
        WorldCreature nearest = null;
        for (Visual visual : visuals.values()) {
            WorldCreature candidate = visual.state;
            if (candidate.id() == parent.id()
                    || !AnimalBreedingRules.canBreedTogether(parent.type(), candidate.type())) continue;
            float dx = candidate.x() - parent.x();
            float dy = candidate.y() - parent.y();
            float dz = candidate.z() - parent.z();
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    public float breedingCooldownRemaining(long firstId, long secondId) {
        return Math.max(breedingCooldowns.getOrDefault(firstId, 0f),
                breedingCooldowns.getOrDefault(secondId, 0f));
    }

    public void startBreedingCooldown(long firstId, long secondId) {
        if (!animalBreedingEnabled) return;
        breedingCooldowns.put(firstId, AnimalBreedingRules.BREEDING_COOLDOWN_SECONDS);
        breedingCooldowns.put(secondId, AnimalBreedingRules.BREEDING_COOLDOWN_SECONDS);
    }

    public boolean spawnBaby(WorldCreature baby, VoxelWorld world) {
        if (!animalBreedingEnabled || baby == null || world == null
                || !AnimalBreedingRules.isBaby(baby.type()) || visuals.containsKey(baby.id())
                || !world.addCreature(baby)) return false;
        Visual visual = createVisual(baby);
        visuals.put(baby.id(), visual);
        node.attachChild(visual.root);
        babyGrowthSeconds.put(baby.id(), 0f);
        return true;
    }

    public boolean hasDomesticAnimalNear(Vector3f position, float radius) {
        if (position == null || !Float.isFinite(radius) || radius < 0f) return false;
        float limit = radius * radius;
        for (Visual visual : visuals.values()) {
            if (!AnimalBreedingRules.isFarmAnimal(visual.state.type())) continue;
            float dx = visual.state.x() - position.x;
            float dy = visual.state.y() - position.y;
            float dz = visual.state.z() - position.z;
            if (dx * dx + dy * dy + dz * dz <= limit) return true;
        }
        return false;
    }

    public void setPetModes(Map<Long, PetMode> savedModes) {
        setPetModes(savedModes, true);
    }

    public void setPetModes(Map<Long, PetMode> savedModes, boolean allowFoxCompanions) {
        foxCompanionsEnabled = allowFoxCompanions;
        petModes.clear();
        if (savedModes == null) return;
        for (Map.Entry<Long, PetMode> entry : savedModes.entrySet()) {
            Visual visual = visuals.get(entry.getKey());
            if (visual != null && PetRules.shouldPersistMode(visual.state.type(), entry.getValue())) {
                // Keep fox records even in older profiles so saving there cannot erase newer data.
                petModes.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public Map<Long, PetMode> petModesSnapshot() {
        Map<Long, PetMode> snapshot = new HashMap<>();
        for (Map.Entry<Long, PetMode> entry : petModes.entrySet()) {
            Visual visual = visuals.get(entry.getKey());
            if (visual != null && PetRules.shouldPersistMode(visual.state.type(), entry.getValue())) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(snapshot);
    }

    public boolean isTamedFox(long creatureId) {
        Visual visual = visuals.get(creatureId);
        return foxCompanionsEnabled && visual != null && visual.state.type() == CreatureType.FOX
                && petModes.containsKey(creatureId);
    }

    public boolean tameFox(long creatureId) {
        Visual visual = visuals.get(creatureId);
        if (!foxCompanionsEnabled || visual == null || !PetRules.isTameable(visual.state.type())
                || petModes.containsKey(creatureId)) return false;
        petModes.put(creatureId, PetMode.FOLLOW);
        return true;
    }

    public PetMode toggleNearestPetMode(Vector3f position, float maxDistance) {
        if (position == null || !Float.isFinite(position.x) || !Float.isFinite(position.y)
                || !Float.isFinite(position.z) || !Float.isFinite(maxDistance) || maxDistance < 0f) return null;
        float limit = maxDistance * maxDistance;
        Visual nearestPet = null;
        float bestDistance = limit;
        for (Visual visual : visuals.values()) {
            if (!PetRules.isCompanion(visual.state.type(), petModes.containsKey(visual.state.id()),
                    foxCompanionsEnabled)) continue;
            float dx = visual.state.x() - position.x;
            float dy = visual.state.y() - position.y;
            float dz = visual.state.z() - position.z;
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                bestDistance = distance;
                nearestPet = visual;
            }
        }
        if (nearestPet == null) return null;
        long id = nearestPet.state.id();
        PetMode next = petModes.getOrDefault(id, PetMode.FOLLOW).toggle();
        if (nearestPet.state.type() == CreatureType.DOG && next == PetMode.FOLLOW) {
            petModes.remove(id);
        } else {
            petModes.put(id, next);
        }
        return next;
    }

    /** Backwards-compatible method name retained for existing integrations. */
    public PetMode toggleNearestDogMode(Vector3f position, float maxDistance) {
        return toggleNearestPetMode(position, maxDistance);
    }

    public boolean hasAttackTargetAlongRay(Vector3f origin, Vector3f direction, float reach,
                                            float hitRadius, VoxelWorld world) {
        return findAttackTargetAlongRay(origin, direction, reach, hitRadius, world) != null;
    }

    public String attackAlongRay(Vector3f origin, Vector3f direction, float reach,
                                 float hitRadius, float damage, VoxelWorld world) {
        if (!Float.isFinite(damage) || damage <= 0f) return null;
        Visual target = findAttackTargetAlongRay(origin, direction, reach, hitRadius, world);
        if (target == null) return null;
        target.health = Math.max(0f, target.health - damage);
        if (target.health <= 0f) {
            long id = target.state.id();
            target.root.removeFromParent();
            visuals.remove(id);
            babyGrowthSeconds.remove(id);
            breedingCooldowns.remove(id);
            productionCooldowns.remove(id);
            world.removeCreature(id);
            return target.state.type().displayName() + " побеждён!";
        }
        return "Попадание по " + target.state.type().displayName() + ": здоровье "
                + Math.round(target.health) + "/" + Math.round(target.state.type().maxHealth()) + ".";
    }

    private Visual findAttackTargetAlongRay(Vector3f origin, Vector3f direction, float reach,
                                            float hitRadius, VoxelWorld world) {
        if (origin == null || direction == null || world == null
                || direction.lengthSquared() < 0.0001f
                || !Float.isFinite(reach) || reach <= 0f
                || !Float.isFinite(hitRadius) || hitRadius < 0f) return null;
        Vector3f ray = direction.normalize();
        float effectiveReach = reach;
        CollisionResults blockHits = new CollisionResults();
        Ray blockRay = new Ray(origin, ray);
        blockRay.setLimit(reach);
        world.getNode().collideWith(blockRay, blockHits);
        if (blockHits.size() > 0) {
            effectiveReach = Math.min(effectiveReach, blockHits.getClosestCollision().getDistance());
        }
        if (effectiveReach <= 0f) return null;
        Visual target = null;
        float closest = effectiveReach;
        for (Visual visual : visuals.values()) {
            float targetHeight = visual.state.type() == CreatureType.CRYSTAL_SENTINEL ? 1.25f : .72f;
            float targetRadius = visual.state.type() == CreatureType.CRYSTAL_SENTINEL
                    ? Math.max(hitRadius, 1.05f) : hitRadius;
            Vector3f center = new Vector3f(visual.state.x(), visual.state.y() + targetHeight, visual.state.z());
            Vector3f offset = center.subtract(origin);
            float along = offset.dot(ray);
            if (along < 0f || along > closest) continue;
            float missSquared = offset.lengthSquared() - along * along;
            if (missSquared <= targetRadius * targetRadius) {
                closest = along;
                target = visual;
            }
        }
        return target;
    }

    public float consumePlayerDamage() {
        float damage = pendingPlayerDamage;
        pendingPlayerDamage = 0f;
        return damage;
    }

    public boolean hasFarmerNear(Vector3f position, float radius) {
        if (position == null || !Float.isFinite(radius) || radius < 0f) return false;
        float limit = radius * radius;
        for (Visual visual : visuals.values()) {
            if (visual.state.type() != CreatureType.VILLAGER_FARMER) continue;
            float dx = visual.state.x() - position.x;
            float dy = visual.state.y() - position.y;
            float dz = visual.state.z() - position.z;
            if (dx * dx + dy * dy + dz * dz <= limit) return true;
        }
        return false;
    }

    private float pendingPlayerDamage;

    public WorldCreature nearest(Vector3f position, float maxDistance) {
        WorldCreature closest = null;
        float bestDistanceSquared = maxDistance * maxDistance;
        for (Visual visual : visuals.values()) {
            WorldCreature state = visual.state;
            float dx = state.x() - position.x;
            float dy = state.y() - position.y;
            float dz = state.z() - position.z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                closest = state;
            }
        }
        return closest;
    }

    public void sync(VoxelWorld world) {
        node.detachAllChildren();
        visuals.clear();
        for (WorldCreature creature : world.getCreatures()) {
            Visual visual = createVisual(creature);
            visuals.put(creature.id(), visual);
            node.attachChild(visual.root);
        }
    }

    /** Apply authoritative host creature states without recreating existing visuals. */
    public void applyNetworkSnapshot(java.util.List<WorldCreature> snapshot, VoxelWorld world) {
        java.util.Set<Long> keep = new java.util.HashSet<>();
        for (WorldCreature state : snapshot) {
            keep.add(state.id());
            Visual visual = visuals.get(state.id());
            if (visual == null) {
                visual = createVisual(state);
                visuals.put(state.id(), visual);
                node.attachChild(visual.root);
            } else {
                WorldCreature previous = visual.state;
                float dx = state.x() - previous.x();
                float dz = state.z() - previous.z();
                visual.walking = dx * dx + dz * dz > 0.0001f;
                visual.state = state;
            }
            visual.yaw = state.yaw();
            visual.root.setLocalTranslation(state.x(), state.y(), state.z());
            visual.root.setLocalRotation(new Quaternion().fromAngleAxis(state.yaw(), Vector3f.UNIT_Y));
        }
        for (Long id : new java.util.ArrayList<>(visuals.keySet())) {
            if (!keep.contains(id)) {
                Visual removed = visuals.remove(id);
                if (removed != null) removed.root.removeFromParent();
            }
        }
        world.replaceCreatureSnapshot(snapshot);
    }

    /** Animate authoritative network creature states without running local AI. */
    public void updateNetworkAnimations(float tpf) {
        for (Visual visual : visuals.values()) {
            visual.animationClock += Math.max(0f, tpf) * (visual.walking ? 8f : 1.8f);
            float bob = visual.state.type() == CreatureType.SLIME
                    ? slimeBounce(visual.animationClock)
                    : visual.state.type() == CreatureType.RABBIT
                    ? rabbitHop(visual.animationClock, visual.walking)
                    : visual.walking ? Math.abs(gaitSwing(visual.animationClock, true)) * 0.025f : 0f;
            visual.root.setLocalTranslation(visual.state.x(), visual.state.y() + bob, visual.state.z());
            visual.root.setLocalRotation(new Quaternion().fromAngleAxis(visual.state.yaw(), Vector3f.UNIT_Y));
            float scale = AnimalBreedingRules.isBaby(visual.state.type()) ? 0.55f : 1f;
            visual.root.setLocalScale(scale, scale * (visual.state.type() == CreatureType.SLIME
                    ? 0.82f + 0.18f * (float) Math.abs(Math.sin(visual.animationClock * 1.6f)) : 1f), scale);
            animateLimbs(visual);
        }
    }

    public void update(float tpf, Vector3f playerPosition, VoxelWorld world) {
        update(tpf, playerPosition, world, 0.25f);
    }

    /** Updates local creature AI with the current normalized day phase (0..1). */
    public void update(float tpf, Vector3f playerPosition, VoxelWorld world, float dayPhase) {
        update(tpf, playerPosition, world, dayPhase, false);
    }

    /** Updates local creature AI with optional profession-specific workplace routines. */
    public void update(float tpf, Vector3f playerPosition, VoxelWorld world,
                       float dayPhase, boolean workplacesEnabled) {
        if (animalBreedingEnabled) advanceAnimalTimers(tpf);
        List<Long> defeated = new ArrayList<>();
        // Determine which hostiles threaten a village once per tick, not once per guardian.
        List<Visual> protectedHostiles = findVillageThreats();
        for (Visual visual : visuals.values()) {
            WorldCreature state = visual.state;
            float previousX = state.x();
            float previousZ = state.z();
            float nextX = state.x();
            float nextZ = state.z();
            visual.attackCooldown = Math.max(0f, visual.attackCooldown - Math.max(0f, tpf));

            if (state.type().isHostile() && playerPosition != null) {
                float dx = playerPosition.x - state.x();
                float dz = playerPosition.z - state.z();
                float distance = (float) Math.sqrt(dx * dx + dz * dz);
                if (distance <= 13f && Math.abs(playerPosition.y - state.y()) <= 2.2f) {
                    if (distance > 0.001f) visual.yaw = com.jme3.math.FastMath.atan2(dx, dz);
                    if (distance > 1.55f) {
                        float step = state.type().moveSpeed() * Math.min(Math.max(tpf, 0f), .05f);
                        nextX += (float) Math.sin(visual.yaw) * step;
                        nextZ += (float) Math.cos(visual.yaw) * step;
                    } else if (visual.attackCooldown <= 0f) {
                        pendingPlayerDamage += state.type().attackDamage();
                        visual.attackCooldown = 1.25f;
                    }
                } else {
                    wander(visual, tpf, state);
                    nextX = visual.nextX;
                    nextZ = visual.nextZ;
                }
            } else if (state.type().isVillager() && state.type() != CreatureType.VILLAGER_GUARD) {
                Visual threat = nearestVillagerThreat(visual, protectedHostiles);
                if (threat != null) {
                    float dx = state.x() - threat.state.x();
                    float dz = state.z() - threat.state.z();
                    float distance = (float) Math.sqrt(dx * dx + dz * dz);
                    if (distance > 0.001f) {
                        visual.yaw = com.jme3.math.FastMath.atan2(dx, dz);
                    } else {
                        visual.yaw += (float) Math.PI;
                    }
                    float step = state.type().moveSpeed() * VillagerBehaviorRules.FLEE_SPEED_MULTIPLIER
                            * Math.min(Math.max(tpf, 0f), .05f);
                    nextX += (float) Math.sin(visual.yaw) * step;
                    nextZ += (float) Math.cos(visual.yaw) * step;
                    visual.pauseTimer = 0f;
                } else if (VillagerBehaviorRules.shouldRest(state.type(), dayPhase)) {
                    visual.working = false;
                    visual.pauseTimer = Math.max(0f, visual.pauseTimer - Math.max(0f, tpf));
                } else if (workplacesEnabled
                        && VillagerWorkplaceRules.shouldWork(state.type(), dayPhase)
                        && visitWorkplace(visual, tpf, world)) {
                    nextX = visual.nextX;
                    nextZ = visual.nextZ;
                } else {
                    visual.working = false;
                    wander(visual, tpf, state);
                    nextX = visual.nextX;
                    nextZ = visual.nextZ;
                }
            } else if (state.type().isGuardian()) {
                Visual threat = nearestProtectedHostile(visual, protectedHostiles);
                if (threat != null) {
                    float dx = threat.state.x() - state.x();
                    float dz = threat.state.z() - state.z();
                    float distance = (float) Math.sqrt(dx * dx + dz * dz);
                    if (distance > .001f) visual.yaw = com.jme3.math.FastMath.atan2(dx, dz);
                    if (distance > 1.65f) {
                        float step = Math.min(distance - 1.65f,
                                state.type().moveSpeed() * Math.min(Math.max(tpf, 0f), .05f));
                        nextX += (float) Math.sin(visual.yaw) * step;
                        nextZ += (float) Math.cos(visual.yaw) * step;
                    } else if (visual.attackCooldown <= 0f) {
                        float guardianDamage = state.type() == CreatureType.IRON_GOLEM ? 12f : 4f;
                        threat.health = Math.max(0f, threat.health - guardianDamage);
                        visual.attackCooldown = state.type() == CreatureType.IRON_GOLEM ? 1.1f : 1.6f;
                        if (threat.health <= 0f) defeated.add(threat.state.id());
                    }
                } else {
                    wander(visual, tpf, state);
                    nextX = visual.nextX;
                    nextZ = visual.nextZ;
                }
            } else if (PetRules.isCompanion(state.type(), petModes.containsKey(state.id()),
                    foxCompanionsEnabled) && playerPosition != null) {
                if (petModes.getOrDefault(state.id(), PetMode.FOLLOW) == PetMode.FOLLOW) {
                    float dx = playerPosition.x - state.x();
                    float dz = playerPosition.z - state.z();
                    float distance = (float) Math.sqrt(dx * dx + dz * dz);
                    if (distance > .001f) visual.yaw = com.jme3.math.FastMath.atan2(dx, dz);
                    float step = companionStep(distance, state.type().moveSpeed(), tpf);
                    nextX += (float) Math.sin(visual.yaw) * step;
                    nextZ += (float) Math.cos(visual.yaw) * step;
                }
            } else {
                wander(visual, tpf, state);
                nextX = visual.nextX;
                nextZ = visual.nextZ;
            }

            if (playerPosition != null && !state.type().isHostile()) {
                float playerDx = nextX - playerPosition.x;
                float playerDz = nextZ - playerPosition.z;
                if (playerDx * playerDx + playerDz * playerDz < 1.8f)
                    visual.pauseTimer = Math.max(visual.pauseTimer, .8f);
            }

            boolean attemptedMovement = Math.abs(nextX - state.x()) > 0.00001f
                    || Math.abs(nextZ - state.z()) > 0.00001f;
            CreatureNavigation.Step resolved = CreatureNavigation.resolveStep(
                    state.type(), state.x(), state.y(), state.z(), nextX, nextZ, world);
            if (resolved != null) {
                boolean blockedAtPen = AnimalBreedingRules.isFarmAnimal(state.type())
                        && attemptedMovement
                        && Math.abs(resolved.x() - state.x()) < 0.00001f
                        && Math.abs(resolved.z() - state.z()) < 0.00001f;
                if (blockedAtPen) {
                    // Turn away from a fence instead of spending several seconds walking into it.
                    visual.yaw += visual.random.nextBoolean()
                            ? (float) (Math.PI * 0.5) : (float) (-Math.PI * 0.5);
                    visual.turnTimer = 0.8f + visual.random.nextFloat() * 1.2f;
                    visual.pauseTimer = Math.max(visual.pauseTimer, 0.3f);
                }
                state = new WorldCreature(state.id(), state.type(),
                        resolved.x(), resolved.y(), resolved.z(), visual.yaw);
                visual.state = state;
                world.updateCreature(state);
            } else {
                visual.pauseTimer = Math.max(visual.pauseTimer, 0.25f);
            }

            boolean walking = (state.x() - previousX) * (state.x() - previousX)
                    + (state.z() - previousZ) * (state.z() - previousZ) > .000001f;
            visual.walking = walking;
            visual.animationClock += Math.max(0f, tpf) * (walking ? 8f : 1.8f);
            float bob = state.type() == CreatureType.SLIME
                    ? slimeBounce(visual.animationClock)
                    : state.type() == CreatureType.RABBIT
                    ? rabbitHop(visual.animationClock, walking)
                    : walking ? Math.abs(gaitSwing(visual.animationClock, true)) * .025f : 0f;
            visual.root.setLocalTranslation(state.x(), state.y() + bob, state.z());
            visual.root.setLocalRotation(new Quaternion().fromAngleAxis(state.yaw(), Vector3f.UNIT_Y));
            float scale = AnimalBreedingRules.isBaby(state.type()) ? 0.55f : 1f;
            visual.root.setLocalScale(scale, scale * (state.type() == CreatureType.SLIME
                    ? .82f + .18f * (float) Math.abs(Math.sin(visual.animationClock * 1.6f)) : 1f), scale);
            animateLimbs(visual);
        }
        for (Long id : defeated) {
            Visual removed = visuals.remove(id);
            if (removed != null) removed.root.removeFromParent();
            babyGrowthSeconds.remove(id);
            breedingCooldowns.remove(id);
            productionCooldowns.remove(id);
            world.removeCreature(id);
        }
        if (animalBreedingEnabled) matureBabies(world);
        if (animalFeedersEnabled) advanceFeeders(tpf, world);
    }

    private void advanceFeeders(float tpf, VoxelWorld world) {
        float elapsed = Float.isFinite(tpf) ? Math.max(0f, tpf) : 0f;
        if (elapsed <= 0f || feederStocks.isEmpty()) return;
        for (Map.Entry<BlockPos, AnimalFeederRules.Stock> entry
                : new ArrayList<>(feederStocks.entrySet())) {
            BlockPos position = entry.getKey();
            AnimalFeederRules.Stock stock = entry.getValue();
            if (world.getBlock(position) != BlockType.FEED_TROUGH) {
                feederStocks.remove(position);
                continue;
            }
            Visual eater = nearestFeedableAnimal(position, stock);
            if (eater == null) continue;
            AnimalFeederRules.Stock timed = AnimalFeederRules.advance(stock, elapsed);
            if (timed.secondsUntilFeed() > 0f) {
                feederStocks.put(position, timed);
                continue;
            }
            AnimalFeederRules.Stock consumed = AnimalFeederRules.consume(timed, eater.state.type());
            if (consumed.equals(timed)) {
                feederStocks.put(position, timed);
                continue;
            }
            feederStocks.put(position, consumed);
            long animalId = eater.state.id();
            if (AnimalBreedingRules.isBaby(eater.state.type())) {
                babyGrowthSeconds.computeIfPresent(animalId, (id, age) ->
                        AnimalBreedingRules.advanceGrowth(age, AnimalFeederRules.GROWTH_BOOST_SECONDS));
            } else if (animalProductsEnabled && productionCooldowns.getOrDefault(animalId, 0f) > 0f) {
                productionCooldowns.computeIfPresent(animalId, (id, remaining) ->
                        AnimalBreedingRules.advanceCooldown(remaining, AnimalFeederRules.GROWTH_BOOST_SECONDS));
            } else if (breedingCooldowns.getOrDefault(animalId, 0f) > 0f) {
                breedingCooldowns.computeIfPresent(animalId, (id, remaining) ->
                        AnimalBreedingRules.advanceCooldown(remaining, AnimalFeederRules.GROWTH_BOOST_SECONDS));
            }
        }
    }

    private Visual nearestFeedableAnimal(BlockPos trough, AnimalFeederRules.Stock stock) {
        float bestDistance = AnimalFeederRules.FEEDING_RADIUS * AnimalFeederRules.FEEDING_RADIUS;
        Visual best = null;
        for (Visual visual : visuals.values()) {
            WorldCreature animal = visual.state;
            if (!AnimalBreedingRules.isFarmAnimal(animal.type())
                    || !AnimalFeederRules.hasFood(stock, animal.type())) continue;
            float dx = animal.x() - (trough.x() + .5f);
            float dy = animal.y() - (trough.y() + .5f);
            float dz = animal.z() - (trough.z() + .5f);
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance > bestDistance) continue;
            boolean canBenefit;
            if (AnimalBreedingRules.isBaby(animal.type())) {
                canBenefit = babyGrowthSeconds.getOrDefault(animal.id(), 0f)
                        < AnimalBreedingRules.GROWTH_SECONDS;
            } else {
                canBenefit = (animalProductsEnabled
                        && productionCooldowns.getOrDefault(animal.id(), 0f) > 0f)
                        || breedingCooldowns.getOrDefault(animal.id(), 0f) > 0f;
            }
            if (canBenefit) {
                bestDistance = distance;
                best = visual;
            }
        }
        return best;
    }

    private void advanceAnimalTimers(float tpf) {
        float elapsed = Float.isFinite(tpf) ? Math.max(0f, tpf) : 0f;
        if (elapsed <= 0f) return;
        babyGrowthSeconds.replaceAll((id, age) ->
                AnimalBreedingRules.advanceGrowth(age, elapsed));
        breedingCooldowns.replaceAll((id, cooldown) ->
                AnimalBreedingRules.advanceCooldown(cooldown, elapsed));
        breedingCooldowns.values().removeIf(value -> value <= 0f);
        if (animalProductsEnabled) {
            // Keep zero values: they mean the animal is ready, and must survive save/reload.
            productionCooldowns.replaceAll((id, cooldown) ->
                    AnimalBreedingRules.advanceCooldown(cooldown, elapsed));
        }
    }

    private void matureBabies(VoxelWorld world) {
        List<WorldCreature> ready = new ArrayList<>();
        for (Map.Entry<Long, Float> entry : babyGrowthSeconds.entrySet()) {
            if (!AnimalBreedingRules.isMature(entry.getValue())) continue;
            Visual visual = visuals.get(entry.getKey());
            if (visual == null || !AnimalBreedingRules.isBaby(visual.state.type())) continue;
            CreatureType adultType = AnimalBreedingRules.adultTypeFor(visual.state.type());
            WorldCreature baby = visual.state;
            ready.add(new WorldCreature(baby.id(), adultType, baby.x(), baby.y(), baby.z(), baby.yaw()));
        }
        for (WorldCreature grown : ready) {
            Visual previous = visuals.remove(grown.id());
            if (previous != null) previous.root.removeFromParent();
            world.updateCreature(grown);
            Visual adult = createVisual(grown);
            visuals.put(grown.id(), adult);
            node.attachChild(adult.root);
            babyGrowthSeconds.remove(grown.id());
            if (animalProductsEnabled && AnimalProductRules.produces(grown.type())) {
                productionCooldowns.putIfAbsent(grown.id(),
                        AnimalProductRules.initialCooldown(grown.type()));
            }
        }
    }

    private void wander(Visual visual, float tpf, WorldCreature state) {
        visual.turnTimer -= tpf;
        if (visual.turnTimer <= 0f) {
            visual.turnTimer = 2.5f + visual.random.nextFloat() * 5.5f;
            visual.yaw = visual.random.nextFloat() * (float) (Math.PI * 2.0);
            visual.pauseTimer = .5f + visual.random.nextFloat() * 2.4f;
        }
        float nextX = state.x();
        float nextZ = state.z();
        if (visual.pauseTimer > 0f) visual.pauseTimer -= tpf;
        else {
            float step = state.type().moveSpeed() * Math.min(Math.max(tpf, 0f), .05f);
            nextX += (float) Math.sin(visual.yaw) * step;
            nextZ += (float) Math.cos(visual.yaw) * step;
        }
        visual.nextX = nextX;
        visual.nextZ = nextZ;
    }

    private List<Visual> findVillageThreats() {
        List<WorldCreature> villagers = new ArrayList<>();
        List<Visual> hostiles = new ArrayList<>();
        for (Visual visual : visuals.values()) {
            if (visual.state.type().isVillager()) villagers.add(visual.state);
            if (visual.state.type().isHostile() && visual.health > 0f) hostiles.add(visual);
        }
        if (villagers.isEmpty() || hostiles.isEmpty()) return List.of();

        List<Visual> threats = new ArrayList<>();
        for (Visual hostile : hostiles) {
            if (CreatureThreatRules.isThreateningVillage(hostile.state, villagers, 18f)) {
                threats.add(hostile);
            }
        }
        return threats;
    }

    private boolean visitWorkplace(Visual visual, float tpf, VoxelWorld world) {
        WorldCreature state = visual.state;
        float elapsed = Float.isFinite(tpf) ? Math.max(0f, tpf) : 0f;
        visual.workplaceSearchTimer = Math.max(0f, visual.workplaceSearchTimer - elapsed);

        if (visual.workplace != null
                && !VillagerWorkplaceRules.isWorkplaceFor(
                        state.type(), world.getBlock(visual.workplace))) {
            visual.workplace = null;
            visual.workplaceSearchTimer = 0f;
        }
        if (visual.workplaceSearchTimer <= 0f) {
            visual.workplace = VillagerWorkplaceRules.findNearestWorkplace(
                    state.type(), state.x(), state.y(), state.z(), world,
                    VillagerWorkplaceRules.SEARCH_RADIUS);
            visual.workplaceSearchTimer = VillagerWorkplaceRules.SEARCH_INTERVAL_SECONDS;
        }
        if (visual.workplace == null) {
            visual.working = false;
            return false;
        }

        float stationX = visual.workplace.x() + 0.5f;
        float stationZ = visual.workplace.z() + 0.5f;
        float awayX = state.x() - stationX;
        float awayZ = state.z() - stationZ;
        float awayLength = (float) Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (awayLength < 0.001f) {
            int direction = (int) (state.id() & 3L);
            awayX = direction == 0 ? 1f : direction == 2 ? -1f : 0f;
            awayZ = direction == 1 ? 1f : direction == 3 ? -1f : 0f;
            awayLength = 1f;
        }
        float targetX = stationX + awayX / awayLength * 1.25f;
        float targetZ = stationZ + awayZ / awayLength * 1.25f;
        float dx = targetX - state.x();
        float dz = targetZ - state.z();
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance > 0.32f) {
            visual.working = false;
            visual.yaw = com.jme3.math.FastMath.atan2(dx, dz);
            float step = Math.min(distance - 0.32f,
                    state.type().moveSpeed() * 1.15f * Math.min(elapsed, 0.05f));
            visual.nextX = state.x() + (float) Math.sin(visual.yaw) * step;
            visual.nextZ = state.z() + (float) Math.cos(visual.yaw) * step;
        } else {
            visual.working = true;
            visual.nextX = state.x();
            visual.nextZ = state.z();
            visual.yaw = com.jme3.math.FastMath.atan2(
                    stationX - state.x(), stationZ - state.z());
        }
        return true;
    }

    private Visual nearestVillagerThreat(Visual villager, List<Visual> protectedHostiles) {
        Visual closest = null;
        float radiusSquared = VillagerBehaviorRules.FLEE_RADIUS * VillagerBehaviorRules.FLEE_RADIUS;
        float best = radiusSquared;
        for (Visual candidate : protectedHostiles) {
            if (!candidate.state.type().isHostile() || candidate.health <= 0f) continue;
            float dx = candidate.state.x() - villager.state.x();
            float dz = candidate.state.z() - villager.state.z();
            float dy = Math.abs(candidate.state.y() - villager.state.y());
            float distance = dx * dx + dz * dz;
            if (dy <= 4f && VillagerBehaviorRules.shouldFlee(
                    villager.state.type(), candidate.state.type(), distance,
                    VillagerBehaviorRules.FLEE_RADIUS) && distance < best) {
                best = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private Visual nearestProtectedHostile(Visual guardian, List<Visual> protectedHostiles) {
        Visual closest = null;
        float best = 14f * 14f;
        for (Visual candidate : protectedHostiles) {
            if (candidate.health <= 0f) continue;
            float dx = candidate.state.x() - guardian.state.x();
            float dz = candidate.state.z() - guardian.state.z();
            float distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    static float gaitSwing(float animationClock, boolean walking) {
        return walking ? com.jme3.math.FastMath.sin(animationClock) * 0.42f : 0f;
    }

    static float slimeBounce(float animationClock) {
        return Math.abs(com.jme3.math.FastMath.sin(animationClock * 2f)) * 0.13f;
    }

    static float rabbitHop(float animationClock, boolean hopping) {
        if (!Float.isFinite(animationClock)) return 0f;
        return hopping ? Math.abs(com.jme3.math.FastMath.sin(animationClock * 2.6f)) * 0.11f : 0f;
    }

    static float companionStep(float distance, float speed, float tpf) {
        if (!Float.isFinite(distance) || !Float.isFinite(speed) || !Float.isFinite(tpf)
                || distance < 0f || speed < 0f || tpf < 0f) {
            throw new IllegalArgumentException("Companion movement values must be finite and non-negative");
        }
        if (distance <= 2.25f) return 0f;
        return Math.min(distance - 2.25f, speed * Math.min(tpf, 0.05f));
    }

    private void animateLimbs(Visual visual) {
        float swing = gaitSwing(visual.animationClock, visual.walking);
        for (Map.Entry<String, Geometry> part : visual.animationParts.entrySet()) {
            String name = part.getKey();
            float angle = 0f;
            if (name.contains("leg")) {
                angle = name.contains("back") ? -swing : swing;
                if (name.contains("right")) angle = -angle;
            } else if (name.contains("arm")) {
                angle = name.contains("right") ? -swing * 0.7f : swing * 0.7f;
                if (visual.working) {
                    float side = name.contains("right") ? -1f : 1f;
                    angle += side * (float) Math.sin(visual.animationClock * 3.2f) * 0.18f;
                }
            }
            part.getValue().setLocalRotation(new Quaternion().fromAngleAxis(angle, Vector3f.UNIT_X));
        }
    }

    private Visual createVisual(WorldCreature creature) {
        Node root = new Node("Creature-" + creature.type().id() + "-" + creature.id());
        switch (creature.type()) {
            case COW, CALF -> buildCow(root);
            case SHEEP, LAMB -> buildSheep(root);
            case PIG, PIGLET -> buildPig(root);
            case CHICKEN, CHICK -> buildChicken(root);
            case VILLAGER, VILLAGER_FARMER, VILLAGER_BLACKSMITH,
                    VILLAGER_LIBRARIAN, VILLAGER_GUARD -> buildVillager(root, creature.type());
            case SLIME -> buildSlime(root);
            case FOX -> buildFox(root);
            case TURTLE -> buildTurtle(root);
            case DOG -> buildDog(root);
            case RABBIT -> buildRabbit(root);
            case IRON_GOLEM -> buildIronGolem(root);
            case ZOMBIE -> buildZombie(root);
            case SKELETON -> buildSkeleton(root);
            case SPIDER -> buildSpider(root);
            case CRYSTAL_SENTINEL -> buildCrystalSentinel(root);
            case TEMPLE_WARDEN -> buildTempleWarden(root);
        }
        Map<String, Geometry> animationParts = new HashMap<>();
        for (com.jme3.scene.Spatial child : root.getChildren()) {
            if (child instanceof Geometry geometry) {
                String name = geometry.getName();
                if (name.contains("leg") || name.contains("arm")) animationParts.put(name, geometry);
            }
        }
        root.setLocalTranslation(creature.x(), creature.y(), creature.z());
        root.setLocalRotation(new Quaternion().fromAngleAxis(creature.yaw(), Vector3f.UNIT_Y));
        if (AnimalBreedingRules.isBaby(creature.type())) root.setLocalScale(0.55f, 0.55f, 0.55f);
        return new Visual(creature, root, animationParts);
    }

    private void buildCow(Node root) {
        addBox(root, "cow-body", .49f, .34f, .76f, 0f, .65f, 0f, color(.88f, .86f, .78f));
        addBox(root, "cow-spot-a", .18f, .12f, .20f, -.29f, .77f, -.15f, color(.32f, .20f, .12f));
        addBox(root, "cow-spot-b", .16f, .10f, .19f, .29f, .55f, .26f, color(.32f, .20f, .12f));
        addBox(root, "cow-head", .25f, .26f, .28f, 0f, .85f, -.79f, color(.48f, .30f, .19f));
        addBox(root, "cow-snout", .17f, .11f, .12f, 0f, .68f, -1.10f, color(.88f, .59f, .53f));
        legs(root, color(.28f, .23f, .19f), .12f, .27f, .13f);
        addBox(root, "cow-ear-left", .08f, .07f, .16f, -.29f, .88f, -.77f, color(.60f, .39f, .30f));
        addBox(root, "cow-ear-right", .08f, .07f, .16f, .29f, .88f, -.77f, color(.60f, .39f, .30f));
    }

    private void buildSheep(Node root) {
        ColorRGBA wool = color(.91f, .91f, .86f);
        addBox(root, "sheep-wool", .50f, .40f, .70f, 0f, .67f, 0f, wool);
        addBox(root, "sheep-wool-top", .31f, .13f, .34f, 0f, 1.04f, -.08f, wool);
        addBox(root, "sheep-head", .22f, .24f, .25f, 0f, .82f, -.79f, color(.58f, .48f, .39f));
        addBox(root, "sheep-muzzle", .13f, .09f, .09f, 0f, .67f, -1.07f, color(.76f, .60f, .55f));
        legs(root, color(.37f, .32f, .28f), .10f, .27f, .11f);
    }

    private void buildPig(Node root) {
        ColorRGBA pink = color(.91f, .55f, .57f);
        addBox(root, "pig-body", .48f, .32f, .69f, 0f, .63f, 0f, pink);
        addBox(root, "pig-head", .25f, .24f, .26f, 0f, .83f, -.76f, pink);
        addBox(root, "pig-snout", .17f, .12f, .08f, 0f, .73f, -1.06f, color(.77f, .36f, .40f));
        legs(root, color(.73f, .39f, .43f), .11f, .25f, .12f);
        addBox(root, "pig-ear-left", .09f, .12f, .07f, -.20f, 1.10f, -.80f, pink);
        addBox(root, "pig-ear-right", .09f, .12f, .07f, .20f, 1.10f, -.80f, pink);
    }

    private void buildChicken(Node root) {
        addBox(root, "chicken-body", .29f, .34f, .36f, 0f, .57f, 0f, color(.91f, .89f, .82f));
        addBox(root, "chicken-head", .18f, .20f, .18f, 0f, .91f, -.31f, color(.95f, .94f, .88f));
        addBox(root, "chicken-beak", .08f, .06f, .12f, 0f, .85f, -.58f, color(.96f, .55f, .12f));
        addBox(root, "chicken-comb", .07f, .10f, .06f, 0f, 1.18f, -.30f, color(.78f, .12f, .12f));
        addBox(root, "chicken-leg-left", .045f, .18f, .045f, -.12f, .18f, 0f, color(.90f, .60f, .12f));
        addBox(root, "chicken-leg-right", .045f, .18f, .045f, .12f, .18f, 0f, color(.90f, .60f, .12f));
    }

    private void buildVillager(Node root, CreatureType type) {
        ColorRGBA robe = switch (type) {
            case VILLAGER_FARMER -> color(.30f, .48f, .20f);
            case VILLAGER_BLACKSMITH -> color(.43f, .25f, .23f);
            case VILLAGER_LIBRARIAN -> color(.30f, .34f, .60f);
            case VILLAGER_GUARD -> color(.43f, .43f, .40f);
            default -> color(.44f, .32f, .20f);
        };
        addBox(root, "villager-robe", .34f, .52f, .27f, 0f, .69f, 0f, robe);
        addBox(root, "villager-head", .23f, .24f, .23f, 0f, 1.43f, -.02f, color(.75f, .57f, .43f));
        addBox(root, "villager-nose", .06f, .08f, .13f, 0f, 1.36f, -.31f, color(.66f, .45f, .35f));
        addBox(root, "villager-eye-left", .035f, .035f, .025f, -.09f, 1.48f, -.235f, color(.96f, .92f, .82f));
        addBox(root, "villager-eye-right", .035f, .035f, .025f, .09f, 1.48f, -.235f, color(.96f, .92f, .82f));
        addBox(root, "villager-pupil-left", .014f, .022f, .012f, -.09f, 1.48f, -.261f, color(.16f, .12f, .09f));
        addBox(root, "villager-pupil-right", .014f, .022f, .012f, .09f, 1.48f, -.261f, color(.16f, .12f, .09f));
        addBox(root, "villager-hat", .25f, .07f, .25f, 0f, 1.70f, -.02f, color(.35f, .25f, .15f));
        addBox(root, "villager-hat-brim", .32f, .035f, .31f, 0f, 1.63f, -.02f, color(.28f, .19f, .11f));
        addBox(root, "villager-robe-belt", .345f, .055f, .28f, 0f, .46f, 0f, color(.26f, .18f, .12f));
        addBox(root, "villager-arm-left", .10f, .28f, .11f, -.40f, .68f, 0f, color(.55f, .40f, .27f));
        addBox(root, "villager-arm-right", .10f, .28f, .11f, .40f, .68f, 0f, color(.55f, .40f, .27f));
        addBox(root, "villager-leg-left", .12f, .18f, .13f, -.16f, .18f, .02f, color(.30f, .25f, .20f));
        addBox(root, "villager-leg-right", .12f, .18f, .13f, .16f, .18f, .02f, color(.30f, .25f, .20f));
    }

    private void buildIronGolem(Node root) {
        ColorRGBA iron = color(.60f, .61f, .56f);
        addBox(root, "golem-body", .55f, .72f, .38f, 0f, .91f, 0f, iron);
        addBox(root, "golem-head", .38f, .31f, .36f, 0f, 1.86f, -.02f, color(.68f, .68f, .61f));
        addBox(root, "golem-eye-left", .055f, .045f, .025f, -.15f, 1.90f, -.39f, color(.55f, .18f, .14f));
        addBox(root, "golem-eye-right", .055f, .045f, .025f, .15f, 1.90f, -.39f, color(.55f, .18f, .14f));
        addBox(root, "golem-nose", .07f, .09f, .08f, 0f, 1.68f, -.38f, color(.48f, .45f, .39f));
        addBox(root, "golem-arm-left", .16f, .57f, .16f, -.68f, .89f, 0f, iron);
        addBox(root, "golem-arm-right", .16f, .57f, .16f, .68f, .89f, 0f, iron);
        addBox(root, "golem-leg-left", .19f, .34f, .20f, -.27f, .28f, .03f, color(.48f, .49f, .45f));
        addBox(root, "golem-leg-right", .19f, .34f, .20f, .27f, .28f, .03f, color(.48f, .49f, .45f));
        addBox(root, "golem-vines", .12f, .26f, .08f, .38f, 1.17f, -.29f, color(.20f, .42f, .17f));
    }

    private void buildZombie(Node root) {
        ColorRGBA skin = color(.28f, .56f, .31f);
        addBox(root, "zombie-body", .29f, .38f, .16f, 0f, .98f, 0f, color(.17f, .35f, .55f));
        addBox(root, "zombie-head", .22f, .23f, .22f, 0f, 1.62f, 0f, skin);
        addBox(root, "zombie-eye-left", .035f, .035f, .02f, -.09f, 1.66f, -.225f, ColorRGBA.Black);
        addBox(root, "zombie-eye-right", .035f, .035f, .02f, .09f, 1.66f, -.225f, ColorRGBA.Black);
        addBox(root, "zombie-arm-left", .09f, .34f, .09f, -.39f, 1.10f, -.03f, skin);
        addBox(root, "zombie-arm-right", .09f, .34f, .09f, .39f, 1.10f, -.03f, skin);
        addBox(root, "zombie-leg-left", .13f, .34f, .14f, -.15f, .36f, 0f, color(.18f, .29f, .38f));
        addBox(root, "zombie-leg-right", .13f, .34f, .14f, .15f, .36f, 0f, color(.18f, .29f, .38f));
    }

    private void buildSkeleton(Node root) {
        ColorRGBA bone = color(.80f, .79f, .70f);
        addBox(root, "skeleton-ribcage", .24f, .37f, .13f, 0f, 1.02f, 0f, bone);
        addBox(root, "skeleton-head", .20f, .21f, .20f, 0f, 1.63f, 0f, bone);
        addBox(root, "skeleton-eye-left", .04f, .04f, .025f, -.08f, 1.66f, -.21f, ColorRGBA.Black);
        addBox(root, "skeleton-eye-right", .04f, .04f, .025f, .08f, 1.66f, -.21f, ColorRGBA.Black);
        addBox(root, "skeleton-arm-left", .065f, .34f, .07f, -.36f, 1.08f, 0f, bone);
        addBox(root, "skeleton-arm-right", .065f, .34f, .07f, .36f, 1.08f, 0f, bone);
        addBox(root, "skeleton-leg-left", .07f, .34f, .08f, -.13f, .35f, 0f, bone);
        addBox(root, "skeleton-leg-right", .07f, .34f, .08f, .13f, .35f, 0f, bone);
    }

    private void buildCrystalSentinel(Node root) {
        ColorRGBA stone = color(.23f, .27f, .35f);
        ColorRGBA crystal = color(.12f, .88f, .90f);
        ColorRGBA amethyst = color(.55f, .27f, .82f);
        addBox(root, "crystal-sentinel-body", .56f, .66f, .42f, 0f, 1.02f, 0f, stone);
        addBox(root, "crystal-sentinel-head", .40f, .38f, .38f, 0f, 1.98f, -.02f, color(.31f, .34f, .43f));
        addBox(root, "crystal-sentinel-core", .22f, .25f, .08f, 0f, 1.18f, -.47f, crystal);
        addBox(root, "crystal-sentinel-eye-left", .055f, .06f, .03f, -.15f, 2.02f, -.41f, amethyst);
        addBox(root, "crystal-sentinel-eye-right", .055f, .06f, .03f, .15f, 2.02f, -.41f, amethyst);
        addBox(root, "crystal-sentinel-spike-left", .13f, .38f, .13f, -.51f, 1.88f, .02f, crystal);
        addBox(root, "crystal-sentinel-spike-right", .13f, .38f, .13f, .51f, 1.88f, .02f, crystal);
        addBox(root, "crystal-sentinel-arm-left", .18f, .52f, .18f, -.72f, 1.02f, 0f, stone);
        addBox(root, "crystal-sentinel-arm-right", .18f, .52f, .18f, .72f, 1.02f, 0f, stone);
        addBox(root, "crystal-sentinel-leg-left", .21f, .36f, .21f, -.30f, .34f, .02f, stone);
        addBox(root, "crystal-sentinel-leg-right", .21f, .36f, .21f, .30f, .34f, .02f, stone);
        addBox(root, "crystal-sentinel-back-crystal", .12f, .30f, .12f, 0f, 1.68f, .37f, amethyst);
    }

    private void buildTempleWarden(Node root) {
        ColorRGBA sandstone = color(.70f, .56f, .36f);
        ColorRGBA bronze = color(.78f, .53f, .20f);
        ColorRGBA rune = color(.20f, .78f, .92f);
        addBox(root, "temple-warden-body", .58f, .65f, .42f, 0f, 1.02f, 0f, sandstone);
        addBox(root, "temple-warden-chest", .28f, .30f, .08f, 0f, 1.13f, -.45f, bronze);
        addBox(root, "temple-warden-head", .39f, .38f, .38f, 0f, 1.96f, -.02f, sandstone);
        addBox(root, "temple-warden-brow", .43f, .10f, .09f, 0f, 2.15f, -.40f, bronze);
        addBox(root, "temple-warden-eye-left", .06f, .06f, .03f, -.15f, 1.99f, -.41f, rune);
        addBox(root, "temple-warden-eye-right", .06f, .06f, .03f, .15f, 1.99f, -.41f, rune);
        addBox(root, "temple-warden-headdress", .50f, .11f, .46f, 0f, 2.36f, .02f, bronze);
        addBox(root, "temple-warden-arm-left", .18f, .52f, .18f, -.72f, 1.02f, 0f, sandstone);
        addBox(root, "temple-warden-arm-right", .18f, .52f, .18f, .72f, 1.02f, 0f, sandstone);
        addBox(root, "temple-warden-rune-left", .09f, .23f, .07f, -.73f, 1.03f, -.18f, rune);
        addBox(root, "temple-warden-rune-right", .09f, .23f, .07f, .73f, 1.03f, -.18f, rune);
        addBox(root, "temple-warden-leg-left", .21f, .36f, .21f, -.29f, .34f, .02f, bronze);
        addBox(root, "temple-warden-leg-right", .21f, .36f, .21f, .29f, .34f, .02f, bronze);
    }

    private void buildSpider(Node root) {
        ColorRGBA shell = color(.19f, .12f, .15f);
        addBox(root, "spider-body", .38f, .24f, .48f, 0f, .47f, .18f, shell);
        addBox(root, "spider-head", .25f, .20f, .26f, 0f, .53f, -.36f, color(.30f, .18f, .19f));
        for (int side : new int[]{-1, 1}) {
            for (int leg = 0; leg < 4; leg++) {
                float z = -.48f + leg * .32f;
                addBox(root, "spider-leg-" + side + "-" + leg, .055f, .055f, .30f,
                        side * (.38f + leg * .025f), .26f, z, shell);
            }
        }
        addBox(root, "spider-eye-left", .045f, .045f, .025f, -.12f, .59f, -.61f, color(.86f, .16f, .12f));
        addBox(root, "spider-eye-right", .045f, .045f, .025f, .12f, .59f, -.61f, color(.86f, .16f, .12f));
    }

    private void buildFox(Node root) {
        ColorRGBA orange = color(.87f, .39f, .12f);
        addBox(root, "fox-body", .30f, .27f, .55f, 0f, .48f, 0f, orange);
        addBox(root, "fox-chest", .17f, .18f, .28f, 0f, .43f, -.36f, color(.92f, .82f, .67f));
        addBox(root, "fox-head", .22f, .22f, .25f, 0f, .75f, -.57f, orange);
        addBox(root, "fox-muzzle", .12f, .09f, .20f, 0f, .62f, -.82f, color(.93f, .84f, .72f));
        addBox(root, "fox-ear-left", .07f, .14f, .07f, -.15f, 1.02f, -.55f, orange);
        addBox(root, "fox-ear-right", .07f, .14f, .07f, .15f, 1.02f, -.55f, orange);
        addBox(root, "fox-tail", .13f, .15f, .42f, 0f, .50f, .78f, color(.91f, .80f, .65f));
        legs(root, color(.35f, .25f, .19f), .08f, .21f, .09f);
    }

    private void buildTurtle(Node root) {
        ColorRGBA green = color(.31f, .58f, .31f);
        addBox(root, "turtle-body", .39f, .12f, .47f, 0f, .34f, 0f, green);
        addBox(root, "turtle-shell", .43f, .22f, .47f, 0f, .55f, .02f, color(.27f, .38f, .22f));
        addBox(root, "turtle-shell-top", .30f, .10f, .33f, 0f, .78f, .02f, color(.43f, .49f, .24f));
        addBox(root, "turtle-head", .17f, .16f, .20f, 0f, .48f, -.60f, green);
        addBox(root, "turtle-eye-left", .025f, .03f, .02f, -.10f, .53f, -.77f, ColorRGBA.Black);
        addBox(root, "turtle-eye-right", .025f, .03f, .02f, .10f, .53f, -.77f, ColorRGBA.Black);
        addBox(root, "turtle-leg-front-left", .12f, .10f, .14f, -.32f, .15f, -.30f, green);
        addBox(root, "turtle-leg-front-right", .12f, .10f, .14f, .32f, .15f, -.30f, green);
        addBox(root, "turtle-leg-back-left", .12f, .10f, .14f, -.32f, .15f, .30f, green);
        addBox(root, "turtle-leg-back-right", .12f, .10f, .14f, .32f, .15f, .30f, green);
    }

    private void buildDog(Node root) {
        ColorRGBA coat = color(.72f, .55f, .36f);
        addBox(root, "dog-body", .31f, .30f, .55f, 0f, .52f, 0f, coat);
        addBox(root, "dog-chest", .18f, .18f, .22f, 0f, .48f, -.35f, color(.86f, .78f, .63f));
        addBox(root, "dog-head", .22f, .22f, .25f, 0f, .80f, -.58f, coat);
        addBox(root, "dog-muzzle", .14f, .10f, .17f, 0f, .66f, -.84f, color(.88f, .79f, .67f));
        addBox(root, "dog-nose", .045f, .04f, .04f, 0f, .69f, -.99f, ColorRGBA.Black);
        addBox(root, "dog-ear-left", .08f, .16f, .08f, -.17f, 1.04f, -.59f, color(.43f, .28f, .19f));
        addBox(root, "dog-ear-right", .08f, .16f, .08f, .17f, 1.04f, -.59f, color(.43f, .28f, .19f));
        addBox(root, "dog-tail", .08f, .20f, .08f, 0f, .83f, .59f, coat);
        legs(root, color(.43f, .31f, .22f), .09f, .23f, .10f);
    }

    private void buildRabbit(Node root) {
        ColorRGBA fur = color(.88f, .79f, .66f);
        ColorRGBA belly = color(.96f, .91f, .82f);
        ColorRGBA innerEar = color(.88f, .48f, .49f);
        addBox(root, "rabbit-body", .23f, .19f, .34f, 0f, .43f, .04f, fur);
        addBox(root, "rabbit-belly", .16f, .11f, .25f, 0f, .39f, -.02f, belly);
        addBox(root, "rabbit-head", .19f, .18f, .18f, 0f, .69f, -.30f, fur);
        addBox(root, "rabbit-muzzle", .10f, .07f, .08f, 0f, .60f, -.45f, belly);
        addBox(root, "rabbit-nose", .035f, .025f, .02f, 0f, .63f, -.51f, innerEar);
        addBox(root, "rabbit-eye-left", .022f, .028f, .018f, -.115f, .72f, -.455f, color(.16f, .12f, .11f));
        addBox(root, "rabbit-eye-right", .022f, .028f, .018f, .115f, .72f, -.455f, color(.16f, .12f, .11f));
        addBox(root, "rabbit-ear-left", .055f, .23f, .055f, -.105f, .98f, -.30f, fur);
        addBox(root, "rabbit-ear-right", .055f, .23f, .055f, .105f, .98f, -.30f, fur);
        addBox(root, "rabbit-ear-inner-left", .020f, .16f, .012f, -.105f, .99f, -.354f, innerEar);
        addBox(root, "rabbit-ear-inner-right", .020f, .16f, .012f, .105f, .99f, -.354f, innerEar);
        addBox(root, "rabbit-tail", .09f, .09f, .09f, 0f, .48f, .40f, belly);
        ColorRGBA feet = color(.76f, .63f, .51f);
        addBox(root, "rabbit-leg-front-left", .065f, .11f, .07f, -.14f, .16f, -.20f, feet);
        addBox(root, "rabbit-leg-front-right", .065f, .11f, .07f, .14f, .16f, -.20f, feet);
        addBox(root, "rabbit-leg-back-left", .085f, .13f, .10f, -.15f, .15f, .22f, feet);
        addBox(root, "rabbit-leg-back-right", .085f, .13f, .10f, .15f, .15f, .22f, feet);
    }

    private void buildSlime(Node root) {
        addBox(root, "slime-body", .40f, .38f, .40f, 0f, .48f, 0f, color(.22f, .78f, .37f));
        addBox(root, "slime-eye-left", .055f, .07f, .035f, -.16f, .58f, -.41f, color(0f, 0f, 0f));
        addBox(root, "slime-eye-right", .055f, .07f, .035f, .16f, .58f, -.41f, color(0f, 0f, 0f));
        addBox(root, "slime-mouth", .09f, .025f, .025f, 0f, .35f, -.42f, color(.06f, .22f, .10f));
    }

    private void legs(Node root, ColorRGBA color, float halfX, float halfY, float halfZ) {
        addBox(root, "leg-front-left", halfX, halfY, halfZ, -.28f, halfY, -.49f, color);
        addBox(root, "leg-front-right", halfX, halfY, halfZ, .28f, halfY, -.49f, color);
        addBox(root, "leg-back-left", halfX, halfY, halfZ, -.28f, halfY, .49f, color);
        addBox(root, "leg-back-right", halfX, halfY, halfZ, .28f, halfY, .49f, color);
    }

    private void addBox(Node parent, String name, float x, float y, float z,
                        float px, float py, float pz, ColorRGBA color) {
        Geometry geometry = new Geometry(name, new Box(x, y, z));
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        String rootName = parent.getName();
        String textureId = rootName != null && rootName.startsWith("Creature-")
                ? rootName.substring("Creature-".length()).split("-", 2)[0] : "";
        try {
            textureId = AnimalBreedingRules.textureTypeFor(CreatureType.fromId(textureId)).id();
        } catch (IllegalArgumentException ignored) {
            // Keep a plain material if an optional creature texture cannot be mapped.
        }
        Texture skin = creatureTextures.get(textureId);
        if (skin != null) material.setTexture("ColorMap", skin);
        material.setColor("Color", color);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(px, py, pz);
        parent.attachChild(geometry);
    }

    private ColorRGBA color(float r, float g, float b) { return new ColorRGBA(r, g, b, 1f); }

    private static final class Visual {
        private WorldCreature state;
        private final Node root;
        private final Random random;
        private final Map<String, Geometry> animationParts;
        private float turnTimer;
        private float pauseTimer;
        private float yaw;
        private float animationClock;
        private float attackCooldown;
        private float health;
        private float nextX;
        private float nextZ;
        private float workplaceSearchTimer;
        private BlockPos workplace;
        private boolean walking;
        private boolean working;

        private Visual(WorldCreature state, Node root, Map<String, Geometry> animationParts) {
            this.state = state;
            this.root = root;
            this.animationParts = animationParts;
            this.random = new Random(state.id() * 0x9E3779B97F4A7C15L + state.type().ordinal());
            this.yaw = state.yaw();
            this.health = state.type().maxHealth();
            this.nextX = state.x();
            this.nextZ = state.z();
            this.turnTimer = 1f + random.nextFloat() * 3f;
        }
    }
}
