package dev.tvhomemade.launcher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.tvhomemade.launcher.ui.LauncherScreen
import dev.tvhomemade.launcher.ui.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(applicationContext, LayoutStore(applicationContext)) }
    // Owned here rather than recomputed in Compose so reordering only shuffles the existing
    // tiles instead of reloading every banner.
    private val state = mutableStateOf<LoadState>(LoadState.Loading)
    private var loadJob: Job? = null
    private val photo = mutableStateOf<ImageBitmap?>(null)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This is the home screen: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Package broadcasts are protected and only the system can send them, so exporting is harmless.
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        reload()
        loadPhoto()
        setContent {
            LauncherScreen(
                state = state.value,
                photo = photo.value,
                onLaunch = ::launch,
                onOpenSettings = ::openSettings,
                loadInputs = ::loadInputs,
                onOpenInput = { startSafely(TvInputs.viewIntent(it), it.label) },
                // Each arrow press only reorders on screen; the result is saved once when moving ends.
                onMove = { app, delta -> rearrange { it.withMoved(app, delta) } },
                onChangeSection = { app -> rearrange { it.withSectionChanged(app) } },
                onMoveFinished = { (state.value as? LoadState.Ready)?.let { saveLayout(it.catalog) } },
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            state.value = try {
                LoadState.Ready(withContext(Dispatchers.Default) { repository.load() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the app list", e)
                LoadState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // Read once per process: tools/set-photo.ps1 restarts the launcher after pushing a new photo.
    private fun loadPhoto() {
        lifecycleScope.launch {
            try {
                photo.value = withContext(Dispatchers.IO) { BackdropPhoto.load(applicationContext) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The home screen must keep working, so the photo is skipped, but not silently.
                Log.e(TAG, "Could not load the backdrop photo", e)
                Toast.makeText(this@MainActivity, "No se pudo leer la foto de fondo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun rearrange(transform: (AppCatalog) -> AppCatalog) {
        val current = (state.value as? LoadState.Ready)?.catalog ?: return
        val updated = transform(current)
        if (updated != current) state.value = LoadState.Ready(updated)
    }

    private fun saveLayout(catalog: AppCatalog) {
        try {
            repository.save(catalog)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Could not save the layout", e)
            Toast.makeText(this, "No se pudo guardar el orden; se perderá al reiniciar", Toast.LENGTH_LONG).show()
        }
    }

    private fun launch(app: TvApp) {
        if (app.isLiveTv) {
            val tuner = try {
                TvInputs.tuner(this)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Could not look up the TV tuner", e)
                null
            }
            if (tuner != null) {
                startSafely(TvInputs.viewIntent(tuner), app.label)
                return
            }
            // No tuner reported: opening the app normally is still better than nothing.
            Log.w(TAG, "No tuner input found; opening ${app.packageName} on its last source")
        }
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startSafely(intent, app.label)
    }

    private fun openSettings() {
        startSafely(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Ajustes")
    }

    /** Null means the list could not be read; the user has already been told. */
    private fun loadInputs(): List<TvInputEntry>? =
        try {
            TvInputs.load(this)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not read TV inputs", e)
            Toast.makeText(this, "No se pudieron leer las entradas", Toast.LENGTH_LONG).show()
            null
        }

    private fun startSafely(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            reportLaunchFailure(label, e)
        } catch (e: SecurityException) {
            reportLaunchFailure(label, e)
        }
    }

    private fun reportLaunchFailure(label: String, e: Exception) {
        Log.e(TAG, "Could not open $label", e)
        Toast.makeText(this, "No se pudo abrir $label", Toast.LENGTH_LONG).show()
        // The app may have been disabled or uninstalled behind our back; refresh the tiles.
        reload()
    }

    private companion object {
        const val TAG = "TvHomemade"
    }
}
