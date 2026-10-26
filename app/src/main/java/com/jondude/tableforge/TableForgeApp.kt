package com.jondude.tableforge

import android.app.Application
import com.jondude.tableforge.data.AppDatabase
import com.jondude.tableforge.data.SettingsStore

class TableForgeApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
