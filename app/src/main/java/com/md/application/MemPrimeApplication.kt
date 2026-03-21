package com.md.application


import android.app.Application
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @DefaultDispatcher
    @Provides
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @IoDispatcher
    @Provides
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @MainDispatcher
    @Provides
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher

// Definition of the Application graph
@Component(  modules = [
DispatcherModule::class,
])
interface ApplicationComponent {
    // See: https://developer.android.com/training/dependency-injection/dagger-android

}

@HiltAndroidApp
class MemPrimeApplication : Application() {
    // See https://developer.android.com/training/dependency-injection/dagger-android
    val unused = DaggerApplicationComponent.create()

    override fun onCreate() {
        super.onCreate()
        // Cancel any previously-scheduled transcription work on startup.
        // The Vosk speech-to-text model consumes hundreds of MB of RAM when loaded,
        // which causes the system to repeatedly kill the app due to LOW_MEMORY pressure.
        // WorkManager persists work across app restarts, so a previously-triggered
        // manual transcription will re-run on the next launch if not cancelled here.
        // Transcription should only run when explicitly triggered by the user via the UI.
        val workManager = androidx.work.WorkManager.getInstance(this)
        workManager.cancelUniqueWork("TranscriptionWorker")  // legacy name
        workManager.cancelUniqueWork("ManualTranscription")  // current name from UI
    }
}