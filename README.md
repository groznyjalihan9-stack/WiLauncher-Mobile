# WiLauncher для Android

Лаунчер Minecraft: Java Edition для телефонов и планшетов — мобильная версия [WiLauncher](https://github.com/groznyjalihan9-stack/WiLauncher-releases).

**Скачать APK:** [WiLauncher-releases → Releases](https://github.com/groznyjalihan9-stack/WiLauncher-releases/releases) (файлы `WiLauncher-Android-*`).
Для большинства телефонов нужен `arm64-v8a`, для старых 32-битных — `armeabi-v7a`, для эмуляторов на ПК — `x86_64`.

## Что умеет

- **Встроенная оптимизация.** В каждой игре с Fabric, Quilt, Forge или NeoForge лаунчер сам ставит и держит включёнными Sodium (на Forge — Embeddium), Lithium, FerriteCore, ModernFix, Entity Culling и ImmediatelyFast. Эти моды нельзя удалить или отключить в списке модов, а если удалить файл вручную, его вернут при следующем запуске. Встроенный мод убирается сам, если он сломает игру: вы поставили свою копию, конфликтующий мод (например OptiFine) или выбрали рендерер, на котором он не работает.
- **Fabric по умолчанию.** При установке новой версии сразу выбраны свежий Fabric и Fabric API, так что оптимизация работает без настройки. Если нужна «чистая» игра, Fabric можно убрать.
- **Каталог WiLauncher** вместо поиска по сайту. Это короткий проверенный список модов и ресурспаков, которые работают на телефоне, с описаниями на русском. Он разбит на разделы: оптимизация, интерфейс, карты, графика, геймплей, ресурспаки. Мод ставится в выбранную игру одной кнопкой вместе с зависимостями. Полный поиск по Modrinth остаётся на кнопке «Все моды (Modrinth)».
- **Установка мода из файла.** Если открыть `.jar` в браузере или файловом менеджере через «Открыть с помощью → WiLauncher», мод сразу попадёт в выбранную игру.
- **Сенсорная раскладка под телефон.** Джойстик под левым пальцем. Под правым — прыжок, атака, «использовать», присед, инвентарь и «выбросить». Сверху по центру — чат, пауза и вид, а угол оставлен свободным для миникарты. В меню на экране — Esc, клавиатура, Enter и Backspace. Для управления как в Bedrock в каталоге есть TouchController.
- **Настройки игры для телефона.** Дальность прорисовки 6, быстрая графика, без облаков и теней существ, 60 FPS, автопрыжок. Язык игры берётся из языка телефона.
- **Аккаунты:** офлайн и Ely.by (сервер уже добавлен). Входа через Microsoft нет: у WiLauncher нет своего client ID, а чужие использовать нельзя.
- Фиолетовая тема и иконка WiLauncher, обновления из репозитория WiLauncher-releases.

## Основа и лицензия

WiLauncher для Android — **изменённая версия [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher)** (FCL-Team), который основан на HMCLCore, PojavLauncher и Boat. Это не официальная сборка FCL. Что именно изменено, описано в [NOTICE.md](NOTICE.md), оригинальное README FCL лежит в [docs/upstream](docs/upstream).

Распространяется по [GNU GPL v3](LICENSE), как и FCL. Компоненты сторонних проектов используются по своим лицензиям (список в [docs/upstream/FCL-README_EN.md](docs/upstream/FCL-README_EN.md#-license)). WiLauncher не содержит файлов Minecraft — игра скачивается с серверов Mojang на устройстве. WiLauncher не связан с Mojang, Microsoft и FCL-Team.

Сборка из исходников описана в [docs/BUILD-ANDROID.md](docs/BUILD-ANDROID.md).

---

## English

WiLauncher for Android is a Minecraft: Java Edition launcher for phones, a modified version of Fold Craft Launcher (GPL-3.0). It adds built-in, non-removable optimisation mods for modded games, Fabric preselected for new installs, a curated mobile-friendly mod catalog with one-tap install, "open .jar with WiLauncher", a phone-tuned touch layout and mobile defaults for `options.txt`. Changes are listed in [NOTICE.md](NOTICE.md); build steps are in [docs/BUILD-ANDROID.md](docs/BUILD-ANDROID.md).
