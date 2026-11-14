package com.blockworld.ui;

import com.blockworld.core.GamePaths;
import com.blockworld.core.GameLog;
import com.blockworld.core.LogTailReader;
import com.blockworld.core.GameVersion;
import com.blockworld.core.VersionManager;
import com.blockworld.core.WorldCatalog;
import com.blockworld.core.WorldFavorites;
import com.blockworld.core.WorldBackupManager;
import com.blockworld.core.WorldEntry;
import com.blockworld.core.WorldCreationOptions;
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
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.Cursor;
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
import java.util.Locale;

/** Desktop launcher: every saved world is visible regardless of selected game profile. */
public final class Launcher {
    private static volatile Launcher activeLauncher;
    private JFrame frame;
    private JComboBox<GameVersion> versionSelector;
    private JComboBox<WorldEntry> worldSelector;
    private JLabel status;
    private JLabel worldInfo;
    private JLabel versionInfo;
    private JLabel systemInfo;
    private JTextField worldSearch;
    private List<WorldEntry> availableWorlds = List.of();
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
        GameLog.info("===== Minecraft 3D process start =====");
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
                        "Minecraft 3D — ошибка запуска", JOptionPane.ERROR_MESSAGE);
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
                    "Minecraft 3D — ошибка", JOptionPane.ERROR_MESSAGE);
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
        frame = new JFrame("Minecraft 3D — лаунчер");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(780, 600));
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getContentPane().setBackground(new java.awt.Color(238, 243, 238));

        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBackground(new java.awt.Color(38, 62, 48));
        header.setBorder(BorderFactory.createEmptyBorder(18, 24, 16, 24));
        JLabel title = new JLabel("MINECRAFT 3D", JLabel.LEFT);
        title.setForeground(java.awt.Color.WHITE);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29));
        JLabel subtitle = new JLabel("ЛАУНЧЕР  •  ИГРОВЫЕ МИРЫ  •  ВЕРСИИ  •  НАСТРОЙКИ");
        subtitle.setForeground(new java.awt.Color(207, 225, 210));
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 2));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(subtitle);
        JLabel release = new JLabel("1.9.22  •  CHUNK STREAMING PRIORITY");
        release.setForeground(new java.awt.Color(224, 245, 226));
        release.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        header.add(heading, BorderLayout.CENTER);
        header.add(release, BorderLayout.EAST);
        frame.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 12));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(12, 24, 8, 24));

        JPanel selectors = new JPanel(new GridLayout(4, 2, 10, 8));
        selectors.setOpaque(false);
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
                            + "  |  " + entry.integrityStatus());
                }
                return label;
            }
        });
        selectors.add(worldSelector);

        selectors.add(new JLabel("Поиск миров:"));
        worldSearch = new JTextField();
        worldSearch.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        worldSearch.setToolTipText("Поиск по названию, версии и состоянию сохранения");
        worldSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { applyWorldFilter(selectedWorldPath()); }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        worldSearch.addActionListener(event -> launchSelectedWorld());
        selectors.add(worldSearch);

        favoritesOnly = new JCheckBox("Показывать только избранные миры");
        favoritesOnly.setOpaque(false);
        favoritesOnly.addActionListener(event -> applyWorldFilter(selectedWorldPath()));
        selectors.add(favoritesOnly);

        worldInfo = new JLabel("Выбери мир, чтобы увидеть размер и состояние сохранения.");
        worldInfo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        selectors.add(worldInfo);
        worldSelector.addActionListener(event -> {
            updateSelectedWorldInfo();
            updateFavoriteButtonText();
        });

        versionInfo = new JLabel();
        versionInfo.setOpaque(true);
        versionInfo.setBackground(java.awt.Color.WHITE);
        versionInfo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(210, 220, 210)),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        versionSelector.addActionListener(event -> updateSelectedVersionInfo());
        updateSelectedVersionInfo();

        JPanel selectionArea = new JPanel(new BorderLayout(8, 8));
        selectionArea.setOpaque(false);
        selectionArea.add(selectors, BorderLayout.NORTH);
        selectionArea.add(versionInfo, BorderLayout.SOUTH);
        center.add(selectionArea, BorderLayout.NORTH);

        JTextArea details = new JTextArea(
                "ОБНОВЛЕНИЕ ДВИЖКА ЧАНКОВ — 1.9.22\n\n" +
                "• После перемещения игрока ближайшие чанки подгружаются в первую очередь.\n" +
                "• Очередь загрузки перестраивается вокруг новой позиции камеры.\n" +
                "• Лимит 0 чанков за кадр теперь действительно откладывает построение мешей.\n\n" +
                "ОБНОВЛЕНИЕ СТАБИЛЬНОСТИ — 1.9.21 / 1.9.20\n\n" +
                "• Исправлена версия в заголовке лаунчера и запись диагностических логов.\n" +
                "• Журнал ограничен 1 МиБ, предыдущий запуск сохраняется для диагностики.\n\n" +
                "ПРЕДЫДУЩЕЕ ОБНОВЛЕНИЕ ЛАУНЧЕРА — 1.9.19\n\n" +
                "• Окна модов, ресурс-паков и мультиплеера получили новый интерфейс.\n" +
                "• Добавлены статусы дополнений, описание наборов и проверка параметров сервера.\n\n" +
                "ПРЕДЫДУЩЕЕ ОБНОВЛЕНИЕ — 1.9.18\n\n" +
                "• В окне резервных копий появилась кнопка ручного создания копии мира.\n" +
                "• Копия включает сохранение и дополнительные файлы прогресса игрока.\n" +
                "• F5 быстро обновляет список миров.\n\n" +
                "ПОСЛЕДНЕЕ ОБНОВЛЕНИЕ ИГРЫ — 1.9.17\n\n" +
                "• ЛКМ наносит удары мобам и игрокам в пределах дистанции.\n" +
                "• Удар не проходит сквозь стены; броня уменьшает урон в PvP.\n" +
                "• Если под прицелом нет цели, ЛКМ продолжает добывать блоки.\n\n" +
                "ВОЗМОЖНОСТИ ЛАУНЧЕРА\n" +
                "• Общий список миров, избранное, поиск и миниатюры сохранений.\n" +
                "• Создание миров с выбором размера и seed.\n" +
                "• Настройки графики, моды и PNG-ресурс-паки.\n" +
                "• Резервные копии, профиль игрока и диагностический журнал.\n\n" +
                "Миры доступны из разных профилей игры. Перед заменой существующего мира\n" +
                "лаунчер предлагает создать резервную копию.");
        details.setEditable(false);
        details.setFocusable(false);
        details.setOpaque(false);
        details.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        center.add(new JScrollPane(details), BorderLayout.CENTER);
        frame.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 24, 16, 24));
        JPanel buttons = new JPanel(new GridLayout(4, 4, 7, 7));
        JButton play = createLauncherButton("ИГРАТЬ");
        play.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        play.setBackground(new java.awt.Color(195, 225, 198));
        play.setToolTipText("Запустить выбранный мир с выбранным профилем версии.");
        JButton newWorld = createLauncherButton("НОВЫЙ МИР");
        JButton settings = createLauncherButton("НАСТРОЙКИ");
        JButton mods = createLauncherButton("МОДЫ");
        JButton packs = createLauncherButton("РЕСУРС-ПАКИ");
        JButton multiplayer = createLauncherButton("СЕТЕВАЯ ИГРА");
        JButton refresh = createLauncherButton("ОБНОВИТЬ СПИСОК");
        JButton worlds = createLauncherButton("ПАПКА МИРОВ");
        favoriteButton = createLauncherButton("В ИЗБРАННОЕ");
        JButton backups = createLauncherButton("РЕЗЕРВНЫЕ КОПИИ");
        JButton diagnostics = createLauncherButton("ДИАГНОСТИКА");
        JButton profile = createLauncherButton("ПРОФИЛЬ ИГРОКА");
        JButton versionProfiles = createLauncherButton("ПРОФИЛИ ВЕРСИЙ");
        JButton close = createLauncherButton("ВЫХОД");
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
        versionProfiles.addActionListener(event -> showVersionProfilesDialog());
        close.addActionListener(event -> frame.dispose());
        newWorld.setToolTipText("Создать мир: выбрать название, размер карты и seed.");
        settings.setToolTipText("Настроить разрешение, графику и производительность.");
        mods.setToolTipText("Установить, включить или отключить моды выбранного профиля.");
        packs.setToolTipText("Импортировать PNG-ресурс-паки и выбрать активный набор.");
        multiplayer.setToolTipText("Создать LAN-сервер или подключиться к существующей комнате.");
        refresh.setToolTipText("Обновить список сохранённых миров (также клавиша F5).");
        worlds.setToolTipText("Открыть папку с сохранёнными мирами.");
        favoriteButton.setToolTipText("Добавить выбранный мир в избранное или убрать его оттуда.");
        backups.setToolTipText("Создать, восстановить или удалить резервную копию мира.");
        diagnostics.setToolTipText("Посмотреть сведения о Java, памяти, настройках и журнале.");
        profile.setToolTipText("Изменить имя и образ персонажа, посмотреть статистику.");
        versionProfiles.setToolTipText("Управлять профилями версий, их настройками и модами.");
        close.setToolTipText("Закрыть лаунчер.");
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
        buttons.add(versionProfiles);
        buttons.add(close);
        bottom.add(buttons, BorderLayout.CENTER);

        systemInfo = new JLabel(systemSummary());
        systemInfo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        systemInfo.setForeground(new java.awt.Color(91, 105, 94));
        systemInfo.setBorder(BorderFactory.createEmptyBorder(7, 2, 0, 2));
        bottom.add(systemInfo, BorderLayout.NORTH);

        status = new JLabel("Миры общие для всех версий. Создай новый мир или выбери сохранённый.");
        status.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        bottom.add(status, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        installKeyboardShortcuts();
        frame.setVisible(true);
    }

    private void installKeyboardShortcuts() {
        frame.getRootPane().registerKeyboardAction(
                event -> refreshWorldList(selectedWorldPath()),
                KeyStroke.getKeyStroke("F5"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static JPanel createSectionBanner(String title, String subtitle) {
        JPanel banner = new JPanel(new BorderLayout(6, 4));
        banner.setBackground(new java.awt.Color(38, 62, 48));
        banner.setBorder(BorderFactory.createEmptyBorder(14, 18, 13, 18));
        JLabel heading = new JLabel(title);
        heading.setForeground(java.awt.Color.WHITE);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        JLabel description = new JLabel(subtitle);
        description.setForeground(new java.awt.Color(207, 225, 210));
        description.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 3));
        labels.setOpaque(false);
        labels.add(heading);
        labels.add(description);
        banner.add(labels, BorderLayout.CENTER);
        return banner;
    }

    private static JButton createLauncherButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(new java.awt.Color(38, 62, 48));
        button.setBackground(new java.awt.Color(231, 239, 231));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(190, 207, 191)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(text);
        button.getAccessibleContext().setAccessibleDescription(text);
        return button;
    }

    private String systemSummary() {
        long maxMiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        return "Java " + System.getProperty("java.version")
                + "  •  " + Runtime.getRuntime().availableProcessors() + " процессор(а)"
                + "  •  лимит памяти JVM " + maxMiB + " MiB";
    }

    private void updateSelectedVersionInfo() {
        if (versionInfo == null || versionSelector == null) return;
        GameVersion selected = (GameVersion) versionSelector.getSelectedItem();
        if (selected == null) return;
        String mode = selected.survival() ? "Выживание" : "Творческий профиль";
        versionInfo.setText("<html><b>Профиль " + htmlEscape(selected.id()) + " — "
                + htmlEscape(selected.displayName()) + "</b> &nbsp; | &nbsp; "
                + htmlEscape(selected.description()) + "<br>Размер нового мира по умолчанию: "
                + (selected.worldRadius() * 2 + 1) + "×" + (selected.worldRadius() * 2 + 1)
                + " блоков &nbsp; | &nbsp; Режим: " + mode + "</html>");
    }

    private static String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private Path selectedWorldPath() {
        WorldEntry selected = (WorldEntry) worldSelector.getSelectedItem();
        return selected == null ? null : selected.file();
    }

    private void refreshWorldList(Path selectPath) {
        if (worldSelector == null) return;
        try {
            worldPreviewIconCache.clear();
            favoriteNames = WorldFavorites.list();
            availableWorlds = WorldCatalog.list();
            applyWorldFilter(selectPath);
        } catch (IOException ex) {
            status.setText("Не удалось прочитать список миров: " + ex.getMessage());
        }
    }

    private void applyWorldFilter(Path selectPath) {
        if (worldSelector == null) return;
        List<WorldEntry> filtered = LauncherWorldFilter.filter(
                availableWorlds, worldSearch == null ? "" : worldSearch.getText(),
                favoriteNames, favoritesOnly != null && favoritesOnly.isSelected());
        worldSelector.removeAllItems();
        for (WorldEntry world : filtered) worldSelector.addItem(world);
        if (selectPath != null) {
            for (int i = 0; i < worldSelector.getItemCount(); i++) {
                if (worldSelector.getItemAt(i).file().equals(selectPath)) {
                    worldSelector.setSelectedIndex(i);
                    break;
                }
            }
        }

        if (filtered.isEmpty()) {
            if (availableWorlds.isEmpty()) {
                status.setText("Сохранённых миров пока нет. Нажми «НОВЫЙ МИР».");
            } else if (favoritesOnly.isSelected() && (worldSearch == null || worldSearch.getText().isBlank())) {
                status.setText("В избранном пока нет миров.");
            } else {
                status.setText("Поиск не дал результатов. Попробуй другое название, версию или сними фильтр избранного.");
            }
        } else {
            long damaged = filtered.stream().filter(world ->
                    !"ЗАГОЛОВОК В ПОРЯДКЕ".equals(world.integrityStatus())).count();
            status.setText("Показано миров: " + filtered.size() + " из " + availableWorlds.size()
                    + "; избранных: " + favoriteNames.size() + "; требуют проверки: " + damaged + ".");
        }
        updateSelectedWorldInfo();
        updateFavoriteButtonText();
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

        JDialog dialog = new JDialog(frame, "Minecraft 3D — Профиль игрока", true);
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
        JButton save = createLauncherButton("СОХРАНИТЬ");
        JButton cancel = createLauncherButton("ОТМЕНА");
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
        JButton create = createLauncherButton("СОЗДАТЬ КОПИЮ");
        JButton restore = createLauncherButton("ВОССТАНОВИТЬ");
        JButton delete = createLauncherButton("УДАЛИТЬ КОПИЮ");
        JButton refresh = createLauncherButton("ОБНОВИТЬ");
        JButton close = createLauncherButton("ЗАКРЫТЬ");
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
        create.addActionListener(event -> {
            try {
                Path backup = WorldBackupManager.create(selected.file());
                reload.run();
                backups.setSelectedValue(backup, true);
                info.setText("Резервная копия создана: " + backup.getFileName());
                refreshWorldList(selected.file());
                status.setText("Создана резервная копия мира: " + backup.getFileName());
            } catch (IOException error) {
                GameLog.error("Не удалось создать резервную копию мира", error);
                JOptionPane.showMessageDialog(dialog,
                        "Не удалось создать резервную копию:\n" + error.getMessage(),
                        "Ошибка резервного копирования", JOptionPane.ERROR_MESSAGE);
            }
        });
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
        buttons.add(create); buttons.add(restore); buttons.add(delete); buttons.add(refresh); buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);
        reload.run();
        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showDiagnosticsDialog() {
        GameSettings current = GameSettings.load();
        Path logFile = Path.of(".logs", "latest-run.log");
        String log;
        int maxChars = 28_000;
        try {
            log = Files.isRegularFile(logFile) ? LogTailReader.readTail(logFile, maxChars)
                    : "Журнал ещё не создан. Запусти игру для сбора диагностических записей.";
        } catch (IOException error) {
            log = "Не удалось прочитать журнал: " + error.getMessage();
        }
        long freeMiB = Runtime.getRuntime().freeMemory() / (1024L * 1024L);
        long totalMiB = Runtime.getRuntime().totalMemory() / (1024L * 1024L);
        long maxMiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        String summary = "Minecraft 3D — ДИАГНОСТИКА\n"
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
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Диагностика", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(report), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton logFolder = createLauncherButton("ПАПКА ЖУРНАЛОВ");
        JButton close = createLauncherButton("ЗАКРЫТЬ");
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
        JTextField nameField = new JTextField("Мой мир", 20);
        JComboBox<WorldCreationOptions.WorldSize> sizeField =
                new JComboBox<>(WorldCreationOptions.WorldSize.values());
        sizeField.setSelectedItem(WorldCreationOptions.WorldSize.NORMAL);
        JTextField seedField = new JTextField(20);

        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        fields.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        fields.add(new JLabel("Название мира:"));
        fields.add(nameField);
        fields.add(new JLabel("Размер мира:"));
        fields.add(sizeField);
        fields.add(new JLabel("Seed (необязательно):"));
        fields.add(seedField);
        fields.add(new JLabel("Пустое поле — случайный seed."));
        fields.add(new JLabel("Большой мир генерируется дольше."));

        int result = JOptionPane.showConfirmDialog(frame, fields,
                "Minecraft 3D — создание мира", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Введи название мира.",
                    "Не указано название", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long seed = null;
        String seedText = seedField.getText().trim();
        if (!seedText.isEmpty()) {
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame,
                        "Seed должен быть целым числом от -9223372036854775808 до 9223372036854775807.",
                        "Некорректный seed", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        WorldCreationOptions.WorldSize selectedSize =
                (WorldCreationOptions.WorldSize) sizeField.getSelectedItem();
        WorldCreationOptions options = new WorldCreationOptions(seed, selectedSize);
        try {
            Path file = WorldCatalog.pathForName(name);
            String safeName = file.getFileName().toString().replaceFirst("(?i)\\.bwworld$", "");
            if (Files.exists(file)) {
                int answer = JOptionPane.showConfirmDialog(frame,
                        "Мир «" + safeName + "» уже существует. Создать резервную копию и заменить его новым?",
                        "Мир уже существует", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) return;
                backupExistingWorld(file);
            }
            launchGame(file, safeName, true, options);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Не удалось подготовить мир:\n" + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchGame(Path worldFile, String worldName, boolean createNewWorld) {
        launchGame(worldFile, worldName, createNewWorld, null, null, null);
    }

    private void launchGame(Path worldFile, String worldName, boolean createNewWorld,
                            WorldCreationOptions worldCreationOptions) {
        launchGame(worldFile, worldName, createNewWorld, null, null, worldCreationOptions);
    }

    private void launchGame(Path worldFile, String worldName, boolean createNewWorld,
                            MultiplayerClient multiplayerClient, MultiplayerServer multiplayerServer) {
        launchGame(worldFile, worldName, createNewWorld, multiplayerClient, multiplayerServer, null);
    }

    private void launchGame(Path worldFile, String worldName, boolean createNewWorld,
                            MultiplayerClient multiplayerClient, MultiplayerServer multiplayerServer,
                            WorldCreationOptions worldCreationOptions) {
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
                refreshWorldList(null);
                frame.setVisible(true);
                frame.toFront();
                status.setText("Мир «" + worldName + "» закрыт. Список миров обновлён.");
            });
        };

        Thread gameThread = new Thread(() -> {
            try {
                new BlockWorldGame(versionToLaunch, worldFile, createNewWorld, onGameExit,
                        GameSettings.load(versionToLaunch.id()), multiplayerClient, worldCreationOptions,
                        versionToLaunch.id()).launch();
            } catch (Throwable error) {
                if (multiplayerClient != null) multiplayerClient.close();
                if (multiplayerServer != null) multiplayerServer.close();
                GameLog.error("Ошибка запуска игрового окна (включая системные/native ошибки)", error);
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.toFront();
                    status.setText("Игра завершилась с ошибкой. Журнал: .logs\\latest-run.log");
                    JOptionPane.showMessageDialog(frame,
                            "Не удалось запустить Minecraft 3D:\n" + formatThrowable(error)
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

    private String selectedProfileId() {
        GameVersion selected = (GameVersion) versionSelector.getSelectedItem();
        return selected == null ? VersionManager.getDefaultVersion().id() : selected.id();
    }

    private void showVersionProfilesDialog() {
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Профили версий", true);
        dialog.setLayout(new BorderLayout(8, 8));
        DefaultListModel<GameVersion> model = new DefaultListModel<>();
        for (GameVersion version : VersionManager.getSupportedVersions()) model.addElement(version);
        JList<GameVersion> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        list.setSelectedValue(versionSelector.getSelectedItem(), true);

        JLabel info = new JLabel();
        info.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        Runnable updateInfo = () -> {
            GameVersion selected = list.getSelectedValue();
            if (selected == null) {
                info.setText("Выбери версию из списка.");
                return;
            }
            Path folder = GamePaths.profileDirectory(selected.id());
            Path settingsFile = GamePaths.profileSettingsFile(selected.id());
            try {
                int mods = new ModManager(selected.id()).listAll().size();
                info.setText("<html><b>" + htmlEscape(selected.id()) + " — "
                        + htmlEscape(selected.displayName()) + "</b><br>Модов профиля: " + mods
                        + " &nbsp; | &nbsp; "
                        + (Files.isRegularFile(settingsFile) ? "есть отдельные настройки" : "настройки наследуются от общих")
                        + "<br>Папка профиля: " + htmlEscape(folder.toString()) + "</html>");
            } catch (IOException error) {
                info.setText("Не удалось прочитать профиль: " + htmlEscape(error.getMessage()));
            }
        };
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateInfo.run();
        });

        JPanel actions = new JPanel(new GridLayout(1, 5, 6, 6));
        JButton activate = createLauncherButton("АКТИВИРОВАТЬ");
        JButton settings = createLauncherButton("НАСТРОЙКИ");
        JButton mods = createLauncherButton("МОДЫ");
        JButton folder = createLauncherButton("ПАПКА");
        JButton close = createLauncherButton("ЗАКРЫТЬ");
        activate.addActionListener(event -> {
            GameVersion selected = list.getSelectedValue();
            if (selected == null) return;
            versionSelector.setSelectedItem(selected);
            updateSelectedVersionInfo();
            status.setText("Активный профиль: " + selected.id() + " — " + selected.displayName());
            dialog.dispose();
        });
        settings.addActionListener(event -> {
            GameVersion selected = list.getSelectedValue();
            if (selected == null) return;
            versionSelector.setSelectedItem(selected);
            updateSelectedVersionInfo();
            dialog.dispose();
            showSettingsDialog();
        });
        mods.addActionListener(event -> {
            GameVersion selected = list.getSelectedValue();
            if (selected == null) return;
            versionSelector.setSelectedItem(selected);
            updateSelectedVersionInfo();
            dialog.dispose();
            showModsDialog();
        });
        folder.addActionListener(event -> {
            GameVersion selected = list.getSelectedValue();
            if (selected != null) openFolder(GamePaths.profileDirectory(selected.id()));
        });
        close.addActionListener(event -> dialog.dispose());

        actions.add(activate);
        actions.add(settings);
        actions.add(mods);
        actions.add(folder);
        actions.add(close);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.add(info, BorderLayout.NORTH);
        dialog.add(actions, BorderLayout.SOUTH);
        updateInfo.run();
        dialog.setSize(820, 480);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showSettingsDialog() {
        String profileId = selectedProfileId();
        GameSettings current = GameSettings.load(profileId);
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Настройки " + profileId, true);
        dialog.setLayout(new BorderLayout(10, 10));

        JComboBox<String> resolution = new JComboBox<>(
                new String[]{"1024x640", "1280x720", "1600x900", "1920x1080"});
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
        JComboBox<String> fpsLimit = new JComboBox<>(
                new String[]{"Без ограничения", "30", "60", "90", "120", "144"});
        fpsLimit.setSelectedItem(fpsLabel(current.fpsLimit()));

        resolution.setToolTipText("Размер игрового окна. Выбирай разрешение, которое поддерживает монитор.");
        fullscreen.setToolTipText("Переключает игру в полноэкранный режим.");
        vsync.setToolTipText("Синхронизирует кадры с частотой монитора и может уменьшить разрывы изображения.");
        distance.setToolTipText("Чем больше дальность, тем дальше виден мир, но тем выше нагрузка.");
        fov.setToolTipText("Угол обзора камеры: от 55° до 100°.");
        vegetation.setToolTipText("Количество декоративной растительности в мире.");
        chunkBudget.setToolTipText("Сколько чанков обрабатывать за кадр. Высокие значения быстрее загружают мир, но сильнее нагружают CPU.");
        fpsLimit.setToolTipText("Ограничение частоты кадров. Меньший лимит может снизить нагрев и расход энергии.");

        JPanel screen = createSettingsPanel("Окно игры");
        addSetting(screen, "Разрешение:", resolution);
        addSetting(screen, "Режим экрана:", fullscreen);
        JPanel graphics = createSettingsPanel("Изображение и окружение");
        addSetting(graphics, "Дальность чанков:", distance);
        addSetting(graphics, "Поле зрения (FOV):", fov);
        addSetting(graphics, "Растительность:", vegetation);
        JPanel performance = createSettingsPanel("Производительность");
        addSetting(performance, "Синхронизация:", vsync);
        addSetting(performance, "Чанков за кадр:", chunkBudget);
        addSetting(performance, "Ограничение FPS:", fpsLimit);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("ЭКРАН", screen);
        tabs.addTab("ГРАФИКА", graphics);
        tabs.addTab("ПРОИЗВОДИТЕЛЬНОСТЬ", performance);

        JLabel estimate = new JLabel();
        estimate.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        Runnable updateEstimate = () -> {
            String selectedResolution = String.valueOf(resolution.getSelectedItem());
            int selectedDistance = (Integer) distance.getSelectedItem();
            int selectedChunks = (Integer) chunkBudget.getSelectedItem();
            int score = Math.max(0, selectedDistance - 3) * 2
                    + vegetation.getSelectedIndex() + Math.max(0, selectedChunks - 2)
                    + (selectedResolution.startsWith("1920") ? 2 : 0);
            String load = score >= 8 ? "повышенная" : score >= 4 ? "средняя" : "низкая";
            estimate.setText("<html><b>Оценка выбранных параметров:</b> нагрузка " + load
                    + " &nbsp;|&nbsp; " + selectedResolution
                    + " &nbsp;|&nbsp; чанки " + selectedDistance
                    + " &nbsp;|&nbsp; FPS " + fpsLabel(parseFpsLabel(String.valueOf(fpsLimit.getSelectedItem())))
                    + "</html>");
        };
        resolution.addActionListener(event -> updateEstimate.run());
        distance.addActionListener(event -> updateEstimate.run());
        vegetation.addActionListener(event -> updateEstimate.run());
        chunkBudget.addActionListener(event -> updateEstimate.run());
        fpsLimit.addActionListener(event -> updateEstimate.run());
        fov.addChangeListener(event -> updateEstimate.run());
        fullscreen.addActionListener(event -> updateEstimate.run());
        vsync.addActionListener(event -> updateEstimate.run());
        updateEstimate.run();

        JLabel notice = new JLabel("Настройки хранятся отдельно для каждого профиля версии. Изменения применятся при следующем запуске мира.");
        notice.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        center.add(estimate, BorderLayout.NORTH);
        center.add(tabs, BorderLayout.CENTER);
        center.add(notice, BorderLayout.SOUTH);
        dialog.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton reset = createLauncherButton("ПО УМОЛЧАНИЮ");
        JButton recommend = createLauncherButton("АВТОПОДБОР");
        JButton save = createLauncherButton("СОХРАНИТЬ");
        JButton cancel = createLauncherButton("ОТМЕНА");
        recommend.setToolTipText("Подобрать умеренные параметры с учётом памяти JVM и числа процессоров.");
        reset.addActionListener(event -> {
            GameSettings defaults = GameSettings.DEFAULT;
            resolution.setSelectedItem(defaults.width() + "x" + defaults.height());
            distance.setSelectedItem(defaults.renderDistance());
            fov.setValue(defaults.fov());
            fullscreen.setSelected(defaults.fullscreen());
            vsync.setSelected(defaults.vsync());
            vegetation.setSelectedIndex(defaults.vegetationDensity());
            chunkBudget.setSelectedItem(defaults.chunkBuildsPerFrame());
            fpsLimit.setSelectedItem(fpsLabel(defaults.fpsLimit()));
            updateEstimate.run();
        });
        recommend.addActionListener(event -> {
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
            updateEstimate.run();
        });
        save.addActionListener(event -> {
            String[] parts = String.valueOf(resolution.getSelectedItem()).split("x");
            try {
                GameSettings updated = new GameSettings(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        fullscreen.isSelected(), vsync.isSelected(), (Integer) distance.getSelectedItem(),
                        (Integer) fov.getValue(), vegetation.getSelectedIndex(),
                        (Integer) chunkBudget.getSelectedItem(),
                        parseFpsLabel(String.valueOf(fpsLimit.getSelectedItem())));
                GameSettings.save(profileId, updated);
                status.setText("Настройки профиля " + profileId
                        + " сохранены. Они применятся при следующем запуске мира.");
                dialog.dispose();
            } catch (Exception error) {
                GameLog.error("Не удалось сохранить настройки профиля " + profileId, error);
                JOptionPane.showMessageDialog(dialog,
                        "Не удалось сохранить настройки: " + error.getMessage(),
                        "Ошибка настроек", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(event -> dialog.dispose());
        actions.add(reset); actions.add(recommend); actions.add(save); actions.add(cancel);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(save);
        dialog.setMinimumSize(new Dimension(720, 470));
        dialog.setSize(760, 500);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static JPanel createSettingsPanel(String title) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 12, 14));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        return panel;
    }

    private static void addSetting(JPanel panel, String label, JComponent component) {
        JLabel caption = new JLabel(label);
        caption.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        panel.add(caption);
        panel.add(component);
    }

    private void showModsDialog() {
        String profileId = selectedProfileId();
        ModManager manager = new ModManager(profileId);
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Моды " + profileId, true);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<ModManager.ModInfo> model = new DefaultListModel<>();
        JList<ModManager.ModInfo> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        list.setBackground(new java.awt.Color(250, 252, 250));
        list.setSelectionBackground(new java.awt.Color(214, 232, 216));
        list.setSelectionForeground(new java.awt.Color(30, 48, 35));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> source, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        source, value, index, selected, focused);
                if (value instanceof ModManager.ModInfo mod) {
                    String state = mod.enabled() ? "● ВКЛЮЧЕН" : "○ ОТКЛЮЧЕН";
                    String version = mod.version().isBlank() ? "версия не указана" : mod.version();
                    String api = mod.apiProvider() ? "BlockWorld API найден" : "API provider не найден";
                    label.setText("<html><b>" + htmlEscape(mod.title()) + "</b> &nbsp; " + state
                            + "<br><small>" + htmlEscape(mod.fileName()) + " · "
                            + htmlEscape(version) + " · " + api + "</small>"
                            + "<br><small>" + htmlEscape(mod.description()) + "</small></html>");
                    label.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
                }
                return label;
            }
        });

        JLabel selectionInfo = new JLabel("Выбери мод, чтобы посмотреть его состояние и описание.");
        selectionInfo.setOpaque(true);
        selectionInfo.setBackground(java.awt.Color.WHITE);
        selectionInfo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(215, 225, 215)),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));

        JButton install = createLauncherButton("УСТАНОВИТЬ JAR");
        JButton toggle = createLauncherButton("ВКЛЮЧИТЬ");
        JButton remove = createLauncherButton("УДАЛИТЬ");
        JButton folder = createLauncherButton("ПАПКА ПРОФИЛЯ");
        JButton sharedFolder = createLauncherButton("ОБЩИЕ МОДЫ");
        JButton refresh = createLauncherButton("ОБНОВИТЬ");
        JButton close = createLauncherButton("ЗАКРЫТЬ");
        toggle.setEnabled(false);
        remove.setEnabled(false);

        Runnable updateSelection = () -> {
            ModManager.ModInfo selected = list.getSelectedValue();
            toggle.setEnabled(selected != null);
            remove.setEnabled(selected != null);
            toggle.setText(selected != null && selected.enabled() ? "ОТКЛЮЧИТЬ" : "ВКЛЮЧИТЬ");
            if (selected == null) {
                selectionInfo.setText("Выбери мод, чтобы посмотреть его состояние и описание.");
            } else {
                selectionInfo.setText("<html><b>" + htmlEscape(selected.title()) + "</b> · "
                        + (selected.enabled() ? "включён" : "отключён")
                        + " · " + (selected.apiProvider() ? "BlockWorld API найден" : "API provider не найден")
                        + "<br>" + htmlEscape(selected.description()) + "</html>");
            }
        };
        Runnable reload = () -> {
            model.clear();
            try {
                for (ModManager.ModInfo mod : manager.listAll()) model.addElement(mod);
                if (!model.isEmpty()) list.setSelectedIndex(0);
                updateSelection.run();
                if (model.isEmpty()) selectionInfo.setText("Моды пока не установлены. Добавь проверенный файл .jar.");
            } catch (IOException error) {
                selectionInfo.setText("Не удалось прочитать моды: " + htmlEscape(error.getMessage()));
                GameLog.error("Не удалось обновить список модов профиля " + profileId, error);
            }
        };
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelection.run();
        });

        install.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выбери мод .jar");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Java mod (*.jar)", "jar"));
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Мод — исполняемый Java-файл. Он может получить права текущего пользователя. "
                            + "Устанавливай только файлы от авторов, которым доверяешь. Продолжить?",
                    "Предупреждение безопасности", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                manager.install(chooser.getSelectedFile().toPath());
                reload.run();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка установки мода", JOptionPane.ERROR_MESSAGE);
            }
        });
        toggle.addActionListener(event -> {
            ModManager.ModInfo selected = list.getSelectedValue();
            if (selected == null) return;
            if (!selected.enabled()) {
                int answer = JOptionPane.showConfirmDialog(dialog,
                        "При включении этот мод сможет выполнять Java-код внутри игры. Продолжить?",
                        "Включить мод", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) return;
            }
            try {
                manager.setEnabled(selected, !selected.enabled());
                reload.run();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка изменения мода", JOptionPane.ERROR_MESSAGE);
            }
        });
        remove.addActionListener(event -> {
            ModManager.ModInfo selected = list.getSelectedValue();
            if (selected == null) return;
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Удалить мод «" + selected.title() + "»?", "Удалить мод",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                manager.remove(selected);
                reload.run();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка удаления мода", JOptionPane.ERROR_MESSAGE);
            }
        });
        folder.addActionListener(event -> openFolder(manager.root()));
        sharedFolder.addActionListener(event -> openFolder(GamePaths.modsDirectory()));
        refresh.addActionListener(event -> reload.run());
        close.addActionListener(event -> dialog.dispose());

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(createSectionBanner("МОДЫ ПРОФИЛЯ " + profileId,
                "Управление дополнениями для выбранной версии игры."), BorderLayout.NORTH);
        JLabel warning = new JLabel("Безопасность: моды JAR исполняют Java-код. Используй только доверенные файлы.");
        warning.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        top.add(warning, BorderLayout.SOUTH);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        center.add(selectionInfo, BorderLayout.SOUTH);
        JPanel buttons = new JPanel(new GridLayout(2, 4, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        buttons.add(install);
        buttons.add(toggle);
        buttons.add(remove);
        buttons.add(refresh);
        buttons.add(folder);
        buttons.add(sharedFolder);
        buttons.add(close);
        dialog.add(top, BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        reload.run();
        dialog.getRootPane().setDefaultButton(refresh);
        dialog.setMinimumSize(new Dimension(760, 500));
        dialog.setSize(820, 540);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showMultiplayerDialog() {
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Сетевая игра", true);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel choices = new JPanel(new GridLayout(1, 2, 12, 12));
        choices.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));

        JPanel hostCard = new JPanel(new BorderLayout(8, 10));
        hostCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("СОЗДАТЬ МИР ДЛЯ ДРУЗЕЙ"),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel hostDescription = new JLabel("<html>Открой LAN-сервер на этом компьютере. "
                + "Выбери сохранённый мир, количество игроков и пароль комнаты.</html>");
        JButton host = createLauncherButton("СОЗДАТЬ LAN-СЕРВЕР");
        hostCard.add(hostDescription, BorderLayout.CENTER);
        hostCard.add(host, BorderLayout.SOUTH);

        JPanel joinCard = new JPanel(new BorderLayout(8, 10));
        joinCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("ПОДКЛЮЧИТЬСЯ К ДРУГУ"),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel joinDescription = new JLabel("<html>Введи IP-адрес и порт сервера, никнейм, "
                + "пароль комнаты и данные игрового аккаунта.</html>");
        JButton join = createLauncherButton("ПОДКЛЮЧИТЬСЯ");
        joinCard.add(joinDescription, BorderLayout.CENTER);
        joinCard.add(join, BorderLayout.SOUTH);

        choices.add(hostCard);
        choices.add(joinCard);
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(createSectionBanner("СЕТЕВАЯ ИГРА",
                "Создай комнату или присоединись к существующей игре."), BorderLayout.NORTH);
        JLabel note = new JLabel("<html>Синхронизация: игроки, перемещение и блоки. "
                + "Некоторые игровые системы, включая инвентарь и существ, пока работают локально.</html>");
        note.setBorder(BorderFactory.createEmptyBorder(2, 16, 0, 16));
        top.add(note, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = createLauncherButton("ЗАКРЫТЬ");
        close.addActionListener(event -> dialog.dispose());
        host.addActionListener(event -> {
            dialog.dispose();
            hostMultiplayerServer();
        });
        join.addActionListener(event -> {
            dialog.dispose();
            joinMultiplayerServer();
        });
        bottom.add(close);

        dialog.add(top, BorderLayout.NORTH);
        dialog.add(choices, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(join);
        dialog.setSize(760, 380);
        dialog.setMinimumSize(new Dimension(680, 340));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void hostMultiplayerServer() {
        JTextField port = new JTextField("25575", 12);
        JTextField limit = new JTextField("8", 12);
        JPasswordField password = new JPasswordField(12);
        JPasswordField accountPassword = new JPasswordField(12);
        JComboBox<String> accountMode = new JComboBox<>(new String[]{"Создать аккаунт", "Войти в аккаунт"});
        port.setToolTipText("Сетевой порт от 1 до 65535. Друзьям понадобится этот порт.");
        limit.setToolTipText("Максимальное количество игроков: от 1 до 32.");
        password.setToolTipText("Необязательный пароль для входа в комнату.");
        accountPassword.setToolTipText("Пароль игрового аккаунта, не менее 8 символов.");

        JPanel form = new JPanel(new GridLayout(5, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        form.add(new JLabel("Порт сервера:")); form.add(port);
        form.add(new JLabel("Максимум игроков:")); form.add(limit);
        form.add(new JLabel("Пароль комнаты (необязательно):")); form.add(password);
        form.add(new JLabel("Пароль аккаунта (8+ символов):")); form.add(accountPassword);
        form.add(new JLabel("Аккаунт ведущего:")); form.add(accountMode);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.add(createSectionBanner("СОЗДАНИЕ LAN-СЕРВЕРА",
                "Сервер будет работать на этом компьютере, пока открыта сетевая сессия."), BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JLabel note = new JLabel("Выбери мир в главном окне заранее. Пароль комнаты можно оставить пустым.");
        note.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        content.add(note, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(frame, content, "Создать LAN-сервер",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        WorldEntry selectedWorld = (WorldEntry) worldSelector.getSelectedItem();
        if (selectedWorld == null) {
            JOptionPane.showMessageDialog(frame, "Сначала создай или выбери мир в лаунчере.",
                    "Нет выбранного мира", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        MultiplayerServer server = null;
        MultiplayerClient client = null;
        try {
            int portNumber = Integer.parseInt(port.getText().trim());
            int playerLimit = Integer.parseInt(limit.getText().trim());
            if (portNumber < 1 || portNumber > 65535) {
                throw new IllegalArgumentException("Порт должен быть от 1 до 65535.");
            }
            if (playerLimit < 1 || playerLimit > 32) {
                throw new IllegalArgumentException("Количество игроков должно быть от 1 до 32.");
            }
            String secret = new String(password.getPassword());
            String accountSecret = new String(accountPassword.getPassword());
            if (accountSecret.length() < 8) {
                throw new IllegalArgumentException("Пароль аккаунта должен содержать минимум 8 символов.");
            }
            boolean createAccount = accountMode.getSelectedIndex() == 0;
            server = new MultiplayerServer(secret, playerLimit,
                    GamePaths.multiplayerServerWorld(selectedWorld.file()), true);
            int actualPort = server.start(portNumber);
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
        JTextField host = new JTextField("127.0.0.1", 16);
        JTextField port = new JTextField("25575", 8);
        JTextField nickname = new JTextField("Player", 16);
        JPasswordField password = new JPasswordField(16);
        JPasswordField accountPassword = new JPasswordField(16);
        JComboBox<String> accountMode = new JComboBox<>(new String[]{"Войти в аккаунт", "Создать аккаунт"});
        host.setToolTipText("IP-адрес или имя компьютера ведущего LAN-сервера.");
        port.setToolTipText("Порт, который указал ведущий.");
        nickname.setToolTipText("Имя, которое увидят другие игроки.");
        password.setToolTipText("Пароль комнаты, если ведущий его установил.");
        accountPassword.setToolTipText("Пароль игрового аккаунта, не менее 8 символов.");

        JPanel form = new JPanel(new GridLayout(6, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        form.add(new JLabel("Адрес сервера:")); form.add(host);
        form.add(new JLabel("Порт:")); form.add(port);
        form.add(new JLabel("Никнейм:")); form.add(nickname);
        form.add(new JLabel("Пароль комнаты:")); form.add(password);
        form.add(new JLabel("Пароль аккаунта (8+ символов):")); form.add(accountPassword);
        form.add(new JLabel("Аккаунт:")); form.add(accountMode);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.add(createSectionBanner("ПОДКЛЮЧЕНИЕ К СЕРВЕРУ",
                "Введи адрес ведущего и свои данные для входа."), BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        JLabel note = new JLabel("Уточни IP-адрес и пароль у владельца комнаты.");
        note.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        content.add(note, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(frame, content, "Подключиться к серверу",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        try {
            String address = host.getText().trim();
            int portNumber = Integer.parseInt(port.getText().trim());
            if (address.isBlank()) throw new IllegalArgumentException("Укажи адрес сервера.");
            if (portNumber < 1 || portNumber > 65535) {
                throw new IllegalArgumentException("Порт должен быть от 1 до 65535.");
            }
            String playerName = nickname.getText().trim();
            if (playerName.isBlank()) playerName = "Player";
            String accountSecret = new String(accountPassword.getPassword());
            if (accountSecret.length() < 8) {
                throw new IllegalArgumentException("Пароль аккаунта должен содержать минимум 8 символов.");
            }
            boolean createAccount = accountMode.getSelectedIndex() == 1;
            showMultiplayerLobby("Подключение к " + address + ":" + portNumber,
                    new MultiplayerClient(), null, playerName, new String(password.getPassword()),
                    accountSecret, createAccount, address, portNumber, null);
        } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(frame, "Проверь адрес и порт: " + error.getMessage(),
                    "Неверные данные сервера", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showMultiplayerLobby(String infoText, MultiplayerClient client, MultiplayerServer server,
                                      String nickname, String password, String accountPassword,
                                      boolean createAccount, String host, int port, WorldEntry hostWorld) {
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Сетевая комната", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JLabel info = new JLabel("Подготовка подключения...");
        info.setOpaque(true);
        info.setBackground(java.awt.Color.WHITE);
        info.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel connectionState = new JLabel("● УСТАНОВКА СОЕДИНЕНИЯ");
        connectionState.setForeground(new java.awt.Color(130, 94, 33));
        connectionState.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        JTextArea chat = new JTextArea();
        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        chat.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        chat.setBackground(new java.awt.Color(248, 250, 248));
        chat.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        chat.setText("Сетевая комната Minecraft 3D\n" + infoText + "\n\nПодключаемся...");
        JTextField message = new JTextField();
        message.setToolTipText("Напиши сообщение и нажми Enter или кнопку «Отправить».");
        JButton send = createLauncherButton("ОТПРАВИТЬ");
        send.setEnabled(false);
        JButton play = createLauncherButton("ЗАПУСТИТЬ ВЫБРАННЫЙ МИР");
        play.setEnabled(false);
        play.setBackground(new java.awt.Color(195, 225, 198));
        boolean[] handedOffToGame = {false};

        Runnable sendMessage = () -> {
            if (message.getText().isBlank()) return;
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

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(createSectionBanner("СЕТЕВАЯ КОМНАТА",
                "Общение и вход в совместный мир."), BorderLayout.NORTH);
        top.add(info, BorderLayout.CENTER);
        JPanel compose = new JPanel(new BorderLayout(8, 8));
        compose.add(message, BorderLayout.CENTER);
        compose.add(send, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottom.add(connectionState, BorderLayout.NORTH);
        bottom.add(compose, BorderLayout.CENTER);
        bottom.add(play, BorderLayout.SOUTH);

        dialog.add(top, BorderLayout.NORTH);
        dialog.add(new JScrollPane(chat), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(760, 520);
        dialog.setMinimumSize(new Dimension(640, 440));
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
                        connectionState.setText("● СОЕДИНЕНИЕ УСТАНОВЛЕНО");
                        connectionState.setForeground(new java.awt.Color(43, 116, 61));
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
                        connectionState.setText("● НЕТ СОЕДИНЕНИЯ");
                        connectionState.setForeground(new java.awt.Color(170, 55, 45));
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
        JDialog dialog = new JDialog(frame, "Minecraft 3D — Ресурс-паки", true);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<ResourcePackManager.ResourcePackInfo> model = new DefaultListModel<>();
        JList<ResourcePackManager.ResourcePackInfo> packs = new JList<>(model);
        packs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        packs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        packs.setBackground(new java.awt.Color(250, 252, 250));
        packs.setSelectionBackground(new java.awt.Color(214, 232, 216));
        packs.setSelectionForeground(new java.awt.Color(30, 48, 35));
        String[] activePackId = {"Default"};
        packs.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> source, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        source, value, index, selected, focused);
                if (value instanceof ResourcePackManager.ResourcePackInfo pack) {
                    String state = pack.id().equals(activePackId[0]) ? "● АКТИВЕН" : "○ ДОСТУПЕН";
                    String type = pack.builtIn() ? "встроенный" : "установленный";
                    label.setText("<html><b>" + htmlEscape(pack.name()) + "</b> &nbsp; " + state
                            + "<br><small>" + htmlEscape(pack.id()) + " · " + type + "</small></html>");
                    label.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
                }
                return label;
            }
        });

        JLabel details = new JLabel("Выбери ресурс-пак, чтобы увидеть описание.");
        details.setOpaque(true);
        details.setBackground(java.awt.Color.WHITE);
        details.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(215, 225, 215)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JButton apply = createLauncherButton("ПРИМЕНИТЬ");
        JButton install = createLauncherButton("ИМПОРТ ZIP");
        JButton remove = createLauncherButton("УДАЛИТЬ ПАК");
        JButton folder = createLauncherButton("ПАПКА ПАКОВ");
        JButton refresh = createLauncherButton("ОБНОВИТЬ");
        JButton close = createLauncherButton("ЗАКРЫТЬ");
        apply.setEnabled(false);
        remove.setEnabled(false);

        Runnable updateSelection = () -> {
            ResourcePackManager.ResourcePackInfo selected = packs.getSelectedValue();
            apply.setEnabled(selected != null && !selected.id().equals(activePackId[0]));
            remove.setEnabled(selected != null && !selected.builtIn());
            if (selected == null) {
                details.setText("Выбери ресурс-пак, чтобы увидеть описание.");
            } else {
                String state = selected.id().equals(activePackId[0]) ? "Сейчас активен" : "Не активен";
                String source = selected.builtIn() ? "Встроенный ресурс-пак" : "Установленный ресурс-пак";
                details.setText("<html><b>" + htmlEscape(selected.name()) + "</b> · " + state
                        + "<br>" + source
                        + "<br><br>" + htmlEscape(selected.description())
                        + "<br><br><small>ID: " + htmlEscape(selected.id())
                        + "<br>Каталог: " + htmlEscape(selected.directory().toString())
                        + "<br>Текстуры: textures/blocks/&lt;block-id&gt;.png (16×16)</small></html>");
            }
        };
        Runnable reload = () -> {
            try {
                String selectedId = manager.selectedPackId();
                activePackId[0] = selectedId;
                model.clear();
                for (ResourcePackManager.ResourcePackInfo pack : manager.listPacks()) {
                    model.addElement(pack);
                    if (pack.id().equals(selectedId)) packs.setSelectedValue(pack, true);
                }
                if (packs.getSelectedValue() == null && !model.isEmpty()) packs.setSelectedIndex(0);
                packs.repaint();
                updateSelection.run();
            } catch (IOException error) {
                details.setText("Не удалось прочитать ресурс-паки: " + htmlEscape(error.getMessage()));
                GameLog.error("Не удалось обновить список ресурс-паков", error);
            }
        };
        packs.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelection.run();
        });

        apply.addActionListener(event -> {
            ResourcePackManager.ResourcePackInfo selected = packs.getSelectedValue();
            if (selected == null) return;
            try {
                manager.select(selected.id());
                status.setText("Ресурс-пак «" + selected.name() + "» применится при следующем запуске мира.");
                reload.run();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка применения пакета", JOptionPane.ERROR_MESSAGE);
            }
        });
        install.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Импорт ресурс-пака ZIP");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Resource pack (*.zip)", "zip"));
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            try {
                ResourcePackManager.ResourcePackInfo installed = manager.installZip(chooser.getSelectedFile().toPath());
                reload.run();
                packs.setSelectedValue(installed, true);
                updateSelection.run();
                JOptionPane.showMessageDialog(dialog,
                        "Установлен ресурс-пак: " + installed.name()
                                + ". Проверь описание и нажми «ПРИМЕНИТЬ».",
                        "Импорт завершён", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка импорта", JOptionPane.ERROR_MESSAGE);
            }
        });
        remove.addActionListener(event -> {
            ResourcePackManager.ResourcePackInfo selected = packs.getSelectedValue();
            if (selected == null) return;
            if (selected.builtIn()) {
                JOptionPane.showMessageDialog(dialog, "Встроенный ресурс-пак нельзя удалить.",
                        "Информация", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Удалить ресурс-пак «" + selected.name() + "»?",
                    "Удалить пак", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                manager.remove(selected.id());
                reload.run();
            } catch (IOException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                        "Ошибка удаления", JOptionPane.ERROR_MESSAGE);
            }
        });
        folder.addActionListener(event -> openFolder(manager.root()));
        refresh.addActionListener(event -> reload.run());
        close.addActionListener(event -> dialog.dispose());

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(createSectionBanner("РЕСУРС-ПАКИ",
                "Выбери внешний вид блоков и текстур для следующего запуска мира."), BorderLayout.NORTH);
        JLabel hint = new JLabel("Импорт ZIP: в корне архива должны находиться pack.properties и папка textures/blocks.");
        hint.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        top.add(hint, BorderLayout.SOUTH);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        center.add(new JScrollPane(packs), BorderLayout.CENTER);
        center.add(details, BorderLayout.SOUTH);
        JPanel buttons = new JPanel(new GridLayout(2, 3, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        buttons.add(apply);
        buttons.add(install);
        buttons.add(remove);
        buttons.add(folder);
        buttons.add(refresh);
        buttons.add(close);

        dialog.add(top, BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        reload.run();
        dialog.getRootPane().setDefaultButton(apply);
        dialog.setMinimumSize(new Dimension(700, 480));
        dialog.setSize(760, 520);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
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
