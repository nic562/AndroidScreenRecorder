package io.github.nic562.screen.recorder.tools

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

/**
 * Created by Nic on 2021/11/27.
 */
object Video {
    fun getThumb(videoPath: String): Bitmap? {
        val media = MediaMetadataRetriever().apply {
            setDataSource(videoPath)
        }
        return media.getFrameAtTime(1000000L)
    }

    fun getThumb2File(videoPath: String, dstFilePath: String, quality: Int = 100): File? {
        getThumb(videoPath)?.apply {
            return bitmap2File(this, dstFilePath, quality)
        }
        return null
    }

    fun bitmap2File(bitmap: Bitmap, dstFilePath: String, quality: Int = 100): File {
        val f = File(dstFilePath)
        if (f.exists()) {
            throw FileAlreadyExistsException(f)
        }
        FileOutputStream(f).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
            it.flush()
        }
        return f
    }

}