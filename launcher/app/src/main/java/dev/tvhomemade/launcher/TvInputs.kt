package dev.tvhomemade.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

enum class InputKind { TUNER, HDMI, OTHER }

data class TvInputEntry(
    val id: String,
    val label: String,
    val kind: InputKind,
    /** Only trusted when true: this firmware reports idle HDMI ports as "standby", not "disconnected". */
    val connected: Boolean,
)

object TvInputs {

    /** Antenna/cable first, then HDMI ports, then AV and the rest. */
    fun load(context: Context): List<TvInputEntry> {
        val manager = manager(context)
        val ports = manager.tvInputList
            // CEC devices show up as children of their HDMI port; listing the port is enough.
            .filter { it.isPassthroughInput && it.parentId == null }
            .map { info ->
                val label = info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.loadLabel(context).toString()
                TvInputEntry(
                    id = info.id,
                    label = label,
                    kind = if (info.type == TvInputInfo.TYPE_HDMI) InputKind.HDMI else InputKind.OTHER,
                    connected = manager.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED,
                )
            }
            .sortedWith(compareBy<TvInputEntry> { it.kind != InputKind.HDMI }.thenBy { it.label.lowercase() })
        return listOfNotNull(tuner(context)) + ports
    }

    /**
     * The built-in antenna/cable tuner. This set exposes two (HW0 and HW8); HW0 is the one the
     * vendor's own input menu calls "TV", so the lowest id wins.
     */
    fun tuner(context: Context): TvInputEntry? =
        manager(context).tvInputList
            .filter {
                it.type == TvInputInfo.TYPE_TUNER &&
                    (it.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }
            .minByOrNull { it.id }
            ?.let { TvInputEntry(id = it.id, label = "TV", kind = InputKind.TUNER, connected = false) }

    /**
     * Opens the input in the vendor TV app. Launching that app without this intent resumes
     * whatever source it showed last, which is how "TV en vivo" ended up on an empty HDMI port.
     */
    fun viewIntent(input: TvInputEntry): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                TvContract.buildChannelUriForPassthroughInput(input.id),
                TvContract.Channels.CONTENT_ITEM_TYPE,
            )
            .setPackage(LauncherConfig.LIVE_TV_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun manager(context: Context): TvInputManager =
        context.getSystemService(TvInputManager::class.java)
            ?: error("This device has no TV input service")
}
