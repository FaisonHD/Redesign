package de.simonhub.app

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.math.roundToInt

internal data class HomeWeather(
    val city: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val fetchedAt: Instant = Instant.now()
) {
    fun json()=JSONObject().put("city",city).put("temperature",temperature).put("high",high).put("low",low).put("code",code).put("fetchedAt",fetchedAt.toString())
    companion object {
        fun parse(json: String)=runCatching {
            val value=JSONObject(json)
            HomeWeather(value.getString("city"),value.getInt("temperature"),value.getInt("high"),value.getInt("low"),value.getInt("code"),Instant.parse(value.getString("fetchedAt")))
        }.getOrNull()
    }
}

/** Small, cached city-based weather lookup. No device-location permission is requested. */
internal object HomeWeatherSource {
    fun fetch(query: String): HomeWeather {
        val term=query.trim()
        require(term.length in 2..80) {"Bitte einen Ort mit mindestens zwei Zeichen eingeben."}
        val geocoding=readJson("https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(term)}&count=1&language=de&format=json")
        val results=geocoding.optJSONArray("results") ?: error("Ort nicht gefunden. Bitte einen anderen Namen eingeben.")
        require(results.length()>0) {"Ort nicht gefunden. Bitte einen anderen Namen eingeben."}
        val place=results.getJSONObject(0)
        val forecast=readJson("https://api.open-meteo.com/v1/forecast?latitude=${place.getDouble("latitude")}&longitude=${place.getDouble("longitude")}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")
        val current=forecast.getJSONObject("current")
        val daily=forecast.getJSONObject("daily")
        return HomeWeather(
            city=place.getString("name"),
            temperature=current.getDouble("temperature_2m").roundToInt(),
            high=daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low=daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            code=current.getInt("weather_code")
        )
    }

    private fun readJson(address: String): JSONObject {
        val connection=URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod="GET"
            connection.connectTimeout=4_000
            connection.readTimeout=4_000
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("User-Agent","SimonHub/"+BuildConfig.VERSION_NAME+" Android")
            val status=connection.responseCode
            if(status !in 200..299) error("Wetterdienst nicht erreichbar ($status).")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use {JSONObject(it.readText())}
        } finally {connection.disconnect()}
    }
}

internal fun weatherDescription(code: Int)=when(code) {
    0->"Sonnig";1->"Überwiegend sonnig";2->"Teils bewölkt";3->"Bewölkt";45,48->"Neblig"
    in 51..57->"Nieselregen";in 61..67->"Regen";in 71..77->"Schnee";in 80..82->"Schauer";in 85..86->"Schneeschauer"
    in 95..99->"Gewitter";else->"Wetter"
}

