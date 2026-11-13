package com.blockworld.ui;

import com.blockworld.core.GamePaths;
import com.blockworld.core.GameLog;
import com.blockworld.core.GameVersion;
import com.blockworld.core.VersionManager;
import com.blockworld.core.WorldCatalog;
import com.blockworld.core.WorldFavorites;
import com.blockworld.core.WorldBackupManager;
import com.blockworld.core.WorldEntry;
import com.blockworld.core.PlayerProfileStore;
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
import javax.swing.DefaultListCellRenderer;
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
import javax.swing.ImageIcon;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Desktop launcher: every saved world is visible regardless of selected game profile. */
public final class Launcher {
    private static volatile Launcher activeLauncher;
    private JFrame frame;
    private JComboBox<GameVersion> versionSelector;
    private JComboBox<WorldEntry> worldSelector;
    private JLabel status;
    private JLabel worldInfo;
    private JCheckBox favoritesOnly;
    private JButton favoriteButton;
    private Set<String> favoriteNames = Set.of();
    private final Map<Path, ImageIcon> worldPreviewIconCache = new HashMap<>();

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
        frame.setMinimumSize(new Dimension(760, 560));
        frame.setSize(850, 630);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("BLOCKWORLD 3D", JLabel.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        title.setBorder(BorderFactory.createEmptyBorder(20, 12, 8, 12));
        frame.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 12));
        center.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));

        JPanel selectors = new JPanel(new GridLayout(4, 2, 10, 8));
        selectors.add(new JLabel("Версия игры:"));
        versionSelector = new JComboBox<>(VersionManager.getSupportedVersions().toArray(GameVersion[]::new));
        versionSelector.setSelectedItem(VersionManager.getDefaultVersion());
        versionSelector.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        selectors.add(versionSelector);
        selectors.add(new JLabel("Сохранённый мир (общий для всех версий):"));
        worldSelector = new JComboBox<>();
        worldSelector.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        worldSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof WorldEntry entry) {
                    String marker = favoriteNames.contains(entry.file().getFileName().toString()) ? "★ " : "";
                    ImageIcon preview = worldPreviewIcon(entry.file());
                    label.setIcon(preview);
                    label.setIconTextGap(10);
                    label.setText(marker + entry.name() + "  |  " + entry.lastVersion()
                            + "  |  " + entry.integrityStatus()
                            + (preview == null ? "  |  превью появится после загрузки" : ""));
                }
                return label;
            }
        });
        selectors.add(worldSelector);
        favoritesOnly = new JCheckBox("Показывать только избранные миры");
        favoritesOnly.addActionListener(event -> refreshWorldList(selectedWorldPath()));
        selectors.add(favoritesOnly);
        worldInfo = new JLabel("Выбери мир, чтобы увидеть размер и состояние сохранения.");
        worldInfo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        selectors.add(worldInfo);
        worldSelector.addActionListener(event -> {
            updateSelectedWorldInfo();
            updateFavoriteButtonText();
        });
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
        JPanel buttons = new JPanel(new GridLayout(4, 4, 7, 7));
        JButton play = new JButton("ИГРАТЬ");
        JButton newWorld = new JButton("НОВЫЙ МИР");
        JButton settings = new JButton("НАСТРОЙКИ");
        JButton mods = new JButton("МОДЫ");
        JButton packs = new JButton("РЕСУРС-ПАКИ");
        JButton multiplayer = new JButton("СЕТЕВАЯ ИГРА");
        JButton refresh = new JButton("ОБНОВИТЬ СПИСОК");
        JButton worlds = new JButton("ПАПКА МИРОВ");
        favoriteButton = new JButton("В ИЗБРАННОЕ");
        JButton backups = new JButton("РЕЗЕРВНЫЕ КОПИИ");
        JButton diagnostics = new JButton("ДИАГНОСТИКА");
        JButton profile = new JButton("ПРОФИЛЬ ИГРОКА");
        JButton close = new JButton("ВЫХОД");
        play.addActionListener(event -> launchSelectedWorld());
        newWorld.addActionListener(event -> createWorld());
        settings.addActionListener(event -> showSettingsDialog());
        mods.addActionListener(event -> showModsDialog());
        packs.addActionListener(event -> showResourcePacksDialog());
        multiplayer.addActionListener(event -> showMultiplayerDialog());
        refresh.addActionListener(event -> refreshWorldList(selectedWorldPath()));
        worlds.addActionListener(event -> openWorldFolder());
        favoriteButton.addActionListener(event -> toggleFavorite());
        backups.addActionListener(event -> showBackupDialog());
        diagnostics.addActionListener(event -> showDiagnosticsDialog());
        profile.addActionListener(event -> showPlayerProfileDialog());
        close.addActionListener(event -> frame.dispose());
        buttons.add(play);
        buttons.add(newWorld);
        buttons.add(settings);
        buttons.add(mods);
        buttons.add(packs);
        buttons.add(multiplayer);
        buttons.add(refresh);
        buttons.add(worlds);
        buttons.add(favoriteButton);
        buttons.add(backups);
        buttons.add(diagnostics);
        buttons.add(profile);
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
            worldPreviewIconCache.clear();
            favoriteNames = WorldFavorites.list();
            List<WorldEntry> allWorlds = WorldCatalog.list();
            List<WorldEntry> worlds = allWorlds.stream()
                    .filter(world -> !favoritesOnly.isSelected()
                            || favoriteNames.contains(world.file().getFileName().toString()))
                    .toList();
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
                status.setText(favoritesOnly.isSelected()
                        ? "В избранном пока нет миров."
                        : "Пока нет сохранённых миров. Нажми «НОВЫЙ МИР».");
            } else {
                long damaged = worlds.stream().filter(world ->
                        !"ЗАГОЛОВОК В ПОРЯДКЕ".equals(world.integrityStatus())).count();
                status.setText("Миров: " + worlds.size() + "; избранных: " + favoriteNames.size()
                        + "; требуют проверки: " + damaged + ".");
            }
            updateSelectedWorldInfo();
            updateFavoriteButtonText();
        } catch (IOException ex) {
            status.setText("Не удалось прочитать список миров: " + ex.getMessage());
        }
    }

    private void updateSelectedWorldInfo() {
        if (worldInfo == null) return;
        WorldEntry selected = (WorldEntry) worldSelector.getSelectedItem();
        if (selected == null) {
            worldInfo.setIcon(null);
            worldInfo.setText("Нет выбранного мира.");
            return;
        }
        double kib = selected.sizeBytes() / 1024.0;
        String size = kib >= 1024.0
                ? String.format(java.util.Locale.ROOT, "%.2f MiB", kib / 1024.0)
                : String.format(java.util.Locale.ROOT, "%.1f KiB", kib);
        String changed = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(selected.modified());
        ImageIcon preview = worldPreviewIcon(selected.file());
        worldInfo.setIcon(preview);
        worldInfo.setIconTextGap(12);
        worldInfo.setText(selected.name() + "  |  " + size + "  |  " + selected.integrityStatus()
                + "  |  изменён: " + changed
                + (preview == null ? "  |  скриншот появится после входа в мир" : "  |  есть скриншот"));
    }

    private ImageIcon worldPreviewIcon(Path worldFile) {
        if (worldFile == null) return null;
        Path previewFile = GamePaths.worldPreviewFile(worldFile);
        if (!Files.isRegularFile(previewFile)) return null;
        ImageIcon cached = worldPreviewIconCache.get(previewFile);
        if (cached != null) return cached;
        try {
            BufferedImage source = ImageIO.read(previewFile.toFile());
            if (source == null) return null;
            Image scaled = source.getScaledInstance(96, 54, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            worldPreviewIconCache.put(previewFile, icon);
            return icon;
        } catch (IOException | RuntimeException error) {
            GameLog.error("Не удалось загрузить превью мира " + previewFile, error);
            return null;
        }
    }

    private void updateFavoriteButtonText() {
        if (favoriteButton == null) return;
        Path selected = selectedWorldPath();
        favoriteButton.setEnabled(selected != null);
        favoriteButton.setText(selected != null && favoriteNames.contains(selected.getFileName().toString())
                ? "УБРАТЬ ИЗ ИЗБРАННОГО" : "В ИЗБРАННОЕ");
    }

    private void toggleFavorite() {
        Path selected = selectedWorldPath();
        if (selected == null) return;
        boolean currentlyFavorite = favoriteNames.contains(selected.getFileName().toString());
        try {
            WorldFavorites.setFavorite(selected, !currentlyFavorite);
            refreshWorldList(selected);
            status.setText(currentlyFavorite ? "Мир удалён из избранного." : "Мир добавлен в избранное.");
        } catch (IOException error) {
            JOptionPane.showMessageDialog(frame, "Не удалось изменить избранное:\n" + error.getMessage(),
                    "Избранные миры", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void showPlayerProfileDialog() {
        PlayerProfileStore.Profile loaded;
        try {
            loaded = PlayerProfileStore.load();
        } catch (IOException error) {
            GameLog.error("Не удалось загрузить профиль игрока", error);
            loaded = PlayerProfileStore.Profile.defaults();
        }
        final PlayerProfileStore.Profile current = loaded;

        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Профиль игрока", true);
        dialog.setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridLayout(2, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        JTextField displayName = new JTextField(current.displayName(), 22);
        JComboBox<String> appearance = new JComboBox<>(new String[]{
                "Исследователь", "Лесной", "Песчаный", "Кристальный"
        });
        appearance.setSelectedIndex(current.appearanceIndex());
        form.add(new JLabel("Имя персонажа:"));
        form.add(displayName);
        form.add(new JLabel("Образ персонажа:"));
        form.add(appearance);
        dialog.add(form, BorderLayout.NORTH);

        long seconds = current.playTimeSeconds();
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        String time = days > 0 ? days + " д. " + hours + " ч. " + minutes + " мин."
                : hours > 0 ? hours + " ч. " + minutes + " мин."
                : minutes + " мин.";
        JTextArea statistics = new JTextArea(
                "СТАТИСТИКА ИГРОКА\n"
                + "Успешно загружено миров: " + current.sessionsPlayed() + "\n"
                + "Общее время в игре: " + time + "\n"
                + "Профиль сохраняется на этом компьютере и используется во всех мирах.",
                4, 34);
        statistics.setEditable(false);
        statistics.setFocusable(false);
        statistics.setOpaque(false);
        statistics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        statistics.setBorder(BorderFactory.createEmptyBorder(4, 18, 8, 18));
        dialog.add(statistics, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton("СОХРАНИТЬ");
        JButton cancel = new JButton("ОТМЕНА");
        save.addActionListener(event -> {
            if (displayName.getText().isBlank()) {
                JOptionPane.showMessageDialog(dialog, "Введи имя персонажа.",
                        "Профиль игрока", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                PlayerProfileStore.Profile updated = new PlayerProfileStore.Profile(
                        displayName.getText(), appearance.getSelectedIndex(),
                        current.sessionsPlayed(), current.playTimeSeconds());
                PlayerProfileStore.save(updated);
                dialog.dispose();
                JOptionPane.showMessageDialog(frame,
                        "Профиль «" + updated.displayName() + "» сохранён. Новый образ применится при следующем запуске мира.",
                        "Профиль игрока", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException error) {
                GameLog.error("Не удалось сохранить профиль игрока", error);
                JOptionPane.showMessageDialog(dialog, "Не удалось сохранить профиль:\n" + error.getMessage(),
                        "Ошибка профиля", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(event -> dialog.dispose());
        actions.add(save);
        actions.add(cancel);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(430, 250));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showBackupDialog() {
        WorldEntry selected = (WorldEntry) worldSelector.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(frame, "Сначала выбери сохранённый мир.",
                    "Резервные копии", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JDialog dialog = new JDialog(frame, "Резервные копии — " + selected.name(), true);
        dialog.setLayout(new BorderLayout(8, 8));
        DefaultListModel<Path> model = new DefaultListModel<>();
        JList<Path> backups = new JList<>(model);
        backups.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backups.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        backups.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof Path path) label.setText(path.getFileName().toString());
                return label;
            }
        });
        JLabel info = new JLabel("Восстановление сначала создаёт копию текущего мира.");
        info.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        dialog.add(info, BorderLayout.NORTH);
        dialog.add(new JScrollPane(backups), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton restore = new JButton("ВОССТАНОВИТЬ");
        JButton delete = new JButton("УДАЛИТЬ КОПИЮ");
        JButton refresh = new JButton("ОБНОВИТЬ");
        JButton close = new JButton("ЗАКРЫТЬ");
        Runnable reload = () -> {
            model.clear();
            try {
                for (Path backup : WorldBackupManager.list(selected.file())) model.addElement(backup);
                info.setText("Найдено копий: " + model.size()
                        + ". При восстановлении текущий мир тоже будет сохранён.");
            } catch (IOException error) {
                info.setText("Не удалось прочитать копии: " + error.getMessage());
            }
        };
        restore.addActionListener(event -> {
            Path backup = backups.getSelectedValue();
            if (backup == null) return;
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Восстановить эту копию? Текущее сохранение автоматически получит резервную копию.",
                    "Восстановление мира", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                Path safety = WorldBackupManager.restore(selected.file(), backup);
                refreshWorldList(selected.file());
                status.setText("Мир восстановлен. Предыдущее состояние сохранено: " + safety.getFileName());
                dialog.dispose();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, "Не удалось восстановить копию:\n" + error.getMessage(),
                        "Ошибка восстановления", JOptionPane.ERROR_MESSAGE);
            }
        });
        delete.addActionListener(event -> {
            Path backup = backups.getSelectedValue();
            if (backup == null) return;
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Удалить резервную копию «" + backup.getFileName() + "»?",
                    "Удаление копии", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                // The manager removes the world file and all of its saved progress companions.
                WorldBackupManager.delete(selected.file(), backup);
                reload.run();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка удаления", JOptionPane.ERROR_MESSAGE);
            }
        });
        refresh.addActionListener(event -> reload.run());
        close.addActionListener(event -> dialog.dispose());
        buttons.add(restore); buttons.add(delete); buttons.add(refresh); buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);
        reload.run();
        dialog.setSize(680, 420);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showDiagnosticsDialog() {
        GameSettings current = GameSettings.load();
        Path logFile = Path.of(".logs", "latest-run.log");
        String log;
        try {
            log = Files.isRegularFile(logFile) ? Files.readString(logFile, java.nio.charset.StandardCharsets.UTF_8)
                    : "Журнал ещё не создан. Запусти игру для сбора диагностических записей.";
        } catch (IOException error) {
            log = "Не удалось прочитать журнал: " + error.getMessage();
        }
        int maxChars = 28_000;
        if (log.length() > maxChars) {
            log = "[Показан конец журнала; начало опущено]\n" + log.substring(log.length() - maxChars);
        }
        long freeMiB = Runtime.getRuntime().freeMemory() / (1024L * 1024L);
        long totalMiB = Runtime.getRuntime().totalMemory() / (1024L * 1024L);
        long maxMiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        String summary = "BLOCKWORLD 3D — ДИАГНОСТИКА\n"
                + "Java: " + System.getProperty("java.version") + "\n"
                + "Система: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")\n"
                + "Процессоры: " + Runtime.getRuntime().availableProcessors() + "\n"
                + "Память JVM: свободно " + freeMiB + " MiB, выделено " + totalMiB
                + " MiB, максимум " + maxMiB + " MiB\n"
                + "Графика: " + current.width() + "x" + current.height()
                + ", FPS limit " + (current.fpsLimit() < 0 ? "без ограничения" : current.fpsLimit())
                + ", чанков/кадр " + current.chunkBuildsPerFrame() + "\n"
                + "Ошибок в показанном фрагменте журнала: "
                + log.lines().filter(line -> line.contains("[ERROR]")).count()
                + "\nФайл: " + logFile.toAbsolutePath() + "\n\nПОСЛЕДНИЕ ЗАПИСИ\n";
        JTextArea report = new JTextArea(summary + log, 24, 88);
        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        report.setCaretPosition(report.getDocument().getLength());
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Диагностика", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(report), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton logFolder = new JButton("ПАПКА ЖУРНАЛОВ");
        JButton close = new JButton("ЗАКРЫТЬ");
        logFolder.addActionListener(event -> openFolder(logFile.getParent()));
        close.addActionListener(event -> dialog.dispose());
        buttons.add(logFolder); buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(900, 620);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
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
        launchGame(worldFile, worldName, createNewWorld, null, null);
    }

    private void launchGame(Path worldFile, String worldName, boolean createNewWorld,
                            MultiplayerClient multiplayerClient, MultiplayerServer multiplayerServer) {
        GameVersion selected = (GameVersion) versionSelector.getSelectedItem();
        GameVersion versionToLaunch = selected == null ? VersionManager.getDefaultVersion() : selected;

        status.setText("Запуск " + versionToLaunch.id() + " — мир «" + worldName + "»...");
        GameLog.info("Запрошен запуск профиля " + versionToLaunch.id() + " для мира «" + worldName
                + "»; файл=" + worldFile.toAbsolutePath()
                + (multiplayerClient == null ? "" : "; multiplayer playerId=" + multiplayerClient.playerId()));
        frame.setVisible(false);

        Runnable onGameExit = () -> {
            if (multiplayerClient != null) multiplayerClient.close();
            if (multiplayerServer != null) multiplayerServer.close();
            SwingUtilities.invokeLater(() -> {
                refreshWorldList(worldFile);
                frame.setVisible(true);
                frame.toFront();
                status.setText("Мир «" + worldName + "» закрыт. Он доступен и в других версиях.");
            });
        };

        Thread gameThread = new Thread(() -> {
            try {
                new BlockWorldGame(versionToLaunch, worldFile, createNewWorld, onGameExit,
                        GameSettings.load(), multiplayerClient).launch();
            } catch (Throwable error) {
                if (multiplayerClient != null) multiplayerClient.close();
                if (multiplayerServer != null) multiplayerServer.close();
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
            if (multiplayerClient != null) multiplayerClient.close();
            if (multiplayerServer != null) multiplayerServer.close();
            GameLog.error("Авария потока запуска игры " + thread.getName(), error);
            SwingUtilities.invokeLater(() -> {
                frame.setVisible(true);
                frame.toFront();
                status.setText("Поток игры аварийно завершился. См. .logs\\latest-run.log и hs_err_pid*.log.");
                JOptionPane.showMessageDialog(frame,
                        "Поток запуска игры завершился с ошибкой:\n" + formatThrowable(error)
                                + "\n\nПроверь .logs\\latest-run.log и .logs\\hs_err_pid*.log.",
                        "Ошибка запуска", JOptionPane.ERROR_MESSAGE);
            });
        });
        gameThread.start();
    }

    private void backupExistingWorld(Path worldFile) throws IOException {
        Path backup = WorldBackupManager.create(worldFile);
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

    private static String fpsLabel(int fps) {
        return fps < 0 ? "Без ограничения" : Integer.toString(fps);
    }

    private static int parseFpsLabel(String value) {
        return "Без ограничения".equals(value) ? -1 : Integer.parseInt(value);
    }

    private void showSettingsDialog() {
        GameSettings current = GameSettings.load();
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Настройки", true);
        dialog.setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridLayout(8, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));
        JComboBox<String> resolution = new JComboBox<>(new String[]{"1024x640", "1280x720", "1600x900", "1920x1080"});
        resolution.setSelectedItem(current.width() + "x" + current.height());
        JCheckBox fullscreen = new JCheckBox("Полноэкранный режим", current.fullscreen());
        JCheckBox vsync = new JCheckBox("Вертикальная синхронизация (VSync)", current.vsync());
        JComboBox<Integer> distance = new JComboBox<>(new Integer[]{3, 4, 6, 8});
        distance.setSelectedItem(current.renderDistance());
        JSpinner fov = new JSpinner(new SpinnerNumberModel(current.fov(), 55, 100, 1));
        JComboBox<String> vegetation = new JComboBox<>(new String[]{"Выкл.", "Мало", "Обычно", "Много"});
        vegetation.setSelectedIndex(current.vegetationDensity());
        JComboBox<Integer> chunkBudget = new JComboBox<>(new Integer[]{1, 2, 3, 4, 6});
        chunkBudget.setSelectedItem(current.chunkBuildsPerFrame());
        chunkBudget.setToolTipText("Больше чанков за кадр ускоряет подгрузку, но сильнее нагружает процессор.");
        JComboBox<String> fpsLimit = new JComboBox<>(new String[]{"Без ограничения", "30", "60", "90", "120", "144"});
        fpsLimit.setSelectedItem(fpsLabel(current.fpsLimit()));
        form.add(new JLabel("Разрешение:")); form.add(resolution);
        form.add(new JLabel("Дальность чанков:")); form.add(distance);
        form.add(new JLabel("Поле зрения (FOV):")); form.add(fov);
        form.add(new JLabel("Экран:")); form.add(fullscreen);
        form.add(new JLabel("Синхронизация:")); form.add(vsync);
        form.add(new JLabel("Растительность:")); form.add(vegetation);
        form.add(new JLabel("Чанков за кадр:")); form.add(chunkBudget);
        form.add(new JLabel("Ограничение FPS:")); form.add(fpsLimit);
        dialog.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton("ПО УМОЛЧАНИЮ");
        JButton recommend = new JButton("АВТОПОДБОР");
        JButton save = new JButton("СОХРАНИТЬ");
        JButton cancel = new JButton("ОТМЕНА");
        recommend.setToolTipText("Подбирает умеренные настройки по памяти JVM и числу процессоров.");
        recommend.addActionListener(e -> {
            GameSettings suggested = GameSettings.recommended(Runtime.getRuntime().maxMemory(),
                    Runtime.getRuntime().availableProcessors());
            resolution.setSelectedItem(suggested.width() + "x" + suggested.height());
            distance.setSelectedItem(suggested.renderDistance());
            fov.setValue(suggested.fov());
            fullscreen.setSelected(suggested.fullscreen());
            vsync.setSelected(suggested.vsync());
            vegetation.setSelectedIndex(suggested.vegetationDensity());
            chunkBudget.setSelectedItem(suggested.chunkBuildsPerFrame());
            fpsLimit.setSelectedItem(fpsLabel(suggested.fpsLimit()));
        });
        reset.addActionListener(e -> {
            resolution.setSelectedItem("1024x640"); distance.setSelectedItem(3);
            fov.setValue(68); fullscreen.setSelected(false); vsync.setSelected(false);
            vegetation.setSelectedIndex(2); chunkBudget.setSelectedItem(2); fpsLimit.setSelectedItem("60");
        });
        save.addActionListener(e -> {
            String[] parts = String.valueOf(resolution.getSelectedItem()).split("x");
            try {
                GameSettings settings = new GameSettings(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        fullscreen.isSelected(), vsync.isSelected(), (Integer) distance.getSelectedItem(),
                        (Integer) fov.getValue(), vegetation.getSelectedIndex(), (Integer) chunkBudget.getSelectedItem(),
                        parseFpsLabel(String.valueOf(fpsLimit.getSelectedItem())));
                GameSettings.save(settings);
                status.setText("Настройки сохранены. Разрешение и экран применятся при следующем запуске.");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Не удалось сохранить настройки: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> dialog.dispose());
        actions.add(reset); actions.add(recommend); actions.add(save); actions.add(cancel);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.pack(); dialog.setMinimumSize(new Dimension(560, 360)); dialog.setLocationRelativeTo(frame); dialog.setVisible(true);
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
                "Совместный мир: персонажи, перемещение и блоки синхронизируются через сервер. Инвентарь и существа пока локальные.",
                "BLOCKWORLD 3D — Multiplayer", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 0) hostMultiplayerServer();
        else if (choice == 1) joinMultiplayerServer();
    }

    private void hostMultiplayerServer() {
        JTextField port = new JTextField("25575");
        JTextField limit = new JTextField("8");
        JPasswordField password = new JPasswordField();
        JPasswordField accountPassword = new JPasswordField();
        JComboBox<String> accountMode = new JComboBox<>(new String[]{"Создать аккаунт", "Войти в аккаунт"});
        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.add(new JLabel("Порт:")); form.add(port);
        form.add(new JLabel("Игроков (1–32):")); form.add(limit);
        form.add(new JLabel("Пароль комнаты (необязательно):")); form.add(password);
        form.add(new JLabel("Аккаунт ведущего (8+ символов):")); form.add(accountPassword);
        form.add(new JLabel("Действие с аккаунтом:")); form.add(accountMode);
        if (JOptionPane.showConfirmDialog(frame, form, "Создать LAN-сервер", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        WorldEntry selectedWorld = (WorldEntry) worldSelector.getSelectedItem();
        if (selectedWorld == null) {
            JOptionPane.showMessageDialog(frame, "Сначала создай или выбери мир в лаунчере.",
                    "Нет выбранного мира", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        MultiplayerServer server = null;
        MultiplayerClient client = null;
        try {
            String secret = new String(password.getPassword());
            String accountSecret = new String(accountPassword.getPassword());
            if (accountSecret.length() < 8) throw new IllegalArgumentException("Пароль аккаунта должен содержать минимум 8 символов.");
            boolean createAccount = accountMode.getSelectedIndex() == 0;
            server = new MultiplayerServer(secret, Integer.parseInt(limit.getText().trim()),
                    GamePaths.multiplayerServerWorld(selectedWorld.file()), true);
            int actualPort = server.start(Integer.parseInt(port.getText().trim()));
            client = new MultiplayerClient();
            showMultiplayerLobby("LAN-сервер запущен. Порт: " + actualPort
                    + ". Сохранение сервера: " + GamePaths.multiplayerServerWorld(selectedWorld.file()).getFileName()
                    + ". Игроки должны использовать адрес этого компьютера в своей сети.",
                    client, server, "Host", secret, accountSecret, createAccount, "127.0.0.1", actualPort, selectedWorld);
        } catch (Exception error) {
            if (client != null) client.close();
            if (server != null) server.close();
            JOptionPane.showMessageDialog(frame, "Не удалось создать сервер: " + error.getMessage(),
                    "Сетевая игра", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinMultiplayerServer() {
        JTextField host = new JTextField("127.0.0.1");
        JTextField port = new JTextField("25575");
        JTextField nickname = new JTextField("Player");
        JPasswordField password = new JPasswordField();
        JPasswordField accountPassword = new JPasswordField();
        JComboBox<String> accountMode = new JComboBox<>(new String[]{"Войти в аккаунт", "Создать аккаунт"});
        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.add(new JLabel("Адрес:")); form.add(host);
        form.add(new JLabel("Порт:")); form.add(port);
        form.add(new JLabel("Никнейм / аккаунт:")); form.add(nickname);
        form.add(new JLabel("Пароль комнаты:")); form.add(password);
        form.add(new JLabel("Пароль аккаунта (8+ символов):")); form.add(accountPassword);
        form.add(new JLabel("Действие с аккаунтом:")); form.add(accountMode);
        if (JOptionPane.showConfirmDialog(frame, form, "Подключиться к серверу", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        try {
            String address = host.getText().trim();
            int portNumber = Integer.parseInt(port.getText().trim());
            if (address.isBlank()) throw new IllegalArgumentException("Укажи адрес сервера");
            if (portNumber < 1 || portNumber > 65535) throw new IllegalArgumentException("Порт должен быть от 1 до 65535");
            String playerName = nickname.getText().trim();
            if (playerName.isBlank()) playerName = "Player";
            String accountSecret = new String(accountPassword.getPassword());
            if (accountSecret.length() < 8) throw new IllegalArgumentException("Пароль аккаунта должен содержать минимум 8 символов.");
            boolean createAccount = accountMode.getSelectedIndex() == 1;
            showMultiplayerLobby("Подключение к " + address + ":" + portNumber,
                    new MultiplayerClient(), null, playerName, new String(password.getPassword()), accountSecret, createAccount, address, portNumber, null);
        } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(frame, "Проверь адрес и порт: " + error.getMessage(),
                    "Неверные данные сервера", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showMultiplayerLobby(String infoText, MultiplayerClient client, MultiplayerServer server,
                                      String nickname, String password, String accountPassword,
                                      boolean createAccount, String host, int port, WorldEntry hostWorld) {
        JDialog dialog = new JDialog(frame, "BLOCKWORLD 3D — Сетевая комната", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        JLabel info = new JLabel(infoText + " | Подключение...");
        info.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        JTextArea chat = new JTextArea();
        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        JTextField message = new JTextField();
        JButton send = new JButton("ОТПРАВИТЬ");
        send.setEnabled(false);
        JButton play = new JButton("ЗАПУСТИТЬ ВЫБРАННЫЙ МИР");
        play.setEnabled(false);
        boolean[] handedOffToGame = {false};

        Runnable sendMessage = () -> {
            try {
                client.sendChat(message.getText());
                message.setText("");
            } catch (IOException error) {
                chat.append("Не отправлено: " + error.getMessage() + System.lineSeparator());
                send.setEnabled(client.isConnected());
            }
        };
        send.addActionListener(event -> sendMessage.run());
        message.addActionListener(event -> sendMessage.run());

        JPanel compose = new JPanel(new BorderLayout(6, 6));
        compose.add(message, BorderLayout.CENTER);
        compose.add(send, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(compose, BorderLayout.CENTER);
        bottom.add(play, BorderLayout.SOUTH);
        dialog.add(info, BorderLayout.NORTH);
        dialog.add(new JScrollPane(chat), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(680, 440);
        dialog.setLocationRelativeTo(frame);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) {
                if (!handedOffToGame[0]) {
                    client.close();
                    if (server != null) server.close();
                }
            }
        });

        play.addActionListener(event -> {
            WorldEntry selected = hostWorld != null ? hostWorld : (WorldEntry) worldSelector.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog,
                        "Сначала создай или выбери мир в лаунчере, затем запускай сетевую игру.",
                        "Нет выбранного мира", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            handedOffToGame[0] = true;
            dialog.dispose();
            launchGame(selected.file(), selected.name(), false, client, server);
        });

        dialog.setVisible(true);
        Thread connector = new Thread(() -> {
            try {
                client.connect(host, port, password, nickname, accountPassword, createAccount, text -> SwingUtilities.invokeLater(() -> {
                    if (dialog.isDisplayable()) {
                        chat.append(text + System.lineSeparator());
                        chat.setCaretPosition(chat.getDocument().getLength());
                    }
                }));
                SwingUtilities.invokeLater(() -> {
                    if (dialog.isDisplayable()) {
                        info.setText("Подключено к " + host + ":" + port
                                + ". Нажми кнопку ниже, чтобы войти в общий мир.");
                        send.setEnabled(true);
                        play.setEnabled(true);
                        message.requestFocusInWindow();
                    } else {
                        client.close();
                        if (server != null) server.close();
                    }
                });
            } catch (Exception error) {
                client.close();
                SwingUtilities.invokeLater(() -> {
                    if (dialog.isDisplayable()) {
                        chat.append("Ошибка подключения: " + error.getMessage() + System.lineSeparator());
                        info.setText("Не удалось подключиться. Проверь адрес, порт и пароль.");
                        send.setEnabled(false);
                        play.setEnabled(false);
                    }
                });
            }
        }, "BlockWorld-Multiplayer-Connect");
        connector.setDaemon(true);
        connector.start();
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
