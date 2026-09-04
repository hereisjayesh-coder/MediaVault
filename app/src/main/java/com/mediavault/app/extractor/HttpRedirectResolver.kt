package com.mediavault.app.extractor

import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.urlresolution.RedirectResolver
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real [RedirectResolver]: walks a redirect chain by hand — never [HttpURLConnection]'s own
 * auto-follow — so every hop can be validated with [isSafeRedirectTarget] before it's trusted.
 * This is the only class in MediaVault that resolves a URL's redirects before source/extractor
 * routing has happened, which is exactly why it stays this defensive.
 *
 * Two real-world share-link shapes had to be confirmed live (neither behaves like a textbook
 * HTTP redirect) and both are handled in [followOneHop]:
 * - A Reddit `/s/...` share link doesn't answer `HEAD` at all (the connection just hangs until
 *   it times out) but answers `GET` with a normal `301` — so `HEAD` is only ever a fast-path
 *   attempt, never trusted as proof a URL *isn't* a redirect.
 * - A Facebook `/share/...` link never sends an HTTP redirect either — it answers `200` with a
 *   full HTML page whose `<link rel="canonical">`/`og:url` tag names the real post. Resolving
 *   these means reading (a bounded slice of) the body, not just the headers.
 */
@Singleton
class HttpRedirectResolver @Inject constructor() : RedirectResolver {

    override suspend fun resolveFinalUrl(url: String): AppResult<String> = withContext(Dispatchers.IO) {
        var current = url
        repeat(MAX_REDIRECTS) {
            val hop = try {
                followOneHop(current)
            } catch (e: SocketTimeoutException) {
                return@withContext AppResult.Failure(AppError.Timeout(TIMEOUT_MESSAGE, e))
            } catch (e: IOException) {
                return@withContext AppResult.Failure(AppError.Network(NETWORK_MESSAGE, e))
            }

            when (hop) {
                is Hop.Terminal -> return@withContext AppResult.Success(current)
                is Hop.Redirect -> {
                    if (!isSafeRedirectTarget(hop.location)) {
                        return@withContext AppResult.Failure(AppError.Unsupported("This link redirects somewhere MediaVault won't follow."))
                    }
                    current = hop.location
                }
            }
        }
        AppResult.Failure(AppError.Unsupported("This link redirects too many times to resolve."))
    }

    private sealed class Hop {
        data class Redirect(val location: String) : Hop()
        data object Terminal : Hop()
    }

    private data class Probe(val responseCode: Int, val location: String?, val body: String?)

    private fun followOneHop(urlString: String): Hop {
        // HEAD is a fast, no-body-download path for a real HTTP redirect — worth trying first,
        // but never trusted as proof of "not a redirect": some servers just don't answer it at
        // all rather than replying 405 (confirmed live for Reddit's share redirector), so any
        // failure here — timeout included — silently falls through to GET instead of failing
        // the whole resolution.
        val head = runCatching { probe(urlString, "HEAD", readBody = false) }.getOrNull()
        if (head != null && isRedirect(head)) {
            return toRedirectHop(urlString, head.location)
        }

        val get = probe(urlString, "GET", readBody = true)
        if (isRedirect(get)) {
            return toRedirectHop(urlString, get.location)
        }

        // No HTTP redirect at all — the real destination may still be embedded in the page
        // itself (confirmed live for Facebook's `/share/...` links: HTTP 200, real post URL
        // only in a `<link rel="canonical">`/`og:url` tag).
        val canonical = get.body?.let { extractCanonicalUrl(it) }
        return if (canonical != null && canonical != urlString) toRedirectHop(urlString, canonical) else Hop.Terminal
    }

    private fun isRedirect(probe: Probe): Boolean = probe.responseCode in REDIRECT_CODES && !probe.location.isNullOrBlank()

    private fun toRedirectHop(baseUrl: String, location: String?): Hop {
        val resolved = try {
            URL(URL(baseUrl), location).toString()
        } catch (e: MalformedURLException) {
            return Hop.Terminal
        }
        return Hop.Redirect(resolved)
    }

    private fun probe(urlString: String, method: String, readBody: Boolean): Probe {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connect()
            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            val contentType = connection.contentType.orEmpty()
            val body = if (readBody && responseCode == HttpURLConnection.HTTP_OK && contentType.contains("html", ignoreCase = true)) {
                readBounded(connection)
            } else {
                null
            }
            Probe(responseCode, location, body)
        } finally {
            connection.disconnect()
        }
    }

    /** Reads at most [MAX_BODY_BYTES] of the response — a canonical/og:url tag lives in
     * `<head>`, always well within that, so the (sometimes multi-megabyte) rest of the page is
     * never downloaded. */
    private fun readBounded(connection: HttpURLConnection): String? = try {
        connection.inputStream.use { stream ->
            val buffer = ByteArray(MAX_BODY_BYTES)
            var totalRead = 0
            while (totalRead < buffer.size) {
                val read = stream.read(buffer, totalRead, buffer.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            String(buffer, 0, totalRead, Charsets.UTF_8)
        }
    } catch (e: IOException) {
        null
    }

    /** Extracts the URL from a `<link rel="canonical" href="...">` or `<meta property="og:url"
     * content="...">` tag — attributes are matched independently of their order within the tag,
     * since HTML doesn't guarantee one. */
    internal fun extractCanonicalUrl(html: String): String? {
        val tag = CANONICAL_LINK_TAG.find(html)?.value ?: OG_URL_META_TAG.find(html)?.value ?: return null
        val value = URL_ATTRIBUTE_VALUE.find(tag)?.groupValues?.get(1) ?: return null
        return value.replace("&amp;", "&")
    }

    /** Rejects a redirect hop before it's ever followed: non-http(s) schemes (`intent://`,
     * `javascript:`, ...) and loopback/private/link-local targets, so a malicious or
     * misconfigured redirect can never be used to probe MediaVault's own device network
     * (SSRF), and a redirect can never smuggle in a non-web destination. */
    internal fun isSafeRedirectTarget(location: String): Boolean {
        val url = try {
            URL(location)
        } catch (e: MalformedURLException) {
            return false
        }
        if (url.protocol != "http" && url.protocol != "https") return false
        val host = url.host
        if (host.isNullOrEmpty()) return false

        return try {
            InetAddress.getAllByName(host).none { address ->
                address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
                    address.isMulticastAddress || address.isAnyLocalAddress
            }
        } catch (e: UnknownHostException) {
            false
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BODY_BYTES = 300_000
        const val TIMEOUT_MS = 8_000
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) MediaVault"
        const val TIMEOUT_MESSAGE = "Connection timed out. This source may be unavailable or blocked on your current network."
        const val NETWORK_MESSAGE = "Couldn't reach the source. Check your connection and try again."
        val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
        val CANONICAL_LINK_TAG = Regex("""<link\s+[^>]*rel=["']canonical["'][^>]*>""", RegexOption.IGNORE_CASE)
        val OG_URL_META_TAG = Regex("""<meta\s+[^>]*property=["']og:url["'][^>]*>""", RegexOption.IGNORE_CASE)
        val URL_ATTRIBUTE_VALUE = Regex("""(?:href|content)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
