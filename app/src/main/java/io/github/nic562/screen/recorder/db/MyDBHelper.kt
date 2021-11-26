package io.github.nic562.screen.recorder.db

import android.content.Context
import io.github.nic562.screen.recorder.db.dao.DaoMaster
import org.greenrobot.greendao.database.Database

class MyDBHelper(context: Context) : DaoMaster.OpenHelper(context, "myRecorder") {
    override fun onUpgrade(db: Database?, oldVersion: Int, newVersion: Int) {
    }
}