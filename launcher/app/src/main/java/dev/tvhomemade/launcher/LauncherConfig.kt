package dev.tvhomemade.launcher

object LauncherConfig {
    /** The vendor tuner app. Its own banner is TV clip art, so the launcher draws its tile itself. */
    const val LIVE_TV_PACKAGE = "com.mediatek.wwtv.tvcenter"

    /** Fixed dock under the title, in this order. Exactly four fit across the screen without scrolling. */
    val dock = listOf(
        LIVE_TV_PACKAGE,
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.apple.atve.androidtv.appletv",
    )

    /** First tiles of the grid, in this order; every other app follows alphabetically. */
    val gridFirst = listOf(
        "com.disney.disneyplus",
        "com.wbd.stream",
        "com.amazon.amazonvideo.livingroom",
        "com.mubi",
        "com.crunchyroll.crunchyroid",
        "com.directv.dtvlatam",
        "com.google.android.youtube.tvmusic",
        "com.esaba.downloader",
        "com.poster.iptv.android",
        "com.android.mgstv",
        "com.android.vending",
        "com.homwee.chtvmanager",
        "com.homwee.multiscreenshare",
        "com.homwee.mmp.fileexplorer",
        "com.homwee.aipont",
    )

    /** Kept installed as a fallback home, but it should never show up as an app. */
    val hidden = setOf(
        "me.efesser.flauncher",
    )

    /** The vendor label for the tuner app is just "LiveTV". */
    val labelOverrides = mapOf(
        LIVE_TV_PACKAGE to "TV en vivo",
    )
}
