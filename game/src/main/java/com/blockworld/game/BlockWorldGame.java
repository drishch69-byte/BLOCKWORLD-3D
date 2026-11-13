package com.blockworld.game;

import com.blockworld.core.BlockHit;
import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.ItemType;
import com.blockworld.core.ToolType;
import com.blockworld.core.ArmorSlot;
import com.blockworld.core.ArmorProtection;
import com.blockworld.core.CraftingRecipe;
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
import com.blockworld.core.PlayerDataStore;
import com.blockworld.core.GameVersion;
import com.blockworld.core.GameSettings;
import com.blockworld.core.GamePaths;
import com.blockworld.core.ResourcePackManager;
import com.blockworld.core.ModLoader;
import com.blockworld.api.ModContext;
import com.blockworld.core.GameLog;
import com.blockworld.core.VoxelWorld;
import com.jme3.app.SimpleApplication;
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
    private final Path saveFile;
    private final boolean createNewWorld;
    private final Runnable onGameExit;
    private final GameSettings gameSettings;
    private final Map<BlockType, Integer> inventory = new EnumMap<>(BlockType.class);
    private final Map<ItemType, Integer> craftedItems = new EnumMap<>(ItemType.class);
    private final Set<ArmorSlot> equippedArmor = EnumSet.noneOf(ArmorSlot.class);
    private final Map<BlockPos, Float> farmGrowth = new HashMap<>();
    private final boolean equipmentEnabled;
    private final boolean agricultureEnabled;
    private final boolean release100Enabled;
    private final List<CraftingRecipe> craftingRecipes;
    private final List<TradeRecipe> tradeRecipes = TradeRecipe.villageOffers();
    private final AchievementTracker achievements = new AchievementTracker();
    private GameSounds gameSounds;
    private float fishingCooldown;
    private float biomeDiscoveryClock;
    private ToolType selectedTool;
    private int craftingPage;

    private VoxelWorld world;
    private ModLoader modLoader;
    private CreatureManager creatureManager;
    private PlayerAvatar playerAvatar;
    private boolean thirdPersonView;
    private Vector3f playerEyePosition = new Vector3f(0f, 12f, 8f);
    private boolean modsWorldReady;
    private int loadedModCount;
    private BitmapText hud;
    private BitmapText crosshair;
    private Geometry hudPanel;
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
    private boolean achievementsOpen;
    private float farmSaveClock;
    private boolean paused;
    private List<BlockType> hotbar;
    private BlockType selectedBlock = BlockType.GRASS;
    private volatile String statusMessage = "Добро пожаловать в BlockWorld!";
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
        this(version, saveFile, createNewWorld, onGameExit, GameSettings.load());
    }

    public BlockWorldGame(GameVersion version, Path saveFile, boolean createNewWorld,
                          Runnable onGameExit, GameSettings gameSettings) {
        this.version = version;
        this.equipmentEnabled = supportsEquipment(version);
        this.agricultureEnabled = supportsAgriculture(version);
        this.release100Enabled = supportsRelease100(version);
        List<CraftingRecipe> allRecipes = release100Enabled ? CraftingRecipe.release100Recipes()
                : agricultureEnabled ? CraftingRecipe.advancedRecipes() : CraftingRecipe.starterRecipes();
        this.craftingRecipes = equipmentEnabled ? allRecipes : List.copyOf(allRecipes.subList(0, 4));
        this.saveFile = saveFile;
        this.createNewWorld = createNewWorld;
        this.onGameExit = onGameExit;
        this.gameSettings = gameSettings == null ? GameSettings.DEFAULT : gameSettings;
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
        settings.setTitle("BLOCKWORLD 3D  |  " + version.id() + " " + version.displayName());
        settings.setResolution(gameSettings.width(), gameSettings.height());
        settings.setFullscreen(gameSettings.fullscreen());
        settings.setResizable(true);
        settings.setVSync(gameSettings.vsync());
        settings.setSamples(0);
        setSettings(settings);
        setShowSettings(false);
        GameLog.info("Запуск окна игры: " + version.id() + "; resolution=" + gameSettings.width() + "x" + gameSettings.height()
                + "; samples=0; vsync=" + gameSettings.vsync() + "; fullscreen=" + gameSettings.fullscreen() + "; resizable=true"
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
        playerAvatar = new PlayerAvatar(assetManager);
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
            inputManager.setCursorVisible(false);
            cam.setLocation(new Vector3f(0f, 12f, 8f));
            playerEyePosition = cam.getLocation().clone();
            cam.setRotation(new Quaternion().fromAngles(pitch, yaw, 0f));
        } else {
            cam.setLocation(new Vector3f(0f, 16f, 26f));
            cam.lookAt(new Vector3f(0f, 1f, 0f), Vector3f.UNIT_Y);
        }

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
        try {
            if (!createNewWorld && Files.exists(saveFile)) {
                try {
                    String savedWith = world.load(saveFile);
                    statusMessage = "Загружен мир. Последнее сохранение: " + savedWith + ".";
                    loaded = true;
                    if (!savedWith.equals(version.id())) statusMessage += " Открыт в версии " + version.id() + ".";
                    if (world.getBlockCount() == 0) {
                        statusMessage = "Сохранение пустое. Создаю новый мир вместо пустой сцены.";
                        loaded = false;
                    }
                } catch (IOException ex) {
                    statusMessage = "Не удалось загрузить мир: " + ex.getMessage();
                    try {
                        Path backup = saveFile.resolveSibling(saveFile.getFileName() + ".load-error-backup");
                        Files.copy(saveFile, backup, StandardCopyOption.REPLACE_EXISTING);
                        statusMessage += " Резервная копия: " + backup.getFileName() + ".";
                    } catch (IOException backupError) {
                        statusMessage += " Не удалось создать резервную копию файла.";
                    }
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
            if (loaded && release100Enabled) {
                try { AchievementDataStore.load(saveFile, achievements); }
                catch (IOException ex) { GameLog.error("Не удалось загрузить достижения", ex); }
            }
            if (!loaded) {
                long newSeed = ThreadLocalRandom.current().nextLong();
                world.generate(version, newSeed);
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

        crosshair = new BitmapText(guiFont);
        crosshair.setText("+");
        crosshair.setSize(24f);
        crosshair.setColor(ColorRGBA.White);
        crosshair.setLocalTranslation(settings.getWidth() / 2f - 5f, settings.getHeight() / 2f + 7f, 0f);
        guiNode.attachChild(crosshair);

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
        pauseText.setText("BLOCKWORLD 3D — ПАУЗА\n\nESC — продолжить игру\nF6 — сохранить мир\nF10 — выйти в главное меню");
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
        inputManager.addMapping("SLOT_1", new KeyTrigger(KeyInput.KEY_1));
        inputManager.addMapping("SLOT_2", new KeyTrigger(KeyInput.KEY_2));
        inputManager.addMapping("SLOT_3", new KeyTrigger(KeyInput.KEY_3));
        inputManager.addMapping("SLOT_4", new KeyTrigger(KeyInput.KEY_4));
        inputManager.addMapping("SLOT_5", new KeyTrigger(KeyInput.KEY_5));
        inputManager.addMapping("SLOT_6", new KeyTrigger(KeyInput.KEY_6));
        inputManager.addMapping("SLOT_7", new KeyTrigger(KeyInput.KEY_7));
        inputManager.addMapping("SLOT_8", new KeyTrigger(KeyInput.KEY_8));
        inputManager.addMapping("SLOT_9", new KeyTrigger(KeyInput.KEY_9));
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
            if (!isPressed) {
                return;
            }
            if (paused && !name.equals("TOGGLE_PAUSE") && !name.equals("SAVE") && !name.equals("EXIT_GAME")) return;
            switch (name) {
                case BREAK -> breakTargetBlock();
                case PLACE -> placeTargetBlock();
                case SAVE -> saveWorld();
                case "TOGGLE_CAMERA" -> toggleCameraView();
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
                case "CRAFT_PAGE_PREV" -> changeCraftPage(-1);
                case "CRAFT_PAGE_NEXT" -> changeCraftPage(1);
                case "EQUIP_HELMET" -> toggleArmor(ArmorSlot.HELMET);
                case "EQUIP_CHEST" -> toggleArmor(ArmorSlot.CHESTPLATE);
                case "EQUIP_LEGS" -> toggleArmor(ArmorSlot.LEGGINGS);
                case "EQUIP_BOOTS" -> toggleArmor(ArmorSlot.BOOTS);
                case "TOGGLE_PAUSE" -> setPaused(!paused);
                case "EXIT_GAME" -> {
                    if (paused) stop();
                    else { statusMessage = "Нажми ESC, чтобы открыть меню паузы, затем F10 для выхода."; updateHud(); }
                }
                case "SLOT_1" -> { if (furnaceOpen) smeltInFurnace(0); else if (tradeOpen) tradeWithVillager(0); else if (craftingOpen && 1 <= craftingRecipes.size()) craftRecipe(0); else if (!craftingOpen && !achievementsOpen) selectSlot(0); }
                case "SLOT_2" -> { if (furnaceOpen) smeltInFurnace(1); else if (tradeOpen) tradeWithVillager(1); else if (craftingOpen && 2 <= craftingRecipes.size()) craftRecipe(1); else if (!craftingOpen) selectSlot(1); }
                case "SLOT_3" -> { if (furnaceOpen) smeltInFurnace(2); else if (tradeOpen) tradeWithVillager(2); else if (craftingOpen && 3 <= craftingRecipes.size()) craftRecipe(2); else if (!craftingOpen) selectSlot(2); }
                case "SLOT_4" -> { if (furnaceOpen) smeltInFurnace(3); else if (tradeOpen) tradeWithVillager(3); else if (craftingOpen && 4 <= craftingRecipes.size()) craftRecipe(3); else if (!craftingOpen) selectSlot(3); }
                case "SLOT_5" -> { if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 5 <= craftingRecipes.size()) craftRecipe(4); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(4); }
                case "SLOT_6" -> { if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 6 <= craftingRecipes.size()) craftRecipe(5); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(5); }
                case "SLOT_7" -> { if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 7 <= craftingRecipes.size()) craftRecipe(6); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(6); }
                case "SLOT_8" -> { if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 8 <= craftingRecipes.size()) craftRecipe(7); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(7); }
                case "SLOT_9" -> { if (!tradeOpen && !achievementsOpen && !furnaceOpen && craftingOpen && 9 <= craftingRecipes.size()) craftRecipe(8); else if (!craftingOpen && !furnaceOpen && !tradeOpen && !achievementsOpen) selectSlot(8); }
                default -> { }
            }
        };
        inputManager.addListener(actions, BREAK, PLACE, SAVE, EAT, "EXIT_GAME", "TOGGLE_PAUSE",
                "SLOT_1", "SLOT_2", "SLOT_3", "SLOT_4", "SLOT_5",
                "SLOT_6", "SLOT_7", "SLOT_8", "SLOT_9", "RENDER_DISTANCE", "TOGGLE_CULLING", "WEATHER", TALK,
                "TOGGLE_INVENTORY", "TOGGLE_CRAFTING", "CYCLE_TOOL", "USE_UTILITY", "FISHING_CAST", "TOGGLE_ACHIEVEMENTS", "CRAFT_PAGE_PREV", "CRAFT_PAGE_NEXT",
                "EQUIP_HELMET", "EQUIP_CHEST", "EQUIP_LEGS", "EQUIP_BOOTS", "TOGGLE_CAMERA");
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
                    "BLOCKWORLD 3D — ошибка игрового цикла", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void updateFrame(float tpf) {
        if (tpf > 0f) fpsEstimate = fpsEstimate * 0.88f + (1f / tpf) * 0.12f;
        if (version.id().compareTo("0.6.0") >= 0) updateNature(tpf);

        if (worldDataReady) {
            if (!modsWorldReady && modLoader != null) {
                modLoader.worldReady();
                modsWorldReady = true;
            }
            if (!worldNodeAttached) {
                rootNode.attachChild(world.getNode());
                creatureManager.sync(world);
                rootNode.attachChild(creatureManager.getNode());
                GameLog.info("Существа присоединены к сцене: " + creatureManager.size());
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
                world.updateStreaming(cam.getLocation(), worldReady ? 2 : 5);
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
        if (worldReady && creatureManager != null && world != null) {
            creatureManager.update(tpf, version.survival() ? playerEyePosition : cam.getLocation(), world);
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

    private void setPaused(boolean value) {
        if (paused == value || guiNode == null) return;
        paused = value;
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
                    gameSettings.vsync(), distance, gameSettings.fov()));
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

    private void breakTargetBlock() {
        if (!worldReady) {
            statusMessage = "Мир ещё загружается.";
            updateHud();
            return;
        }
        BlockHit hit = getTargetBlock();
        if (hit == null) {
            statusMessage = "Рядом нет блока.";
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
        if (!worldReady) return;
        if (tradeOpen) {
            tradeOpen = false;
            showInventoryOverlay(false);
            statusMessage = "Торговля закрыта.";
            updateHud();
            return;
        }
        BlockHit hit = getTargetBlock();
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
        } else if (creature.type() == com.blockworld.core.CreatureType.VILLAGER && release100Enabled && version.survival()) {
            inventoryOpen = false;
            craftingOpen = false;
            furnaceOpen = false;
            achievementsOpen = false;
            tradeOpen = true;
            showInventoryOverlay(true);
            statusMessage = "Житель открыл торговлю. Нажми 1–4 для обмена; F — закрыть.";
        } else if (creature.type() == com.blockworld.core.CreatureType.VILLAGER) {
            statusMessage = "Житель: Добро пожаловать в нашу деревню! Загляни в дом.";
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
                statusMessage = "Грядка создана. Наведи прицел на неё и нажми G, чтобы посадить семена.";
            }
        } else if (craftedItems.getOrDefault(ItemType.FURNACE, 0) > 0) {
            BlockPos place = hit.adjacentPosition();
            Vector3f center = new Vector3f(place.x(), place.y(), place.z());
            if (cam.getLocation().distance(center) < 1.45f) {
                statusMessage = "Слишком близко: нельзя поставить печь внутри игрока.";
            } else if (world.addBlock(place, BlockType.FURNACE)) {
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
            float next = FarmRules.advance(previous, tpf);
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
    }

    private void changeCraftPage(int delta) {
        if (!craftingOpen) return;
        int pageCount = Math.max(1, (craftingRecipes.size() + 8) / 9);
        craftingPage = Math.max(0, Math.min(pageCount - 1, craftingPage + delta));
        updateInventoryUi();
    }

    private void toggleInventory() {
        if (!version.survival() || !worldReady) return;
        inventoryOpen = !inventoryOpen;
        craftingOpen = false;
        furnaceOpen = false;
        tradeOpen = false;
        achievementsOpen = false;
        showInventoryOverlay(inventoryOpen);
        statusMessage = inventoryOpen
                ? (release100Enabled ? "Инвентарь открыт. H/J/K/L — броня, T — инструмент, P — рыбалка, M — достижения."
                    : agricultureEnabled ? "Инвентарь открыт. H/J/K/L — броня, T — инструмент, G — ферма, I — закрыть."
                    : equipmentEnabled ? "Инвентарь открыт. H/J/K/L — экипировка, I — закрыть, C — крафт."
                    : "Инвентарь открыт. I — закрыть, C — мастерская.")
                : "Инвентарь закрыт.";
        updateHud();
    }

    private void toggleCrafting() {
        if (!version.survival() || !worldReady) return;
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
        if (furnaceOpen) {
            text.append("BLOCKWORLD 3D — ПЕЧЬ\n\n");
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
            text.append("BLOCKWORLD 3D — ТОРГОВЕЦ\n\n");
            text.append("Обменяй ресурсы на полезные предметы.\n\n");
            for (int i = 0; i < tradeRecipes.size(); i++) {
                TradeRecipe offer = tradeRecipes.get(i);
                text.append(i + 1).append(". ").append(offer.description()).append("\n   Нужно: ")
                        .append(offer.costText()).append(offer.canTrade(craftedItems) ? "  [ГОТОВО]" : "  [НЕ ХВАТАЕТ]").append("\n");
            }
            text.append("\nНажми 1–4 для обмена, F — закрыть.");
        } else if (achievementsOpen) {
            text.append("BLOCKWORLD 3D — ДОСТИЖЕНИЯ\n\n");
            text.append("Открыто: ").append(achievements.unlockedCount()).append("/").append(achievements.totalCount())
                    .append("    Биомы: ").append(achievements.discoveredBiomeCount()).append("/5\n\n");
            for (Achievement achievement : Achievement.values()) {
                text.append(achievements.isUnlocked(achievement) ? "[✓] " : "[ ] ")
                        .append(achievement.title()).append(" — ").append(achievement.description()).append("\n");
            }
            text.append("\nM — закрыть достижения.");
        } else if (craftingOpen) {
            int pageCount = Math.max(1, (craftingRecipes.size() + 8) / 9);
            int start = craftingPage * 9;
            int end = Math.min(craftingRecipes.size(), start + 9);
            text.append("BLOCKWORLD 3D — МАСТЕРСКАЯ  |  СТРАНИЦА ").append(craftingPage + 1)
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
            text.append("BLOCKWORLD 3D — ИНВЕНТАРЬ\n\nБлоки:\n");
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

    private void selectSlot(int index) {
        if (index < 0 || index >= hotbar.size()) return;
        selectedBlock = hotbar.get(index);
        statusMessage = "Выбран блок: " + selectedBlock.displayName() + ".";
        updateHud();
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
            if (release100Enabled) AchievementDataStore.save(saveFile, achievements);
            statusMessage = "Мир, инвентарь, ферма и достижения сохранены!";
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
                ? "WASD — ходьба    Мышь — обзор    F5 — камера    ПРОБЕЛ — прыжок    E — еда    "
                    + (equipmentEnabled ? "T — инструмент    " : "")
                    + "I — инвентарь    C — крафт    F — печь/общение"
                    + (agricultureEnabled ? "    G — ферма/печь" : "")
                    + (release100Enabled ? "    P — рыбалка    M — достижения" : "") + "    ESC — пауза"
                : "WASD — движение    Мышь — обзор    F — общение/контакт    ESC — пауза";
        String needs = version.survival()
                ? "Здоровье: " + (int) health + "/20    Голод: " + (int) hunger + "/20    Еда: " + foodCount
                    + (equipmentEnabled ? "    Защита: " + armorDefense() + "/8"
                        + "    Инструмент: " + (selectedTool == null ? "нет" : selectedTool.item().displayName()) : "")
                : "Творческий режим";
        String nature = version.id().compareTo("0.6.0") >= 0
                ? String.format(java.util.Locale.ROOT, "Время: %s    Погода: %s    R — сменить погоду",
                    dayClock < 0.25f ? "утро" : dayClock < 0.50f ? "день" : dayClock < 0.75f ? "вечер" : "ночь", raining ? "дождь" : "ясно")
                : "";
        String biomeLine = agricultureEnabled && worldReady
                ? "Биом: " + world.getBiomeAt(Math.round(cam.getLocation().x), Math.round(cam.getLocation().z)).displayName()
                    + (release100Enabled ? "    Достижения: " + achievements.unlockedCount() + "/" + achievements.totalCount() : "")
                : "";
        String loading = worldReady ? "Мир загружен" : (worldDataReady ? loadingMessage : "Фоновая генерация/загрузка мира...");
        String performance = String.format(java.util.Locale.ROOT,
                "FPS: %.0f    Меши чанков: %d    Чанки данных: %d    Очередь: %d    Дальность: %d    Блоки: %,d",
                fpsEstimate, world.getRenderedChunkCount(), world.getChunkCount(),
                world.getPendingChunkCount(), world.getRenderDistanceChunks(), world.getBlockCount());
        String spawn = String.format(java.util.Locale.ROOT, "Spawn: X %.0f / Y %.0f / Z %.0f",
                spawnPosition.x, spawnPosition.y, spawnPosition.z);
        hud.setText("BLOCKWORLD 3D  |  " + version.id() + " " + version.displayName()
                + "\n" + controls + "    F6 — сохранить    F7 — дальность    F8 — режим граней    F10 — выход из паузы"
                + "\n" + needs
                + (nature.isEmpty() ? "" : "\n" + nature)
                + (biomeLine.isEmpty() ? "" : "\n" + biomeLine)
                + "\n" + performance + "    Моды: " + loadedModCount
                + "    Существа: " + world.getCreatureCount()
                + "\n" + spawn + "    Seed: " + world.getSeed() + "    Блок: " + selectedBlock.displayName()
                + "\n" + bar
                + "\n" + "Грани: " + (twoSidedRendering ? "двусторонние" : "оптимизированные")
                + "\n" + loading
                + "\n" + statusMessage);
        if (inventoryOpen || craftingOpen || furnaceOpen || tradeOpen || achievementsOpen) updateInventoryUi();
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        if (hud != null && guiNode != null) {
            hud.setLocalTranslation(15f, height - 14f, 0f);
            if (hudPanel != null) hudPanel.setLocalTranslation(7f, height - 181f, 1f);
            if (crosshair != null) crosshair.setLocalTranslation(width / 2f - 5f, height / 2f + 7f, 0f);
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
                "BLOCKWORLD 3D — ошибка графического движка", JOptionPane.ERROR_MESSAGE));
    }

    @Override
    public void destroy() {
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
                    GameLog.info("Мир, инвентарь и достижения автоматически сохранены при закрытии: " + saveFile.toAbsolutePath());
                } catch (Throwable error) {
                    GameLog.error("Не удалось сохранить мир при закрытии", error);
                }
            }
        } finally {
            try {
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
