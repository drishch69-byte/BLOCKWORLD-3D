package com.blockworld.game;

import com.blockworld.core.BlockHit;
import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.ItemType;
import com.blockworld.core.ToolType;
import com.blockworld.core.MiningRules;
import com.blockworld.core.ArmorSlot;
import com.blockworld.core.ArmorProtection;
import com.blockworld.core.CraftingRecipe;
import com.blockworld.core.CombatRules;
import com.blockworld.core.FarmDataStore;
import com.blockworld.core.FarmRules;
import com.blockworld.core.FoodRules;
import com.blockworld.core.FurnaceRecipe;
import com.blockworld.core.TradeRecipe;
import com.blockworld.core.FishingRules;
import com.blockworld.core.Achievement;
import com.blockworld.core.AchievementTracker;
import com.blockworld.core.AchievementDataStore;
import com.blockworld.core.BiomeType;
import com.blockworld.core.WorldNavigation;
import com.blockworld.core.WorldCreationOptions;
import com.blockworld.core.TreasureLoot;
import com.blockworld.core.PlayerDataStore;
import com.blockworld.core.PetDataStore;
import com.blockworld.core.PetMode;
import com.blockworld.core.PlayerProfileStore;
import com.blockworld.core.GameVersion;
import com.blockworld.core.GameSettings;
import com.blockworld.core.GamePaths;
import com.blockworld.core.WorldCatalog;
import com.blockworld.core.WorldBackupManager;
import com.blockworld.core.WorldFavorites;
import com.blockworld.core.ResourcePackManager;
import com.blockworld.core.ModLoader;
import com.blockworld.api.ModContext;
import com.blockworld.core.GameLog;
import com.blockworld.net.MultiplayerClient;
import com.blockworld.core.VoxelWorld;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Main 3D game window and input handling. */
public final class BlockWorldGame extends SimpleApplication {
    private static final String BREAK = "breakBlock";
    private static final String PLACE = "placeBlock";
    private static final String SAVE = "saveWorld";
    private static final String EAT = "eatFood";
    private static final String TALK = "interactCreature";

    private final GameVersion version;
    private volatile Path saveFile;
    private final boolean createNewWorld;
    private final Runnable onGameExit;
    private final GameSettings gameSettings;
    private final MultiplayerClient multiplayerClient;
    private final WorldCreationOptions worldCreationOptions;
    private final ConcurrentLinkedQueue<String> multiplayerInbox = new ConcurrentLinkedQueue<>();
    private final Map<Integer, RemotePlayerState> remotePlayers = new HashMap<>();
    private Map<BlockPos, BlockType> pendingNetworkWorld;
    private long pendingNetworkSeed;
    private boolean pendingNetworkActiveBiomes;
    private boolean pendingNetworkExtendedBiomes;
    private int pendingNetworkRadius;
    private int pendingNetworkBlockCount;
    private final Map<Long, com.blockworld.core.WorldCreature> pendingNetworkCreatures = new HashMap<>();
    private int pendingNetworkCreatureCount;
    private float creatureSyncClock;
    private boolean multiplayerSnapshotStarted;
    private boolean multiplayerSyncReady;
    private float multiplayerPositionClock;
    private float multiplayerStatsClock;
    private boolean multiplayerWorldReady;
    private boolean multiplayerProfileReady;
    private boolean multiplayerProfileInitialized;
    private EnumMap<BlockType, Integer> pendingProfileBlocks;
    private EnumMap<ItemType, Integer> pendingProfileItems;
    private EnumSet<ArmorSlot> pendingProfileArmor;
    private int expectedProfileBlocks;
    private int expectedProfileItems;
    private int expectedProfileArmor;
    private int receivedProfileBlocks;
    private int receivedProfileItems;
    private float pendingProfileHealth = 20f;
    private float pendingProfileHunger = 20f;
    private int pendingProfileFoodCount;
    private boolean multiplayerCraftPending;
    private boolean multiplayerTradePending;
    private final Map<BlockType, Integer> inventory = new EnumMap<>(BlockType.class);
    private final Map<ItemType, Integer> craftedItems = new EnumMap<>(ItemType.class);
    private final Set<ArmorSlot> equippedArmor = EnumSet.noneOf(ArmorSlot.class);
    private final Map<BlockPos, Float> farmGrowth = new HashMap<>();
    private final Map<Long, PetMode> pendingPetModes = new HashMap<>();
    private final boolean equipmentEnabled;
    private final boolean agricultureEnabled;
    private final boolean release100Enabled;
    private final boolean explorationEnabled;
    private final boolean petsEnabled;
    private final List<CraftingRecipe> craftingRecipes;
    private final List<TradeRecipe> tradeRecipes = TradeRecipe.villageOffers();
    private final AchievementTracker achievements = new AchievementTracker();
    private GameSounds gameSounds;
    private float fishingCooldown;
    private float biomeDiscoveryClock;
    private ToolType selectedTool;
    private int craftingPage;
    private int inventoryPage;

    private VoxelWorld world;
    private ModLoader modLoader;
    private CreatureManager creatureManager;
    private NatureDecorationManager natureDecorations;
    private PlayerAvatar playerAvatar;
    private PlayerProfileStore.Profile playerProfile = PlayerProfileStore.Profile.defaults();
    private ScreenshotAppState worldScreenshotState;
    private boolean worldPreviewCaptureSetup;
    private boolean worldPreviewCaptureRequested;
    private boolean profileSessionStarted;
    private long profileCheckpointNanos;
    private boolean thirdPersonView;
    private Vector3f playerEyePosition = new Vector3f(0f, 12f, 8f);
    private boolean modsWorldReady;
    private int loadedModCount;
    private BitmapText hud;
    private BitmapText crosshair;
    private BitmapText miningHud;
    private Geometry hudPanel;
    private Geometry navigationPanel;
    private BitmapText navigationHud;
    private boolean mapOpen;
    private Geometry loadingPad;
    private Geometry pausePanel;
    private BitmapText pauseText;
    private Geometry inventoryPanel;
    private BitmapText inventoryText;
    private InventoryVisualPanel inventoryVisualPanel;
    private boolean inventoryOpen;
    private boolean craftingOpen;
    private boolean furnaceOpen;
    private boolean tradeOpen;
    private boolean chestOpen;
    private boolean chestOperationPending;
    private BlockPos openChestPosition;
    private final Map<ItemType, Integer> sharedChestContents = new EnumMap<>(ItemType.class);
    private int pendingChestItems;
    private int receivedChestItems;
    private int chestPage;
    private ItemType selectedChestItem;
    private boolean achievementsOpen;
    private float farmSaveClock;
    private boolean paused;
    private boolean miningPressed;
    private BlockPos miningTarget;
    private BlockType miningBlock;
    private float miningElapsed;
    private float miningDuration;
    private List<BlockType> hotbar;
    private BlockType selectedBlock = BlockType.GRASS;
    private volatile String statusMessage = "Добро пожаловать в Minecraft 3D!";
    private boolean exitNotified;
    private volatile boolean runtimeFault;
    private volatile String runtimeFaultMessage = "";
    private volatile boolean worldDataReady;
    private volatile String loadingMessage = "Подготовка мира...";
    private boolean worldReady;
    private boolean worldNodeAttached;
    private Vector3f spawnPosition = new Vector3f(0f, 4.68f, 8f);
    private float fpsEstimate = 60f;
    private int renderDistanceIndex = 0;
    private boolean twoSidedRendering = true;
    private DirectionalLight natureSun;
    private float dayClock = 0f;
    private boolean raining = false;
    private static final int[] RENDER_DISTANCES = {3, 4, 6, 8};

    // Survival state.
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean jumpRequested;
    private boolean onGround;
    private float verticalVelocity;
    private float fallStartY = Float.NaN;
    private float yaw;
    private float pitch = -0.42f;
    private float health = 20f;
    private float hunger = 20f;
    private int foodCount = 3;
    private float hungerClock;
    private float starvationClock;
    private float hudRefreshClock;
    private float chunkWaitClock;

    public BlockWorldGame(GameVersion version, Path saveFile, boolean createNewWorld, Runnable onGameExit) {
        this(version, saveFile, createNewWorld, onGameExit, GameSettings.load(), null);
    }

    public BlockWorldGame(GameVersion version, Path saveFile, boolean createNewWorld,
                          Runnable onGameExit, GameSettings gameSettings) {
        this(version, saveFile, createNewWorld, onGameExit, gameSettings, null);
    }

    public BlockWorldGame(GameVersion version, Path saveFile, boolean createNewWorld,
                          Runnable onGameExit, GameSettings gameSettings, MultiplayerClient multiplayerClient) {
        this(version, saveFile, createNewWorld, onGameExit, gameSettings, multiplayerClient, null);
    }

    public BlockWorldGame(GameVersion version, Path saveFile, boolean createNewWorld,
                          Runnable onGameExit, GameSettings gameSettings, MultiplayerClient multiplayerClient,
                          WorldCreationOptions worldCreationOptions) {
        this.version = version;
        this.equipmentEnabled = supportsEquipment(version);
        this.agricultureEnabled = supportsAgriculture(version);
        this.release100Enabled = supportsRelease100(version);
        this.explorationEnabled = WorldNavigation.supportsExploration(version.id());
        this.petsEnabled = version.id().compareTo("1.9.0") >= 0;
        List<CraftingRecipe> allRecipes = release100Enabled ? CraftingRecipe.release100Recipes()
                : agricultureEnabled ? CraftingRecipe.advancedRecipes() : CraftingRecipe.starterRecipes();
        this.craftingRecipes = equipmentEnabled ? allRecipes : List.copyOf(allRecipes.subList(0, 4));
        this.saveFile = saveFile;
        this.createNewWorld = createNewWorld;
        this.onGameExit = onGameExit;
        this.gameSettings = gameSettings == null ? GameSettings.DEFAULT : gameSettings;
        this.multiplayerClient = multiplayerClient;
        this.worldCreationOptions = worldCreationOptions;
        if (multiplayerClient != null) multiplayerClient.setEventListener(multiplayerInbox::add);
    }

    private static boolean supportsEquipment(GameVersion version) {
        if (version == null || version.id() == null) return false;
        String[] parts = version.id().split("\\.");
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 0 || (major == 0 && minor >= 9);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean supportsRelease100(GameVersion version) {
        if (version == null || version.id() == null) return false;
        try { return Integer.parseInt(version.id().split("\\.")[0]) >= 1; }
        catch (RuntimeException ex) { return false; }
    }

    private static boolean supportsAgriculture(GameVersion version) {
        if (version == null || version.id() == null) return false;
        String[] parts = version.id().split("\\.");
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 0 || minor > 9 || (minor == 9 && patch >= 5);
        } catch (NumberFormatException ex) { return false; }
    }

    public void launch() {
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Minecraft 3D  |  " + version.id() + " " + version.displayName());
        settings.setResolution(gameSettings.width(), gameSettings.height());
        settings.setFullscreen(gameSettings.fullscreen());
        settings.setResizable(true);
        settings.setVSync(gameSettings.vsync());
        settings.setFrameRate(gameSettings.fpsLimit());
        settings.setSamples(0);
        setSettings(settings);
        setShowSettings(false);
        GameLog.info("Запуск окна игры: " + version.id() + "; resolution=" + gameSettings.width() + "x" + gameSettings.height()
                + "; samples=0; vsync=" + gameSettings.vsync() + "; fpsLimit=" + gameSettings.fpsLimit()
                + "; fullscreen=" + gameSettings.fullscreen() + "; resizable=true"
                + "; Java=" + System.getProperty("java.version")
                + "; OS=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + "; thread=" + Thread.currentThread().getName());
        GameLog.info("Передан вызов SimpleApplication.start().");
        start();
        GameLog.info("SimpleApplication.start() завершился без выброшенного исключения.");
    }

    @Override
    public void simpleInitApp() {
        GameLog.info("jMonkeyEngine вызвал simpleInitApp(); контекст и рендер-поток созданы.");
        try {
            playerProfile = PlayerProfileStore.load();
        } catch (IOException error) {
            GameLog.error("Не удалось загрузить профиль игрока; применён стандартный профиль", error);
            playerProfile = PlayerProfileStore.Profile.defaults();
        }
        setDisplayFps(false);
        setDisplayStatView(false);
        // A wider FOV and a short near plane keep nearby terrain visible in first-person view.
        cam.setFrustumPerspective(gameSettings.fov(), (float) settings.getWidth() / settings.getHeight(), 0.1f, 900f);
        viewPort.setBackgroundColor(new ColorRGBA(0.48f, 0.72f, 0.94f, 1f));

        flyCam.setMoveSpeed(11f);
        natureSun = new DirectionalLight();
        natureSun.setDirection(new Vector3f(-0.45f, -1f, -0.35f).normalizeLocal());
        natureSun.setColor(ColorRGBA.White.mult(1.15f));
        rootNode.addLight(natureSun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.62f, 0.66f, 0.72f, 1f));
        rootNode.addLight(ambient);

        java.util.Map<BlockType, ColorRGBA> palette = java.util.Map.of();
        com.jme3.texture.Texture2D textureAtlas = null;
        try {
            ResourcePackManager packs = new ResourcePackManager();
            palette = packs.loadPalette();
            textureAtlas = packs.loadTextureAtlas();
            GameLog.info("Применён ресурс-пак «" + packs.selectedPackId() + "» с PNG-текстурным атласом.");
        } catch (IOException | RuntimeException ex) {
            GameLog.error("Не удалось загрузить текстуры ресурс-пака; используется цветовой режим", ex);
        }
        world = new VoxelWorld(assetManager, palette, textureAtlas);
        creatureManager = new CreatureManager(assetManager);
        natureDecorations = new NatureDecorationManager(assetManager);
        natureDecorations.setDensity(gameSettings.vegetationDensity());
        playerAvatar = new PlayerAvatar(assetManager);
        playerAvatar.setStyle(playerProfile.appearanceIndex());
        playerAvatar.setDisplayName(assetManager, playerProfile.displayName());
        if (release100Enabled) {
            gameSounds = new GameSounds(assetManager, rootNode);
            GameLog.info("Звуковые эффекты 1.0.0 инициализированы (если доступно аудиоустройство).");
        }
        GameLog.info("Сцена создана; материал чанков, существа и AssetManager инициализированы. Цветов ресурс-пака: " + palette.size()
                + "; текстурный атлас: " + (textureAtlas != null));
        int savedDistance = gameSettings.renderDistance();
        for (int i = 0; i < RENDER_DISTANCES.length; i++) if (RENDER_DISTANCES[i] == savedDistance) renderDistanceIndex = i;
        world.setRenderDistanceChunks(RENDER_DISTANCES[renderDistanceIndex]);
        modLoader = new ModLoader(GamePaths.modsDirectory());
        loadedModCount = modLoader.load(new ModContext(world, version, GamePaths.configDirectory()));
        GameLog.info("Активных модов: " + loadedModCount);
        world.setTwoSidedRendering(twoSidedRendering);

        hotbar = switch (version.id()) {
            case "0.1.0" -> List.of(BlockType.GRASS, BlockType.DIRT, BlockType.STONE);
            case "0.2.0" -> List.of(BlockType.GRASS, BlockType.DIRT, BlockType.STONE,
                    BlockType.WOOD, BlockType.SAND);
            default -> List.of(BlockType.GRASS, BlockType.DIRT, BlockType.STONE,
                    BlockType.WOOD, BlockType.LEAVES, BlockType.SAND,
                    BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE);
        };

        if (version.survival()) {
            for (BlockType type : BlockType.values()) inventory.put(type, 0);
            inventory.put(BlockType.GRASS, 16);
            inventory.put(BlockType.DIRT, 16);
            inventory.put(BlockType.STONE, 16);
            inventory.put(BlockType.WOOD, 4);
            inventory.put(BlockType.COAL_ORE, 2);
            for (ItemType item : ItemType.values()) craftedItems.put(item, 0);
            if (agricultureEnabled) craftedItems.put(ItemType.WHEAT_SEEDS, 2);
            flyCam.setEnabled(false);
            cam.setLocation(new Vector3f(0f, 12f, 8f));
            playerEyePosition = cam.getLocation().clone();
            cam.setRotation(new Quaternion().fromAngles(pitch, yaw, 0f));
        } else {
            cam.setLocation(new Vector3f(0f, 16f, 26f));
            cam.lookAt(new Vector3f(0f, 1f, 0f), Vector3f.UNIT_Y);
        }

        // Hide the pointer in every active play mode; pause/launcher screens show it again.
        inputManager.setCursorVisible(false);

        setupHud();
        setupInputs();
        GameLog.info("HUD и управление инициализированы; запускается подготовка данных мира.");
        statusMessage = "Мир подготавливается в фоновом потоке...";
        updateHud();
        GameLog.info("Запуск профиля " + version.id() + "; сохранение: " + saveFile.toAbsolutePath());
        // Data generation/loading does not block the render thread. Chunks are meshed gradually below.
        CompletableFuture.runAsync(this::prepareWorldData).whenComplete((ignored, error) -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                GameLog.error("Фоновая генерация мира завершилась аварийно", cause);
                statusMessage = "Сбой генерации мира: " + cause.getClass().getSimpleName()
                        + " — " + (cause.getMessage() == null ? "без описания" : cause.getMessage());
                loadingMessage = "Ошибка подготовки мира. См. журнал.";
                worldDataReady = true;
            } else {
                GameLog.info("Фоновая подготовка мира завершена без исключения.");
            }
        });
    }

    private void prepareWorldData() {
        boolean loaded = false;
        String recoveryReason = null;
        try {
            if (!createNewWorld && Files.exists(saveFile)) {
                try {
                    String savedWith = world.load(saveFile);
                    statusMessage = "Загружен мир. Последнее сохранение: " + savedWith + ".";
                    loaded = true;
                    if (!savedWith.equals(version.id())) statusMessage += " Открыт в версии " + version.id() + ".";
                    if (world.getBlockCount() == 0) {
                        recoveryReason = "Сохранение оказалось пустым";
                        loaded = false;
                    }
                } catch (IOException ex) {
                    recoveryReason = "Не удалось загрузить сохранение: " + ex.getMessage();
                    GameLog.error("Существующее сохранение не загружено; оригинал будет сохранён отдельно", ex);
                }
            }

            if (loaded && version.survival()) {
                try { PlayerDataStore.load(saveFile, inventory, craftedItems, equippedArmor); }
                catch (IOException ex) { GameLog.error("Не удалось загрузить инвентарь игрока; используются стартовые значения", ex); }
            }
            if (loaded && agricultureEnabled) {
                try { FarmDataStore.load(saveFile, farmGrowth); }
                catch (IOException ex) { GameLog.error("Не удалось загрузить состояние грядок", ex); }
                farmGrowth.keySet().removeIf(position -> world.getBlock(position) != BlockType.WHEAT_CROP);
            }
            if (loaded && petsEnabled) {
                try { PetDataStore.load(saveFile, pendingPetModes); }
                catch (IOException ex) { GameLog.error("Не удалось загрузить команды питомцев", ex); }
            }
            if (release100Enabled) achievements.setExtendedBiomeSet(world.hasExtendedBiomes());
            if (loaded && release100Enabled) {
                try { AchievementDataStore.load(saveFile, achievements); }
                catch (IOException ex) { GameLog.error("Не удалось загрузить достижения", ex); }
            }
            if (!loaded) {
                if (recoveryReason != null) {
                    Path originalSave = saveFile;
                    String backupName = "не создана";
                    try {
                        Path backup = WorldBackupManager.create(originalSave);
                        backupName = backup.getFileName().toString();
                    } catch (IOException backupError) {
                        GameLog.error("Полная резервная копия повреждённого мира не создана; пробуем сохранить исходный файл отдельно", backupError);
                        try {
                            Path fallbackBackup = originalSave.resolveSibling(
                                    originalSave.getFileName() + ".load-error-backup");
                            Files.copy(originalSave, fallbackBackup, StandardCopyOption.REPLACE_EXISTING);
                            backupName = fallbackBackup.getFileName().toString()
                                    + " (только основной файл; sidecar-файлы остались на месте)";
                        } catch (IOException copyError) {
                            GameLog.error("Не удалось сделать аварийную копию исходного файла; исходник не будет перезаписан", copyError);
                        }
                    }
                    Path recoveredSave;
                    try {
                        recoveredSave = WorldCatalog.recoveryPathFor(originalSave);
                    } catch (IOException pathError) {
                        GameLog.error("Не удалось сформировать имя восстановленного мира; используется резервное имя", pathError);
                        recoveredSave = originalSave.resolveSibling(
                                "Recovered_" + System.currentTimeMillis() + ".bwworld");
                        int suffix = 2;
                        while (Files.exists(recoveredSave)) {
                            recoveredSave = originalSave.resolveSibling(
                                    "Recovered_" + System.currentTimeMillis() + "_" + suffix++ + ".bwworld");
                        }
                    }
                    saveFile = recoveredSave;
                    if (WorldFavorites.isFavorite(originalSave)) {
                        try {
                            WorldFavorites.setFavorite(recoveredSave, true);
                        } catch (IOException favoriteError) {
                            GameLog.error("Не удалось перенести отметку избранного на восстановленный мир", favoriteError);
                        }
                    }
                    statusMessage = recoveryReason + ". Исходный файл сохранён отдельно: "
                            + backupName + ". Для нового мира создан отдельный файл: "
                            + recoveredSave.getFileName() + ".";
                    GameLog.info("Повреждённый/пустой мир изолирован; создан путь восстановления "
                            + recoveredSave.toAbsolutePath());
                }
                long newSeed = worldCreationOptions != null && worldCreationOptions.seed() != null
                        ? worldCreationOptions.seed() : ThreadLocalRandom.current().nextLong();
                int generationRadius = worldCreationOptions == null
                        ? version.worldRadius() : worldCreationOptions.worldRadius();
                world.generate(version, newSeed, generationRadius);
                if (release100Enabled) achievements.setExtendedBiomeSet(world.hasExtendedBiomes());
                farmGrowth.clear();
                try {
                    world.save(saveFile, version);
                    if (version.survival()) PlayerDataStore.save(saveFile, inventory, craftedItems, equippedArmor);
                    if (agricultureEnabled) FarmDataStore.save(saveFile, farmGrowth);
                    if (release100Enabled) AchievementDataStore.save(saveFile, achievements);
                    statusMessage += " Новый мир создан. Seed: " + newSeed + ".";
                } catch (IOException ex) {
                    statusMessage += " Мир создан, но не сохранён: " + ex.getMessage();
                }
            }
            loadingMessage = "Подготовка чанков...";
            GameLog.info("Данные мира готовы: блоков=" + world.getBlockCount()
                    + ", чанков=" + world.getChunkCount() + ", seed=" + world.getSeed());
        } catch (RuntimeException ex) {
            statusMessage = "Ошибка подготовки мира: " + ex.getMessage();
            loadingMessage = "Не удалось подготовить мир";
            GameLog.error("Ошибка подготовки данных мира", ex);
        } finally {
            worldDataReady = true;
        }
    }

    private void setupHud() {
        guiNode.detachAllChildren();
        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");

        Material panelMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        panelMaterial.setColor("Color", new ColorRGBA(0.025f, 0.04f, 0.065f, 0.84f));
        panelMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        panelMaterial.getAdditionalRenderState().setDepthWrite(false);
        hudPanel = new Geometry("HUD-Background", new Quad(900f, 174f));
        hudPanel.setMaterial(panelMaterial);
        hudPanel.setQueueBucket(RenderQueue.Bucket.Gui);
        hudPanel.setLocalTranslation(7f, settings.getHeight() - 181f, 1f);
        guiNode.attachChild(hudPanel);

        hud = new BitmapText(guiFont);
        hud.setSize(15f);
        hud.setColor(ColorRGBA.White);
        hud.setQueueBucket(RenderQueue.Bucket.Gui);
        hud.setLocalTranslation(15f, settings.getHeight() - 14f, 0f);
        guiNode.attachChild(hud);

        navigationPanel = new Geometry("WorldMapPanel", new Quad(340f, 230f));
        navigationPanel.setMaterial(panelMaterial);
        navigationPanel.setQueueBucket(RenderQueue.Bucket.Gui);
        navigationPanel.setLocalTranslation(7f, 7f, 1f);
        navigationPanel.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        guiNode.attachChild(navigationPanel);

        navigationHud = new BitmapText(guiFont);
        navigationHud.setSize(14f);
        navigationHud.setColor(ColorRGBA.White);
        navigationHud.setQueueBucket(RenderQueue.Bucket.Gui);
        navigationHud.setLocalTranslation(15f, 225f, 0f);
        navigationHud.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        guiNode.attachChild(navigationHud);

        crosshair = new BitmapText(guiFont);
        crosshair.setText("+");
        crosshair.setSize(24f);
        crosshair.setColor(ColorRGBA.White);
        crosshair.setLocalTranslation(settings.getWidth() / 2f - 5f, settings.getHeight() / 2f + 7f, 0f);
        guiNode.attachChild(crosshair);

        miningHud = new BitmapText(guiFont);
        miningHud.setSize(15f);
        miningHud.setColor(new ColorRGBA(1f, 0.88f, 0.56f, 1f));
        miningHud.setQueueBucket(RenderQueue.Bucket.Gui);
        miningHud.setLocalTranslation(settings.getWidth() / 2f - 150f,
                settings.getHeight() / 2f - 24f, 2f);
        miningHud.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        guiNode.attachChild(miningHud);

        Material pauseMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        pauseMaterial.setColor("Color", new ColorRGBA(0.015f, 0.025f, 0.045f, 0.94f));
        pauseMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        pauseMaterial.getAdditionalRenderState().setDepthWrite(false);
        pausePanel = new Geometry("PauseMenuPanel", new Quad(520f, 250f));
        pausePanel.setMaterial(pauseMaterial);
        pausePanel.setQueueBucket(RenderQueue.Bucket.Gui);
        pausePanel.setLocalTranslation(settings.getWidth() / 2f - 260f, settings.getHeight() / 2f - 125f, 4f);
        pauseText = new BitmapText(guiFont);
        pauseText.setSize(22f);
        pauseText.setColor(ColorRGBA.White);
        pauseText.setQueueBucket(RenderQueue.Bucket.Gui);
        pauseText.setText("Minecraft 3D — ПАУЗА\n\nESC — продолжить игру\nF6 — сохранить мир\nF10 — выйти в главное меню");
        pauseText.setLocalTranslation(settings.getWidth() / 2f - 205f, settings.getHeight() / 2f + 82f, 5f);

        Material inventoryMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        inventoryMaterial.setColor("Color", new ColorRGBA(0.025f, 0.04f, 0.065f, 0.96f));
        inventoryMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        inventoryMaterial.getAdditionalRenderState().setDepthWrite(false);
        inventoryPanel = new Geometry("InventoryCraftingPanel", new Quad(700f, 500f));
        inventoryPanel.setMaterial(inventoryMaterial);
        inventoryPanel.setQueueBucket(RenderQueue.Bucket.Gui);
        inventoryPanel.setLocalTranslation(settings.getWidth() / 2f - 350f, settings.getHeight() / 2f - 250f, 8f);
        inventoryText = new BitmapText(guiFont);
        inventoryText.setSize(12.5f);
        inventoryText.setColor(ColorRGBA.White);
        inventoryText.setQueueBucket(RenderQueue.Bucket.Gui);
        inventoryText.setLocalTranslation(settings.getWidth() / 2f - 325f, settings.getHeight() / 2f + 220f, 9f);
        inventoryVisualPanel = new InventoryVisualPanel(assetManager, guiNode, guiFont);
        inventoryVisualPanel.layout(settings.getWidth(), settings.getHeight());
        updateInventoryUi();
    }

    private void setupInputs() {
        inputManager.addMapping(BREAK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(PLACE, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping(SAVE, new KeyTrigger(KeyInput.KEY_F6));
        inputManager.addMapping("TOGGLE_CAMERA", new KeyTrigger(KeyInput.KEY_F5));
        inputManager.addMapping("CYCLE_PLAYER_STYLE", new KeyTrigger(KeyInput.KEY_V));
        inputManager.addMapping("PET_COMMAND", new KeyTrigger(KeyInput.KEY_N));
        inputManager.addMapping("TOGGLE_MAP", new KeyTrigger(KeyInput.KEY_B));
        inputManager.addMapping("SLOT_1", new KeyTrigger(KeyInput.KEY_1));
        inputManager.addMapping("SLOT_2", new KeyTrigger(KeyInput.KEY_2));
        inputManager.addMapping("SLOT_3", new KeyTrigger(KeyInput.KEY_3));
        inputManager.addMapping("SLOT_4", new KeyTrigger(KeyInput.KEY_4));
        inputManager.addMapping("SLOT_5", new KeyTrigger(KeyInput.KEY_5));
        inputManager.addMapping("SLOT_6", new KeyTrigger(KeyInput.KEY_6));
        inputManager.addMapping("SLOT_7", new KeyTrigger(KeyInput.KEY_7));
        inputManager.addMapping("SLOT_8", new KeyTrigger(KeyInput.KEY_8));
        inputManager.addMapping("SLOT_9", new KeyTrigger(KeyInput.KEY_9));
        inputManager.addMapping("HOTBAR_SCROLL_UP", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addMapping("HOTBAR_SCROLL_DOWN", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("TOGGLE_PAUSE", new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("EXIT_GAME", new KeyTrigger(KeyInput.KEY_F10));
        inputManager.addMapping(EAT, new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping(TALK, new KeyTrigger(KeyInput.KEY_F));
        inputManager.addMapping("RENDER_DISTANCE", new KeyTrigger(KeyInput.KEY_F7));
        inputManager.addMapping("TOGGLE_CULLING", new KeyTrigger(KeyInput.KEY_F8));
        inputManager.addMapping("WEATHER", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addMapping("TOGGLE_INVENTORY", new KeyTrigger(KeyInput.KEY_I));
        inputManager.addMapping("TOGGLE_CRAFTING", new KeyTrigger(KeyInput.KEY_C));
        inputManager.addMapping("CYCLE_TOOL", new KeyTrigger(KeyInput.KEY_T));
        inputManager.addMapping("USE_UTILITY", new KeyTrigger(KeyInput.KEY_G));
        inputManager.addMapping("FISHING_CAST", new KeyTrigger(KeyInput.KEY_P));
        inputManager.addMapping("TOGGLE_ACHIEVEMENTS", new KeyTrigger(KeyInput.KEY_M));
        inputManager.addMapping("CRAFT_PAGE_PREV", new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("CRAFT_PAGE_NEXT", new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping("EQUIP_HELMET", new KeyTrigger(KeyInput.KEY_H));
        inputManager.addMapping("EQUIP_CHEST", new KeyTrigger(KeyInput.KEY_J));
        inputManager.addMapping("EQUIP_LEGS", new KeyTrigger(KeyInput.KEY_K));
        inputManager.addMapping("EQUIP_BOOTS", new KeyTrigger(KeyInput.KEY_L));
        inputManager.addMapping("OPEN_CHAT", new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("CHEST_TAKE", new KeyTrigger(KeyInput.KEY_Z));
        inputManager.addMapping("CHEST_PUT", new KeyTrigger(KeyInput.KEY_X));

        if (version.survival()) {
            inputManager.addMapping("MOVE_FORWARD", new KeyTrigger(KeyInput.KEY_W));
            inputManager.addMapping("MOVE_BACK", new KeyTrigger(KeyInput.KEY_S));
            inputManager.addMapping("MOVE_LEFT", new KeyTrigger(KeyInput.KEY_A));
            inputManager.addMapping("MOVE_RIGHT", new KeyTrigger(KeyInput.KEY_D));
            inputManager.addMapping("JUMP", new KeyTrigger(KeyInput.KEY_SPACE));
            inputManager.addMapping("LOOK_RIGHT", new MouseAxisTrigger(MouseInput.AXIS_X, true));
            inputManager.addMapping("LOOK_LEFT", new MouseAxisTrigger(MouseInput.AXIS_X, false));
            inputManager.addMapping("LOOK_UP", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
            inputManager.addMapping("LOOK_DOWN", new MouseAxisTrigger(MouseInput.AXIS_Y, false));

            ActionListener movementListener = (name, pressed, tpf) -> {
                if (paused) return;
                switch (name) {
                    case "MOVE_FORWARD" -> moveForward = pressed;
                    case "MOVE_BACK" -> moveBackward = pressed;
                    case "MOVE_LEFT" -> moveLeft = pressed;
                    case "MOVE_RIGHT" -> moveRight = pressed;
                    case "JUMP" -> {
                        if (pressed && onGround) {
                            verticalVelocity = 7f;
                            onGround = false;
                        }
                    }
                    default -> { }
                }
            };
            inputManager.addListener(movementListener,
                    "MOVE_FORWARD", "MOVE_BACK", "MOVE_LEFT", "MOVE_RIGHT", "JUMP");
            AnalogListener lookListener = (name, value, tpf) -> {
                if (paused) return;
                float sensitivity = 2.2f;
                switch (name) {
                    case "LOOK_LEFT" -> yaw += value * sensitivity;
                    case "LOOK_RIGHT" -> yaw -= value * sensitivity;
                    case "LOOK_UP" -> pitch += value * sensitivity;
                    case "LOOK_DOWN" -> pitch -= value * sensitivity;
                    default -> { }
                }
                pitch = Math.max(-1.45f, Math.min(1.45f, pitch));
                cam.setRotation(new Quaternion().fromAngles(pitch, yaw, 0f));
            };
            inputManager.addListener(lookListener, "LOOK_LEFT", "LOOK_RIGHT", "LOOK_UP", "LOOK_DOWN");
        }

        ActionListener actions = (name, isPressed, tpf) -> {
            if (name.equals(BREAK)) {
                if (isPressed) beginMining();
                else cancelMining();
                return;
            }
            if (!isPressed) {
                return;
            }
            if (paused && !name.equals("TOGGLE_PAUSE") && !name.equals("SAVE") && !name.equals("EXIT_GAME")) return;
            switch (name) {
                case PLACE -> placeTargetBlock();
                case SAVE -> saveWorld();
                case "TOGGLE_CAMERA" -> toggleCameraView();
                case "CYCLE_PLAYER_STYLE" -> cyclePlayerStyle();
                case "PET_COMMAND" -> commandNearestPet();
                case "TOGGLE_MAP" -> toggleMap();
                case EAT -> eatFood();
                case TALK -> interactCreature();
                case "RENDER_DISTANCE" -> cycleRenderDistance();
                case "TOGGLE_CULLING" -> toggleCulling();
                case "WEATHER" -> toggleWeather();
                case "TOGGLE_INVENTORY" -> toggleInventory();
                case "TOGGLE_CRAFTING" -> toggleCrafting();
                case "CYCLE_TOOL" -> cycleTool();
                case "USE_UTILITY" -> useSelectedUtility();
                case "FISHING_CAST" -> castFishingRod();
                case "TOGGLE_ACHIEVEMENTS" -> toggleAchievements();
                case "CRAFT_PAGE_PREV" -> { if (chestOpen) changeChestPage(-1); else if (inventoryOpen) changeInventoryPage(-1); else changeCraftPage(-1); }
                case "CRAFT_PAGE_NEXT" -> { if (chestOpen) changeChestPage(1); else if (inventoryOpen) changeInventoryPage(1); else changeCraftPage(1); }
                case "EQUIP_HELMET" -> toggleArmor(ArmorSlot.HELMET);
                case "EQUIP_CHEST" -> toggleArmor(ArmorSlot.CHESTPLATE);
                case "EQUIP_LEGS" -> toggleArmor(ArmorSlot.LEGGINGS);
                case "EQUIP_BOOTS" -> toggleArmor(ArmorSlot.BOOTS);
                case "OPEN_CHAT" -> openMultiplayerChat();
                case "CHEST_TAKE" -> transferSelectedChestItem(true);
                case "CHEST_PUT" -> transferSelectedChestItem(false);
                case "TOGGLE_PAUSE" -> setPaused(!paused);
                case "EXIT_GAME" -> {
                    if (paused) stop();
                    else { statusMessage = "Нажми ESC, чтобы открыть меню паузы, затем F10 для выхода."; updateHud(); }
                }
                case "SLOT_1" -> { if (chestOpen) selectChestSlot(0); else if (inventoryOpen) setInventoryPage(0); else if (furnaceOpen) smeltInFurnace(0); else if (tradeOpen) tradeWithVillager(0); else if (craftingOpen && 1 <= craftingRecipes.size()) craftRecipe(0); else if (!craftingOpen && !achievementsOpen) selectSlot(0); }
                case "SLOT_2" -> { if (chestOpen) selectChestSlot(1); else if (inventoryOpen) setInventoryPage(1); else if (furnaceOpen) smeltInFurnace(1); else if (tradeOpen) tradeWithVillager(1); else if (craftingOpen && 2 <= craftingRecipes.size()) craftRecipe(1); else if (!craftingOpen) selectSlot(1); }
                case "SLOT_3" -> { if (chestOpen) selectChestSlot(2); else  if (furnaceOpen) smeltInFurnace(2); else if (tradeOpen) tradeWithVillager(2); else if (craftingOpen && 3 <= craftingRecipes.size()) craftRecipe(2); else if (!craftingOpen) selectSlot(2); }
                case "SLOT_4" -> { if (chestOpen) selectChestSlot(3); else  if (furnaceOpen) smeltInFurnace(3); else if (tradeOpen) tradeWithVillager(3); else if (craftingOpen && 4 <= craftingRecipes.size()) craftRecipe(3); else if (!craftingOpen) selectSlot(3); }
                case "SLOT_5" -> { if (chestOpen) selectChestSlot(4); else  if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 5 <= craftingRecipes.size()) craftRecipe(4); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(4); }
                case "SLOT_6" -> { if (chestOpen) selectChestSlot(5); else  if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 6 <= craftingRecipes.size()) craftRecipe(5); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(5); }
                case "SLOT_7" -> { if (chestOpen) selectChestSlot(6); else  if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 7 <= craftingRecipes.size()) craftRecipe(6); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(6); }
                case "SLOT_8" -> { if (chestOpen) selectChestSlot(7); else  if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 8 <= craftingRecipes.size()) craftRecipe(7); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(7); }
                case "SLOT_9" -> { if (chestOpen) selectChestSlot(8); else  if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 9 <= craftingRecipes.size()) craftRecipe(8); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(8); }
                default -> { }
            }
        };
        AnalogListener hotbarWheel = (name, value, tpf) -> {
            if (paused || !worldReady || craftingOpen || inventoryOpen || chestOpen || furnaceOpen || tradeOpen) return;
            if (name.equals("HOTBAR_SCROLL_UP")) cycleHotbar(-1);
            else if (name.equals("HOTBAR_SCROLL_DOWN")) cycleHotbar(1);
        };
        inputManager.addListener(hotbarWheel, "HOTBAR_SCROLL_UP", "HOTBAR_SCROLL_DOWN");

        inputManager.addListener(actions, BREAK, PLACE, SAVE, EAT, "EXIT_GAME", "TOGGLE_PAUSE",
                "SLOT_1", "SLOT_2", "SLOT_3", "SLOT_4", "SLOT_5",
                "SLOT_6", "SLOT_7", "SLOT_8", "SLOT_9", "RENDER_DISTANCE", "TOGGLE_CULLING", "WEATHER", TALK,
                "TOGGLE_INVENTORY", "TOGGLE_CRAFTING", "CYCLE_TOOL", "USE_UTILITY", "FISHING_CAST", "TOGGLE_ACHIEVEMENTS", "CRAFT_PAGE_PREV", "CRAFT_PAGE_NEXT",
                "EQUIP_HELMET", "EQUIP_CHEST", "EQUIP_LEGS", "EQUIP_BOOTS", "TOGGLE_CAMERA", "CYCLE_PLAYER_STYLE", "TOGGLE_MAP",
                "OPEN_CHAT", "CHEST_TAKE", "CHEST_PUT", "PET_COMMAND");
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (paused) return;
        if (runtimeFault) {
            hudRefreshClock += Math.max(0f, tpf);
            if (hudRefreshClock >= 0.5f) {
                hudRefreshClock = 0f;
                updateHud();
            }
            return;
        }
        try {
            updateFrame(tpf);
            updateMining(tpf);
            updateMultiplayer(tpf);
            captureWorldPreviewIfReady();
            checkpointPlayerProfile(false);
        } catch (RuntimeException error) {
            runtimeFault = true;
            runtimeFaultMessage = error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "без описания" : error.getMessage());
            statusMessage = "Игровой цикл остановлен из-за ошибки: " + runtimeFaultMessage;
            loadingMessage = "Аварийная остановка кадра. См. .logs\\latest-run.log";
            GameLog.error("Необработанная ошибка игрового кадра; окно оставлено открытым для диагностики", error);
            updateHud();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Игровой цикл встретил ошибку и приостановлен, чтобы окно не исчезло.\n"
                            + runtimeFaultMessage + "\n\nЖурнал: .logs\\latest-run.log",
                    "Minecraft 3D — ошибка игрового цикла", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void updateFrame(float tpf) {
        if (tpf > 0f) fpsEstimate = fpsEstimate * 0.88f + (1f / tpf) * 0.12f;
        if (version.id().compareTo("0.6.0") >= 0) updateNature(tpf);

        if (worldDataReady) {
            if (!worldPreviewCaptureSetup) {
                setupWorldPreviewCapture();
                worldPreviewCaptureSetup = true;
            }
            if (!modsWorldReady && modLoader != null) {
                modLoader.worldReady();
                modsWorldReady = true;
            }
            if (!worldNodeAttached) {
                rootNode.attachChild(world.getNode());
                creatureManager.sync(world);
                if (petsEnabled) creatureManager.setPetModes(pendingPetModes);
                rootNode.attachChild(creatureManager.getNode());
                natureDecorations.rebuild(world);
                rootNode.attachChild(natureDecorations.getNode());
                GameLog.info("Существа присоединены к сцене: " + creatureManager.size()
                        + "; растений на лугах: " + natureDecorations.size());
                worldNodeAttached = true;
                spawnPosition = world.getSuggestedSpawn();
                if (version.survival()) playerEyePosition = spawnPosition.clone();
                createLoadingPad();
                GameLog.info("Точка появления: " + spawnPosition + "; чанк="
                        + com.blockworld.core.ChunkPos.fromBlock(Math.round(spawnPosition.x), Math.round(spawnPosition.z)));
                if (version.survival()) {
                    cam.setLocation(spawnPosition.clone());
                    cam.setRotation(new Quaternion().fromAngles(pitch, yaw, 0f));
                } else {
                    cam.setLocation(spawnPosition.add(0f, 16f, 26f));
                    cam.lookAt(spawnPosition, Vector3f.UNIT_Y);
                }
            }
            // Build only a few chunk meshes per frame to avoid long freezes on modest PCs.
            try {
                int chunkBudget = gameSettings.chunkBuildsPerFrame();
                world.updateStreaming(cam.getLocation(), worldReady ? chunkBudget : chunkBudget + 2);
            } catch (RuntimeException ex) {
                statusMessage = "Ошибка меша чанка: " + ex.getClass().getSimpleName()
                        + " — " + (ex.getMessage() == null ? "без описания" : ex.getMessage());
                loadingMessage = "Ошибка отрисовки. См. .logs\\latest-run.log";
                GameLog.error("Не удалось построить чанк мира", ex);
                updateHud();
            }
            if (!worldReady && world.isChunkRenderedAt(spawnPosition.x, spawnPosition.z)) {
                if (version.survival()) {
                    cam.setLocation(spawnPosition.clone());
                    cam.setRotation(new Quaternion().fromAngles(pitch, yaw, 0f));
                    onGround = true;
                    verticalVelocity = 0f;
                }
                worldReady = true;
                beginPlayerProfileSession();
                loadingMessage = "Мир загружен";
                if (loadingPad != null) { loadingPad.removeFromParent(); loadingPad = null; }
                statusMessage = "Чанк появления отрисован. Управление готово.";
                GameLog.info("Мир готов: мешей=" + world.getRenderedChunkCount()
                        + ", чанков данных=" + world.getChunkCount());
                updateHud();
            } else if (!worldReady) {
                chunkWaitClock += tpf;
                if (chunkWaitClock >= 5f) {
                    chunkWaitClock = 0f;
                    statusMessage = "Старт: " + Math.round(spawnPosition.x) + ", "
                            + Math.round(spawnPosition.y) + ", " + Math.round(spawnPosition.z)
                            + "; блоков " + world.getBlockCount()
                            + "; чанков данных " + world.getChunkCount()
                            + "; мешей " + world.getRenderedChunkCount()
                            + "; очередь " + world.getPendingChunkCount()
                            + "; меш появления=" + world.isChunkRenderedAt(spawnPosition.x, spawnPosition.z)
                            + (world.getLastMeshError().isBlank() ? "." : "; ошибка: " + world.getLastMeshError());
                    updateHud();
                }
            }
        }

        if (worldReady && agricultureEnabled && version.survival()) updateFarmGrowth(tpf);
        if (worldReady && release100Enabled && version.survival()) {
            fishingCooldown = FishingRules.advanceCooldown(fishingCooldown, tpf);
            biomeDiscoveryClock += Math.max(0f, tpf);
            if (biomeDiscoveryClock >= 2f) {
                biomeDiscoveryClock = 0f;
                BiomeType currentBiome = world.getBiomeAt(Math.round(version.survival() ? playerEyePosition.x : cam.getLocation().x), Math.round(version.survival() ? playerEyePosition.z : cam.getLocation().z));
                if (achievements.discover(currentBiome)) {
                    if (achievements.hasDiscoveredAllBiomes()) unlockAchievement(Achievement.ALL_BIOMES);
                    else saveAchievementsQuietly();
                }
            }
        }
        if (worldReady && modLoader != null) modLoader.update(tpf);
        if (worldReady && creatureManager != null && world != null
                && (multiplayerClient == null || !multiplayerClient.isConnected() || multiplayerClient.isHost())) {
            creatureManager.update(tpf, version.survival() ? playerEyePosition : cam.getLocation(), world);
            float incomingDamage = creatureManager.consumePlayerDamage();
            if (version.survival() && incomingDamage > 0f) {
                float appliedDamage = CombatRules.applyArmor(incomingDamage, equippedArmor);
                health = Math.max(0f, health - appliedDamage);
                statusMessage = String.format(java.util.Locale.ROOT,
                        "Монстр атаковал! Урон %.1f; здоровье %.0f/20.", appliedDamage, health);
                savePlayerDataQuietly();
            }
        } else if (worldReady && creatureManager != null && multiplayerClient != null
                && multiplayerClient.isConnected()) {
            creatureManager.updateNetworkAnimations(tpf);
        }

        if (worldReady && version.survival() && world != null) {
            updateSurvivalMovement(tpf);
            updateSurvivalNeeds(tpf);
            if (thirdPersonView && playerAvatar != null) {
                playerAvatar.update(playerEyePosition, yaw,
                        moveForward || moveBackward || moveLeft || moveRight, tpf);
                updateThirdPersonCamera();
            }
        }

        hudRefreshClock += tpf;
        if (hudRefreshClock >= 0.25f) {
            hudRefreshClock = 0f;
            updateHud();
        }
    }

    private void updateMultiplayer(float tpf) {
        if (multiplayerClient == null) return;

        String event;
        int processed = 0;
        while (processed < 5000 && (event = multiplayerInbox.poll()) != null) {
            processMultiplayerEvent(event);
            processed++;
        }

        if (worldReady && !multiplayerSnapshotStarted && multiplayerClient.isHost()
                && !multiplayerClient.hasServerWorld()) {
            multiplayerSnapshotStarted = true;
            uploadInitialWorldSnapshot();
        }

        if (worldReady && multiplayerSyncReady && multiplayerClient.isConnected()) {
            multiplayerPositionClock += Math.max(0f, tpf);
            if (multiplayerPositionClock >= 0.10f) {
                multiplayerPositionClock = 0f;
                Vector3f position = version.survival() ? playerEyePosition : cam.getLocation();
                try {
                    multiplayerClient.sendPosition(position.x, position.y, position.z, yaw,
                            moveForward || moveBackward || moveLeft || moveRight);
                } catch (IOException error) {
                    multiplayerSyncReady = false;
                    statusMessage = "Сетевая связь потеряна: " + error.getMessage();
                    GameLog.error("Не удалось отправить координаты игрока", error);
                }
            }
            multiplayerStatsClock += Math.max(0f, tpf);
            if (multiplayerStatsClock >= 1f) {
                multiplayerStatsClock = 0f;
                try { multiplayerClient.sendPlayerStats(health, hunger, foodCount); }
                catch (IOException error) {
                    statusMessage = "Не удалось синхронизировать здоровье и голод: " + error.getMessage();
                    GameLog.error("Не удалось отправить показатели игрока", error);
                }
            }
            if (multiplayerClient.isHost()) {
                creatureSyncClock += Math.max(0f, tpf);
                if (creatureSyncClock >= 0.25f) {
                    creatureSyncClock = 0f;
                    sendCreatureSnapshot();
                }
            }
        }

        for (RemotePlayerState remote : remotePlayers.values()) {
            if (remote.hasPosition && remote.avatar != null) {
                remote.avatar.update(remote.position, remote.yaw, remote.moving, tpf);
            }
        }
    }

    private void uploadInitialWorldSnapshot() {
        Map<BlockPos, BlockType> blocks = world.snapshotBlocks();
        List<com.blockworld.core.WorldCreature> creatures = world.getCreatures();
        long seed = world.getSeed();
        boolean active = world.hasActiveBiomes();
        boolean extended = world.hasExtendedBiomes();
        int radius = world.getWorldRadius();
        statusMessage = "Передача мира на сервер (" + blocks.size() + " блоков)...";
        Thread sender = new Thread(() -> {
            try {
                multiplayerClient.sendWorldSnapshot(seed, active, extended, radius, blocks, creatures);
                GameLog.info("Отправлен снимок сетевого мира: seed=" + seed + ", blocks=" + blocks.size());
            } catch (IOException | RuntimeException error) {
                GameLog.error("Не удалось передать снимок мира на сервер", error);
                multiplayerInbox.add("NETWORK_ERROR " + error.getMessage());
            }
        }, "BlockWorld-World-Snapshot-Sender");
        sender.setDaemon(true);
        sender.start();
    }

    private void sendCreatureSnapshot() {
        List<com.blockworld.core.WorldCreature> snapshot = world.getCreatures();
        Thread sender = new Thread(() -> {
            try {
                multiplayerClient.sendCreatureSnapshot(snapshot);
            } catch (IOException | RuntimeException error) {
                multiplayerInbox.add("NETWORK_ERROR " + error.getMessage());
            }
        }, "BlockWorld-Creature-Snapshot-Sender");
        sender.setDaemon(true);
        sender.start();
    }

    private List<ItemType> chestItemSlots() {
        List<ItemType> result = new ArrayList<>();
        for (ItemType item : ItemType.values()) {
            if (sharedChestContents.getOrDefault(item, 0) > 0
                    || craftedItems.getOrDefault(item, 0) > 0) result.add(item);
        }
        return result;
    }

    private void rebuildChestSlots() {
        List<ItemType> slots = chestItemSlots();
        if (selectedChestItem != null && !slots.contains(selectedChestItem)) selectedChestItem = null;
        if (selectedChestItem == null && !slots.isEmpty()) selectedChestItem = slots.get(0);
        int pages = Math.max(1, (slots.size() + 8) / 9);
        chestPage = Math.max(0, Math.min(pages - 1, chestPage));
    }

    private void selectChestSlot(int slot) {
        if (!chestOpen || slot < 0 || slot >= 9) return;
        List<ItemType> slots = chestItemSlots();
        int index = chestPage * 9 + slot;
        if (index >= slots.size()) return;
        selectedChestItem = slots.get(index);
        statusMessage = "Выбран предмет: " + selectedChestItem.displayName()
                + ". Z — забрать, X — положить.";
        updateInventoryUi();
        updateHud();
    }

    private void changeChestPage(int delta) {
        if (!chestOpen) return;
        int pages = Math.max(1, (chestItemSlots().size() + 8) / 9);
        chestPage = Math.max(0, Math.min(pages - 1, chestPage + delta));
        rebuildChestSlots();
        updateInventoryUi();
    }

    private void transferSelectedChestItem(boolean take) {
        if (!chestOpen || openChestPosition == null || selectedChestItem == null
                || chestOperationPending || !multiplayerSyncReady) return;
        try {
            if (take) multiplayerClient.takeFromChest(openChestPosition, selectedChestItem, 1);
            else multiplayerClient.putIntoChest(openChestPosition, selectedChestItem, 1);
            chestOperationPending = true;
            statusMessage = take ? "Забираю предмет из общего сундука..." : "Кладу предмет в общий сундук...";
            updateHud();
        } catch (IOException | IllegalArgumentException error) {
            statusMessage = "Не удалось изменить сундук: " + error.getMessage();
            updateHud();
        }
    }

    private void openMultiplayerChat() {
        if (multiplayerClient == null || !multiplayerClient.isConnected()) return;
        SwingUtilities.invokeLater(() -> {
            String message = JOptionPane.showInputDialog(null,
                    "Сообщение или обмен предметами:\n/trade Ник ПРЕДМЕТ_ОТДАЮ Количество ПРЕДМЕТ_ПОЛУЧАЮ Количество",
                    "Minecraft 3D — Чат", JOptionPane.PLAIN_MESSAGE);
            if (message == null || message.isBlank()) return;
            try { multiplayerClient.sendChat(message); }
            catch (IOException error) {
                multiplayerInbox.add("NETWORK_ERROR " + error.getMessage());
            }
        });
    }

    private void refreshMultiplayerReadiness() {
        multiplayerSyncReady = multiplayerClient != null && multiplayerClient.isConnected()
                && multiplayerWorldReady && multiplayerProfileReady;
        if (multiplayerSyncReady) {
            statusMessage = "Сеть готова: общий мир и личный инвентарь синхронизированы.";
            updateHud();
        }
    }

    private void sendMultiplayerProfileSnapshot() {
        if (multiplayerClient == null || !multiplayerClient.isConnected()) return;
        try {
            multiplayerClient.sendPlayerProfile(inventory, craftedItems, equippedArmor, health, hunger, foodCount);
        } catch (IOException | IllegalArgumentException error) {
            statusMessage = "Не удалось передать инвентарь серверу: " + error.getMessage();
            GameLog.error("Не удалось отправить серверный профиль игрока", error);
            updateHud();
        }
    }

    private void applyMultiplayerProfile() {
        if (pendingProfileBlocks == null || pendingProfileItems == null || pendingProfileArmor == null
                || receivedProfileBlocks != expectedProfileBlocks
                || receivedProfileItems != expectedProfileItems
                || pendingProfileArmor.size() != expectedProfileArmor
                || expectedProfileBlocks != BlockType.values().length
                || expectedProfileItems != ItemType.values().length) {
            throw new IllegalArgumentException("Неполный профиль игрока");
        }
        inventory.clear();
        inventory.putAll(pendingProfileBlocks);
        craftedItems.clear();
        craftedItems.putAll(pendingProfileItems);
        equippedArmor.clear();
        equippedArmor.addAll(pendingProfileArmor);
        health = pendingProfileHealth;
        hunger = pendingProfileHunger;
        foodCount = pendingProfileFoodCount;
        pendingProfileBlocks = null;
        pendingProfileItems = null;
        pendingProfileArmor = null;
        multiplayerProfileReady = true;
        try {
            if (version.survival()) PlayerDataStore.save(saveFile, inventory, craftedItems, equippedArmor);
        } catch (IOException error) {
            GameLog.error("Не удалось обновить локальный кэш серверного инвентаря", error);
        }
        refreshMultiplayerReadiness();
        updateInventoryUi();
    }

    private void processMultiplayerEvent(String event) {
        if (event == null || event.isBlank()) return;
        try {
            String[] parts = event.split(" ");
            switch (parts[0]) {
                case "PLAYER_JOIN" -> {
                    if (parts.length >= 3) {
                        int id = Integer.parseInt(parts[1]);
                        if (id != multiplayerClient.playerId()) {
                            String name = new String(java.util.Base64.getDecoder().decode(parts[2]),
                                    java.nio.charset.StandardCharsets.UTF_8);
                            RemotePlayerState remote = remotePlayers.computeIfAbsent(id,
                                    ignored -> createRemotePlayer(name));
                            remote.name = name;
                            if (remote.avatar != null) remote.avatar.setDisplayName(assetManager, name);
                        }
                    }
                }
                case "PLAYER_POS" -> {
                    if (parts.length == 7) {
                        int id = Integer.parseInt(parts[1]);
                        if (id != multiplayerClient.playerId()) {
                            RemotePlayerState remote = remotePlayers.computeIfAbsent(id,
                                    ignored -> createRemotePlayer("Игрок " + id));
                            float x = Float.parseFloat(parts[2]);
                            float y = Float.parseFloat(parts[3]);
                            float z = Float.parseFloat(parts[4]);
                            float rotation = Float.parseFloat(parts[5]);
                            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                                    && Float.isFinite(rotation) && Math.abs(x) < 100_000
                                    && Math.abs(z) < 100_000 && y > -512 && y < 2048) {
                                remote.position.set(x, y, z);
                                remote.yaw = rotation;
                                remote.moving = Boolean.parseBoolean(parts[6]);
                                remote.hasPosition = true;
                            }
                        }
                    }
                }
                case "PLAYER_LEAVE" -> {
                    if (parts.length == 2) {
                        int id = Integer.parseInt(parts[1]);
                        RemotePlayerState remote = remotePlayers.remove(id);
                        if (remote != null && remote.avatar != null) remote.avatar.detach();
                    }
                }
                case "WORLD_CREATURES_BEGIN" -> {
                    if (parts.length == 2) {
                        pendingNetworkCreatureCount = Integer.parseInt(parts[1]);
                        if (pendingNetworkCreatureCount < 0 || pendingNetworkCreatureCount > 256)
                            throw new IllegalArgumentException("Слишком много существ в мире");
                        pendingNetworkCreatures.clear();
                    }
                }
                case "WORLD_CREATURE" -> {
                    if (parts.length == 7 && pendingNetworkCreatures.size() < pendingNetworkCreatureCount) {
                        long id = Long.parseLong(parts[1]);
                        var state = new com.blockworld.core.WorldCreature(id,
                                com.blockworld.core.CreatureType.valueOf(parts[2]),
                                Float.parseFloat(parts[3]), Float.parseFloat(parts[4]),
                                Float.parseFloat(parts[5]), Float.parseFloat(parts[6]));
                        pendingNetworkCreatures.put(id, state);
                    }
                }
                case "WORLD_CREATURES_END" -> {
                    if (pendingNetworkCreatures.size() != pendingNetworkCreatureCount)
                        throw new IllegalArgumentException("Неполный список существ");
                }
                case "CREATURES_BEGIN" -> {
                    if (parts.length == 2) {
                        pendingNetworkCreatureCount = Integer.parseInt(parts[1]);
                        if (pendingNetworkCreatureCount < 0 || pendingNetworkCreatureCount > 256)
                            throw new IllegalArgumentException("Слишком много существ в снимке");
                        pendingNetworkCreatures.clear();
                    }
                }
                case "CREATURE" -> {
                    if (parts.length == 7 && pendingNetworkCreatures.size() < pendingNetworkCreatureCount) {
                        long id = Long.parseLong(parts[1]);
                        var state = new com.blockworld.core.WorldCreature(id,
                                com.blockworld.core.CreatureType.valueOf(parts[2]),
                                Float.parseFloat(parts[3]), Float.parseFloat(parts[4]),
                                Float.parseFloat(parts[5]), Float.parseFloat(parts[6]));
                        pendingNetworkCreatures.put(id, state);
                    }
                }
                case "CREATURES_END" -> {
                    if (pendingNetworkCreatures.size() != pendingNetworkCreatureCount)
                        throw new IllegalArgumentException("Неполный снимок существ");
                    if (creatureManager != null && world != null) {
                        creatureManager.applyNetworkSnapshot(new ArrayList<>(pendingNetworkCreatures.values()), world);
                    }
                }
                case "HOST_CHANGED" -> {
                    statusMessage = multiplayerClient.isHost()
                            ? "Ты стал ведущим сервера. Управление мобами и сохранением мира передано тебе."
                            : "Ведущий сервера изменился.";
                    updateHud();
                }
                case "WORLD_SAVE_ERROR" -> {
                    statusMessage = "Не удалось сохранить общий мир на сервере. Проверь папку servers.";
                    updateHud();
                }
                case "PROFILE_EMPTY" -> {
                    if (!multiplayerProfileInitialized) {
                        multiplayerProfileInitialized = true;
                        statusMessage = "Новый профиль игрока. Сохраняю стартовый инвентарь на сервере...";
                        sendMultiplayerProfileSnapshot();
                        updateHud();
                    }
                }
                case "PROFILE_BEGIN" -> {
                    if (parts.length == 7) {
                        expectedProfileBlocks = Integer.parseInt(parts[1]);
                        expectedProfileItems = Integer.parseInt(parts[2]);
                        expectedProfileArmor = Integer.parseInt(parts[3]);
                        pendingProfileHealth = Float.parseFloat(parts[4]);
                        pendingProfileHunger = Float.parseFloat(parts[5]);
                        pendingProfileFoodCount = Integer.parseInt(parts[6]);
                        if (!Float.isFinite(pendingProfileHealth) || pendingProfileHealth < 0f || pendingProfileHealth > 20f
                                || !Float.isFinite(pendingProfileHunger) || pendingProfileHunger < 0f || pendingProfileHunger > 20f
                                || pendingProfileFoodCount < 0 || pendingProfileFoodCount > 1_000_000
                                || expectedProfileBlocks != BlockType.values().length
                                || expectedProfileItems != ItemType.values().length
                                || expectedProfileArmor < 0 || expectedProfileArmor > ArmorSlot.values().length)
                            throw new IllegalArgumentException("Неверный размер профиля игрока");
                        pendingProfileBlocks = new EnumMap<>(BlockType.class);
                        pendingProfileItems = new EnumMap<>(ItemType.class);
                        pendingProfileArmor = EnumSet.noneOf(ArmorSlot.class);
                        receivedProfileBlocks = 0;
                        receivedProfileItems = 0;
                        multiplayerProfileInitialized = true;
                    }
                }
                case "PROFILE_BLOCK" -> {
                    if (parts.length == 3 && pendingProfileBlocks != null) {
                        BlockType type = BlockType.valueOf(parts[1]);
                        int count = Integer.parseInt(parts[2]);
                        if (count < 0 || count > 1_000_000 || pendingProfileBlocks.put(type, count) != null)
                            throw new IllegalArgumentException("Неверное количество блока в профиле");
                        receivedProfileBlocks++;
                    }
                }
                case "PROFILE_ITEM" -> {
                    if (parts.length == 3 && pendingProfileItems != null) {
                        ItemType type = ItemType.valueOf(parts[1]);
                        int count = Integer.parseInt(parts[2]);
                        if (count < 0 || count > 1_000_000 || pendingProfileItems.put(type, count) != null)
                            throw new IllegalArgumentException("Неверное количество предмета в профиле");
                        receivedProfileItems++;
                    }
                }
                case "PROFILE_ARMOR" -> {
                    if (parts.length == 2 && pendingProfileArmor != null
                            && !pendingProfileArmor.add(ArmorSlot.valueOf(parts[1])))
                        throw new IllegalArgumentException("Повтор слота брони в профиле");
                }
                case "PROFILE_END" -> applyMultiplayerProfile();
                case "CHEST_BEGIN" -> {
                    if (parts.length == 5) {
                        openChestPosition = new BlockPos(Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        pendingChestItems = Integer.parseInt(parts[4]);
                        if (pendingChestItems < 0 || pendingChestItems > ItemType.values().length)
                            throw new IllegalArgumentException("Слишком много предметов в сундуке");
                        receivedChestItems = 0;
                        sharedChestContents.clear();
                    }
                }
                case "CHEST_ITEM" -> {
                    if (parts.length == 3 && openChestPosition != null
                            && receivedChestItems < pendingChestItems) {
                        ItemType item = ItemType.valueOf(parts[1]);
                        int count = Integer.parseInt(parts[2]);
                        if (count < 1 || count > 1_000_000 || sharedChestContents.put(item, count) != null)
                            throw new IllegalArgumentException("Неверное содержимое сундука");
                        receivedChestItems++;
                    }
                }
                case "CHEST_END" -> {
                    if (receivedChestItems != pendingChestItems || openChestPosition == null)
                        throw new IllegalArgumentException("Неполный список общего сундука");
                    chestOpen = true;
                    chestOperationPending = false;
                    inventoryOpen = craftingOpen = furnaceOpen = tradeOpen = achievementsOpen = false;
                    chestPage = 0;
                    rebuildChestSlots();
                    showInventoryOverlay(true);
                    statusMessage = "Общий сундук открыт. 1–9 — выбрать предмет, Z — забрать, X — положить, F — закрыть.";
                    updateInventoryUi();
                    updateHud();
                }
                case "CHEST_ACTION_RESULT" -> {
                    chestOperationPending = false;
                    if (parts.length == 2 && Boolean.parseBoolean(parts[1]))
                        statusMessage = "Общий сундук обновлён.";
                    updateInventoryUi();
                    updateHud();
                }
                case "CHEST_ERROR" -> {
                    chestOperationPending = false;
                    statusMessage = event.substring("CHEST_ERROR ".length());
                    updateInventoryUi();
                    updateHud();
                }
                case "CHAT_TEXT" -> {
                    String message = new String(java.util.Base64.getDecoder().decode(parts[1]),
                            java.nio.charset.StandardCharsets.UTF_8);
                    statusMessage = "Чат: " + message;
                    updateHud();
                }
                case "PLAYER_STATS" -> {
                    if (parts.length == 5) {
                        int id = Integer.parseInt(parts[1]);
                        float remoteHealth = Float.parseFloat(parts[2]);
                        float remoteHunger = Float.parseFloat(parts[3]);
                        int remoteFood = Integer.parseInt(parts[4]);
                        if (Float.isFinite(remoteHealth) && remoteHealth >= 0f && remoteHealth <= 20f
                                && Float.isFinite(remoteHunger) && remoteHunger >= 0f && remoteHunger <= 20f
                                && remoteFood >= 0 && remoteFood <= 1_000_000) {
                            RemotePlayerState remote = remotePlayers.get(id);
                            if (remote != null) {
                                remote.health = remoteHealth;
                                remote.hunger = remoteHunger;
                                remote.foodCount = remoteFood;
                            }
                        }
                    }
                }
                case "PROFILE_SAVED" -> {
                    multiplayerProfileReady = true;
                    refreshMultiplayerReadiness();
                }
                case "PROFILE_ERROR" -> {
                    multiplayerProfileReady = false;
                    multiplayerSyncReady = false;
                    multiplayerCraftPending = false;
                    multiplayerTradePending = false;
                    statusMessage = "Ошибка серверного профиля. Проверь имя игрока и сохранение сервера.";
                    updateHud();
                }
                case "CRAFT_RESULT" -> {
                    if (parts.length == 3) {
                        multiplayerCraftPending = false;
                        int number = Integer.parseInt(parts[1]);
                        boolean success = Boolean.parseBoolean(parts[2]);
                        CraftingRecipe recipe = craftingRecipes.stream().filter(entry -> entry.number() == number)
                                .findFirst().orElse(null);
                        statusMessage = success && recipe != null
                                ? "Сервер создал: " + recipe.result().displayName() + " ×" + recipe.resultCount() + "."
                                : "Сервер отклонил крафт: не хватает ресурсов или рецепт недоступен.";
                        if (success && release100Enabled) unlockAchievement(Achievement.FIRST_CRAFT);
                        updateInventoryUi();
                        updateHud();
                    }
                }
                case "TRADE_RESULT" -> {
                    if (parts.length == 3) {
                        multiplayerTradePending = false;
                        int number = Integer.parseInt(parts[1]);
                        boolean success = Boolean.parseBoolean(parts[2]);
                        TradeRecipe offer = tradeRecipes.stream().filter(entry -> entry.number() == number)
                                .findFirst().orElse(null);
                        statusMessage = success && offer != null
                                ? "Сервер завершил обмен: " + offer.description() + "."
                                : "Сервер отклонил обмен: не хватает предметов.";
                        if (success) unlockAchievement(Achievement.FIRST_TRADE);
                        updateInventoryUi();
                        updateHud();
                    }
                }
                case "WORLD_BEGIN" -> {
                    if (parts.length == 6) {
                        pendingNetworkSeed = Long.parseLong(parts[1]);
                        pendingNetworkActiveBiomes = Boolean.parseBoolean(parts[2]);
                        pendingNetworkExtendedBiomes = Boolean.parseBoolean(parts[3]);
                        pendingNetworkRadius = Integer.parseInt(parts[4]);
                        pendingNetworkBlockCount = Integer.parseInt(parts[5]);
                        if (pendingNetworkBlockCount < 0 || pendingNetworkBlockCount > 1_000_000) {
                            pendingNetworkWorld = null;
                            throw new IllegalArgumentException("Снимок мира слишком большой");
                        }
                        pendingNetworkWorld = new HashMap<>(Math.max(16, pendingNetworkBlockCount));
                    }
                }
                case "WORLD_BLOCK" -> {
                    if (parts.length == 5 && pendingNetworkWorld != null
                            && pendingNetworkWorld.size() < pendingNetworkBlockCount) {
                        BlockPos pos = new BlockPos(Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        BlockType type = BlockType.valueOf(parts[4]);
                        pendingNetworkWorld.put(pos, type);
                    }
                }
                case "WORLD_END" -> {
                    if (pendingNetworkWorld != null
                            && pendingNetworkWorld.size() == pendingNetworkBlockCount && world != null) {
                        world.replaceBlockSnapshot(pendingNetworkSeed, pendingNetworkActiveBiomes,
                                pendingNetworkExtendedBiomes, pendingNetworkRadius, pendingNetworkWorld);
                        farmGrowth.keySet().removeIf(position -> world.getBlock(position) != BlockType.WHEAT_CROP);
                        world.replaceCreatureSnapshot(new ArrayList<>(pendingNetworkCreatures.values()));
                        if (creatureManager != null) creatureManager.sync(world);
                        pendingNetworkWorld = null;
                        multiplayerWorldReady = true;
                        refreshMultiplayerReadiness();
                        Vector3f spawn = world.getSuggestedSpawn();
                        if (version.survival()) {
                            playerEyePosition = spawn.clone();
                            cam.setLocation(spawn.clone());
                            onGround = true;
                            verticalVelocity = 0f;
                        } else {
                            cam.setLocation(spawn.add(0f, 16f, 26f));
                            cam.lookAt(spawn, Vector3f.UNIT_Y);
                        }
                        GameLog.info("Получен снимок общего мира: seed=" + pendingNetworkSeed
                                + ", blocks=" + pendingNetworkBlockCount);
                        statusMessage = "Мир сервера синхронизирован. Игроки и блоки в сети.";
                        updateHud();
                    } else {
                        pendingNetworkWorld = null;
                        statusMessage = "Не удалось синхронизировать мир: неполный снимок.";
                        GameLog.error("Получен неполный снимок сетевого мира.", new IllegalStateException("Incomplete multiplayer world snapshot"));
                    }
                }
                case "WORLD_ACCEPTED" -> {
                    multiplayerWorldReady = true;
                    refreshMultiplayerReadiness();
                    statusMessage = "Мир отправлен серверу. Ожидаю синхронизацию личного профиля.";
                    updateHud();
                }
                case "BLOCK_ADD" -> {
                    if (parts.length == 5 && world != null && worldReady) {
                        BlockPos pos = new BlockPos(Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        BlockType type = BlockType.valueOf(parts[4]);
                        BlockType current = world.getBlock(pos);
                        if (current != type) {
                            if (current != null) world.removeBlock(pos);
                            world.addBlock(pos, type);
                        }
                    }
                }
                case "BLOCK_REMOVE" -> {
                    if (parts.length == 4 && world != null && worldReady) {
                        world.removeBlock(new BlockPos(Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                    }
                }
                case "DISCONNECTED" -> {
                    multiplayerSyncReady = false;
                    multiplayerWorldReady = false;
                    multiplayerProfileReady = false;
                    chestOpen = false;
                    chestOperationPending = false;
                    multiplayerCraftPending = false;
                    multiplayerTradePending = false;
                    for (RemotePlayerState remote : remotePlayers.values()) {
                        if (remote.avatar != null) remote.avatar.detach();
                    }
                    remotePlayers.clear();
                    statusMessage = "Соединение с сервером потеряно. Игра продолжится локально.";
                    updateHud();
                }
                case "NETWORK_ERROR" -> {
                    multiplayerSyncReady = false;
                    statusMessage = "Ошибка сетевой передачи: " + event.substring("NETWORK_ERROR ".length());
                    updateHud();
                }
                default -> { }
            }
        } catch (RuntimeException error) {
            GameLog.error("Некорректное сетевое событие: " + event, error);
        }
    }

    private RemotePlayerState createRemotePlayer(String name) {
        PlayerAvatar avatar = new PlayerAvatar(assetManager);
        avatar.setDisplayName(assetManager, name);
        avatar.attach(rootNode);
        return new RemotePlayerState(name, avatar);
    }

    private void sendMultiplayerBlockAdd(BlockPos pos, BlockType type) {
        if (multiplayerClient == null || !multiplayerSyncReady) return;
        try {
            multiplayerClient.sendBlockAdd(pos, type);
        } catch (IOException error) {
            statusMessage = "Не удалось отправить блок на сервер.";
            GameLog.error("Ошибка синхронизации установки блока", error);
        }
    }

    private void sendMultiplayerBlockRemove(BlockPos pos) {
        if (multiplayerClient == null || !multiplayerSyncReady) return;
        try {
            multiplayerClient.sendBlockRemove(pos);
        } catch (IOException error) {
            statusMessage = "Не удалось отправить изменение блока на сервер.";
            GameLog.error("Ошибка синхронизации разрушения блока", error);
        }
    }

    private static final class RemotePlayerState {
        String name;
        final PlayerAvatar avatar;
        final Vector3f position = new Vector3f();
        float yaw;
        boolean moving;
        boolean hasPosition;
        float health = 20f;
        float hunger = 20f;
        int foodCount;

        RemotePlayerState(String name, PlayerAvatar avatar) {
            this.name = name;
            this.avatar = avatar;
        }
    }

    private void setPaused(boolean value) {
        if (paused == value || guiNode == null) return;
        // Persist the active interval before pausing; paused wall-clock time is not play time.
        if (value) {
            checkpointPlayerProfile(true);
            cancelMining();
        }
        if (value && chestOpen) { chestOpen = false; chestOperationPending = false; }
        paused = value;
        if (profileSessionStarted) profileCheckpointNanos = System.nanoTime();
        if (paused) {
            inventoryOpen = false;
            craftingOpen = false;
            furnaceOpen = false;
            tradeOpen = false;
            achievementsOpen = false;
            showInventoryOverlay(false);
            moveForward = moveBackward = moveLeft = moveRight = false;
            if (flyCam != null) flyCam.setEnabled(false);
            inputManager.setCursorVisible(true);
            if (pausePanel.getParent() == null) guiNode.attachChild(pausePanel);
            if (pauseText.getParent() == null) guiNode.attachChild(pauseText);
            statusMessage = "Игра на паузе.";
        } else {
            guiNode.detachChild(pausePanel);
            guiNode.detachChild(pauseText);
            inputManager.setCursorVisible(false);
            if (!version.survival() && flyCam != null) flyCam.setEnabled(true);
            statusMessage = "Игра продолжается.";
        }
        updateHud();
    }

    /** A visible temporary platform proves the 3D scene is rendering while chunks load. */
    private void createLoadingPad() {
        if (loadingPad != null) loadingPad.removeFromParent();
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.08f, 0.72f, 0.92f, 1f));
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        Quad quad = new Quad(12f, 12f);
        loadingPad = new Geometry("WorldLoadingPad", quad);
        loadingPad.setMaterial(material);
        loadingPad.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        // Suggested spawn is 0.68 units above the top face of its surface block.
        loadingPad.setLocalTranslation(spawnPosition.x - 6f, spawnPosition.y - 0.62f,
                spawnPosition.z + 6f);
        loadingPad.setQueueBucket(RenderQueue.Bucket.Opaque);
        rootNode.attachChild(loadingPad);
    }

    private void toggleCulling() {
        twoSidedRendering = !twoSidedRendering;
        world.setTwoSidedRendering(twoSidedRendering);
        statusMessage = twoSidedRendering
                ? "Двусторонняя отрисовка граней включена (диагностика)."
                : "Отсечение обратных граней включено (быстрее).";
        updateHud();
    }

    private void updateNature(float tpf) {
        dayClock = (dayClock + tpf / 240f) % 1f;
        float sunHeight = FastMath.sin(dayClock * FastMath.TWO_PI);
        float daylight = FastMath.clamp((sunHeight + 0.12f) / 0.72f, 0.12f, 1f);
        float warmth = raining ? 0.58f : 1f;
        natureSun.setDirection(new Vector3f(FastMath.cos(dayClock * FastMath.TWO_PI),
                -Math.max(0.12f, sunHeight), -0.35f).normalizeLocal());
        natureSun.setColor(new ColorRGBA(daylight * warmth, daylight * warmth, daylight, 1f));
        float sky = raining ? 0.34f : 0.48f * daylight + 0.025f;
        viewPort.setBackgroundColor(new ColorRGBA(sky, sky + (raining ? 0.035f : 0.24f * daylight),
                sky + (raining ? 0.055f : 0.46f * daylight), 1f));
    }

    private void toggleWeather() {
        raining = !raining;
        statusMessage = raining ? "Погода: дождливая. Нажми R, чтобы очистить небо."
                : "Погода: ясно.";
        updateHud();
    }

    private void cycleRenderDistance() {
        renderDistanceIndex = (renderDistanceIndex + 1) % RENDER_DISTANCES.length;
        int distance = RENDER_DISTANCES[renderDistanceIndex];
        world.setRenderDistanceChunks(distance);
        try {
            GameSettings.save(new GameSettings(gameSettings.width(), gameSettings.height(), gameSettings.fullscreen(),
                    gameSettings.vsync(), distance, gameSettings.fov(),
                    gameSettings.vegetationDensity(), gameSettings.chunkBuildsPerFrame(), gameSettings.fpsLimit()));
        } catch (IOException ex) { GameLog.error("Не удалось сохранить дальность прорисовки", ex); }
        statusMessage = "Дальность прорисовки: " + distance + " чанков. Для слабого ПК выбери 3.";
        updateHud();
    }

    private void updateSurvivalMovement(float tpf) {
        Vector3f direction = new Vector3f();
        Vector3f forward = PlayerCamera.forwardDirection(yaw);
        Vector3f left = new Vector3f(-forward.z, 0f, forward.x);
        if (moveForward) direction.addLocal(forward);
        if (moveBackward) direction.subtractLocal(forward);
        if (moveLeft) direction.addLocal(left);
        if (moveRight) direction.subtractLocal(left);
        if (direction.lengthSquared() > 0f) {
            direction.normalizeLocal().multLocal(5.2f * tpf);
        }

        Vector3f location = playerEyePosition.clone();
        location.addLocal(direction.x, 0f, direction.z);
        float minWorldX = world.getMinBlockX() + 1.5f;
        float maxWorldX = world.getMaxBlockX() - 1.5f;
        float minWorldZ = world.getMinBlockZ() + 1.5f;
        float maxWorldZ = world.getMaxBlockZ() - 1.5f;
        location.x = Math.max(minWorldX, Math.min(maxWorldX, location.x));
        location.z = Math.max(minWorldZ, Math.min(maxWorldZ, location.z));
        int groundX = Math.round(location.x);
        int groundZ = Math.round(location.z);
        float groundEyeY = world.highestBlockY(groundX, groundZ) + 1.68f;

        boolean wasOnGround = onGround;
        float previousY = location.y;
        if (wasOnGround || Float.isNaN(fallStartY)) fallStartY = previousY;
        verticalVelocity -= 19f * tpf;
        location.y += verticalVelocity * tpf;
        if (location.y <= groundEyeY) {
            if (!wasOnGround) applyLandingImpact(Math.max(0f, fallStartY - groundEyeY));
            location.y = groundEyeY;
            verticalVelocity = 0f;
            onGround = true;
            fallStartY = groundEyeY;
        } else {
            onGround = false;
        }
        playerEyePosition.set(location);
        if (!thirdPersonView) cam.setLocation(location);
    }

    private void toggleMap() {
        if (!explorationEnabled) {
            statusMessage = "Карта и компас доступны в профиле 1.2.0.";
        } else {
            mapOpen = !mapOpen;
            statusMessage = mapOpen ? "Карта открыта. Нажми B, чтобы закрыть." : "Карта закрыта.";
        }
        updateHud();
    }

    private void cyclePlayerStyle() {
        if (!version.survival() || playerAvatar == null) {
            statusMessage = "Настройка внешнего вида доступна в профиле выживания.";
            updateHud();
            return;
        }
        int style = playerAvatar.cycleStyle();
        playerProfile = playerProfile.withAppearance(style);
        try {
            PlayerProfileStore.save(playerProfile);
        } catch (IOException error) {
            GameLog.error("Не удалось сохранить выбранный образ игрока", error);
        }
        statusMessage = "Образ игрока: " + playerAvatar.styleName() + " (" + (style + 1) + "/4).";
        updateHud();
    }

    private void toggleCameraView() {
        if (!version.survival()) {
            statusMessage = "Камера от третьего лица доступна в режиме выживания.";
            updateHud();
            return;
        }
        thirdPersonView = !thirdPersonView;
        if (thirdPersonView) {
            inputManager.setCursorVisible(false);
            if (playerAvatar != null) {
                playerAvatar.update(playerEyePosition, yaw, false, 0f);
                playerAvatar.attach(rootNode);
            }
            updateThirdPersonCamera();
            statusMessage = "Камера от третьего лица. F5 — вернуться к первому лицу.";
        } else {
            if (playerAvatar != null) playerAvatar.detach();
            cam.setLocation(playerEyePosition.clone());
            cam.setRotation(new Quaternion().fromAngles(pitch, yaw, 0f));
            statusMessage = "Камера от первого лица. F5 — вид от третьего лица.";
        }
        updateHud();
    }

    private void updateThirdPersonCamera() {
        if (!thirdPersonView || playerEyePosition == null || world == null) return;
        Vector3f target = PlayerCamera.lookTarget(playerEyePosition);
        Vector3f desired = PlayerCamera.orbitPosition(playerEyePosition, yaw, pitch);
        Vector3f offset = desired.subtract(target);
        float desiredDistance = offset.length();
        Vector3f direction = offset.normalizeLocal();

        CollisionResults cameraHits = new CollisionResults();
        Ray cameraRay = new Ray(target, direction);
        cameraRay.setLimit(desiredDistance);
        world.getNode().collideWith(cameraRay, cameraHits);

        float obstructionDistance = cameraHits.size() == 0
                ? -1f : cameraHits.getClosestCollision().getDistance();
        float safeDistance = PlayerCamera.safeDistance(desiredDistance, obstructionDistance, 0.32f);
        cam.setLocation(target.add(direction.mult(safeDistance)));
        cam.lookAt(target, Vector3f.UNIT_Y);
    }

    private void applyLandingImpact(float fallDistance) {
        if (!equipmentEnabled || fallDistance <= 5f) return;
        int defense = armorDefense();
        float damage = ArmorProtection.landingDamage(fallDistance, equippedArmor);
        if (damage <= 0.1f) return;
        health = Math.max(0f, health - damage);
        statusMessage = String.format(java.util.Locale.ROOT,
                "Приземление: урон %.1f; защита экипировки %d/8.", damage, defense);
        updateHud();
    }

    private void updateSurvivalNeeds(float tpf) {
        hungerClock += tpf;
        if (hunger > 0f) {
            starvationClock = 0f;
        } else {
            starvationClock += tpf;
        }
        if (hungerClock >= 35f) {
            hungerClock = 0f;
            hunger = Math.max(0f, hunger - 1f);
        }
        if (hunger <= 0f && starvationClock >= 8f) {
            starvationClock = 0f;
            health = Math.max(0f, health - 1f);
        }
        if (health <= 0f) {
            health = 20f;
            hunger = 10f;
            verticalVelocity = 0f;
            spawnPosition = world.getSuggestedSpawn();
            fallStartY = spawnPosition.y;
            onGround = true;
            playerEyePosition.set(spawnPosition);
            if (!thirdPersonView) cam.setLocation(spawnPosition.clone());
            statusMessage = "Ты возродился на точке появления. Часть прогресса персонажа пока не сохраняется.";
            savePlayerDataQuietly();
        }
    }

    private BlockHit getTargetBlock() {
        if (!worldReady) return null;
        CollisionResults results = new CollisionResults();
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        ray.setLimit(6f);
        world.getNode().collideWith(ray, results);
        if (results.size() == 0) return null;
        CollisionResult closest = results.getClosestCollision();
        return world.getHitFace(closest.getGeometry(), closest.getTriangleIndex());
    }

    private void beginMining() {
        if (paused || !worldReady || inventoryOpen || craftingOpen || furnaceOpen
                || tradeOpen || chestOpen || achievementsOpen) return;
        if (!version.survival() || version.id().compareTo("1.9.4") < 0) {
            breakTargetBlock();
            return;
        }
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди завершения синхронизации общего мира.";
            updateHud();
            return;
        }

        BlockHit hit = getTargetBlock();
        Vector3f attackOrigin = cam.getLocation();
        boolean blockBlocksAttack = hit != null && attackOrigin.distance(new Vector3f(
                hit.position().x() + .5f, hit.position().y() + .5f, hit.position().z() + .5f)) <= 3.4f;
        if (!blockBlocksAttack && creatureManager != null) {
            String combatMessage = creatureManager.attackAlongRay(attackOrigin, cam.getDirection(),
                    3.4f, .82f, CombatRules.unarmedContactDamage(), world);
            if (combatMessage != null) {
                cancelMining();
                statusMessage = combatMessage;
                updateHud();
                return;
            }
        }
        if (hit == null) {
            cancelMining();
            statusMessage = "Наведи прицел на блок и удерживай ЛКМ для добычи.";
            updateHud();
            return;
        }

        BlockType block = world.getBlock(hit.position());
        if (block == null) {
            cancelMining();
            return;
        }
        if (equipmentEnabled && !ToolType.canMine(block, selectedTool)) {
            cancelMining();
            int required = ToolType.requiredTier(block);
            statusMessage = "Для блока «" + block.displayName() + "» нужна кирка уровня " + required
                    + ". Нажми T, чтобы выбрать инструмент.";
            updateHud();
            return;
        }

        miningPressed = true;
        miningTarget = hit.position();
        miningBlock = block;
        miningElapsed = 0f;
        miningDuration = MiningRules.durationSeconds(block, selectedTool);
        updateMiningHud(0f, block);
    }

    private void updateMining(float tpf) {
        if (!miningPressed) return;
        if (paused || !worldReady || inventoryOpen || craftingOpen || furnaceOpen
                || tradeOpen || chestOpen || achievementsOpen
                || (multiplayerClient != null && multiplayerClient.isConnected()
                    && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending))) {
            cancelMining();
            return;
        }

        BlockHit hit = getTargetBlock();
        if (hit == null || !hit.position().equals(miningTarget)
                || world.getBlock(miningTarget) != miningBlock
                || (equipmentEnabled && !ToolType.canMine(miningBlock, selectedTool))) {
            cancelMining();
            return;
        }

        if (Float.isFinite(tpf) && tpf > 0f) miningElapsed += tpf;
        float progress = Math.min(1f, miningElapsed / Math.max(0.01f, miningDuration));
        updateMiningHud(progress, miningBlock);
        if (progress >= 1f) {
            BlockPos completedTarget = miningTarget;
            BlockType completedBlock = miningBlock;
            cancelMining();
            BlockHit currentHit = getTargetBlock();
            if (currentHit != null && currentHit.position().equals(completedTarget)
                    && world.getBlock(completedTarget) == completedBlock) {
                breakTargetBlock();
            }
        }
    }

    private void updateMiningHud(float progress, BlockType block) {
        if (miningHud == null || block == null) return;
        int percent = Math.max(0, Math.min(100, Math.round(progress * 100f)));
        int filled = Math.max(0, Math.min(10, Math.round(percent / 10f)));
        miningHud.setText("Добыча: " + block.displayName() + "  " + percent + "%  ["
                + "#".repeat(filled) + "-".repeat(10 - filled) + "]");
        miningHud.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
    }

    private void cancelMining() {
        miningPressed = false;
        miningTarget = null;
        miningBlock = null;
        miningElapsed = 0f;
        miningDuration = 0f;
        if (miningHud != null) {
            miningHud.setText("");
            miningHud.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        }
    }

    private void breakTargetBlock() {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди завершения синхронизации общего мира.";
            updateHud();
            return;
        }

        if (!worldReady) {
            statusMessage = "Мир ещё загружается.";
            updateHud();
            return;
        }
        BlockHit hit = getTargetBlock();
        Vector3f attackOrigin = cam.getLocation();
        boolean blockBlocksAttack = hit != null && attackOrigin.distance(new Vector3f(
                hit.position().x() + .5f, hit.position().y() + .5f, hit.position().z() + .5f)) <= 3.4f;
        if (!blockBlocksAttack && creatureManager != null) {
            String combatMessage = creatureManager.attackAlongRay(attackOrigin, cam.getDirection(),
                    3.4f, .82f, CombatRules.unarmedContactDamage(), world);
            if (combatMessage != null) {
                statusMessage = combatMessage;
                updateHud();
                return;
            }
        }
        if (hit == null) {
            statusMessage = "Рядом нет блока или противника.";
            updateHud();
            return;
        }
        BlockPos pos = hit.position();
        BlockType removed = world.getBlock(pos);
        if (version.survival() && equipmentEnabled && removed != null && !ToolType.canMine(removed, selectedTool)) {
            int required = ToolType.requiredTier(removed);
            statusMessage = "Для блока «" + removed.displayName() + "» нужна кирка уровня " + required
                    + ". Нажми T, чтобы выбрать инструмент.";
            updateHud();
            return;
        }
        if (world.removeBlock(pos)) {
            sendMultiplayerBlockRemove(pos);
            if (agricultureEnabled && removed == BlockType.WHEAT_CROP) {
                farmGrowth.remove(pos);
                saveFarmQuietly();
            }
            if (version.survival() && removed != null) {
                if (agricultureEnabled && removed == BlockType.FURNACE) {
                    craftedItems.merge(ItemType.FURNACE, 1, Integer::sum);
                    statusMessage = "Печь возвращена в инвентарь.";
                } else if (release100Enabled && removed == BlockType.EMERALD_ORE) {
                    int drops = 1 + (selectedTool == ToolType.IRON_PICKAXE ? 1 : 0);
                    craftedItems.merge(ItemType.EMERALD, drops, Integer::sum);
                    statusMessage = "Найдено: изумруд ×" + drops + ".";
                    unlockAchievement(Achievement.FIND_EMERALD);
                } else {
                    int drops = 1 + (equipmentEnabled ? ToolType.bonusDrops(removed, selectedTool) : 0);
                    inventory.merge(removed, drops, Integer::sum);
                    statusMessage = "Собрано: " + removed.displayName() + " × " + drops
                            + " (всего " + inventory.get(removed) + ").";
                    if (release100Enabled && removed == BlockType.COPPER_ORE) unlockAchievement(Achievement.FIND_COPPER);
                }
                if (release100Enabled) {
                    unlockAchievement(Achievement.FIRST_BLOCK);
                    playSound(GameSounds.Effect.MINE);
                }
                if (agricultureEnabled && removed == BlockType.GRASS
                        && ThreadLocalRandom.current().nextFloat() < 0.22f) {
                    craftedItems.merge(ItemType.WHEAT_SEEDS, 1, Integer::sum);
                    statusMessage += " Найдены семена пшеницы!";
                }
                savePlayerDataQuietly();
            } else {
                statusMessage = "Блок разрушен.";
            }
            updateHud();
        }
    }

    private void placeTargetBlock() {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди завершения синхронизации общего мира.";
            updateHud();
            return;
        }

        if (!worldReady) {
            statusMessage = "Мир ещё загружается.";
            updateHud();
            return;
        }
        BlockHit hit = getTargetBlock();
        if (hit == null) {
            statusMessage = "Наведи прицел на блок, чтобы поставить рядом новый.";
            updateHud();
            return;
        }
        BlockPos target = hit.adjacentPosition();
        Vector3f targetCenter = new Vector3f(target.x(), target.y(), target.z());
        int available = inventory.getOrDefault(selectedBlock, 0);
        if ((version.survival() ? playerEyePosition : cam.getLocation()).distance(targetCenter) < 1.45f) {
            statusMessage = "Слишком близко: нельзя поставить блок внутри игрока.";
        } else if (version.survival() && available <= 0) {
            statusMessage = "В инвентаре нет блока «" + selectedBlock.displayName() + "».";
        } else if (world.addBlock(target, selectedBlock)) {
            sendMultiplayerBlockAdd(target, selectedBlock);
            if (version.survival()) {
                inventory.put(selectedBlock, available - 1);
                savePlayerDataQuietly();
            }
            statusMessage = "Поставлен блок: " + selectedBlock.displayName() + ".";
        } else {
            statusMessage = "Место занято или достигнут лимит блоков.";
        }
        updateHud();
    }

    private void interactCreature() {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди завершения синхронизации общего мира.";
            updateHud();
            return;
        }

        if (!worldReady) return;
        if (chestOpen) {
            chestOpen = false;
            chestOperationPending = false;
            showInventoryOverlay(false);
            statusMessage = "Общий сундук закрыт.";
            updateHud();
            return;
        }
        if (tradeOpen) {
            tradeOpen = false;
            showInventoryOverlay(false);
            statusMessage = "Торговля закрыта.";
            updateHud();
            return;
        }
        BlockHit hit = getTargetBlock();
        BlockType chestType = hit == null ? null : world.getBlock(hit.position());
        if (explorationEnabled && hit != null
                && (chestType == BlockType.TREASURE_CHEST || chestType == BlockType.CRYSTAL_CACHE
                    || chestType == BlockType.ANCIENT_CACHE)) {
            if (!version.survival()) {
                statusMessage = "Сундук хранит находки. Открой его в режиме выживания.";
            } else if (multiplayerClient != null && multiplayerClient.isConnected()) {
                try {
                    multiplayerClient.openSharedChest(hit.position());
                    statusMessage = "Запрашиваю общий сундук у сервера...";
                } catch (IOException error) {
                    statusMessage = "Не удалось открыть общий сундук: " + error.getMessage();
                }
            } else if (world.removeBlock(hit.position())) {
                sendMultiplayerBlockRemove(hit.position());
                Map<ItemType, Integer> loot = chestType == BlockType.ANCIENT_CACHE
                        ? TreasureLoot.rollAncientCache(world.getSeed(), hit.position())
                        : chestType == BlockType.CRYSTAL_CACHE
                            ? TreasureLoot.rollCrystalCache(world.getSeed(), hit.position())
                            : TreasureLoot.roll(world.getSeed(), hit.position());
                loot.forEach((item, count) -> craftedItems.merge(item, count, Integer::sum));
                String found = loot.entrySet().stream()
                        .map(entry -> entry.getKey().displayName() + " ×" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(", "));
                String chestMessage = "Сундук открыт! Найдено: " + found + ".";
                saveWorld();
                if (statusMessage.startsWith("Ошибка сохранения:")) {
                    statusMessage += " Сундук открыт, но сохранение не удалось.";
                } else {
                    statusMessage = chestMessage + " Содержимое сохранено.";
                }
            } else {
                statusMessage = "Этот сундук уже пуст.";
            }
            updateHud();
            return;
        }
        if (agricultureEnabled && hit != null && world.getBlock(hit.position()) == BlockType.FURNACE) {
            furnaceOpen = !furnaceOpen;
            inventoryOpen = false;
            craftingOpen = false;
            tradeOpen = false;
            achievementsOpen = false;
            showInventoryOverlay(furnaceOpen);
            statusMessage = furnaceOpen ? "Печь открыта. 1 — железо, 2 — медь, 3 — рыба; F — закрыть." : "Печь закрыта.";
            updateHud();
            return;
        }
        if (furnaceOpen) {
            furnaceOpen = false;
            showInventoryOverlay(false);
            statusMessage = "Печь закрыта.";
            updateHud();
            return;
        }
        if (creatureManager == null) return;
        com.blockworld.core.WorldCreature creature = creatureManager.nearest(version.survival() ? playerEyePosition : cam.getLocation(), 3.2f);
        if (creature == null) {
            statusMessage = "Рядом нет существ или печей. Подойди ближе и нажми F.";
        } else if (creature.type().isVillager() && release100Enabled && version.survival()) {
            inventoryOpen = false;
            craftingOpen = false;
            furnaceOpen = false;
            achievementsOpen = false;
            tradeOpen = true;
            showInventoryOverlay(true);
            statusMessage = "Житель открыл торговлю. Нажми 1–4 для обмена; F — закрыть.";
        } else if (creature.type().isVillager()) {
            statusMessage = creature.type().displayName() + ": Добро пожаловать в нашу деревню!";
        } else if (creature.type() == com.blockworld.core.CreatureType.SLIME) {
            statusMessage = "Слайм подпрыгивает рядом. Он не нападает.";
        } else {
            statusMessage = creature.type().displayName() + " рядом. Существо спокойно гуляет по траве.";
        }
        updateHud();
    }

    private void tradeWithVillager(int index) {
        if (!tradeOpen || !release100Enabled || index < 0 || index >= tradeRecipes.size()) return;
        TradeRecipe offer = tradeRecipes.get(index);
        if (multiplayerClient != null && multiplayerClient.isConnected()) {
            if (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending) {
                statusMessage = "Подожди синхронизации инвентаря или завершения предыдущей операции.";
            } else {
                try {
                    multiplayerClient.sendTradeRequest(offer.number());
                    multiplayerTradePending = true;
                    statusMessage = "Сервер проверяет предметы и выполняет обмен...";
                } catch (IOException | IllegalArgumentException error) {
                    statusMessage = "Не удалось отправить запрос обмена: " + error.getMessage();
                }
            }
            updateInventoryUi();
            updateHud();
            return;
        }
        if (offer.trade(craftedItems)) {
            statusMessage = "Сделка завершена: " + offer.description() + ". Получено "
                    + offer.result().displayName() + " ×" + offer.resultCount() + ".";
            unlockAchievement(Achievement.FIRST_TRADE);
            playSound(GameSounds.Effect.TRADE);
            savePlayerDataQuietly();
        } else {
            statusMessage = "Не хватает предметов для сделки: " + offer.costText() + ".";
        }
        updateInventoryUi();
        updateHud();
    }

    private void castFishingRod() {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди синхронизации личного инвентаря.";
            updateHud();
            return;
        }
        if (!release100Enabled || !version.survival() || !worldReady || inventoryOpen || craftingOpen
                || furnaceOpen || tradeOpen || achievementsOpen) return;
        if (craftedItems.getOrDefault(ItemType.FISHING_ROD, 0) <= 0) {
            statusMessage = "Для рыбалки нужна удочка. Создай её в мастерской.";
            updateHud();
            return;
        }
        if (fishingCooldown > 0f) {
            statusMessage = "Удочка отдыхает ещё " + String.format(java.util.Locale.ROOT, "%.1f", fishingCooldown) + " с.";
            updateHud();
            return;
        }
        BlockHit hit = getTargetBlock();
        if (hit == null || world.getBlock(hit.position()) != BlockType.WATER
                || cam.getLocation().distance(new Vector3f(hit.position().x(), hit.position().y(), hit.position().z())) > FishingRules.CAST_RANGE) {
            statusMessage = "Наведи прицел на воду не дальше " + FishingRules.CAST_RANGE + " блоков и нажми P.";
            updateHud();
            return;
        }
        fishingCooldown = FishingRules.CAST_COOLDOWN_SECONDS;
        if (FishingRules.catchesFish(ThreadLocalRandom.current().nextFloat())) {
            craftedItems.merge(ItemType.RAW_FISH, 1, Integer::sum);
            statusMessage = "Клюнуло! Поймана сырая рыба ×1. Её можно приготовить в печи.";
            unlockAchievement(Achievement.FIRST_FISH);
            playSound(GameSounds.Effect.FISH);
            savePlayerDataQuietly();
        } else {
            statusMessage = "Рыба не клюнула. Попробуй ещё раз через пару секунд.";
        }
        updateInventoryUi();
        updateHud();
    }

    private void toggleAchievements() {
        if (!release100Enabled || !worldReady || !version.survival()) return;
        achievementsOpen = !achievementsOpen;
        inventoryOpen = false;
        craftingOpen = false;
        furnaceOpen = false;
        tradeOpen = false;
        showInventoryOverlay(achievementsOpen);
        statusMessage = achievementsOpen ? "Достижения открыты. M — закрыть." : "Достижения закрыты.";
        updateHud();
    }

    private void unlockAchievement(Achievement achievement) {
        if (!release100Enabled || !achievements.unlock(achievement)) return;
        statusMessage = "Новое достижение: " + achievement.title() + "!";
        playSound(GameSounds.Effect.ACHIEVEMENT);
        saveAchievementsQuietly();
    }

    private void saveAchievementsQuietly() {
        if (!release100Enabled) return;
        try { AchievementDataStore.save(saveFile, achievements); }
        catch (IOException ex) { GameLog.error("Не удалось сохранить достижения", ex); }
    }

    private void playSound(GameSounds.Effect effect) {
        if (gameSounds != null) gameSounds.play(effect);
    }

    private void useSelectedUtility() {
        if (multiplayerClient != null && multiplayerClient.isConnected() && !multiplayerSyncReady) {
            statusMessage = "Подожди завершения синхронизации общего мира.";
            updateHud();
            return;
        }

        if (!agricultureEnabled || !version.survival() || !worldReady || inventoryOpen || craftingOpen || furnaceOpen) return;
        BlockHit hit = getTargetBlock();
        if (hit == null) {
            statusMessage = "Наведи прицел на землю, грядку или печь и нажми G.";
            updateHud();
            return;
        }
        BlockPos position = hit.position();
        BlockType target = world.getBlock(position);
        if (target == BlockType.FARMLAND) {
            BlockPos crop = position.add(0, 1, 0);
            if (craftedItems.getOrDefault(ItemType.WHEAT_SEEDS, 0) <= 0) {
                statusMessage = "Нет семян. Иногда они выпадают при добыче травы.";
            } else if (world.contains(crop)) {
                statusMessage = "Над грядкой уже что-то растёт или стоит блок.";
            } else if (world.addBlock(crop, BlockType.WHEAT_CROP)) {
                sendMultiplayerBlockAdd(crop, BlockType.WHEAT_CROP);
                craftedItems.merge(ItemType.WHEAT_SEEDS, -1, Integer::sum);
                farmGrowth.put(crop, 0f);
                savePlayerDataQuietly();
                saveFarmQuietly();
                statusMessage = "Семена посажены. Пшеница созреет примерно за " + Math.round(FarmRules.MATURATION_SECONDS) + " секунд.";
            }
        } else if (target == BlockType.WHEAT_CROP) {
            float progress = farmGrowth.getOrDefault(position, 0f);
            if (!FarmRules.isMature(progress)) {
                statusMessage = "Пшеница ещё растёт: " + Math.round(progress) + "/" + Math.round(FarmRules.MATURATION_SECONDS) + " секунд.";
            } else if (world.removeBlock(position)) {
                sendMultiplayerBlockRemove(position);
                farmGrowth.remove(position);
                craftedItems.merge(ItemType.WHEAT, 1, Integer::sum);
                craftedItems.merge(ItemType.WHEAT_SEEDS, FarmRules.harvestSeeds(2), Integer::sum);
                savePlayerDataQuietly();
                saveFarmQuietly();
                statusMessage = "Урожай собран: пшеница ×1 и семена ×2.";
                if (release100Enabled) unlockAchievement(Achievement.FIRST_HARVEST);
            }
        } else if ((target == BlockType.GRASS || target == BlockType.DIRT) && selectedTool == ToolType.WOODEN_HOE) {
            if (world.removeBlock(position) && world.addBlock(position, BlockType.FARMLAND)) {
                sendMultiplayerBlockRemove(position);
                sendMultiplayerBlockAdd(position, BlockType.FARMLAND);
                statusMessage = "Грядка создана. Наведи прицел на неё и нажми G, чтобы посадить семена.";
            }
        } else if (craftedItems.getOrDefault(ItemType.FURNACE, 0) > 0) {
            BlockPos place = hit.adjacentPosition();
            Vector3f center = new Vector3f(place.x(), place.y(), place.z());
            if (cam.getLocation().distance(center) < 1.45f) {
                statusMessage = "Слишком близко: нельзя поставить печь внутри игрока.";
            } else if (world.addBlock(place, BlockType.FURNACE)) {
                sendMultiplayerBlockAdd(place, BlockType.FURNACE);
                craftedItems.merge(ItemType.FURNACE, -1, Integer::sum);
                savePlayerDataQuietly();
                statusMessage = "Печь установлена. Наведи на неё прицел и нажми F.";
            } else {
                statusMessage = "Место занято: печь не установлена.";
            }
        } else if (selectedTool != ToolType.WOODEN_HOE && craftedItems.getOrDefault(ItemType.WOODEN_HOE, 0) > 0) {
            statusMessage = "Для создания грядки выбери мотыгу клавишей T.";
        } else {
            statusMessage = "Нужна мотыга для грядки или печь в инвентаре.";
        }
        updateInventoryUi();
        updateHud();
    }

    private void smeltInFurnace(int index) {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди синхронизации личного инвентаря.";
            updateHud();
            return;
        }
        if (!furnaceOpen || !agricultureEnabled || index < 0 || index >= FurnaceRecipe.values().length) return;
        FurnaceRecipe recipe = FurnaceRecipe.values()[index];
        if (recipe.smelt(inventory, craftedItems)) {
            statusMessage = "Переплавка завершена: " + recipe.displayName() + " ×1.";
            playSound(GameSounds.Effect.SMELT);
            savePlayerDataQuietly();
        } else {
            statusMessage = "Для рецепта «" + recipe.displayName() + "» нужны сырьё и 1 угольная руда.";
        }
        updateInventoryUi();
        updateHud();
    }

    private void updateFarmGrowth(float tpf) {
        if (farmGrowth.isEmpty()) return;
        boolean maturityChanged = false;
        Iterator<Map.Entry<BlockPos, Float>> iterator = farmGrowth.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Float> entry = iterator.next();
            if (world.getBlock(entry.getKey()) != BlockType.WHEAT_CROP) {
                iterator.remove();
                maturityChanged = true;
                continue;
            }
            float previous = entry.getValue();
            Vector3f cropCenter = new Vector3f(entry.getKey().x() + .5f,
                    entry.getKey().y(), entry.getKey().z() + .5f);
            boolean farmerNearby = creatureManager != null
                    && creatureManager.hasFarmerNear(cropCenter, 8f);
            float next = FarmRules.advance(previous, tpf, raining, farmerNearby);
            entry.setValue(next);
            if (!FarmRules.isMature(previous) && FarmRules.isMature(next)) maturityChanged = true;
        }
        farmSaveClock += Math.max(0f, tpf);
        if (farmSaveClock >= 5f) {
            farmSaveClock = 0f;
            saveFarmQuietly();
        }
        if (maturityChanged) updateHud();
    }

    private void saveFarmQuietly() {
        if (!agricultureEnabled) return;
        try { FarmDataStore.save(saveFile, farmGrowth); }
        catch (IOException ex) { GameLog.error("Не удалось сохранить рост посевов", ex); }
    }

    private void cycleTool() {
        if (!version.survival() || !equipmentEnabled) return;
        List<ToolType> available = new ArrayList<>();
        for (ToolType tool : ToolType.values()) {
            if (craftedItems.getOrDefault(tool.item(), 0) > 0) available.add(tool);
        }
        if (available.isEmpty()) {
            selectedTool = null;
            statusMessage = "Инструментов пока нет. Создай кирку, лопату или мотыгу в мастерской.";
        } else {
            int current = available.indexOf(selectedTool);
            selectedTool = available.get((current + 1) % available.size());
            statusMessage = "Выбран инструмент: " + selectedTool.item().displayName() + ".";
        }
        updateHud();
    }

    private void toggleArmor(ArmorSlot slot) {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди синхронизации личного инвентаря.";
            updateHud();
            return;
        }
        if (!version.survival() || !equipmentEnabled) return;
        if (!inventoryOpen) {
            statusMessage = "Открой инвентарь клавишей I, чтобы надеть или снять экипировку.";
            updateHud();
            return;
        }
        if (equippedArmor.contains(slot)) {
            equippedArmor.remove(slot);
            craftedItems.merge(slot.item(), 1, Integer::sum);
            statusMessage = "Снято: " + slot.item().displayName() + ".";
        } else if (craftedItems.getOrDefault(slot.item(), 0) > 0) {
            craftedItems.put(slot.item(), craftedItems.get(slot.item()) - 1);
            equippedArmor.add(slot);
            statusMessage = "Надето: " + slot.item().displayName() + ".";
        } else {
            statusMessage = "В инвентаре нет предмета: " + slot.item().displayName() + ".";
        }
        savePlayerDataQuietly();
        updateInventoryUi();
        updateHud();
    }

    private void savePlayerDataQuietly() {
        if (!version.survival()) return;
        try { PlayerDataStore.save(saveFile, inventory, craftedItems, equippedArmor); }
        catch (IOException ex) { GameLog.error("Не удалось сохранить данные игрока", ex); }
        if (multiplayerClient != null && multiplayerClient.isConnected() && multiplayerProfileReady) {
            sendMultiplayerProfileSnapshot();
        }
    }

    private void changeInventoryPage(int delta) {
        if (!inventoryOpen || delta == 0) return;
        setInventoryPage(Math.floorMod(inventoryPage + Integer.signum(delta), 2));
    }

    private void setInventoryPage(int page) {
        if (!inventoryOpen || page < 0 || page > 1 || inventoryPage == page) return;
        inventoryPage = page;
        updateInventoryUi();
        statusMessage = inventoryPage == 0 ? "Инвентарь: ресурсы." : "Инвентарь: экипировка.";
        updateHud();
    }

    private void changeCraftPage(int delta) {
        if (!craftingOpen) return;
        int pageCount = Math.max(1, (craftingRecipes.size() + 8) / 9);
        craftingPage = Math.max(0, Math.min(pageCount - 1, craftingPage + delta));
        updateInventoryUi();
    }

    private void toggleInventory() {
        if (chestOpen) return;
        if (!version.survival() || !worldReady) return;
        cancelMining();
        inventoryOpen = !inventoryOpen;
        if (inventoryOpen) inventoryPage = 0;
        craftingOpen = false;
        furnaceOpen = false;
        tradeOpen = false;
        achievementsOpen = false;
        showInventoryOverlay(inventoryOpen);
        statusMessage = inventoryOpen
                ? "Инвентарь открыт: 1 — ресурсы, 2 — экипировка, ←/→ — вкладки, I — закрыть."
                : "Инвентарь закрыт.";
        updateHud();
    }

    private void toggleCrafting() {
        if (chestOpen) return;
        if (!version.survival() || !worldReady) return;
        cancelMining();
        craftingOpen = !craftingOpen;
        inventoryOpen = false;
        furnaceOpen = false;
        tradeOpen = false;
        achievementsOpen = false;
        craftingPage = 0;
        showInventoryOverlay(craftingOpen);
        statusMessage = craftingOpen
                ? (craftingRecipes.size() > 9 ? "Мастерская открыта. 1–9 — рецепт, стрелки ←/→ — страницы, C — закрыть."
                    : "Мастерская открыта. 1–4 — рецепт, C — закрыть.")
                : "Мастерская закрыта.";
        updateHud();
    }

    private void showInventoryOverlay(boolean show) {
        if (inventoryPanel == null || inventoryText == null) return;
        if (show) {
            updateInventoryUi();
            if (inventoryPanel.getParent() == null) guiNode.attachChild(inventoryPanel);
            if (inventoryText.getParent() == null) guiNode.attachChild(inventoryText);
        } else {
            guiNode.detachChild(inventoryPanel);
            guiNode.detachChild(inventoryText);
        }
        if (inventoryVisualPanel != null) {
            inventoryVisualPanel.refresh(craftedItems);
            inventoryVisualPanel.setVisible(show && (inventoryOpen || craftingOpen));
        }
    }

    private void updateInventoryUi() {
        if (inventoryText == null) return;
        StringBuilder text = new StringBuilder();
        if (chestOpen) {
            text.append("Minecraft 3D — ОБЩИЙ СУНДУК\n\n");
            text.append("Предметы сундука доступны всем игрокам сервера.\n");
            List<ItemType> slots = chestItemSlots();
            int start = chestPage * 9;
            int end = Math.min(slots.size(), start + 9);
            if (slots.isEmpty()) text.append("Сундук пуст. Выбери предмет в своём инвентаре, чтобы положить его.\n");
            for (int i = start; i < end; i++) {
                ItemType item = slots.get(i);
                text.append(i - start + 1).append(". ").append(item.displayName())
                        .append(" | сундук: ").append(sharedChestContents.getOrDefault(item, 0))
                        .append(" | у тебя: ").append(craftedItems.getOrDefault(item, 0))
                        .append(selectedChestItem == item ? "  < ВЫБРАНО >" : "").append("\n");
            }
            text.append("\nСтраница ").append(chestPage + 1).append(" | ←/→ — страницы\n");
            text.append("Z — забрать 1 предмет, X — положить 1 предмет, F — закрыть");
        } else if (furnaceOpen) {
            text.append("Minecraft 3D — ПЕЧЬ\n\n");
            for (int i = 0; i < FurnaceRecipe.values().length; i++) {
                FurnaceRecipe recipe = FurnaceRecipe.values()[i];
                text.append(i + 1).append(". ").append(recipe.displayName()).append(" ×1\n");
            }
            text.append("\nТопливо: угольная руда ×1 на предмет.\n");
            text.append("Железная руда: ").append(inventory.getOrDefault(BlockType.IRON_ORE, 0)).append("\n");
            text.append("Медная руда: ").append(inventory.getOrDefault(BlockType.COPPER_ORE, 0)).append("\n");
            text.append("Угольная руда: ").append(inventory.getOrDefault(BlockType.COAL_ORE, 0)).append("\n");
            text.append("Сырая рыба: ").append(craftedItems.getOrDefault(ItemType.RAW_FISH, 0)).append("\n\n");
            text.append("Нажми 1–3 для плавки, F — закрыть.");
        } else if (tradeOpen) {
            text.append("Minecraft 3D — ТОРГОВЕЦ\n\n");
            text.append("Обменяй ресурсы на полезные предметы.\n\n");
            for (int i = 0; i < tradeRecipes.size(); i++) {
                TradeRecipe offer = tradeRecipes.get(i);
                text.append(i + 1).append(". ").append(offer.description()).append("\n   Нужно: ")
                        .append(offer.costText()).append(offer.canTrade(craftedItems) ? "  [ГОТОВО]" : "  [НЕ ХВАТАЕТ]").append("\n");
            }
            text.append("\nНажми 1–4 для обмена, F — закрыть.");
        } else if (achievementsOpen) {
            text.append("Minecraft 3D — ДОСТИЖЕНИЯ\n\n");
            text.append("Открыто: ").append(achievements.unlockedCount()).append("/").append(achievements.totalCount())
                    .append("    Биомы: ").append(achievements.discoveredBiomeCount()).append("/5\n\n");
            for (Achievement achievement : Achievement.values()) {
                text.append(achievements.isUnlocked(achievement) ? "[✓] " : "[ ] ")
                        .append(achievement.title()).append(" — ").append(achievement.description()).append("\n");
            }
            text.append("\nM — закрыть достижения.");
        } else if (inventoryOpen) {
            if (inventoryPage == 0) {
                text.append("Minecraft 3D — ИНВЕНТАРЬ | РЕСУРСЫ  (1/2)\n\n");
                text.append("Собранные блоки:\n");
                boolean hasResources = false;
                for (BlockType block : BlockType.values()) {
                    int count = inventory.getOrDefault(block, 0);
                    if (count > 0) {
                        text.append("• ").append(block.displayName()).append(" × ").append(count).append("\n");
                        hasResources = true;
                    }
                }
                if (!hasResources) text.append("Пока нет собранных блоков. Исследуй мир и собирай ресурсы!\n");
                text.append("\nПредметы и их количество показаны справа.");
            } else {
                text.append("Minecraft 3D — ИНВЕНТАРЬ | ЭКИПИРОВКА  (2/2)\n\n");
                if (equipmentEnabled) {
                    for (ArmorSlot slot : ArmorSlot.values()) {
                        text.append("• ").append(slot.displayName()).append(": ")
                                .append(equippedArmor.contains(slot) ? "надето" : "пусто")
                                .append("  [").append(switch (slot) {
                                    case HELMET -> "H";
                                    case CHESTPLATE -> "J";
                                    case LEGGINGS -> "K";
                                    case BOOTS -> "L";
                                }).append("]\n");
                    }
                    text.append("\nЗащита: ").append(armorDefense()).append("/8")
                            .append("\nИнструмент: ")
                            .append(selectedTool == null ? "нет" : selectedTool.item().displayName())
                            .append("\nH/J/K/L — надеть или снять броню    T — сменить инструмент");
                } else {
                    text.append("Экипировка пока недоступна в этом профиле.");
                }
            }
            text.append("\n\n1 — Ресурсы    2 — Экипировка    ←/→ — вкладки    I — закрыть");
            if (release100Enabled) text.append("\nP — рыбалка    M — достижения");
            if (agricultureEnabled) text.append("\nG — ферма и взаимодействие с печью");
        } else if (craftingOpen) {
            int pageCount = Math.max(1, (craftingRecipes.size() + 8) / 9);
            int start = craftingPage * 9;
            int end = Math.min(craftingRecipes.size(), start + 9);
            text.append("Minecraft 3D — МАСТЕРСКАЯ  |  СТРАНИЦА ").append(craftingPage + 1)
                    .append("/").append(pageCount).append("\n\n");
            text.append(craftingRecipes.size() > 9 ? "1–9 — создать рецепт    ←/→ — страницы    C — закрыть\n\n"
                    : "1–4 — создать рецепт    C — закрыть\n\n");
            for (int i = start; i < end; i++) {
                CraftingRecipe recipe = craftingRecipes.get(i);
                text.append(i - start + 1).append(". ").append(recipe.name()).append(" → ")
                        .append(recipe.result().displayName()).append(" × ").append(recipe.resultCount()).append("\n   Нужно: ")
                        .append(recipe.ingredientsText()).append(recipe.canCraft(inventory, craftedItems)
                                ? "  [ГОТОВО]" : "  [НЕ ХВАТАЕТ]").append("\n");
            }
            if (craftingRecipes.size() > 9) {
                text.append("\nСтрелка вправо — следующая страница, влево — предыдущая.");
            }
        } else {
            text.append("Minecraft 3D — ИНВЕНТАРЬ\n\nБлоки:\n");
            for (BlockType block : BlockType.values()) {
                int count = inventory.getOrDefault(block, 0);
                if (count > 0) text.append("• ").append(block.displayName()).append(" × ").append(count).append("\n");
            }
            text.append("\nПредметы и их количество показаны справа.\n");
            if (equipmentEnabled) {
                text.append("\nЭкипировка:\n");
                for (ArmorSlot slot : ArmorSlot.values()) {
                    text.append("• ").append(slot.displayName()).append(": ")
                            .append(equippedArmor.contains(slot) ? "надето" : "пусто")
                            .append("  [").append(switch (slot) {
                                case HELMET -> "H";
                                case CHESTPLATE -> "J";
                                case LEGGINGS -> "K";
                                case BOOTS -> "L";
                            }).append("]\n");
                }
                text.append("\nЗащита: ").append(armorDefense()).append("/8")
                        .append("    Инструмент: ").append(selectedTool == null ? "нет" : selectedTool.item().displayName())
                        .append("\nH/J/K/L — надеть/снять    T — сменить инструмент    C — мастерская")
                        .append(release100Enabled ? "    M — достижения" : "");
            } else {
                text.append("\nC — открыть мастерскую    I — закрыть инвентарь");
            }
            if (release100Enabled) text.append("\nM — достижения    P — рыбалка (с удочкой, прицел на воду)");
        }
        inventoryText.setText(text.toString());
        if (inventoryVisualPanel != null) inventoryVisualPanel.refresh(craftedItems);
    }

    private int armorDefense() {
        return ArmorProtection.defensePoints(equippedArmor);
    }

    private void craftRecipe(int index) {
        index = craftingPage * 9 + index;
        if (!craftingOpen || index < 0 || index >= craftingRecipes.size()) return;
        CraftingRecipe recipe = craftingRecipes.get(index);
        if (multiplayerClient != null && multiplayerClient.isConnected()) {
            if (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending) {
                statusMessage = "Подожди синхронизации инвентаря или завершения предыдущей операции.";
            } else {
                try {
                    String catalog = release100Enabled ? "release100" : agricultureEnabled ? "advanced" : "starter";
                    multiplayerClient.sendCraftRequest(catalog, recipe.number());
                    multiplayerCraftPending = true;
                    statusMessage = "Сервер проверяет ресурсы и выполняет крафт...";
                } catch (IOException | IllegalArgumentException error) {
                    statusMessage = "Не удалось отправить запрос крафта: " + error.getMessage();
                }
            }
            updateInventoryUi();
            updateHud();
            return;
        }
        if (recipe.craft(inventory, craftedItems)) {
            statusMessage = "Создано: " + recipe.result().displayName() + " × " + recipe.resultCount() + ".";
            if (release100Enabled) unlockAchievement(Achievement.FIRST_CRAFT);
            try { PlayerDataStore.save(saveFile, inventory, craftedItems, equippedArmor); }
            catch (IOException ex) { GameLog.error("Не удалось сохранить инвентарь после крафта", ex); }
        } else {
            statusMessage = "Не хватает ингредиентов для рецепта «" + recipe.name() + "».";
        }
        updateInventoryUi();
        updateHud();
    }

    private void eatFood() {
        if (multiplayerClient != null && multiplayerClient.isConnected()
                && (!multiplayerSyncReady || multiplayerCraftPending || multiplayerTradePending)) {
            statusMessage = "Подожди синхронизации личного инвентаря.";
            updateHud();
            return;
        }
        if (!version.survival()) return;
        if (hunger >= 20f) {
            statusMessage = "Голод уже восстановлен.";
        } else if (release100Enabled && craftedItems.getOrDefault(ItemType.COOKED_FISH, 0) > 0) {
            craftedItems.merge(ItemType.COOKED_FISH, -1, Integer::sum);
            hunger = FoodRules.restoreHunger(hunger, FoodRules.COOKED_FISH_RESTORATION);
            statusMessage = "Ты съел приготовленную рыбу. Голод восстановлен на 10.";
            savePlayerDataQuietly();
        } else if (agricultureEnabled && craftedItems.getOrDefault(ItemType.BREAD, 0) > 0) {
            craftedItems.merge(ItemType.BREAD, -1, Integer::sum);
            hunger = FoodRules.restoreHunger(hunger, FoodRules.BREAD_RESTORATION);
            statusMessage = "Ты съел хлеб. Голод восстановлен на 8.";
            savePlayerDataQuietly();
        } else if (foodCount <= 0) {
            statusMessage = "Еды нет. Собери ягоды или вырасти пшеницу и испеки хлеб.";
        } else if (hunger >= 20f) {
            statusMessage = "Голод уже восстановлен.";
        } else {
            foodCount--;
            hunger = FoodRules.restoreHunger(hunger, FoodRules.BERRY_RESTORATION);
            statusMessage = "Ты съел ягоды. Еды осталось: " + foodCount + ".";
        }
        updateHud();
    }

    private void commandNearestPet() {
        if (!petsEnabled || !worldReady || creatureManager == null) return;
        if (multiplayerClient != null && multiplayerClient.isConnected() && !multiplayerClient.isHost()) {
            statusMessage = "Команды питомцам доступны ведущему мира.";
            updateHud();
            return;
        }
        Vector3f playerPosition = version.survival() ? playerEyePosition : cam.getLocation();
        PetMode mode = creatureManager.toggleNearestDogMode(playerPosition, 12f);
        if (mode == null) statusMessage = "Рядом нет пса-компаньона. Подойди ближе и нажми N.";
        else if (mode == PetMode.STAY) statusMessage = "Пёс останется здесь. Нажми N рядом с ним, чтобы позвать обратно.";
        else statusMessage = "Пёс снова следует за тобой.";
        updateHud();
    }

    private void selectSlot(int index) {
        if (inventoryOpen || index < 0 || index >= hotbar.size()) return;
        selectedBlock = hotbar.get(index);
        statusMessage = "Выбран блок: " + selectedBlock.displayName() + ".";
        updateHud();
    }

    /** Scroll-wheel selection for the quick-access hotbar. */
    private void cycleHotbar(int direction) {
        if (hotbar == null || hotbar.isEmpty() || direction == 0) return;
        int current = hotbar.indexOf(selectedBlock);
        if (current < 0) current = 0;
        selectSlot(Math.floorMod(current + Integer.signum(direction), hotbar.size()));
    }

    private void saveWorld() {
        if (!worldReady) {
            statusMessage = "Подожди завершения загрузки мира.";
            updateHud();
            return;
        }
        try {
            if (modLoader != null) modLoader.beforeSave();
            world.save(saveFile, version);
            if (version.survival()) PlayerDataStore.save(saveFile, inventory, craftedItems, equippedArmor);
            if (agricultureEnabled) FarmDataStore.save(saveFile, farmGrowth);
            if (petsEnabled && creatureManager != null)
                PetDataStore.save(saveFile, creatureManager.petModesSnapshot());
            if (release100Enabled) AchievementDataStore.save(saveFile, achievements);
            statusMessage = "Мир, инвентарь, питомцы, ферма и достижения сохранены!";
        } catch (IOException ex) {
            statusMessage = "Ошибка сохранения: " + ex.getMessage();
            GameLog.error("Ошибка сохранения мира " + saveFile, ex);
        }
        updateHud();
    }

    private void updateHud() {
        if (hud == null || world == null || hotbar == null) return;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < hotbar.size(); i++) {
            BlockType block = hotbar.get(i);
            bar.append(i + 1).append(':').append(block.displayName());
            if (version.survival()) bar.append(" x").append(inventory.getOrDefault(block, 0));
            if (block == selectedBlock) bar.append(" [выбран]");
            if (i < hotbar.size() - 1) bar.append("    ");
        }
        String controls = version.survival()
                ? "WASD — ходьба    Мышь — обзор    F5 — камера    V — образ игрока    ПРОБЕЛ — прыжок    E — еда    "
                    + (equipmentEnabled ? "T — инструмент    " : "")
                    + "ЛКМ удерживать — добыча    Колесо мыши — выбор слота    I — инвентарь    C — крафт    F — печь/общение"
                    + (agricultureEnabled ? "    G — ферма/печь" : "")
                    + (release100Enabled ? "    P — рыбалка    M — достижения" : "")
                    + (explorationEnabled ? "    B — карта/компас" : "")
                    + (petsEnabled ? "    N — команда псу" : "") + "    ESC — пауза"
                : "WASD — движение    Мышь — обзор    Колесо мыши — выбор слота    F — общение/контакт"
                    + (explorationEnabled ? "    B — карта/компас" : "")
                    + (petsEnabled ? "    N — команда псу" : "") + "    ESC — пауза";
        String needs = version.survival()
                ? "Здоровье: " + (int) health + "/20    Голод: " + (int) hunger + "/20    Еда: " + foodCount
                    + (equipmentEnabled ? "    Защита: " + armorDefense() + "/8"
                        + "    Инструмент: " + (selectedTool == null ? "нет" : selectedTool.item().displayName()) : "")
                : "Творческий режим";
        String nature = version.id().compareTo("0.6.0") >= 0
                ? String.format(java.util.Locale.ROOT, "Время: %s    Погода: %s    R — сменить погоду",
                    dayClock < 0.25f ? "утро" : dayClock < 0.50f ? "день" : dayClock < 0.75f ? "вечер" : "ночь", raining ? "дождь" : "ясно")
                : "";
        Vector3f hudWorldPosition = version.survival() && playerEyePosition != null
                ? playerEyePosition : cam.getLocation();
        String biomeLine = agricultureEnabled && worldReady
                ? "Биом: " + world.getBiomeAt(Math.round(hudWorldPosition.x), Math.round(hudWorldPosition.z)).displayName()
                    + (release100Enabled ? "    Достижения: " + achievements.unlockedCount() + "/" + achievements.totalCount() : "")
                : "";
        String caveBiomeLine = worldReady && world.hasCaveBiomes() && hudWorldPosition.y < -3f
                ? "Подземный биом: " + world.getCaveBiomeAt(Math.round(hudWorldPosition.x),
                        Math.round(hudWorldPosition.y), Math.round(hudWorldPosition.z)).displayName()
                : "";
        String loading = worldReady ? "Мир загружен" : (worldDataReady ? loadingMessage : "Фоновая генерация/загрузка мира...");
        String performance = String.format(java.util.Locale.ROOT,
                "FPS: %.0f    Меши чанков: %d    Чанки данных: %d    Очередь: %d    Дальность: %d    Блоки: %,d",
                fpsEstimate, world.getRenderedChunkCount(), world.getChunkCount(),
                world.getPendingChunkCount(), world.getRenderDistanceChunks(), world.getBlockCount());
        String spawn = String.format(java.util.Locale.ROOT, "Spawn: X %.0f / Y %.0f / Z %.0f",
                spawnPosition.x, spawnPosition.y, spawnPosition.z);
        hud.setText("Minecraft 3D  |  " + version.id() + " " + version.displayName()
                + "\n" + controls + (multiplayerClient != null && multiplayerClient.isConnected() ? "    Enter — чат/обмен" : "") + "    F6 — сохранить    F7 — дальность    F8 — режим граней    F10 — выход из паузы"
                + "\n" + needs
                + (nature.isEmpty() ? "" : "\n" + nature)
                + (biomeLine.isEmpty() ? "" : "\n" + biomeLine)
                + (caveBiomeLine.isEmpty() ? "" : "\n" + caveBiomeLine)
                + "\n" + performance + "    Моды: " + loadedModCount
                + "    Существа: " + world.getCreatureCount()
                + "\n" + spawn + "    Seed: " + world.getSeed() + "    Блок: " + selectedBlock.displayName()
                + "\n" + bar
                + "\n" + "Грани: " + (twoSidedRendering ? "двусторонние" : "оптимизированные")
                + "\n" + loading
                + "\n" + statusMessage);
        boolean showMap = explorationEnabled && mapOpen && worldReady
                && navigationHud != null && navigationPanel != null;
        if (navigationHud != null && navigationPanel != null) {
            com.jme3.scene.Spatial.CullHint hint = showMap
                    ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always;
            navigationHud.setCullHint(hint);
            navigationPanel.setCullHint(hint);
            if (showMap) {
                Vector3f mapPosition = version.survival() ? playerEyePosition : cam.getLocation();
                navigationHud.setText(WorldNavigation.renderMap(mapPosition.x, mapPosition.z, yaw, world.getLandmarks()));
            }
        }
        if (inventoryOpen || craftingOpen || furnaceOpen || tradeOpen || achievementsOpen) updateInventoryUi();
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        if (hud != null && guiNode != null) {
            hud.setLocalTranslation(15f, height - 14f, 0f);
            if (hudPanel != null) hudPanel.setLocalTranslation(7f, height - 181f, 1f);
            if (crosshair != null) crosshair.setLocalTranslation(width / 2f - 5f, height / 2f + 7f, 0f);
            if (miningHud != null) miningHud.setLocalTranslation(width / 2f - 150f, height / 2f - 24f, 2f);
            if (pausePanel != null) pausePanel.setLocalTranslation(width / 2f - 260f, height / 2f - 125f, 4f);
            if (pauseText != null) pauseText.setLocalTranslation(width / 2f - 205f, height / 2f + 82f, 5f);
            if (inventoryPanel != null) inventoryPanel.setLocalTranslation(width / 2f - 350f, height / 2f - 250f, 8f);
            if (inventoryText != null) inventoryText.setLocalTranslation(width / 2f - 325f, height / 2f + 220f, 9f);
            if (inventoryVisualPanel != null) inventoryVisualPanel.layout(width, height);
        }
    }

    @Override
    public void handleError(String errorMsg, Throwable error) {
        GameLog.error("Ошибка jMonkeyEngine в потоке " + Thread.currentThread().getName() + ": "
                + (errorMsg == null ? "без описания" : errorMsg), error);
        super.handleError(errorMsg, error);
        String details = error == null ? String.valueOf(errorMsg)
                : error.getClass().getSimpleName() + ": " + error.getMessage();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "jMonkeyEngine сообщил об ошибке. Полный стек сохранён в .logs\\latest-run.log.\n\n"
                        + details,
                "Minecraft 3D — ошибка графического движка", JOptionPane.ERROR_MESSAGE));
    }



    private void setupWorldPreviewCapture() {
        if (saveFile == null || saveFile.getFileName() == null) return;
        try {
            Path preview = GamePaths.worldPreviewFile(saveFile);
            Path directory = preview.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            String outputDirectory = directory.toString() + java.io.File.separator;
            String outputName = preview.getFileName().toString().replaceFirst("(?i)\\.png$", "");
            worldScreenshotState = new ScreenshotAppState(outputDirectory, outputName);
            worldScreenshotState.setIsNumbered(false);
            stateManager.attach(worldScreenshotState);
            GameLog.info("Подготовлено настоящее скриншот-превью мира: " + preview.toAbsolutePath());
        } catch (IOException | RuntimeException error) {
            worldScreenshotState = null;
            GameLog.error("Не удалось подготовить скриншот-превью мира", error);
        }
    }

    private void captureWorldPreviewIfReady() {
        if (!worldReady || worldPreviewCaptureRequested || worldScreenshotState == null
                || !worldScreenshotState.isInitialized()) return;
        worldScreenshotState.takeScreenshot();
        worldPreviewCaptureRequested = true;
        GameLog.info("Запрошен скриншот загруженного мира: " + GamePaths.worldPreviewFile(saveFile));
    }

    private void beginPlayerProfileSession() {
        if (profileSessionStarted) return;
        try {
            playerProfile = PlayerProfileStore.recordSessionStart();
            profileSessionStarted = true;
            profileCheckpointNanos = System.nanoTime();
            GameLog.info("Профиль игрока: начата сессия №" + playerProfile.sessionsPlayed()
                    + " для мира " + saveFile.getFileName());
        } catch (IOException error) {
            GameLog.error("Не удалось записать начало игровой сессии в профиль", error);
        }
    }

    private void checkpointPlayerProfile(boolean force) {
        if (paused || !profileSessionStarted || profileCheckpointNanos <= 0L) return;
        long now = System.nanoTime();
        long elapsedSeconds = Math.max(0L, (now - profileCheckpointNanos) / 1_000_000_000L);
        if (elapsedSeconds <= 0L || (!force && elapsedSeconds < 60L)) return;
        try {
            playerProfile = PlayerProfileStore.addPlayTimeSeconds(elapsedSeconds);
            profileCheckpointNanos += elapsedSeconds * 1_000_000_000L;
        } catch (IOException error) {
            GameLog.error("Не удалось сохранить время игры в профиле", error);
        }
    }

    @Override
    public void destroy() {
        checkpointPlayerProfile(true);
        GameLog.info("Начато закрытие игрового приложения; worldReady=" + worldReady
                + "; thread=" + Thread.currentThread().getName());
        try {
            if (modLoader != null && worldReady) modLoader.beforeSave();
            if (world != null && worldReady) {
                try {
                    world.save(saveFile, version);
                    if (version.survival()) PlayerDataStore.save(saveFile, inventory, craftedItems, equippedArmor);
                    if (agricultureEnabled) FarmDataStore.save(saveFile, farmGrowth);
                    if (release100Enabled) AchievementDataStore.save(saveFile, achievements);
                    if (petsEnabled && creatureManager != null)
                        PetDataStore.save(saveFile, creatureManager.petModesSnapshot());
                    GameLog.info("Мир, инвентарь, питомцы, ферма и достижения автоматически сохранены при закрытии: " + saveFile.toAbsolutePath());
                } catch (Throwable error) {
                    GameLog.error("Не удалось сохранить мир при закрытии", error);
                }
            }
        } finally {
            try {
                if (multiplayerClient != null) multiplayerClient.close();
                for (RemotePlayerState remote : remotePlayers.values()) {
                    if (remote.avatar != null) remote.avatar.detach();
                }
                remotePlayers.clear();
                if (modLoader != null) modLoader.close();
                super.destroy();
            } finally {
                if (!exitNotified && onGameExit != null) {
                    exitNotified = true;
                    onGameExit.run();
                    GameLog.info("Колбэк возврата в лаунчер выполнен.");
                }
            }
        }
    }
}
