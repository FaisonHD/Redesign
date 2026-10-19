package de.simonhub.app

/** UI tests use a synthetic forecast and never send a configured city to a weather service. */
class OfflineHubApplication:HubApplication() {
    internal override fun fetchHomeWeather(query:String)=HomeWeather("Beispielstadt",18,21,10,2)
}
