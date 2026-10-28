package com.wilauncher.mobile

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mio.data.Renderer
import com.tungsten.fclcore.download.LibraryAnalyzer
import com.tungsten.fclcore.download.LibraryAnalyzer.LibraryType
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.game.Version
import com.tungsten.fclcore.mod.LocalModFile
import com.tungsten.fclcore.mod.ModManager
import com.tungsten.fclcore.util.Logging.LOG
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level

/**
 * Optimisation mods that WiLauncher keeps in every Fabric, Quilt, Forge and NeoForge game.
 *
 * Before each launch the matching build of every mod is fetched from Modrinth (only when it is
 * missing), re-enabled if it was disabled and recorded in `mods/.wilauncher-builtin.json`. The mod
 * list shows those files as built-in and offers no way to delete or turn them off. A built-in mod is
 * dropped only when it would break the game: the player installed their own copy, a conflicting mod
 * (OptiFine for Sodium, for example), or the renderer can't run it.
 */
object BuiltinMods {

    private const val MANIFEST = ".wilauncher-builtin.json"
    /** A project without a build for this game version is looked up again after a day. */
    private const val RECHECK_MISSING_MS = 24L * 60 * 60 * 1000

    private class Spec(
        val projectId: String,
        val modId: String,
        val title: String,
        val conflicts: Set<String> = emptySet(),
        /** Needs a renderer with modern OpenGL; skipped on Holy-GL4ES and VGPU. */
        val modernRenderer: Boolean = false,
    )

    private val SODIUM = Spec("AANobbMI", "sodium", "Sodium", setOf("optifabric", "optifine", "embeddium", "rubidium", "magnesium"), modernRenderer = true)
    private val EMBEDDIUM = Spec("sk9rgfiA", "embeddium", "Embeddium", setOf("optifine", "rubidium", "sodium"), modernRenderer = true)
    private val LITHIUM = Spec("gvQqBUqZ", "lithium", "Lithium", setOf("canary", "radium"))
    private val FERRITE_CORE = Spec("uXXizFIs", "ferritecore", "FerriteCore")
    private val MODERN_FIX = Spec("nmDcB62a", "modernfix", "ModernFix")
    private val ENTITY_CULLING = Spec("NNAgCjsB", "entityculling", "Entity Culling")
    private val IMMEDIATELY_FAST = Spec("5ZwdcRci", "immediatelyfast", "ImmediatelyFast", modernRenderer = true)

    private val FABRIC = listOf(SODIUM, LITHIUM, FERRITE_CORE, MODERN_FIX, ENTITY_CULLING, IMMEDIATELY_FAST)
    private val NEOFORGE = listOf(SODIUM, LITHIUM, FERRITE_CORE, MODERN_FIX, ENTITY_CULLING, IMMEDIATELY_FAST)
    private val FORGE = listOf(EMBEDDIUM, FERRITE_CORE, MODERN_FIX, ENTITY_CULLING, IMMEDIATELY_FAST)

    /** Names shown in the catalog's "already built in" note. */
    val TITLES = listOf("Sodium / Embeddium", "Lithium", "FerriteCore", "ModernFix", "Entity Culling", "ImmediatelyFast")

    /** Mod ids of common libraries, so a library the player already has is not downloaded twice. */
    private val LIBRARY_IDS = mapOf(
        "P7dR8mSH" to setOf("fabric-api", "fabric"),
        "Ha28R6CL" to setOf("fabric-language-kotlin"),
        "qvIfYCYJ" to setOf("quilted_fabric_api"),
        "ordsPcFz" to setOf("kotlinforforge"),
    )

    private val builtinNames = ConcurrentHashMap<Path, Pair<Long, Set<String>>>()

    /** True when [fileName] (with or without ".disabled") is a built-in mod in [modsDir]. */
    @JvmStatic
    fun isBuiltin(modsDir: Path, fileName: String): Boolean {
        val name = fileName.removeSuffix(ModManager.DISABLED_EXTENSION)
        val manifest = modsDir.resolve(MANIFEST)
        val stamp = runCatching { Files.getLastModifiedTime(manifest).toMillis() }.getOrDefault(-1L)
        val cached = builtinNames[modsDir]
        val names = if (cached != null && cached.first == stamp) cached.second else {
            val read = if (stamp < 0) emptySet() else readManifest(modsDir).files.values.map { it.file }.toSet()
            builtinNames[modsDir] = stamp to read
            read
        }
        return name in names
    }

    private class Entry(val file: String, val version: String, val requiredBy: String?)

    private class Manifest(
        var key: String = "",
        val files: MutableMap<String, Entry> = linkedMapOf(),
        val missing: MutableMap<String, Long> = linkedMapOf(),
    )

    private fun readManifest(modsDir: Path): Manifest {
        val manifest = Manifest()
        val file = modsDir.resolve(MANIFEST)
        if (!Files.isRegularFile(file)) return manifest
        try {
            val json = JsonParser.parseString(String(Files.readAllBytes(file), Charsets.UTF_8)).asJsonObject
            manifest.key = json.get("key")?.asString ?: ""
            json.getAsJsonObject("files")?.entrySet()?.forEach { (project, value) ->
                val o = value.asJsonObject
                val name = o.get("file")?.asString ?: return@forEach
                if (name.contains('/') || name.contains('\\')) return@forEach
                manifest.files[project] = Entry(name, o.get("version")?.asString ?: "", o.get("requiredBy")?.asString)
            }
            json.getAsJsonObject("missing")?.entrySet()?.forEach { (project, value) ->
                manifest.missing[project] = value.asLong
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "Malformed $MANIFEST, starting over", e)
        }
        return manifest
    }

    private fun writeManifest(modsDir: Path, manifest: Manifest) {
        val json = JsonObject()
        json.addProperty("key", manifest.key)
        json.add("files", JsonObject().apply {
            manifest.files.forEach { (project, entry) ->
                add(project, JsonObject().apply {
                    addProperty("file", entry.file)
                    addProperty("version", entry.version)
                    entry.requiredBy?.let { addProperty("requiredBy", it) }
                })
            }
        })
        json.add("missing", JsonObject().apply { manifest.missing.forEach { (k, v) -> addProperty(k, v) } })
        Files.createDirectories(modsDir)
        val tmp = modsDir.resolve("$MANIFEST.tmp")
        Files.write(tmp, json.toString().toByteArray(Charsets.UTF_8))
        Files.move(tmp, modsDir.resolve(MANIFEST), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Modrinth loader names for the game's mod loader, or null for vanilla. */
    @JvmStatic
    fun loadersOf(version: Version): List<String>? {
        val analyzer = LibraryAnalyzer.analyze(version, null)
        return when {
            analyzer.has(LibraryType.QUILT) -> listOf("quilt", "fabric")
            analyzer.has(LibraryType.FABRIC) -> listOf("fabric")
            analyzer.has(LibraryType.NEO_FORGE) -> listOf("neoforge")
            analyzer.has(LibraryType.FORGE) -> listOf("forge")
            else -> null
        }
    }

    /**
     * Makes sure the built-in mods of the game are present and enabled. [mods] is the scan the
     * launcher already did; the returned list reflects any change (a rescan only happens then).
     * Never throws: without network the game simply starts with what is already installed.
     */
    @JvmStatic
    fun ensure(
        repository: DefaultGameRepository,
        versionId: String,
        version: Version,
        rendererId: String,
        mods: List<LocalModFile>?,
        progress: Consumer<String>,
    ): List<LocalModFile>? {
        return try {
            ensure0(repository, versionId, version, rendererId, mods, progress)
        } catch (e: Throwable) {
            LOG.log(Level.WARNING, "Built-in mods check failed", e)
            mods
        }
    }

    private fun ensure0(
        repository: DefaultGameRepository,
        versionId: String,
        version: Version,
        rendererId: String,
        mods: List<LocalModFile>?,
        progress: Consumer<String>,
    ): List<LocalModFile>? {
        val loaders = loadersOf(version) ?: return mods
        val gameVersion = repository.getGameVersion(version).orElse(null) ?: return mods
        val specs = when (loaders.first()) {
            "neoforge" -> NEOFORGE
            "forge" -> FORGE
            else -> FABRIC
        }
        val modManager = repository.getModManager(versionId)
        val modsDir = modManager.modsDirectory
        val manifest = readManifest(modsDir)
        var changed = false

        // A different game version or loader (the game was reinstalled): start from scratch.
        val key = "$gameVersion|${loaders.first()}"
        if (manifest.key != key) {
            manifest.files.values.forEach { deleteBuiltin(modsDir, it.file) }
            changed = changed || manifest.files.isNotEmpty()
            manifest.files.clear()
            manifest.missing.clear()
            manifest.key = key
        }

        val current = mods ?: modManager.mods
        val ownNames = manifest.files.values.map { it.file }.toSet()
        val playerIds = current
            .filter { it.isActive && ModManager.getModName(it.file) !in ownNames }
            .map { it.id }
            .toSet()
        val modernRenderer = rendererId != Renderer.ID_GL4ES && rendererId != Renderer.ID_VGPU

        var online = true
        val now = System.currentTimeMillis()

        fun install(projectId: String, title: String, requiredBy: String?) {
            if (!online || manifest.files.containsKey(projectId)) return
            if (LIBRARY_IDS[projectId]?.any { it in playerIds } == true) return
            val missingSince = manifest.missing[projectId]
            if (missingSince != null && now - missingSince < RECHECK_MISSING_MS) return
            try {
                val ref = Modrinth.findFile(projectId, gameVersion, loaders)
                if (ref == null) {
                    manifest.missing[projectId] = now
                    changed = true
                    return
                }
                manifest.missing.remove(projectId)
                progress.accept(title)
                Modrinth.download(ref, modsDir)
                manifest.files[projectId] = Entry(ref.fileName, ref.versionNumber, requiredBy)
                changed = true
                LOG.info("Installed built-in mod $title ${ref.versionNumber} (${ref.fileName})")
                for (dependency in ref.requiredProjects) {
                    install(dependency, dependency, requiredBy ?: projectId)
                }
            } catch (e: IOException) {
                online = false
                LOG.log(Level.WARNING, "Can't fetch built-in mod $title, will retry next launch", e)
            }
        }

        for (spec in specs) {
            val entry = manifest.files[spec.projectId]
            val unwanted = (spec.modernRenderer && !modernRenderer) ||
                    spec.modId in playerIds || spec.conflicts.any { it in playerIds }
            if (unwanted) {
                if (entry != null) {
                    deleteBuiltin(modsDir, entry.file)
                    manifest.files.remove(spec.projectId)
                    changed = true
                }
                continue
            }
            if (entry != null) {
                when (restore(modsDir, entry.file)) {
                    FileState.PRESENT -> continue
                    FileState.RESTORED -> {
                        changed = true
                        continue
                    }
                    FileState.GONE -> {
                        manifest.files.remove(spec.projectId)
                        changed = true
                    }
                }
            }
            install(spec.projectId, spec.title, null)
        }
        // Libraries of the built-in mods get the same treatment; a deleted one is fetched again.
        for ((projectId, entry) in manifest.files.entries.toList()) {
            if (entry.requiredBy == null) continue
            if (LIBRARY_IDS[projectId]?.any { it in playerIds } == true) {
                // The player added their own copy of the library: keep theirs only.
                deleteBuiltin(modsDir, entry.file)
                manifest.files.remove(projectId)
                changed = true
                continue
            }
            when (restore(modsDir, entry.file)) {
                FileState.PRESENT -> Unit
                FileState.RESTORED -> changed = true
                FileState.GONE -> {
                    manifest.files.remove(projectId)
                    changed = true
                    install(projectId, projectId, entry.requiredBy)
                }
            }
        }

        if (!changed) return current
        writeManifest(modsDir, manifest)
        modManager.refreshMods()
        return modManager.mods
    }

    private enum class FileState { PRESENT, RESTORED, GONE }

    /** Re-enables a built-in file that was turned off. */
    private fun restore(modsDir: Path, name: String): FileState {
        val file = modsDir.resolve(name)
        if (Files.isRegularFile(file)) return FileState.PRESENT
        val disabled = modsDir.resolve(name + ModManager.DISABLED_EXTENSION)
        if (Files.isRegularFile(disabled)) {
            Files.move(disabled, file, StandardCopyOption.REPLACE_EXISTING)
            return FileState.RESTORED
        }
        return FileState.GONE
    }

    private fun deleteBuiltin(modsDir: Path, name: String) {
        Files.deleteIfExists(modsDir.resolve(name))
        Files.deleteIfExists(modsDir.resolve(name + ModManager.DISABLED_EXTENSION))
    }
}
