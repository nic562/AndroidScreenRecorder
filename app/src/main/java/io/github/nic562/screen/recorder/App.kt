package io.github.nic562.screen.recorder

import android.app.Application
import com.tencent.mmkv.MMKV
import io.github.nic562.screen.recorder.component.MMKVDataStore
import io.github.nic562.screen.recorder.db.MyDBHelper
import io.github.nic562.screen.recorder.db.dao.DaoMaster
import io.github.nic562.screen.recorder.db.dao.DaoSession

class App : Application() {
    private val daoSession: DaoSession by lazy {
        val helper =
            if (BuildConfig.DEBUG)
                DaoMaster.DevOpenHelper(this, "debug")
            else
                MyDBHelper(this)
        DaoMaster(helper.writableDb).newSession()
    }

    private val preference by lazy {
        MMKVDataStore()
    }

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        Config.preference = preference
    }

    fun getDB(): DaoSession {
        return daoSession
    }
}