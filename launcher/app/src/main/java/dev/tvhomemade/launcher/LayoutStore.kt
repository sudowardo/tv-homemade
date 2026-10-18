package dev.tvhomemade.launcher

import android.content.Context

/**
 * The dock and grid order chosen on the TV. Until the first edit, the defaults from
 * [LauncherConfig] apply, so a fresh install still looks right.
 */
class LayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)

    fun dock(): List<String> = read(KEY_DOCK) ?: LauncherConfig.dock

    /** Preferred order for the start of the grid; apps not listed follow alphabetically. */
    fun gridOrder(): List<String> = read(KEY_GRID) ?: LauncherConfig.gridFirst

    fun save(dock: List<String>, grid: List<String>) {
        // commit() rather than apply(): the launcher can be killed at any moment on a 1 GB TV,
        // and losing a reorder the user just made would look like the feature is broken.
        val written = prefs.edit()
            .putString(KEY_DOCK, dock.joinToString(SEPARATOR))
            .putString(KEY_GRID, grid.joinToString(SEPARATOR))
            .commit()
        check(written) { "Could not save the home screen layout" }
    }

    private fun read(key: String): List<String>? =
        prefs.getString(key, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }

    private companion object {
        const val KEY_DOCK = "dock"
        const val KEY_GRID = "grid"
        const val SEPARATOR = ","
    }
}
