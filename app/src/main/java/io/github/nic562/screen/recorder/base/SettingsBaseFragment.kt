package io.github.nic562.screen.recorder.base

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.github.nic562.screen.recorder.Config

abstract class SettingsBaseFragment(private val preferenceRes: Int) : PreferenceFragmentCompat() {
    private val dataStore by lazy {
        Config.preference
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = dataStore
        setPreferencesFromResource(preferenceRes, rootKey)
    }
}