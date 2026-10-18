package dev.tvhomemade.launcher.ui

import android.text.format.DateFormat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tvhomemade.launcher.AppCatalog
import dev.tvhomemade.launcher.InputKind
import dev.tvhomemade.launcher.R
import dev.tvhomemade.launcher.TvApp
import dev.tvhomemade.launcher.TvInputEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val catalog: AppCatalog) : LoadState
    data class Failed(val message: String) : LoadState
}

private val ScreenBackground = Color(0xFF0B0D12)
private val EdgePadding = 56.dp
private val HeroHeight = 96.dp
private const val DOCK_SLOTS = 4
private const val GRID_COLUMNS = 5
private val TileSpacing = 22.dp

// One variable font file. The weight axis has to be set explicitly: on this TV the FontWeight
// alone only picked the matching entry and every weight rendered identically thin.
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) = Font(
    R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

// Three weights keep the hierarchy readable from the couch: Light for large display text,
// Normal for secondary text, Medium for labels and titles.
private val Outfit = FontFamily(
    outfit(FontWeight.Light),
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
)

private fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) =
    TextStyle(fontFamily = Outfit, fontSize = size, fontWeight = weight, color = color)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    state: LoadState,
    photo: ImageBitmap?,
    onLaunch: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    loadInputs: () -> List<TvInputEntry>?,
    onOpenInput: (TvInputEntry) -> Unit,
    onSetFavorite: (TvApp, Boolean) -> Unit,
    onMove: (TvApp, Int) -> Unit,
) {
    val catalog = (state as? LoadState.Ready)?.catalog
    // Package of the tile whose options menu is open; looked up again on every change so the
    // menu always reflects the tile's current position.
    var optionsFor by remember { mutableStateOf<String?>(null) }
    // Tile that should take focus on the next frame: the first app on launch, and the edited app
    // after its menu closes (a tile moved to another grid row is recreated and loses focus).
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(catalog != null) {
        if (catalog == null || initialFocusDone) return@LaunchedEffect
        pendingFocus = (catalog.dock + catalog.grid).firstOrNull()?.packageName
        initialFocusDone = true
    }
    catalog?.let { current ->
        val app = optionsFor?.let { pkg -> (current.dock + current.grid).find { it.packageName == pkg } }
        if (app != null) {
            val close = {
                optionsFor = null
                pendingFocus = app.packageName
            }
            TileOptionsDialog(
                app = app,
                catalog = current,
                onDismiss = close,
                onSetFavorite = { favorite ->
                    close()
                    onSetFavorite(app, favorite)
                },
                onMove = { delta -> onMove(app, delta) },
            )
        }
    }
    val scope = rememberCoroutineScope()
    var focusedApp by remember { mutableStateOf<TvApp?>(null) }
    val heroApp = focusedApp ?: catalog?.dock?.firstOrNull() ?: catalog?.grid?.firstOrNull()
    // Non-null while the inputs panel is open.
    var inputs by remember { mutableStateOf<List<TvInputEntry>?>(null) }
    inputs?.let { list ->
        InputsDialog(
            inputs = list,
            onDismiss = { inputs = null },
            onSelect = { input ->
                inputs = null
                onOpenInput(input)
            },
        )
    }

    Box(Modifier.fillMaxSize().background(ScreenBackground)) {
        Backdrop(heroApp, overlay = { photo?.let { BackdropPhoto(it) } })
        // The whole page scrolls: moving into the grid slides the title and dock up, like Apple TV.
        val scrollState = rememberScrollState()
        val edgeMargin = with(LocalDensity.current) { 28.dp.toPx() }
        val scrollSpec = remember(edgeMargin) { MinimalScrollSpec(edgeMargin) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = 28.dp, bottom = 40.dp),
            ) {
                TopBar(
                    onOpenInputs = { inputs = loadInputs() },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(horizontal = EdgePadding),
                )
                when (state) {
                    LoadState.Loading -> Unit
                    is LoadState.Failed -> BasicText(
                        "No se pudieron cargar las apps: ${state.message}",
                        style = text(22.sp),
                        modifier = Modifier.padding(horizontal = EdgePadding, vertical = 120.dp),
                    )
                    is LoadState.Ready -> {
                        Hero(heroApp, Modifier.height(HeroHeight).padding(horizontal = EdgePadding))
                        HomeContent(
                            state.catalog,
                            onFocus = { focusedApp = it },
                            onLaunch = onLaunch,
                            onOptions = { optionsFor = it.packageName },
                            focusRequestFor = pendingFocus,
                            onFocusRequested = { pendingFocus = null },
                            onDockFocused = { scope.launch { scrollState.animateScrollTo(0) } },
                        )
                    }
                }
            }
        }
    }
}

/** [overlay] sits between the app color wash and the dark scrim, so it is dimmed along with it. */
@Composable
private fun Backdrop(app: TvApp?, overlay: @Composable () -> Unit) {
    Crossfade(targetState = app, animationSpec = tween(durationMillis = 500), label = "backdrop") { current ->
        if (current != null) {
            Image(
                bitmap = current.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    overlay()
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.5f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.88f),
                ),
            ),
    )
}

/**
 * Placed right of the hero title and below the clock so no text lands on a face; the faded
 * edges keep it from reading as a framed picture.
 */
@Composable
private fun BackdropPhoto(photo: ImageBitmap) {
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = 0.6f,
            modifier = Modifier
                .padding(start = 420.dp, top = 44.dp)
                .size(width = 350.dp, height = 400.dp)
                // Offscreen compositing is required for DstIn to mask only this layer, not the backdrop below.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black,
                            0.7f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.5f to Color.Black,
                            0.85f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

@Composable
private fun TopBar(onOpenInputs: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    val locale = Locale.getDefault()
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        SimpleDateFormat(pattern, locale).format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    val greeting = remember(now) {
        when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            BasicText(greeting, style = text(30.sp, FontWeight.Light))
            BasicText(date, style = text(17.sp, color = Color.White.copy(alpha = 0.65f)))
        }
        Spacer(Modifier.weight(1f))
        IconPill(R.drawable.ic_input, "Entradas", onOpenInputs)
        Spacer(Modifier.width(12.dp))
        IconPill(R.drawable.ic_settings, "Ajustes", onOpenSettings)
        Spacer(Modifier.width(24.dp))
        BasicText(time, style = text(48.sp, FontWeight.Light))
    }
}

@Composable
private fun Hero(app: TvApp?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Crossfade(targetState = app, animationSpec = tween(durationMillis = 250), label = "hero") { current ->
            if (current != null) {
                BasicText(
                    current.label,
                    style = text(46.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    catalog: AppCatalog,
    onFocus: (TvApp) -> Unit,
    onLaunch: (TvApp) -> Unit,
    onOptions: (TvApp) -> Unit,
    onDockFocused: () -> Unit,
    focusRequestFor: String?,
    onFocusRequested: () -> Unit,
) {
    val hasDock = catalog.dock.isNotEmpty()

    if (hasDock) {
        // The lighter rounded band behind the four favorites is the Apple TV "dock".
        Row(
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(30.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(30.dp))
                // Room for the focused tile to grow and cast its shadow inside the band.
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            catalog.dock.take(DOCK_SLOTS).forEach { app ->
                // Keyed so a reordered tile keeps its state, and focus, instead of the slot keeping it.
                key(app.packageName) {
                    AppTile(
                        app = app,
                        large = true,
                        // Back on the dock the title belongs on screen again, as on Apple TV.
                        onFocused = {
                            onFocus(app)
                            onDockFocused()
                        },
                        onClick = { onLaunch(app) },
                        onLongClick = { onOptions(app) },
                        requestFocus = app.packageName == focusRequestFor,
                        onFocusRequested = onFocusRequested,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Empty slots keep tiles the same size if a dock app is ever uninstalled.
            repeat(DOCK_SLOTS - catalog.dock.size.coerceAtMost(DOCK_SLOTS)) { Spacer(Modifier.weight(1f)) }
        }
    }

    if (catalog.grid.isNotEmpty()) {
        BasicText(
            "Más apps",
            style = text(19.sp, FontWeight.Medium, Color.White.copy(alpha = 0.7f)),
            modifier = Modifier.padding(start = EdgePadding, top = 16.dp, bottom = 2.dp),
        )
        catalog.grid.chunked(GRID_COLUMNS).forEach { rowApps ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EdgePadding, vertical = 11.dp),
            ) {
                rowApps.forEach { app ->
                    key(app.packageName) {
                        AppTile(
                            app = app,
                            large = false,
                            onFocused = { onFocus(app) },
                            onClick = { onLaunch(app) },
                            onLongClick = { onOptions(app) },
                            requestFocus = app.packageName == focusRequestFor,
                            onFocusRequested = onFocusRequested,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(GRID_COLUMNS - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    large: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            // Wait a frame so the tile is attached; right after a dialog closes it is not yet.
            withFrameNanos { }
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                Log.e("TvHomemade", "Could not focus ${app.packageName}", e)
            }
            onFocusRequested()
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "tileScale",
    )
    // Only the focused tile casts a shadow; resting shadows overlapped into a muddy band between rows.
    val lift by animateDpAsState(if (focused) 16.dp else 0.dp, label = "tileLift")
    val highlight by animateFloatAsState(if (focused) 1f else 0f, label = "tileHighlight")
    val shape = RoundedCornerShape(if (large) 14.dp else 12.dp)

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = lift.toPx()
                this.shape = shape
                clip = true
            }
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .dpadClickable(onClick, onLongClick)
            .focusable(),
    ) {
        Image(
            bitmap = app.banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        when {
            app.isLiveTv -> LiveTvArt(large)
            !app.hasBanner -> BasicText(
                app.label,
                style = text(14.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
        // Glossy sheen across the top plus a thin rim: the "lifted glass" look of the focused tile.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = highlight }
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.45f to Color.Transparent,
                    ),
                )
                .border(2.dp, Color.White.copy(alpha = 0.75f), shape),
        )
    }
}

@Composable
private fun LiveTvArt(large: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_live_tv),
            contentDescription = null,
            modifier = Modifier.size(if (large) 40.dp else 30.dp),
        )
        Spacer(Modifier.height(6.dp))
        BasicText("TV en vivo", style = text(if (large) 20.sp else 15.sp, FontWeight.Medium))
    }
}

@Composable
private fun InputsDialog(
    inputs: List<TvInputEntry>,
    onDismiss: () -> Unit,
    onSelect: (TvInputEntry) -> Unit,
) {
    MenuPanel(
        title = "Entradas",
        onDismiss = onDismiss,
        emptyMessage = "Esta tele no reporta entradas".takeIf { inputs.isEmpty() },
    ) { firstRow ->
        inputs.forEachIndexed { index, input ->
            MenuRow(
                label = input.label,
                icon = if (input.kind == InputKind.TUNER) R.drawable.ic_live_tv else R.drawable.ic_input,
                trailing = "Conectado".takeIf { input.connected },
                focusRequester = if (index == 0) firstRow else null,
                onClick = { onSelect(input) },
            )
        }
    }
}

/** Opened by holding OK on a tile: pick favorites and reorder without touching the code. */
@Composable
private fun TileOptionsDialog(
    app: TvApp,
    catalog: AppCatalog,
    onDismiss: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val inDock = app in catalog.dock
    val row = if (inDock) catalog.dock else catalog.grid
    val position = row.indexOf(app)
    val dockFull = catalog.dock.size >= AppCatalog.MAX_DOCK
    MenuPanel(
        title = app.label,
        subtitle = if (inDock) "Favorito ${position + 1} de ${row.size}" else "Más apps · ${position + 1} de ${row.size}",
        onDismiss = onDismiss,
        emptyMessage = null,
    ) { firstRow ->
        when {
            inDock -> MenuRow("Quitar de favoritos", focusRequester = firstRow, onClick = { onSetFavorite(false) })
            !dockFull -> MenuRow("Agregar a favoritos", focusRequester = firstRow, onClick = { onSetFavorite(true) })
        }
        // The dialog stays open while moving so the tile can be walked several places in a row.
        MenuRow(
            "Mover a la izquierda",
            focusRequester = if (inDock || !dockFull) null else firstRow,
            onClick = { onMove(-1) },
        )
        MenuRow("Mover a la derecha", onClick = { onMove(1) })
        MenuRow("Listo", onClick = onDismiss)
        if (!inDock && dockFull) {
            BasicText(
                "Favoritos llenos (máximo ${AppCatalog.MAX_DOCK}). Quita uno para agregar otro.",
                style = text(16.sp, color = Color.White.copy(alpha = 0.6f)),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MenuPanel(
    title: String,
    onDismiss: () -> Unit,
    emptyMessage: String?,
    subtitle: String? = null,
    content: @Composable (firstRow: FocusRequester) -> Unit,
) {
    // A dialog window keeps D-pad focus inside the panel and closes on Back by itself.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val firstRow = remember { FocusRequester() }
        Column(
            Modifier
                .width(400.dp)
                .background(Color(0xF2161B26), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                .padding(vertical = 18.dp),
        ) {
            BasicText(
                title,
                style = text(26.sp, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = if (subtitle == null) 10.dp else 2.dp),
            )
            if (subtitle != null) {
                BasicText(
                    subtitle,
                    style = text(16.sp, color = Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
                )
            }
            if (emptyMessage != null) {
                BasicText(
                    emptyMessage,
                    style = text(18.sp, color = Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
            content(firstRow)
        }
        LaunchedEffect(Unit) {
            if (emptyMessage != null) return@LaunchedEffect
            withFrameNanos { }
            try {
                firstRow.requestFocus()
            } catch (e: IllegalStateException) {
                Log.e("TvHomemade", "Menu \"$title\" has no focusable first row", e)
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    trailing: String? = null,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) ScreenBackground else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .dpadClickable(onClick)
            .focusable()
            .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        BasicText(
            label,
            style = text(22.sp, FontWeight.Medium, contentColor),
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            BasicText(trailing, style = text(16.sp, color = contentColor.copy(alpha = 0.6f)))
        }
    }
}

/** Icon-only at rest so the top bar stays clear of the photo; the label slides out when focused. */
@Composable
private fun IconPill(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "pillScale")
    val contentColor = if (focused) ScreenBackground else Color.White
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .dpadClickable(onClick)
            .focusable()
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.15f), shape)
            .animateContentSize()
            .padding(12.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.size(24.dp),
        )
        if (focused) {
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = text(18.sp, FontWeight.Medium, contentColor), modifier = Modifier.padding(end = 6.dp))
        }
    }
}

/**
 * Scrolls only as far as needed to show the focused tile plus [marginPx]. Compose's default on TV
 * devices pivots the focused item to about a third of the screen, which pushed the greeting off
 * the top on launch even though everything already fit.
 */
@OptIn(ExperimentalFoundationApi::class)
private class MinimalScrollSpec(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = offset - marginPx
        val bottom = offset + size + marginPx
        return when {
            top < 0f -> top
            bottom > containerSize -> minOf(bottom - containerSize, top)
            else -> 0f
        }
    }
}

/**
 * Fires on key release, and only if the press started here. Otherwise the OK release left over
 * from leaving another app would immediately reopen whatever tile has focus.
 *
 * Holding OK fires [onLongClick] once instead, while the key is still down. Remotes repeat
 * KeyDown while held; the system flags the repeat that crosses the long-press timeout.
 */
private fun Modifier.dpadClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    onKeyEvent { event ->
        val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!isSelect) return@onKeyEvent false
        val native = event.nativeKeyEvent
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    pressed = true
                    longPressed = false
                } else if (onLongClick != null && pressed && !longPressed && native.isLongPress) {
                    longPressed = true
                    onLongClick()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (pressed && !longPressed) onClick()
                pressed = false
                longPressed = false
                true
            }
            else -> false
        }
    }
}
