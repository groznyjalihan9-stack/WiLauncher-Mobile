package com.wilauncher.mobile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tungsten.fclcore.util.io.NetworkUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Minimal Modrinth client used by the built-in mods and the WiLauncher catalog: picks the file of a
 * project that fits a game version and loader, and downloads it with SHA-1 verification.
 */
object Modrinth {

    private const val API = "https://api.modrinth.com/v2"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 20000

    data class FileRef(
        val projectId: String,
        val versionId: String,
        val versionNumber: String,
        val url: String,
        val fileName: String,
        val sha1: String,
        val size: Long,
        val requiredProjects: List<String>,
    )

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun jsonList(values: List<String>) = values.joinToString(",", "[", "]") { "\"$it\"" }

    private fun open(url: String): HttpURLConnection {
        val connection = NetworkUtils.createHttpConnection(URL(url))
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        return connection
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getText(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Newest file of [projectId] for [gameVersion] and any of [loaders]; releases win over betas and
     * betas over alphas. Returns null when the project has no build for that game version.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun findFile(projectId: String, gameVersion: String, loaders: List<String>): FileRef? {
        val url = "$API/project/${enc(projectId)}/version" +
                "?loaders=${enc(jsonList(loaders))}&game_versions=${enc(jsonList(listOf(gameVersion)))}"
        val versions = try {
            JsonParser.parseString(getText(url)).asJsonArray.map { it.asJsonObject }
        } catch (e: IllegalStateException) {
            throw IOException("Malformed Modrinth response for $projectId", e)
        }
        val pick = listOf("release", "beta", "alpha").firstNotNullOfOrNull { type ->
            versions.firstOrNull { it.string("version_type") == type }
        } ?: return null
        val files = pick.getAsJsonArray("files").map { it.asJsonObject }
        val file = files.firstOrNull { it.get("primary")?.asBoolean == true } ?: files.firstOrNull() ?: return null
        val deps = pick.getAsJsonArray("dependencies").map { it.asJsonObject }
            .filter { it.string("dependency_type") == "required" }
            .mapNotNull { it.string("project_id") }
        return FileRef(
            projectId = projectId,
            versionId = pick.string("id") ?: return null,
            versionNumber = pick.string("version_number") ?: "",
            url = file.string("url") ?: return null,
            fileName = file.string("filename") ?: return null,
            sha1 = file.getAsJsonObject("hashes")?.string("sha1") ?: return null,
            size = file.get("size")?.asLong ?: 0L,
            requiredProjects = deps,
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString

    /** Only plain file names from Modrinth's CDN are accepted, so a reply can't write outside [dir]. */
    private fun checkSafe(ref: FileRef, extension: String) {
        val name = ref.fileName
        require(name.endsWith(extension) && !name.contains('/') && !name.contains('\\') && !name.startsWith(".")) {
            "Unexpected file name from Modrinth: $name"
        }
        val host = URL(ref.url).host.lowercase()
        require(URL(ref.url).protocol == "https" && (host == "cdn.modrinth.com" || host.endsWith(".modrinth.com"))) {
            "Unexpected download host: $host"
        }
    }

    /** Downloads [ref] into [dir] (atomically, verified by SHA-1) and returns the final path. */
    @JvmStatic
    @Throws(IOException::class)
    @JvmOverloads
    fun download(ref: FileRef, dir: Path, extension: String = ".jar"): Path {
        checkSafe(ref, extension)
        Files.createDirectories(dir)
        val target = dir.resolve(ref.fileName)
        val part = dir.resolve(".${ref.fileName}.part")
        val digest = MessageDigest.getInstance("SHA-1")
        val connection = open(ref.url)
        try {
            if (connection.responseCode != 200) {
                throw IOException("HTTP ${connection.responseCode} for ${ref.url}")
            }
            connection.inputStream.use { input ->
                Files.newOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            Files.deleteIfExists(part)
            throw e
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(ref.sha1, ignoreCase = true)) {
            Files.deleteIfExists(part)
            throw IOException("Checksum mismatch for ${ref.fileName}")
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return target
    }

    /** Maps SHA-1 hashes of local files to their Modrinth project ids (unknown files are left out). */
    @JvmStatic
    @Throws(IOException::class)
    fun identify(sha1: List<String>): Map<String, String> {
        val result = HashMap<String, String>()
        for (chunk in sha1.distinct().chunked(500)) {
            val body = JsonObject().apply {
                add("hashes", JsonArray().apply { chunk.forEach { add(it) } })
                addProperty("algorithm", "sha1")
            }.toString().toByteArray(Charsets.UTF_8)
            val connection = open("$API/version_files")
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body) }
                if (connection.responseCode != 200) {
                    throw IOException("HTTP ${connection.responseCode} for version_files")
                }
                val json = connection.inputStream.use { JsonParser.parseString(it.readBytes().decodeToString()).asJsonObject }
                for ((hash, version) in json.entrySet()) {
                    version.asJsonObject.string("project_id")?.let { result[hash] = it }
                }
            } catch (e: IllegalStateException) {
                throw IOException("Malformed Modrinth response", e)
            } finally {
                connection.disconnect()
            }
        }
        return result
    }

    @JvmStatic
    fun sha1(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
