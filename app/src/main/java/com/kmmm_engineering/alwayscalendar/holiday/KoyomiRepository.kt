package com.kmmm_engineering.alwayscalendar.holiday

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** One day from the Koyomi API. [holiday] is empty when the day is not a holiday. */
data class KoyomiDay(val rokuyou: String, val holiday: String)

/**
 * Rokuyo + holidays from https://koyomi.zingsystem.com/ .
 * One request per year (mode=d, cnt=365 from Jan 1; leap years add Dec 31 with cnt=1).
 * Each year is stored as filesDir/koyomi/{year}.json (rokuyo never changes → kept).
 * A failed year is not retried until the next process start.
 * Callers serialize access (HolidayRepository's mutex).
 */
class KoyomiRepository(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "koyomi").also { it.mkdirs() }
    private val attempted = mutableSetOf<Int>()

    private val _days = MutableStateFlow<Map<String, KoyomiDay>>(emptyMap())
    val days: StateFlow<Map<String, KoyomiDay>> = _days.asStateFlow()

    private fun file(year: Int) = File(dir, "$year.json")

    fun hasYear(year: Int) = file(year).exists()

    /** @return true if the year is available (cached or freshly fetched). */
    suspend fun ensureYear(year: Int): Boolean {
        if (file(year).exists()) return true
        if (!attempted.add(year)) return false
        val ok = fetchYear(year)
        if (ok) rebuild()
        return ok
    }

    fun loadCached() = rebuild()

    private suspend fun fetchYear(year: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = JSONObject()
            val first = request(year, 1, 1, 365) ?: return@withContext false
            merge(out, first)
            if (LocalDate.of(year, 1, 1).isLeapYear) {
                val last = request(year, 12, 31, 1) ?: return@withContext false
                merge(out, last)
            }
            val expected = LocalDate.of(year, 1, 1).lengthOfYear()
            if (out.length() != expected) return@withContext false
            file(year).writeText(out.toString())
            true
        } catch (e: Exception) {
            Log.w(TAG, "koyomi fetch failed for $year", e)
            false
        }
    }

    private fun merge(out: JSONObject, list: JSONObject) {
        val it = list.keys()
        while (it.hasNext()) {
            val k = it.next()
            val d = list.optJSONObject(k) ?: continue
            out.put(
                k,
                JSONObject().put("r", d.optString("rokuyou", "")).put("h", d.optString("holiday", "")),
            )
        }
    }

    private fun request(year: Int, month: Int, day: Int, cnt: Int): JSONObject? {
        val url = URL(
            "https://koyomi.zingsystem.com/api/?mode=d&cnt=$cnt" +
                "&targetyyyy=$year&targetmm=$month&targetdd=$day",
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "koyomi HTTP ${conn.responseCode} for $year")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            // Errors come back as {"err":"401",...} without datelist.
            return JSONObject(body).optJSONObject("datelist")
        } finally {
            conn.disconnect()
        }
    }

    private fun rebuild() {
        val map = HashMap<String, KoyomiDay>()
        dir.listFiles()?.forEach { f ->
            if (!f.name.endsWith(".json")) return@forEach
            runCatching {
                val obj = JSONObject(f.readText())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val d = obj.getJSONObject(k)
                    map[k] = KoyomiDay(d.optString("r", ""), d.optString("h", ""))
                }
            }
        }
        _days.value = map
    }

    companion object {
        private const val TAG = "KoyomiRepository"
    }
}
