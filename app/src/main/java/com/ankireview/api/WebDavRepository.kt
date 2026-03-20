package com.ankireview.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class WebDavItem(
    val href: String,          // relative path, e.g. /72数学 or /72数学/file.md
    val name: String,
    val isDirectory: Boolean,
    val contentType: String = "",
    val size: Long = 0L
) {
    val isMdFile get() = !isDirectory && name.endsWith(".md", ignoreCase = true)
    val isImage  get() = !isDirectory &&
        name.matches(Regex(".*\\.(png|jpg|jpeg|gif|webp|svg)", RegexOption.IGNORE_CASE))
}

class WebDavRepository(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    companion object {
        const val JIANGUOYUN_URL = "https://dav.jianguoyun.com/dav"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val credential = Credentials.basic(username, password)

    // baseUrl = https://dav.jianguoyun.com/dav
    private fun baseUrl() = serverUrl.trimEnd('/')

    // Build full URL from a relative path like "/72数学/file.md"
    // href stored in WebDavItem is already stripped of the /dav prefix
    private fun fullUrl(relativePath: String): String {
        val p = if (relativePath.startsWith("/")) relativePath else "/$relativePath"
        return "${baseUrl()}$p"
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            val req = Request.Builder()
                .url("${baseUrl()}/")
                .header("Authorization", credential)
                .header("Depth", "0")
                .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val code = resp.code
            resp.close()
            code == 207 || code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listFolder(relativePath: String): List<WebDavItem> = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontenttype/>
    <d:getcontentlength/>
    <d:displayname/>
  </d:prop>
</d:propfind>""".trimIndent()

        val targetUrl = if (relativePath.isBlank() || relativePath == "root") {
            "${baseUrl()}/"
        } else {
            val p = relativePath.trimStart('/')
            "${baseUrl()}/$p/"
        }

        val req = Request.Builder()
            .url(targetUrl)
            .header("Authorization", credential)
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .build()

        val resp = client.newCall(req).execute()
        if (resp.code != 207 && !resp.isSuccessful) {
            throw Exception("加载失败 (${resp.code})，请检查文件夹路径是否正确")
        }
        val xml = resp.body?.string() ?: return@withContext emptyList()
        parseWebDavResponse(xml, relativePath)
    }

    suspend fun readFile(relativePath: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(fullUrl(relativePath))
            .header("Authorization", credential)
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("读取文件失败 (${resp.code})")
        resp.body?.string() ?: ""
    }

    suspend fun readFileBytes(relativePath: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(fullUrl(relativePath))
            .header("Authorization", credential)
            .get()
            .build()
        val resp = client.newCall(req).execute()
        resp.body?.bytes() ?: ByteArray(0)
    }

    suspend fun writeFile(relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(fullUrl(relativePath))
            .header("Authorization", credential)
            .put(content.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("写入失败 (${resp.code})")
    }

    fun resolveImagePath(mdPath: String, imageName: String): String {
        val folder = mdPath.substringBeforeLast('/', "")
        return if (folder.isEmpty()) imageName else "$folder/$imageName"
    }

    private fun parseWebDavResponse(xml: String, basePath: String): List<WebDavItem> {
        val items = mutableListOf<WebDavItem>()
        val responseRegex = Regex("<[Dd]:response[^>]*>(.*?)</[Dd]:response>", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex     = Regex("<[Dd]:href[^>]*>(.*?)</[Dd]:href>")
        val collRegex     = Regex("<[Dd]:collection\\s*/>")
        val ctypeRegex    = Regex("<[Dd]:getcontenttype[^>]*>(.*?)</[Dd]:getcontenttype>")
        val sizeRegex     = Regex("<[Dd]:getcontentlength[^>]*>(.*?)</[Dd]:getcontentlength>")

        // Determine the server's WebDAV prefix to strip (e.g. "/dav")
        val davPrefix = try {
            java.net.URL(serverUrl).path.trimEnd('/')  // e.g. "/dav"
        } catch (e: Exception) { "/dav" }

        responseRegex.findAll(xml).forEach { match ->
            val block = match.groupValues[1]
            val rawHref = hrefRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
            val isDir   = collRegex.containsMatchIn(block)
            val ctype   = ctypeRegex.find(block)?.groupValues?.get(1)?.trim() ?: ""
            val size    = sizeRegex.find(block)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L

            // URL-decode the href
            val decoded = java.net.URLDecoder.decode(rawHref, "UTF-8").trimEnd('/')

            // Strip the /dav prefix so we only keep relative paths like /72数学/file.md
            val relativePath = if (decoded.startsWith(davPrefix)) {
                decoded.substring(davPrefix.length)
            } else {
                decoded
            }

            val name = relativePath.substringAfterLast('/')
            if (name.isEmpty()) return@forEach  // skip self

            // Skip the current folder itself
            val baseSegment = basePath.trimEnd('/').substringAfterLast('/')
            if (name == baseSegment && relativePath.trimEnd('/').endsWith(basePath.trimEnd('/'))) return@forEach

            items.add(WebDavItem(
                href        = relativePath,
                name        = name,
                isDirectory = isDir,
                contentType = ctype,
                size        = size
            ))
        }
        return items
    }
}
