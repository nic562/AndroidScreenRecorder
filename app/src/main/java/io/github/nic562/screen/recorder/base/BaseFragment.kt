package io.github.nic562.screen.recorder.base

import android.app.Application
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceDataStore
import io.github.nic562.screen.recorder.App
import io.github.nic562.screen.recorder.db.dao.DaoSession
import java.util.logging.Logger

abstract class BaseFragment : Fragment() {
    fun getApplication(): Application {
        return this.requireActivity().application
    }

    fun getApp(): App {
        return getApplication() as App
    }

    fun getPreference(): PreferenceDataStore {
        return getApp().perferences
    }

    fun getDB(): DaoSession {
        return getApp().getDB()
    }

    protected val logger by lazy {
        Logger.getLogger(this.javaClass.simpleName)
    }

    protected fun toastError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }
}