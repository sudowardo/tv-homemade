package dev.tvhomemade.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class TvApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    /** 16:9 artwork: the app's TV banner, or its icon on a plain card when it has none. */
    val banner: ImageBitmap,
    val hasBanner: Boolean,
    /** The tile draws its own icon and title over [banner], which is just a gradient. */
    val isLiveTv: Boolean,
    /** Tiny copy of the banner; drawn full screen with bilinear filtering it reads as a blur. */
    val backdrop: ImageBitmap,
)

data class AppCatalog(val dock: List<TvApp>, val grid: List<TvApp>) {

    fun withFavorite(app: TvApp, favorite: Boolean): AppCatalog = when {
        favorite && app !in dock && dock.size < MAX_DOCK -> AppCatalog(dock + app, grid - app)
        // A removed favorite goes to the front of the grid, where it is easy to find again.
        !favorite && app in dock -> AppCatalog(dock - app, listOf(app) + grid)
        else -> this
    }

    fun withMoved(app: TvApp, delta: Int): AppCatalog = when (app) {
        in dock -> copy(dock = dock.moved(app, delta))
        in grid -> copy(grid = grid.moved(app, delta))
        else -> this
    }

    private fun List<TvApp>.moved(app: TvApp, delta: Int): List<TvApp> {
        val from = indexOf(app)
        val to = (from + delta).coerceIn(0, lastIndex)
        if (from == to) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    companion object {
        const val MAX_DOCK = 4
    }
}

class AppRepository(private val context: Context, private val layout: LayoutStore) {

    fun load(): AppCatalog {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in LauncherConfig.hidden }
            // Some vendor apps expose two TV entry points; one tile per app is enough.
            .distinctBy { it.packageName }
            .map { toTvApp(pm, it) }
            .toList()

        val byPackage = apps.associateBy { it.packageName }
        val dock = layout.dock().mapNotNull { byPackage[it] }.take(AppCatalog.MAX_DOCK)
        val dockPackages = dock.mapTo(HashSet()) { it.packageName }
        val gridFirst = layout.gridOrder().filter { it !in dockPackages }.mapNotNull { byPackage[it] }
        val gridFirstPackages = gridFirst.mapTo(HashSet()) { it.packageName }
        // Newly installed apps are not in the saved order yet; they land alphabetically at the end.
        val rest = apps
            .filter { it.packageName !in dockPackages && it.packageName !in gridFirstPackages }
            .sortedBy { it.label.lowercase() }
        return AppCatalog(dock = dock, grid = gridFirst + rest)
    }

    fun save(catalog: AppCatalog) {
        layout.save(
            dock = catalog.dock.map { it.packageName },
            grid = catalog.grid.map { it.packageName },
        )
    }

    private fun toTvApp(pm: PackageManager, info: ActivityInfo): TvApp {
        val label = LauncherConfig.labelOverrides[info.packageName] ?: info.loadLabel(pm).toString()
        val isLiveTv = info.packageName == LauncherConfig.LIVE_TV_PACKAGE
        // ActivityInfo.loadBanner already falls back to the application banner.
        val bannerDrawable = if (isLiveTv) null else info.loadBanner(pm)
        val banner = when {
            isLiveTv -> liveTvCard()
            bannerDrawable != null -> bannerDrawable.toBitmap(BANNER_WIDTH, BANNER_HEIGHT, Bitmap.Config.ARGB_8888)
            else -> iconCard(info.loadIcon(pm))
        }
        // Two-step downscale smooths out hard edges before the GPU stretches it to full screen.
        val tiny = Bitmap.createScaledBitmap(banner, 12, 7, true)
        val backdrop = Bitmap.createScaledBitmap(tiny, 48, 27, true)
        return TvApp(
            packageName = info.packageName,
            label = label,
            component = ComponentName(info.packageName, info.name),
            banner = banner.asImageBitmap(),
            hasBanner = bannerDrawable != null || isLiveTv,
            isLiveTv = isLiveTv,
            backdrop = backdrop.asImageBitmap(),
        )
    }

    private fun liveTvCard(): Bitmap {
        val card = Bitmap.createBitmap(BANNER_WIDTH, BANNER_HEIGHT, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, BANNER_WIDTH.toFloat(), BANNER_HEIGHT.toFloat(),
                LIVE_TV_START, LIVE_TV_END, Shader.TileMode.CLAMP,
            )
        }
        Canvas(card).drawRect(0f, 0f, BANNER_WIDTH.toFloat(), BANNER_HEIGHT.toFloat(), paint)
        return card
    }

    private fun iconCard(icon: Drawable): Bitmap {
        val card = Bitmap.createBitmap(BANNER_WIDTH, BANNER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        canvas.drawColor(ICON_CARD_COLOR)
        val size = 96
        val left = (BANNER_WIDTH - size) / 2
        val top = (BANNER_HEIGHT - size) / 2 - 14
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
        return card
    }

    private companion object {
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_CARD_COLOR = 0xFF2A3140.toInt()
        const val LIVE_TV_START = 0xFF1E3A8A.toInt()
        const val LIVE_TV_END = 0xFF0EA5E9.toInt()
    }
}
