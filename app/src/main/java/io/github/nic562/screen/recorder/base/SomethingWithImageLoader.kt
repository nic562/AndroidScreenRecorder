package io.github.nic562.screen.recorder.base

import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.DrawableRes
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Transformation
import java.io.File
import java.lang.Exception

/**
 * Created by Nic on 2020/10/5.
 */
interface SomethingWithImageLoader {

    fun loadImage(
        iv: ImageView,
        url: String,
        pb: ProgressBar? = null,
        @DrawableRes placeholderID: Int? = null,
        @DrawableRes onErrorImgID: Int? = null,
        tagObj: Any? = null,
        transformation: Transformation? = null
    ) {
        pb?.visibility = View.VISIBLE
        iv.apply {
            val p =
                if (url.startsWith("http")) {
                    Picasso.get().load(Uri.parse(url))
                } else {
                    Picasso.get().load(File(url))
                }
            placeholderID?.let {
                p.placeholder(it)
            }
            transformation?.let {
                p.transform(it)
            }
            p.into(this, object : Callback {
                override fun onSuccess() {
                    pb?.visibility = View.GONE
                }

                override fun onError(e: Exception?) {
                    onErrorImgID?.let {
                        iv.setImageResource(it)
                    }
                    pb?.visibility = View.GONE
                    Log.e("ImageLoader", "load image [$url] error:", e)
                }
            })
            if (tagObj != null) {
                tag = tagObj
            }
        }
    }
}