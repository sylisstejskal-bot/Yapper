package app.yapper

import android.app.Application
import android.util.Log
import app.yapper.data.YapperDatabase
import app.yapper.data.YapperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class YapperApp : Application() {
    lateinit var repository: YapperRepository
        private set

    val ready = MutableStateFlow(false)
    val bootError = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        val database = YapperDatabase.create(this)
        repository = YapperRepository(this, database, getSharedPreferences("yapper", MODE_PRIVATE))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.seedIfNeeded()
                ready.value = true
            } catch (error: Throwable) {
                Log.e("Yapper", "Database failed to open", error)
                bootError.value = error.stackTraceToString()
            }
        }
    }
}
