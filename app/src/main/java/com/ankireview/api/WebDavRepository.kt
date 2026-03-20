package com.ankireview.api

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

// ── WebDAV item ──────────────────────────────────
data class WebDavItem(
    val href: String,          // URL path
    val name: String,          // display name
    val isDirectory: Boolean,
    val contentType: String = "",
    val size: Long = 0L
) {
    val isMdFile get() = !isDirectory && name.endsWith(".md", ignoreCase = true)
    val isImage  get() = !isDirectory && name.matches(Regex(".*\\.(png|jpg|jpeg|gif|webp|svg)", RegexOption.IGNORE_CASE))
}

// ── WebDAV Repository ────────────────────────────
class WebDavRepository(
    private val serverUrl: String,   // e.g. https://dav.jianguoyun.com/dav
    private val username: String,
    private val password: String
) {
    companion object {
        const val JIANGUOYUN_URL = "https://dav.jianguoyun.com/dav"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val credential = Credentials.basic(username, password)

    private fun baseUrl(): String = serverUrl.trimEnd('/')

    /** Build full URL for a given path */
    private fun url(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return "${baseUrl()}$p"
    }

    /** PROPFIND — list directory contents */
    suspend fun listFolder(path: String): List<WebDavItem> {
        val body = """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:resourcetype/>
                <d:getcontenttype/>
                <d:getcontentlength/>
                <d:displayname/>
              </d:prop>
            </d:propfind>""".trimIndent()

        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", credential)
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("WebDAV 请求失败 ${resp.code}: ${resp.message}")
        val xml = resp.body?.string() ?: return emptyList()
        return parseWebDavResponse(xml, path)
    }

    /** GET — read file content as text */
    suspend fun readFile(path: String): String {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", credential)
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("读取文件失败: ${resp.code}")
        return resp.body?.string() ?: ""
    }

    /** GET — get authenticated URL for image (returns byte array for display) */
    suspend fun readFileBytes(path: String): ByteArray {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", credential)
            .get()
            .build()
        val resp = client.newCall(req).execute()
        return resp.body?.bytes() ?: ByteArray(0)
    }

    /** PUT — write file content */
    suspend fun writeFile(path: String, content: String) {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", credential)
            .put(content.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("写入文件失败: ${resp.code}")
    }

    /** Test connection — returns true if credentials are valid */
    suspend fun testConnection(): Boolean = try {
        val req = Request.Builder()
            .url(url("/"))
            .header("Authorization", credential)
            .header("Depth", "0")
            .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        resp.code in 200..299 || resp.code == 207
    } catch (e: Exception) { false }

    // ── XML Parser (no external lib needed) ───────
    private fun parseWebDavResponse(xml: String, basePath: String): List<WebDavItem> {
        val items = mutableListOf<WebDavItem>()
        // Simple regex-based XML parsing (avoids SimpleXML dependency issues)
        val responseRegex = Regex("<[Dd]:response[^>]*>(.*?)</[Dd]:response>", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex     = Regex("<[Dd]:href[^>]*>(.*?)</[Dd]:href>")
        val collRegex     = Regex("<[Dd]:collection\\s*/>")
        val ctypeRegex    = Regex("<[Dd]:getcontenttype[^>]*>(.*?)</[Dd]:getcontenttype>")
        val sizeRegex     = Regex("<[Dd]:getcontentlength[^>]*>(.*?)</[Dd]:getcontentlength>")

        val normalizedBase = basePath.trimEnd('/')

        responseRegex.findAll(xml).forEach { match ->
            val block = match.groupValues[1]
            val href  = hrefRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
            val isDir = collRegex.containsMatchIn(block)
            val ctype = ctypeRegex.find(block)?.groupValues?.get(1)?.trim() ?: ""
            val size  = sizeRegex.find(block)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L

            // Decode URL-encoded characters
            val decodedHref = java.net.URLDecoder.decode(href, "UTF-8").trimEnd('/')
            val name = decodedHref.substringAfterLast('/')
            if (name.isEmpty()) return@forEach  // skip self-referencing entry

            // Skip the folder itself
            val normalizedHref = decodedHref.trimEnd('/')
            val baseSegment   = normalizedBase.substringAfterLast('/')
            if (name == baseSegment && normalizedHref.endsWith(normalizedBase)) return@forEach

            items.add(WebDavItem(
                href        = decodedHref,
                name        = name,
                isDirectory = isDir,
                contentType = ctype,
                size        = size
            ))
        }
        return items
    }

    /** Resolve image path relative to a markdown file's location */
    fun resolveImagePath(mdPath: String, imageName: String): String {
        val folder = mdPath.substringBeforeLast('/', "")
        return if (folder.isEmpty()) imageName else "$folder/$imageName"
    }
}
