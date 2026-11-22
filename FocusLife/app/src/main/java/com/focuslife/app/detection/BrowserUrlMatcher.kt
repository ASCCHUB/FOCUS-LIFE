package com.focuslife.app.detection

/** Matches the text of a browser address bar against short-video pages and user-blocked sites. */
object BrowserUrlMatcher {
    private const val END = """(/|\?|#|$)"""
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""(^|[./])facebook\.com/(reel|reels|watch|videos)$END""") to "Facebook videos",
        Regex("""(^|[./])facebook\.com/[^/?#]+/videos$END""") to "Facebook videos",
        Regex("""(^|[./])fb\.watch$END""") to "Facebook videos",
        Regex("""(^|[./])instagram\.com/(reel|reels)$END""") to "Instagram Reels",
        Regex("""(^|[./])youtube\.com/shorts$END""") to "YouTube Shorts",
        Regex("""(^|[./])tiktok\.com$END""") to "TikTok",
        Regex("""(^|[./])(x|twitter)\.com/[^/?#]+/status/\d+/video$END""") to "X videos",
    )

    fun normalizeDomain(input: String): String =
        input.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?').removePrefix("www.")

    /** Returns a short label for what is blocked, or null if the page is allowed. */
    fun match(raw: String?, blockedDomains: Set<String> = emptySet()): String? {
        val url = raw?.trim()?.lowercase()?.removePrefix("https://")?.removePrefix("http://") ?: return null
        if (url.isEmpty() || url.contains(' ')) return null // search text, not a URL
        val host = url.substringBefore('/').substringBefore('?').substringBefore('#').removePrefix("www.")
        for (domain in blockedDomains) {
            val d = normalizeDomain(domain)
            if (d.isNotEmpty() && (host == d || host.endsWith(".$d"))) return d
        }
        return rules.firstOrNull { it.first.containsMatchIn(url) }?.second
    }
}
