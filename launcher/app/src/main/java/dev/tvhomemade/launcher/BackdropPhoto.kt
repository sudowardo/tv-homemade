package dev.tvhomemade.launcher

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Optional personal photo shown behind the home screen. It is pushed to the TV with
 * tools/set-photo.ps1 instead of being bundled, so the APK and the repo stay free of personal content.
 */
object BackdropPhoto {

    const val FILE_NAME = "backdrop.jpg"

    // The photo is drawn at 350x400 dp; anything much larger only costs RAM on a 1 GB TV.
    private const val MAX_SIDE_PX = 1000

    /** Null when no photo has been pushed. Throws if a file is there but is not a readable image. */
    fun load(context: Context): ImageBitmap? {
        val file = file(context)
        if (!file.isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "${file.path} is not a readable image" }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE_PX) sampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: error("Could not decode ${file.path}")
        return bitmap.asImageBitmap()
    }

    /**
     * App-specific external storage: adb can write here and the app can read it without any
     * storage permission. Calling getExternalFilesDir also creates the folder for the push.
     */
    private fun file(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: error("App-specific storage is not available")
        return File(dir, FILE_NAME)
    }
}
