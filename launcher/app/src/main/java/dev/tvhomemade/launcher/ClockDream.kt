package dev.tvhomemade.launcher

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Screensaver: a large thin clock and the date on black. Plain Views instead of Compose because a
 * DreamService has no lifecycle owner, and this runs exactly when the TV should be using the least
 * memory. The block moves to a new spot every minute so nothing burns into the panel.
 */
class ClockDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var block: LinearLayout
    private lateinit var time: TextView
    private lateinit var date: TextView

    private val tick = object : Runnable {
        override fun run() {
            update()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000 - now % 60_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val outfit = ResourcesCompat.getFont(this, R.font.outfit)
            ?: error("Outfit font resource could not be loaded")
        time = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 120f)
            setTextColor(Color.argb(235, 255, 255, 255))
            typeface = outfit
            fontVariationSettings = "'wght' 300"
            includeFontPadding = false
        }
        date = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.argb(150, 255, 255, 255))
            typeface = outfit
            fontVariationSettings = "'wght' 400"
        }
        block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(time)
            addView(date)
            alpha = 0f
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(block, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun update() {
        val now = Date()
        val locale = Locale.getDefault()
        time.text = DateFormat.getTimeFormat(this).format(now)
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        date.text = SimpleDateFormat(pattern, locale).format(now).replaceFirstChar { it.titlecase(locale) }

        // Measure after the text change, then fade out, jump and fade back in.
        block.post {
            val maxX = (root.width - block.width).coerceAtLeast(0)
            val maxY = (root.height - block.height).coerceAtLeast(0)
            block.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                block.translationX = Random.nextInt(maxX + 1).toFloat()
                block.translationY = Random.nextInt(maxY + 1).toFloat()
                block.animate().alpha(1f).setDuration(FADE_MS).start()
            }.start()
        }
    }

    private companion object {
        const val FADE_MS = 700L
    }
}
