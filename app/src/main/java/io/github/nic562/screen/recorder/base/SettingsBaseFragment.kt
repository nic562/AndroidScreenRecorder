package io.github.nic562.screen.recorder.base

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.github.nic562.screen.recorder.Config

abstract class SettingsBaseFragment : PreferenceFragmentCompat() {
    protected abstract val preferenceRes: Int
    protected val dataStore by lazy {
        Config.preference
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = dataStore
        setPreferencesFromResource(preferenceRes, rootKey)
    }
}