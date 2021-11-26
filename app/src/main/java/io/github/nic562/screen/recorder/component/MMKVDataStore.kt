package io.github.nic562.screen.recorder.component

import androidx.preference.PreferenceDataStore
import com.tencent.mmkv.MMKV

class MMKVDataStore : PreferenceDataStore() {
    private val kv by lazy { MMKV.mmkvWithID("SharedPreferences", MMKV.MULTI_PROCESS_MODE) }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return kv.decodeBool(key, defValue)
    }

    override fun putLong(key: String?, value: Long) {
        kv.encode(key, value)
    }

    override fun putInt(key: String?, value: Int) {
        kv.encode(key, value)
    }

    fun remove(key: String) {
        kv.remove(key)
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return kv.decodeInt(key, defValue)
    }

    override fun putBoolean(key: String?, value: Boolean) {
        kv.encode(key, value)
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        kv.encode(key, values)
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return kv.decodeLong(key, defValue)
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return kv.decodeFloat(key, defValue)
    }

    override fun putFloat(key: String?, value: Float) {
        kv.encode(key, value)
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> {
        return kv.decodeStringSet(key, defValues)
    }

    override fun getString(key: String?, defValue: String?): String? {
        return kv.decodeString(key, defValue)
    }

    override fun putString(key: String?, value: String?) {
        kv.encode(key, value)
    }
}