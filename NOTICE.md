# Notice of modification

This program is a modified version of **Fold Craft Launcher** (FCL), © FCL-Team and contributors,
released under the GNU General Public License v3. Base: FCL 1.3.4.2, upstream commit `fc843f1`
(https://github.com/FCL-Team/FoldCraftLauncher). FCL itself builds on HMCLCore, PojavLauncher and Boat.

Modified by the WiLauncher project, October 2026. Main changes:

- Name, application id (`com.wilauncher.mobile`), icon, purple theme, shared-storage folder (`/WiLauncher`), user agent,
  About page, user agreement and privacy policy (`FCL/src/main/assets/legal`), Russian texts.
- Removed: the Quark Drive launch promotion, FCL announcements and update feed (now WiLauncher-releases),
  Microsoft sign-in in builds without an own client id, FCL's signing keys and CI.
- Added `com.wilauncher.mobile` package:
  - `BuiltinMods` — optimisation mods fetched from Modrinth and kept enabled in modded games (hooked into `LauncherHelper`,
    locked in `ModListPage` / `LocalModListAdapter`);
  - `Catalog`, `CatalogPage` — curated catalog tab in the download screen (`catalog/catalog.source.json`, `scripts/build-catalog.mjs`);
  - `Modrinth` — small client with SHA-1 verified downloads;
  - `ModFileImport` — "open .jar with WiLauncher";
  - `GameDefaults` — game language from the device locale.
- Fabric + Fabric API preselected when installing a new version (`VersionInstallInfoPage`).
- Ely.by preconfigured as an authentication server (`AuthlibInjectorServers`).
- New default touch layout (`scripts/build-controls.mjs` → `assets/controllers/00000000.json`) and mobile `options.txt`.
- libfcl built with 16 KB page alignment; Chinese mod names shown only in the Chinese interface.

All files keep the GPL-3.0 license. Third-party components keep their own licenses.
