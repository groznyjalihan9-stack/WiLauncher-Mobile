package com.wilauncher.mobile

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.util.Logging.LOG
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.logging.Level

/**
 * The WiLauncher catalog: a short, curated list of mods and resource packs that work on phones.
 * The list ships with the app (assets/catalog/catalog.json) and is refreshed from the releases
 * repository; files come from Modrinth's CDN and are checked by SHA-1.
 */
object Catalog {

    const val REMOTE_URL =
        "https://raw.githubusercontent.com/groznyjalihan9-stack/WiLauncher-releases/main/android/catalog.json"
    private const val ASSET = "catalog/catalog.json"

    class Category(val id: String, private val title: Map<String, String>) {
        fun title(context: Context) = localized(title, context)
    }

    class Item(
        val id: String,
        val slug: String,
        val name: String,
        val icon: String,
        val type: String,
        val category: String,
        val loaders: List<String>,
        private val summary: Map<String, String>,
        val heavy: Boolean,
        val requires: List<String>,
    ) {
        fun summary(context: Context) = localized(summary, context)
        val isResourcePack get() = type == "resourcepack"
    }

    class Data(val version: Long, val categories: List<Category>, val items: List<Item>)

    private fun localized(map: Map<String, String>, context: Context): String {
        val language = context.resources.configuration.locales[0].language
        val ruLike = language in setOf("ru", "uk", "be", "kk")
        return (if (ruLike) map["ru"] else null) ?: map["en"] ?: map.values.firstOrNull() ?: ""
    }

    private fun parse(text: String): Data {
        val json = JsonParser.parseString(text).asJsonObject
        fun JsonObject.strings(key: String) = getAsJsonObject(key)?.entrySet()?.associate { it.key to it.value.asString } ?: emptyMap()
        fun JsonObject.list(key: String) = getAsJsonArray(key)?.map { it.asString } ?: emptyList()
        val categories = json.getAsJsonArray("categories").map {
            val o = it.asJsonObject
            Category(o.get("id").asString, o.strings("title"))
        }
        val items = json.getAsJsonArray("items").map {
            val o = it.asJsonObject
            Item(
                id = o.get("id").asString,
                slug = o.get("slug")?.asString ?: "",
                name = o.get("name").asString,
                icon = o.get("icon")?.asString ?: "",
                type = o.get("type")?.asString ?: "mod",
                category = o.get("category")?.asString ?: "",
                loaders = o.list("loaders"),
                summary = o.strings("summary"),
                heavy = o.get("heavy")?.asBoolean ?: false,
                requires = o.list("requires"),
            )
        }
        return Data(json.get("version")?.asLong ?: 0L, categories, items)
    }

    private val cacheFile get() = File(FCLPath.FILES_DIR, "wl-catalog.json")

    /** Newest catalog available offline: the cached download if it is newer than the bundled one. */
    @JvmStatic
    fun load(context: Context): Data {
        val bundled = context.assets.open(ASSET).use { parse(it.readBytes().decodeToString()) }
        val cached = runCatching { parse(cacheFile.readText()) }.getOrNull()
        return if (cached != null && cached.version > bundled.version) cached else bundled
    }

    /** Fetches the published catalog; returns it when it is newer than [current], else null. */
    @JvmStatic
    fun refresh(current: Data): Data? {
        return try {
            val text = Modrinth.getText(REMOTE_URL)
            val remote = parse(text)
            if (remote.version <= current.version || remote.items.isEmpty()) return null
            cacheFile.writeText(text)
            remote
        } catch (e: Exception) {
            LOG.log(Level.INFO, "Catalog refresh skipped: $e")
            null
        }
    }

    /** What the selected game can take: its Minecraft version and mod loaders (empty for vanilla). */
    class Target(val versionId: String, val gameVersion: String, val loaders: List<String>, val runDir: Path, val modsDir: Path)

    @JvmStatic
    fun target(repository: DefaultGameRepository, versionId: String?): Target? {
        if (versionId == null || !repository.hasVersion(versionId)) return null
        val version = repository.getResolvedVersion(versionId)
        val gameVersion = repository.getGameVersion(version).orElse(null) ?: return null
        val loaders = BuiltinMods.loadersOf(version) ?: emptyList()
        val runDir = repository.getRunDirectory(versionId).toPath()
        return Target(versionId, gameVersion, loaders, runDir, repository.getModManager(versionId).modsDirectory)
    }

    enum class Availability { OK, NEEDS_LOADER, WRONG_LOADER }

    @JvmStatic
    fun availability(item: Item, target: Target): Availability = when {
        item.isResourcePack && item.requires.isEmpty() -> Availability.OK
        target.loaders.isEmpty() -> Availability.NEEDS_LOADER
        !item.isResourcePack && item.loaders.none { it in target.loaders } -> Availability.WRONG_LOADER
        else -> Availability.OK
    }

    private val hashCache = HashMap<String, Pair<Long, String>>()

    private fun sha1Cached(file: Path): String {
        val key = file.toString()
        val stamp = Files.getLastModifiedTime(file).toMillis() xor Files.size(file)
        hashCache[key]?.let { if (it.first == stamp) return it.second }
        val hash = Modrinth.sha1(file)
        hashCache[key] = stamp to hash
        return hash
    }

    /** Modrinth project ids of the mods and resource packs already in the selected game. */
    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun installedProjects(target: Target): Set<String> {
        val files = buildList {
            listOf(target.modsDir to ".jar", target.runDir.resolve("resourcepacks") to ".zip").forEach { (dir, ext) ->
                if (Files.isDirectory(dir)) Files.list(dir).use { stream ->
                    stream.filter { Files.isRegularFile(it) && (it.fileName.toString().endsWith(ext) || it.fileName.toString().endsWith("$ext.disabled")) }
                        .forEach { add(it) }
                }
            }
        }
        if (files.isEmpty()) return emptySet()
        return Modrinth.identify(files.map { sha1Cached(it) }).values.toSet()
    }

    /**
     * Installs [item] with everything it needs into the selected game and returns the names of the
     * files that were added. Libraries the game already has are not downloaded again.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun install(item: Item, target: Target, installed: Set<String>): List<String> {
        val added = ArrayList<String>()
        val have = HashSet(installed)

        fun installMod(projectId: String, loaders: List<String>) {
            if (!have.add(projectId)) return
            val ref = Modrinth.findFile(projectId, target.gameVersion, loaders)
                ?: throw NotAvailableException(projectId)
            Modrinth.download(ref, target.modsDir)
            added += ref.fileName
            ref.requiredProjects.forEach { installMod(it, target.loaders) }
        }

        item.requires.forEach { installMod(it, target.loaders) }
        if (item.isResourcePack) {
            if (have.add(item.id)) {
                val ref = Modrinth.findFile(item.id, target.gameVersion, listOf("minecraft"))
                    ?: throw NotAvailableException(item.id)
                Modrinth.download(ref, target.runDir.resolve("resourcepacks"), ".zip")
                added += ref.fileName
            }
        } else {
            installMod(item.id, item.loaders.filter { it in target.loaders }.ifEmpty { target.loaders })
        }
        return added
    }

    /** The project has no build for this Minecraft version / loader. */
    class NotAvailableException(val projectId: String) : IOException("No build of $projectId for this game")

    fun lowercaseQuery(text: String) = text.trim().lowercase(Locale.ROOT)
}
