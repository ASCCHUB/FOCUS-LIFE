package com.focuslife.app.domain

/** All user-adjustable blocker settings. Stored in DataStore; pure Kotlin so it can be unit-tested. */
data class BlockerConfig(
    val mode: BlockMode = BlockMode.CURIOUS,
    val curiousCount: Int = 2,
    val curiousCooldownMin: Int = 5,
    val timedWatchMin: Int = 5,
    val timedBlockMin: Int = 5,
    val pauseUntil: Long = 0L,
    val enabledApps: Set<String> = DEFAULT_APPS,
    val xEnabled: Boolean = true,
    val xWarnAfter: Int = 2,
    val xStopAfter: Int = 5,
    val xCooldownMin: Int = 10,
    val browserEnabled: Boolean = true,
    val blockedDomains: Set<String> = emptySet(),
    val limitAction: LimitAction = LimitAction.REMIND,
    val onboardingDone: Boolean = false,
) {
    fun isPaused(now: Long) = pauseUntil > now

    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val INSTAGRAM = "com.instagram.android"
        const val FACEBOOK = "com.facebook.katana"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val TIKTOK_ALT = "com.ss.android.ugc.trill"
        const val SNAPCHAT = "com.snapchat.android"
        const val X = "com.twitter.android"
        val DEFAULT_APPS = setOf(YOUTUBE, INSTAGRAM, FACEBOOK, TIKTOK, TIKTOK_ALT, SNAPCHAT, X)
    }
}

enum class BlockMode(val title: String, val description: String) {
    BLOCK_ALL("Block All", "No reels or shorts at all"),
    CURIOUS("Curious", "A couple of reels, then a break"),
    TIMED("Timed", "Watch for a while, then a break"),
}

enum class LimitAction(val title: String, val description: String) {
    NOTIFY("Notify only", "Just send a reminder notification"),
    REMIND("Show reminder screen", "Full-screen reminder, one 5-minute extension per day"),
    CLOSE("Close the app", "Strict: sends you to the home screen until midnight"),
}
