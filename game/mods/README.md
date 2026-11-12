# BLOCKWORLD 3D Mods

Лаунчер позволяет импортировать, включать, отключать и удалять моды. Папки:

- Windows: `%USERPROFILE%\.blockworld3d\mods`
- Linux/macOS: `~/.blockworld3d/mods`
- Отключённые JAR: подпапка `disabled`
- Конфигурация каждого мода: `~/.blockworld3d/config/mods/<имя-мода>-<id>/`

Включённый `.jar` запускается при старте мира. После изменения списка модов закрой и заново запусти мир.

## Минимальный API

Мод должен реализовать `com.blockworld.api.BlockWorldMod` и объявить provider в `META-INF/services/com.blockworld.api.BlockWorldMod`. Методы жизненного цикла: `onInitialize`, `onWorldReady`, `onUpdate`, `onWorldSave`, `onShutdown`. В `ModContext` доступны `VoxelWorld`, выбранная версия и папка конфигурации. API предварительное и может меняться до 1.0.0.

**Безопасность:** моды — исполняемые Java-программы. Они работают с правами пользователя Windows/Linux. Не включай неизвестные JAR-файлы. Ошибки одного мода пишутся в `.logs/latest-run.log`; игра пытается продолжить работу.
