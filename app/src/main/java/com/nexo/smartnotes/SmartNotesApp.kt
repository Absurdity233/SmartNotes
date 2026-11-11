package com.nexo.smartnotes

import android.app.Application
import com.nexo.smartnotes.data.NotesRepository
import com.nexo.smartnotes.data.Prefs
import com.nexo.smartnotes.data.local.AppDatabase
import com.nexo.smartnotes.data.remote.CloudApi
import com.nexo.smartnotes.util.ReminderScheduler

/**
 * 应用级依赖容器（手写依赖注入，避免引入额外框架）。
 */
class SmartNotesApp : Application() {

    lateinit var repository: NotesRepository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        prefs = Prefs(this)
        val db = AppDatabase.get(this)
        val api = CloudApi { prefs.serverUrlBlocking() }
        repository = NotesRepository(this, db, prefs, api)

        ReminderScheduler.createChannel(this)
    }

    companion object {
        lateinit var instance: SmartNotesApp
            private set
    }
}
