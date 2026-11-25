package com.karelisio.patoune.air

import com.karelisio.patoune.data.AirDao
import com.karelisio.patoune.data.AirDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class City(val name: String, val detail: String, val lat: Double, val lon: Double)

/**
 * Pollens et qualité de l'air via Open-Meteo (données CAMS), pour une ville choisie à la main :
 * aucune autorisation de localisation. Une valeur par jour (maximum horaire) est stockée.
 */
class AirRepo(private val dao: AirDao) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(url: String): JsonObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
    }

    suspend fun search(query: String): List<City> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val r = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=fr&format=json")
        return (r["results"] as? JsonArray).orEmpty().map { it.jsonObject }.map { o ->
            City(
                o["name"]!!.jsonPrimitive.content,
                listOfNotNull(o["admin1"]?.jsonPrimitive?.content, o["country"]?.jsonPrimitive?.content).joinToString(", "),
                o["latitude"]!!.jsonPrimitive.doubleOrNull!!, o["longitude"]!!.jsonPrimitive.doubleOrNull!!,
            )
        }
    }

    suspend fun fetch(city: String, lat: Double, lon: Double, pastDays: Int = 7): Int {
        val vars = "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen,european_aqi"
        val r = get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&hourly=$vars&past_days=$pastDays&forecast_days=1&timezone=auto")
        val days = parseDaily(r, city)
        days.forEach { dao.put(it) }
        return days.size
    }

    companion object {
        /** Agrège les séries horaires en un maximum par jour. */
        fun parseDaily(r: JsonObject, city: String): List<AirDay> {
            val h = r["hourly"]?.jsonObject ?: return emptyList()
            val times = h["time"]!!.jsonArray.map { it.jsonPrimitive.content.take(10) }
            fun series(k: String) = h[k]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull }
            val keys = listOf("european_aqi", "alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "olive_pollen", "ragweed_pollen")
            val data = keys.associateWith { series(it) }
            return times.indices.groupBy { times[it] }.map { (date, idx) ->
                fun max(k: String) = data[k]?.let { s -> idx.mapNotNull { s.getOrNull(it) }.maxOrNull() }
                AirDay(date, city, max("european_aqi"), max("alder_pollen"), max("birch_pollen"), max("grass_pollen"), max("mugwort_pollen"), max("olive_pollen"), max("ragweed_pollen"))
            }
        }
    }
}
