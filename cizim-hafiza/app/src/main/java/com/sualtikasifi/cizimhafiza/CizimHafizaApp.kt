package com.sualtikasifi.cizimhafiza

import android.app.Application
import com.sualtikasifi.cizimhafiza.data.local.WordSeeder
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CizimHafizaApp : Application() {

    @Inject lateinit var wordDao: WordDao

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Re-synced on every launch, not just first install: Room only seeds
        // via a RoomDatabase.Callback on first database creation, so a device
        // that already had the app installed would never pick up new words
        // added to assets/words.json in a later app update — its on-disk
        // database file already exists and that callback never fires again.
        // insertAll uses OnConflictStrategy.IGNORE, so this is a cheap no-op
        // once the counts already match.
        applicationScope.launch {
            val bundledWords = WordSeeder.loadFromAssets(applicationContext)
            if (wordDao.count() != bundledWords.size) {
                wordDao.insertAll(bundledWords)
            }
        }
    }
}
