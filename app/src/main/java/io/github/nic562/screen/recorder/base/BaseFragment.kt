package io.github.nic562.screen.recorder.base

import android.app.Application
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.github.nic562.screen.recorder.App
import io.github.nic562.screen.recorder.R
import io.github.nic562.screen.recorder.db.dao.DaoSession
import java.util.logging.Logger

abstract class BaseFragment : Fragment() {

    protected class StringArrayAdapter(context: Context, array: ArrayList<String>) :
        ArrayAdapter<String>(context, R.layout.layout_popup_item, array)

    fun getApplication(): Application {
        return this.requireActivity().application
    }

    fun getApp(): App {
        return getApplication() as App
    }

    fun getDB(): DaoSession {
        return getApp().getDB()
    }

    protected val logger by lazy {
        Logger.getLogger(this.javaClass.simpleName)
    }

    protected fun toast(msg: String, duration: Int = Toast.LENGTH_LONG) {
        Toast.makeText(requireContext(), msg, duration).show()
    }
}