package de.simonhub.app
import android.app.Application
import android.content.Context
import de.simonhub.data.*
import de.simonhub.integrations.*
open class HubApplication: Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get()=androidx.work.Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()
    val repository by lazy {HubRepository(this)};val secrets by lazy {SecretStore(this)}
    val calendar by lazy {CalendarSource(this)};val vault by lazy {ObsidianSource(this)};val bridge by lazy {BridgeClient(repository,secrets)}
    override fun onCreate() {super.onCreate();Reminders.channel(this)}
    internal open fun fetchHomeWeather(query:String):HomeWeather=HomeWeatherSource.fetch(query)
}
val Context.hub: HubApplication get()=applicationContext as HubApplication

