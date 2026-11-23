package com.focuslife.app.detection

import com.focuslife.app.domain.BlockerConfig

/**
 * Per-app detection rules. View IDs change when apps update; edit them here.
 * feedViewIds: exact IDs. idHints: parts of an ID; a visible element whose ID contains a hint and
 * fills most of the screen counts as the short-video feed.
 * useScrollHeuristic: also treat a full-screen, one-item-at-a-time vertical list as a feed.
 * (Off for YouTube so normal videos are never affected.)
 */
data class AppRule(
    val packageName: String,
    val label: String,
    val feedViewIds: List<String> = emptyList(),
    val idHints: List<String> = emptyList(),
    val wholeAppIsFeed: Boolean = false,
    val useScrollHeuristic: Boolean = true,
)

object ShortVideoRules {
    private fun ids(pkg: String, vararg names: String) = names.map { "$pkg:id/$it" }

    val apps: Map<String, AppRule> = listOf(
        AppRule(BlockerConfig.YOUTUBE, "YouTube Shorts",
            ids(BlockerConfig.YOUTUBE, "reel_recycler", "reel_player_page_container", "reel_watch_player", "shorts_container"),
            idHints = listOf("reel_", "shorts_player"), useScrollHeuristic = false),
        AppRule(BlockerConfig.INSTAGRAM, "Instagram Reels",
            ids(BlockerConfig.INSTAGRAM, "clips_viewer_view_pager", "clips_viewer_container", "root_clips_layout"),
            idHints = listOf("clips_viewer", "clips_video")),
        AppRule(BlockerConfig.FACEBOOK, "Facebook Reels", idHints = listOf("reels_viewer", "fb_shorts", "video_home_reels")),
        AppRule(BlockerConfig.TIKTOK, "TikTok", wholeAppIsFeed = true),
        AppRule(BlockerConfig.TIKTOK_ALT, "TikTok", wholeAppIsFeed = true),
        AppRule(BlockerConfig.SNAPCHAT, "Snapchat Spotlight", ids(BlockerConfig.SNAPCHAT, "spotlight_container", "spotlight_view_pager"), idHints = listOf("spotlight")),
        AppRule(BlockerConfig.X, "X videos", ids(BlockerConfig.X, "immersive_video_player", "immersive_viewpager"),
            idHints = listOf("immersive", "media_viewer", "fullscreen_video", "explore_video")),
    ).associateBy { it.packageName }

    /** Browser package -> address bar view ID. */
    val browsers: Map<String, String> = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.chrome.beta" to "com.chrome.beta:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
    )

    /** Apps shown on the Apps tab (TikTok's two package names are one entry). */
    val userFacing: List<Pair<String, String>> = listOf(
        BlockerConfig.YOUTUBE to "YouTube Shorts (normal videos stay allowed)",
        BlockerConfig.INSTAGRAM to "Instagram Reels",
        BlockerConfig.FACEBOOK to "Facebook Reels",
        BlockerConfig.TIKTOK to "TikTok",
        BlockerConfig.SNAPCHAT to "Snapchat Spotlight",
        BlockerConfig.X to "X videos",
    )
}
