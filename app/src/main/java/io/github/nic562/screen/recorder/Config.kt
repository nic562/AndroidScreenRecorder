package io.github.nic562.screen.recorder

import io.github.nic562.screen.recorder.component.MMKVDataStore

object Config {
    lateinit var preference: MMKVDataStore

    fun getDefaultApiID(): Long {
        return preference.getLong("checkApiID", -1)
    }

    fun updateDefaultApiID(id: Long) {
        preference.putLong("checkApiID", id)
    }

    fun getAutoUpload(): Boolean {
        return preference.getBoolean("auto_upload", false)
    }

    fun getAutoDelete(): Boolean {
        return preference.getBoolean("auto_delete", false)
    }
}