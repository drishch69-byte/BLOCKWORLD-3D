package com.blockworld.ui;

import com.blockworld.core.GamePaths;
import com.blockworld.core.GameLog;
import com.blockworld.core.GameVersion;
import com.blockworld.core.VersionManager;
import com.blockworld.core.WorldCatalog;
import com.blockworld.core.WorldEntry;
import com.blockworld.core.GameSettings;
import com.blockworld.core.ModManager;
import com.blockworld.core.ResourcePackManager;
import com.blockworld.game.BlockWorldGame;
import com.blockworld.net.MultiplayerClient;
import com.blockworld.net.MultiplayerServer;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.ListSelectionModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Desktop launcher: every saved world is visible regardless of selected game profile. */
public final class Launcher {
    private static volatile Launcher activeLauncher;
    private JFrame frame;
    private JComboBox<GameVersion> versionSelector;
    private JComboBox<WorldEntry> worldSelector;
    private JLabel status;

    private Launcher() {
        activeLauncher = this;
        createWindow();
        refreshWorldList(null);
    }

    public static void main(String[] args) {
        GameLog.info("===== BLOCKWORLD process start =====");
        GameLog.info("Java=" + System.getProperty("java.version")
                + "; VM=" + System.getProperty("java.vm.name")
                + "; OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "; arch=" + System.getProperty("os.arch")
                + "; maxHeapMiB=" + (Runtime.getRuntime().maxMemory() / (1024 * 1024))
                + "; cwd=" + Path.of("").toAbsolutePath());
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            GameLog.error("Необработанная ошибка в потоке " + thread.getName(), error);
            Launcher launcher = activeLauncher;
            if (launcher != null) launcher.showFatalError("Необработанная ошибка", error);
        });
        SwingUtilities.invokeLater(() -> {
            try {
                new Launcher();
                GameLog.info("Лаунчер открыт. Java=" + System.getProperty("java.version")
                        + "; OS=" + System.getProperty("os.name")
                        + "; arch=" + System.getProperty("os.arch")
                        + "; cwd=" + Path.of("").toAbsolutePath());
            } catch (Throwable error) {
                GameLog.error("Не удалось открыть лаунчер", error);
                JOptionPane.showMessageDialog(null,
                        "Не удалось открыть лаунчер\n" + formatThrowable(error)
                                + "\n\nЖурнал: .logs\\latest-run.log",
                        "BLOCKWORLD 3D — ошибка запуска", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void showFatalError(String title, Throwable error) {
        Runnable action = () -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.toFront();
            }
            JOptionPane.showMessageDialog(frame,
                    title + "\n" + formatThrowable(error)
                            + "\n\nПодробности: .logs\\latest-run.log",
                    "BLOCKWORLD 3D — ошибка", JOptionPane.ERROR_MESSAGE);
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static String formatThrowable(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String value = trace.toString();
        return value.length() > 9000 ? value.substring(0, 9000) + "\n... (обрезано)" : value;
    }

    private void createWindow() {
        frame = new JFrame("BLOCKWORLD 3D — лаунчер");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setSize(850, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("BLOCKWORLD 3D", JLabel.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        title.setBorder(BorderFactory.createEmptyBorder(20, 12, 8, 12));
        frame.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 12));
        center.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));

        JPanel selectors = new JPanel(new GridLayout(2, 2, 10, 8));
        selectors.add(new JLabel("Версия игры:"));
        versionSelector = new JComboBox<>(VersionManager.getSupportedVersions().toArray(GameVersion[]::new));
        versionSelector.setSelectedItem(VersionManager.getDefaultVersion());
        versionSelector.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        selectors.add(versionSelector);
        selectors.add(new JLabel("Сохранённый мир (общий для всех версий):"));
        worldSelector = new JComboBox<>();
        worldSelector.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        selectors.add(worldSelector);
        center.add(selectors, BorderLayout.NORTH);

        JTextArea details = new JTextArea(
                "0.1.0 Classic — небольшой мир и базовое строительство.\n" +
                "0.2.0 Builder — деревья, листья, песок и строительство.\n" +
                "0.3.0 Adventure — пещеры, уголь, железо и золото.\n" +
                "0.4.0 Survival — ходьба, прыжки, здоровье, голод и сбор блоков.\n" +
                "0.5.0 Chunk Engine — чанки 16×16, потоковая загрузка мира, облегчённый рендеринг.\n" +
                "0.5.1 Chunk Engine Hotfix — исправление камеры появления и диагностика очереди чанков.\n" +
                "0.5.2 World Render Fix — заметный HUD, безопасное появление и журнал ошибок мешей.\n" +
                "0.5.3 Stable Render Diagnostics — тестовая площадка, двусторонние меши и F8 для переключения режима граней.\n" +
                "0.6.0 Nature Engine — реки, вода, подземные лавовые карманы, цикл дня/ночи и погода (R).\n" +
                "0.6.1 Compiler Fix — исправление сборки и обновление профиля.\n" +
                "0.6.2 Safe Launch — защита от аварийного закрытия, журнал ошибок и безопасная очистка.\n" +
                "0.6.3 Startup Crash Diagnostics — отметки этапов запуска, JVM crash-отчёты и дампы памяти в .logs.\n" +
                "0.7.5 Texture Pack Engine — настройки видео, API модов, менеджер модов и настоящие PNG-текстуры ресурс-паков.\n" +
                "0.8.0 Creatures Update — коровы, овцы, свиньи, куры, жители, две маленькие деревни и блуждающие слаймы.\n" +
                "0.8.5 Inventory & Crafting — инвентарь (I), мастерская (C), рецепты досок, палок, верстака и факелов.\n" +
                "0.9.0 Tools & Equipment — кирки с уровнями добычи, лопата, четыре слота брони; T — выбрать инструмент, H/J/K/L — надеть или снять броню в инвентаре.\n" +
                "1.0.0 Trading, Fishing & Achievements — торговля с жителями, рыбалка, медная и изумрудная руда, достижения и звуковые эффекты. F — открыть торговлю рядом с жителем, P — ловить рыбу при наведении на воду, M — достижения.\n\n" +
                "ВАЖНО: список миров не зависит от выбранной версии. Один и тот же файл мира\n" +
                "можно открыть в любом установленном профиле BLOCKWORLD. Перед новым миром\n" +
                "создаётся резервная копия файла с таким же названием.");
        details.setEditable(false);
        details.setFocusable(false);
        details.setOpaque(false);
        details.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        center.add(new JScrollPane(details), BorderLayout.CENTER);
        frame.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 24, 18, 24));
        JPanel buttons = new JPanel(new GridLayout(2, 4, 7, 7));
        JButton play = new JButton("ИГРАТЬ");
        JButton newWorld = new JButton("НОВЫЙ МИР");
        JButton settings = new JButton("НАСТРОЙКИ");
        JButton mods = new JButton("МОДЫ");
        JButton packs = new JButton("РЕСУРС-ПАКИ");
        JButton multiplayer = new JButton("СЕТЕВАЯ ИГРА");
        JButton refresh = new JButton("ОБНОВИТЬ СПИСОК");
        JButton worlds = new JButton("ПАПКА МИРОВ");
        JButton close = new JButton("ВЫХОД");
        play.addActionListener(event -> launchSelectedWorld());
        newWorld.addActionListener(event -> createWorld());
        settings.addActionListener(event -> showSettingsDialog());
        mods.addActionListener(event -> showModsDialog());
        packs.addActionListener(event -> showResourcePacksDialog());
        multiplayer.addActionListener(event -> showMultiplayerDialog());
        refresh.addActionListener(event -> refreshWorldList(selectedWorldPath()));
        worlds.addActionListener(event -> openWorldFolder());
        close.addActionListener(event -> frame.dispose());
        buttons.add(play);
        buttons.add(newWorld);
        buttons.add(settings);
        buttons.add(mods);
        buttons.add(packs);
        buttons.add(multiplayer);
        buttons.add(refresh);
        buttons.add(worlds);
        buttons.add(close);
        bottom.add(buttons, BorderLayout.CENTER);
        status = new JLabel("Миры общие для всех версий. Создай новый мир или выбери сохранённый.");
        status.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        bottom.add(status, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private Path selectedWorldPath() {
        WorldEntry selected = (WorldEntry) worldSelector.getSelectedItem();
        return selected == null ? null : selected.file();
    }

    private void refreshWorldList(Path selectPath) {
        if (worldSelector == null) {
            return;
        }
        try {
            var worlds = WorldCatalog.list();
            worldSelector.removeAllItems();
            for (WorldEntry world : worlds) {
                worldSelector.addItem(world);
            }
            if (selectPath != null) {
                for (int i = 0; i < worldSelector.getItemCount(); i++) {
                    WorldEntry entry = worldSelector.getItemAt(i);
                    if (entry.file().equals(selectPath)) {
                        worldSelector.setSelectedIndex(i);
                        break;
                    }
                }
            }
            if (worlds.isEmpty()) {
                status.setText("Пока нет сохранённых миров. Нажми «НОВЫЙ МИР».");
            } else {
                status.setText("Найдено миров: " + worlds.size() + ". Они доступны во всех версиях.");
            }
        } catch (IOException ex) {
            status.setText("Не удалось прочитать список миров: " + ex.getMessage());
        }
    }

    private void launchSelectedWorld() {
        WorldEntry selected = (WorldEntry) worldSelector.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(frame,
                    "Сохранённых миров пока нет. Сначала создай новый мир.",
                    "Нет миров", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        launchGame(selected.file(), selected.name(), false);
    }

    private void createWorld() {
        String name = JOptionPane.showInputDialog(frame,
                "Название нового мира (например, Мой_мир):", "Новый мир", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        try {
            Path file = WorldCatalog.pathForName(name);
            String safeName = file.getFileName().toString().replaceFirst("(?i)\\.bwworld$", "");
            if (Files.exists(file)) {
                int answer = JOptionPane.showConfirmDialog(frame,
                        "Мир «" + safeName + "» уже существует. Создать резервную копию и заменить его новым?",
                        "Мир уже существует", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) {
                    return;
                }
                backupExistingWorld(file);
            }
            launchGame(file, safeName, true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Не удалось подготовить мир:\n" + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchGame(Path worldFile, String worldName, boolean createNewWorld) {
        GameVersion selected = (GameVersion) versionSelector.getSelectedItem();
        GameVersion versionToLaunch = selected == null ? VersionManager.getDefaultVersion() : selected;

        status.setText("Запуск " + versionToLaunch.id() + " — мир «" + worldName + "»...");
        GameLog.info("Запрошен запуск профиля " + versionToLaunch.id() + " для мира «" + worldName
                + "»; файл=" + worldFile.toAbsolutePath());
        frame.setVisible(false);

        Runnable onGameExit = () -> SwingUtilities.invokeLater(() -> {
            refreshWorldList(worldFile);
            frame.setVisible(true);
            frame.toFront();
            status.setText("Мир «" + worldName + "» закрыт. Он доступен и в других версиях.");
        });

        Thread gameThread = new Thread(() -> {
            try {
                new BlockWorldGame(versionToLaunch, worldFile, createNewWorld, onGameExit, GameSettings.load()).launch();
            } catch (Throwable error) {
                GameLog.error("Ошибка запуска игрового окна (включая системные/native ошибки)", error);
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.toFront();
                    status.setText("Игра завершилась с ошибкой. Журнал: .logs\\latest-run.log");
                    JOptionPane.showMessageDialog(frame,
                            "Не удалось запустить BlockWorld:\n" + formatThrowable(error)
                                    + "\n\nПроверь Java 17+, драйвер видеокарты и журнал .logs\\latest-run.log.",
                            "Ошибка запуска", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "BlockWorld-Game-Thread");
        gameThread.setDaemon(false);
        gameThread.setUncaughtExceptionHandler((thread, error) -> {
            GameLog.error("Авария потока запуска игры " + thread.getName(), error);
            SwingUtilities.invokeLater(() -> {
                frame.setVisible(true);
                frame.toFront();
                status.setText("Поток игры аварийно завершился. См. .logs\\latest-run.log и hs_err_pid*.log.");
                JOptionPane.showMessageDialog(frame,
                        "Поток запуска игры завершился с ошибкой:\n" + formatThrowable(error)
                                + "\n\nПроверь .logs\\latest-run.log и .logs\\hs_err_pid*.log.",
                        "BLOCKWORLD 3D — авария запуска", JOptionPane.ERROR_MESSAGE);
            });
        });
        GameLog.info("Поток запуска игры создан; id=" + gameThread.getId());
        gameThread.start();
    }

    private void backupExistingWorld(Path worldFile) throws IOException {
        if (!Files.exists(worldFile)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = worldFile.resolveSibling(worldFile.getFileName() + ".backup-" + timestamp);
        int suffix = 1;
        while (Files.exists(backup)) {
            backup = worldFile.resolveSibling(worldFile.getFileName() + ".backup-" + timestamp + "-" + suffix++);
        }
        Files.copy(worldFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
        status.setText("Резервная копия: " + backup.getFileName());
    }

    private void openWorldFolder() {
        try {
            Files.createDirectories(GamePaths.worldsDirectory());
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Открытие папок не поддерживается в этой системе.");
            }
            Desktop.getDesktop().open(GamePaths.worldsDirectory().toFile());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Папка миров: " + GamePaths.worldsDirectory() + "\n" + ex.getMessage(),
                    "Папка миров", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showSettingsDialog() {
        GameSettings current = GameSettings.load();
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Настройки", true);
        dialog.setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridLayout(5, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));
        JComboBox<String> resolution = new JComboBox<>(new String[]{"1024x640", "1280x720", "1600x900", "1920x1080"});
        resolution.setSelectedItem(current.width() + "x" + current.height());
        JCheckBox fullscreen = new JCheckBox("Полноэкранный режим", current.fullscreen());
        JCheckBox vsync = new JCheckBox("Вертикальная синхронизация (VSync)", current.vsync());
        JComboBox<Integer> distance = new JComboBox<>(new Integer[]{3, 4, 6, 8});
        distance.setSelectedItem(current.renderDistance());
        JSpinner fov = new JSpinner(new SpinnerNumberModel(current.fov(), 55, 100, 1));
        form.add(new JLabel("Разрешение:")); form.add(resolution);
        form.add(new JLabel("Дальность чанков:")); form.add(distance);
        form.add(new JLabel("Поле зрения (FOV):")); form.add(fov);
        form.add(new JLabel("Экран:")); form.add(fullscreen);
        form.add(new JLabel("Синхронизация:")); form.add(vsync);
        dialog.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton("ПО УМОЛЧАНИЮ");
        JButton save = new JButton("СОХРАНИТЬ");
        JButton cancel = new JButton("ОТМЕНА");
        reset.addActionListener(e -> {
            resolution.setSelectedItem("1024x640"); distance.setSelectedItem(3);
            fov.setValue(68); fullscreen.setSelected(false); vsync.setSelected(false);
        });
        save.addActionListener(e -> {
            String[] parts = String.valueOf(resolution.getSelectedItem()).split("x");
            try {
                GameSettings settings = new GameSettings(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        fullscreen.isSelected(), vsync.isSelected(), (Integer) distance.getSelectedItem(), (Integer) fov.getValue());
                GameSettings.save(settings);
                status.setText("Настройки сохранены. Разрешение и экран применятся при следующем запуске.");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Не удалось сохранить настройки: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> dialog.dispose());
        actions.add(reset); actions.add(save); actions.add(cancel);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.pack(); dialog.setMinimumSize(new Dimension(540, 300)); dialog.setLocationRelativeTo(frame); dialog.setVisible(true);
    }

    private void showModsDialog() {
        ModManager manager = new ModManager();
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Моды", true);
        dialog.setLayout(new BorderLayout(8, 8));
        DefaultListModel<ModManager.ModInfo> model = new DefaultListModel<>();
        JList<ModManager.ModInfo> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JLabel info = new JLabel("Моды запускают Java-код. Устанавливай только файлы из источников, которым доверяешь.");
        info.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        dialog.add(info, BorderLayout.NORTH);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(2, 3, 6, 6));
        JButton install = new JButton("УСТАНОВИТЬ JAR");
        JButton toggle = new JButton("ВКЛ / ВЫКЛ");
        JButton remove = new JButton("УДАЛИТЬ");
        JButton folder = new JButton("ПАПКА МОДОВ");
        JButton refresh = new JButton("ОБНОВИТЬ");
        JButton close = new JButton("ЗАКРЫТЬ");
        Runnable reload = () -> {
            model.clear();
            try { for (ModManager.ModInfo mod : manager.listAll()) model.addElement(mod); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка модов", JOptionPane.ERROR_MESSAGE); }
        };
        install.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Выбери мод .jar");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Java mod (*.jar)", "jar"));
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Мод — исполняемый Java-файл. Он может получить права текущего пользователя. Устанавливать только если доверяешь автору. Продолжить?",
                    "Предупреждение безопасности", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try { manager.install(chooser.getSelectedFile().toPath()); reload.run(); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка установки", JOptionPane.ERROR_MESSAGE); }
        });
        toggle.addActionListener(e -> {
            ModManager.ModInfo selected = list.getSelectedValue(); if (selected == null) return;
            if (!selected.enabled()) {
                int answer = JOptionPane.showConfirmDialog(dialog,
                        "При включении этот мод сможет выполнять Java-код внутри игры. Продолжить?",
                        "Включить мод", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) return;
            }
            try { manager.setEnabled(selected, !selected.enabled()); reload.run(); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE); }
        });
        remove.addActionListener(e -> {
            ModManager.ModInfo selected = list.getSelectedValue(); if (selected == null) return;
            int answer = JOptionPane.showConfirmDialog(dialog, "Удалить мод «" + selected.title() + "»?", "Удалить мод", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) try { manager.remove(selected); reload.run(); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE); }
        });
        folder.addActionListener(e -> openFolder(GamePaths.modsDirectory()));
        refresh.addActionListener(e -> reload.run());
        close.addActionListener(e -> dialog.dispose());
        buttons.add(install); buttons.add(toggle); buttons.add(remove);
        buttons.add(folder); buttons.add(refresh); buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);
        reload.run();
        dialog.setSize(760, 430); dialog.setLocationRelativeTo(frame); dialog.setVisible(true);
    }

    private void showMultiplayerDialog() {
        Object[] options = {"Создать LAN-сервер", "Подключиться", "Отмена"};
        int choice = JOptionPane.showOptionDialog(frame,
                "Сетевая комната: сервер и чат. Синхронизация игрового мира будет добавлена отдельно.",
                "BLOCKWORLD 3D — Multiplayer", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 0) hostMultiplayerServer();
        else if (choice == 1) joinMultiplayerServer();
    }

    private void hostMultiplayerServer() {
        JTextField port = new JTextField("25575");
        JTextField limit = new JTextField("8");
        JPasswordField password = new JPasswordField();
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Порт:")); form.add(port);
        form.add(new JLabel("Игроков (1–32):")); form.add(limit);
        form.add(new JLabel("Пароль (необязательно):")); form.add(password);
        if (JOptionPane.showConfirmDialog(frame, form, "Создать LAN-сервер", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        MultiplayerServer server = null;
        MultiplayerClient client = null;
        try {
            server = new MultiplayerServer(new String(password.getPassword()), Integer.parseInt(limit.getText().trim()));
            int actualPort = server.start(Integer.parseInt(port.getText().trim()));
            client = new MultiplayerClient();
            showMultiplayerLobby("Сервер запущен. Порт: " + actualPort + ". Друзьям в одной Wi-Fi сети нужен локальный IP компьютера.",
                    client, server, "Host", new String(password.getPassword()), "127.0.0.1", actualPort);
        } catch (Exception error) {
            if (client != null) client.close();
            if (server != null) server.close();
            JOptionPane.showMessageDialog(frame, "Не удалось создать сервер: " + error.getMessage(), "Сетевая игра", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinMultiplayerServer() {
        JTextField host = new JTextField("127.0.0.1");
        JTextField port = new JTextField("25575");
        JTextField nickname = new JTextField("Player");
        JPasswordField password = new JPasswordField();
        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Адрес:")); form.add(host);
        form.add(new JLabel("Порт:")); form.add(port);
        form.add(new JLabel("Никнейм:")); form.add(nickname);
        form.add(new JLabel("Пароль:")); form.add(password);
        if (JOptionPane.showConfirmDialog(frame, form, "Подключиться к серверу", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        showMultiplayerLobby("Подключение к " + host.getText() + ":" + port.getText(),
                new MultiplayerClient(), null, nickname.getText(), new String(password.getPassword()), host.getText(),
                Integer.parseInt(port.getText().trim()));
    }

    private void showMultiplayerLobby(String infoText, MultiplayerClient client, MultiplayerServer server,
                                      String nickname, String password, String host, int port) {
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Сетевая комната", false);
        dialog.setLayout(new BorderLayout(8, 8));
        JLabel info = new JLabel("<html>" + infoText + "<br>Предварительная версия: чат работает, игровой мир пока не синхронизируется.</html>");
        info.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        JTextArea chat = new JTextArea(); chat.setEditable(false); chat.setLineWrap(true);
        JTextField message = new JTextField(); JButton send = new JButton("ОТПРАВИТЬ");
        Runnable connect = () -> {
            try {
                client.connect(host, port, password, nickname, line -> SwingUtilities.invokeLater(() -> {
                    chat.append(line + "\\n"); chat.setCaretPosition(chat.getDocument().getLength());
                }));
                info.setText("<html>Подключено к " + host + ":" + port + "<br>Чат доступен. Мир пока не синхронизируется.</html>");
            } catch (Exception error) {
                chat.append("Ошибка подключения: " + error.getMessage() + "\\n");
                JOptionPane.showMessageDialog(dialog, error.getMessage(), "Сетевая игра", JOptionPane.ERROR_MESSAGE);
            }
        };
        connect.run();
        Runnable sendMessage = () -> {
            try { client.sendChat(message.getText()); message.setText(""); }
            catch (IOException error) { JOptionPane.showMessageDialog(dialog, error.getMessage(), "Чат", JOptionPane.ERROR_MESSAGE); }
        };
        send.addActionListener(e -> sendMessage.run()); message.addActionListener(e -> sendMessage.run());
        JPanel bottom = new JPanel(new BorderLayout(6, 6)); bottom.add(message, BorderLayout.CENTER); bottom.add(send, BorderLayout.EAST);
        dialog.add(info, BorderLayout.NORTH); dialog.add(new JScrollPane(chat), BorderLayout.CENTER); dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(650, 420); dialog.setLocationRelativeTo(frame);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { client.close(); if (server != null) server.close(); }
            @Override public void windowClosing(java.awt.event.WindowEvent event) { client.close(); if (server != null) server.close(); }
        });
        dialog.setVisible(true);
    }
    private void showResourcePacksDialog() {
        ResourcePackManager manager = new ResourcePackManager();
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Ресурс-паки", true);
        dialog.setLayout(new BorderLayout(8, 8));
        JComboBox<ResourcePackManager.ResourcePackInfo> packs = new JComboBox<>();
        JLabel description = new JLabel("PNG-текстуры 16×16: textures/blocks/<block-id>.png; доступны варианты _top, _side, _bottom.");
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(18, 18, 12, 18));
        center.add(new JLabel("Выбери набор ресурсов:"), BorderLayout.NORTH);
        center.add(packs, BorderLayout.CENTER);
        center.add(description, BorderLayout.SOUTH);
        dialog.add(center, BorderLayout.CENTER);
        packs.addActionListener(e -> {
            Object selected = packs.getSelectedItem();
            if (selected instanceof ResourcePackManager.ResourcePackInfo pack) {
                description.setText("<html>" + pack.description() + "<br>PNG: textures/blocks/&lt;block-id&gt;.png (16×16; размеры 1–256 автоматически приводятся к 16×16).</html>");
            }
        });
        JPanel buttons = new JPanel(new GridLayout(2, 3, 6, 6));
        JButton apply = new JButton("ПРИМЕНИТЬ");
        JButton install = new JButton("ИМПОРТ ZIP");
        JButton remove = new JButton("УДАЛИТЬ ПАК");
        JButton folder = new JButton("ПАПКА РЕСУРС-ПАКОВ");
        JButton close = new JButton("ЗАКРЫТЬ");
        Runnable reload = () -> {
            try {
                String active = manager.selectedPackId();
                packs.removeAllItems();
                for (var pack : manager.listPacks()) { packs.addItem(pack); if (pack.id().equals(active)) packs.setSelectedItem(pack); }
            } catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка паков", JOptionPane.ERROR_MESSAGE); }
        };
        apply.addActionListener(e -> {
            Object selected = packs.getSelectedItem(); if (!(selected instanceof ResourcePackManager.ResourcePackInfo pack)) return;
            try { manager.select(pack.id()); status.setText("Ресурс-пак «" + pack.name() + "» применится при следующем запуске мира."); dialog.dispose(); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE); }
        });
        install.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Импорт ресурс-пака ZIP");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Resource pack (*.zip)", "zip"));
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            try { var pack = manager.installZip(chooser.getSelectedFile().toPath()); reload.run();
                JOptionPane.showMessageDialog(dialog, "Установлен ресурс-пак: " + pack.name() + ". PNG-текстуры ищутся в textures/blocks/. Теперь выбери его и нажми «ПРИМЕНИТЬ».", "Готово", JOptionPane.INFORMATION_MESSAGE); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка импорта", JOptionPane.ERROR_MESSAGE); }
        });
        remove.addActionListener(e -> {
            Object selected = packs.getSelectedItem();
            if (!(selected instanceof ResourcePackManager.ResourcePackInfo pack)) return;
            if (pack.builtIn()) {
                JOptionPane.showMessageDialog(dialog, "Встроенный ресурс-пак нельзя удалить.", "Информация", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int answer = JOptionPane.showConfirmDialog(dialog, "Удалить ресурс-пак «" + pack.name() + "»?", "Удалить пак", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) try { manager.remove(pack.id()); reload.run(); }
            catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE); }
        });
        folder.addActionListener(e -> openFolder(manager.root()));
        close.addActionListener(e -> dialog.dispose());
        buttons.add(apply); buttons.add(install); buttons.add(remove); buttons.add(folder); buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);
        reload.run(); dialog.setSize(650, 260); dialog.setLocationRelativeTo(frame); dialog.setVisible(true);
    }

    private void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported()) throw new IOException("Открытие папок не поддерживается этой системой.");
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(frame, "Папка: " + folder + "\n" + ex.getMessage(), "Открыть папку", JOptionPane.INFORMATION_MESSAGE);
        }
    }

}
