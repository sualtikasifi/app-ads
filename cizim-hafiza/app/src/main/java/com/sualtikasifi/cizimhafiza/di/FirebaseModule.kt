package com.sualtikasifi.cizimhafiza.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Persistent local cache, explicitly configured and left unbounded.
     *
     * Firestore bills per document read, and reads served from this cache
     * are free: a snapshot listener re-attaching after the app was
     * backgrounded resumes from its stored token and is only charged for
     * documents that actually changed, and every cache-first get() (see
     * FriendRepositoryImpl's league profiles) costs nothing at all. The
     * default cache is persistent too but capped at 100 MB, which this app
     * cannot come close to filling — the cap only ever evicts documents we
     * would then pay to read again.
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(
                persistentCacheSettings { setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) }
            )
        }
    }
}
