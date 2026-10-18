package com.kmmm_engineering.alwayscalendar.holiday

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Calendar

/**
 * Japanese holidays from holidays-jp (MIT).
 * Caches year JSON under filesDir/holidays/{year}.json.
 */
class HolidayRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.filesDir, "holidays").also { it.mkdirs() }
    private val mutex = Mutex()
    val koyomi = KoyomiRepository(context)

    /** date string "yyyy-MM-dd" → holiday name */
    private val _holidays = MutableStateFlow<Map<String, String>>(emptyMap())
    val holidays: StateFlow<Map<String, String>> = _holidays.asStateFlow()

    private val fetchedYears = mutableSetOf<Int>()

    fun isHoliday(year: Int, month: Int, day: Int): Boolean {
        val key = "%04d-%02d-%02d".format(year, month, day)
        return _holidays.value.containsKey(key)
    }

    fun holidayName(year: Int, month: Int, day: Int): String? {
        val key = "%04d-%02d-%02d".format(year, month, day)
        return _holidays.value[key]
    }

    /**
     * Ensure prev/current/next year relative to [aroundYear] are loaded.
     * @return false if any required year could not be obtained (and was not cached).
     */
    suspend fun ensureYearsAround(aroundYear: Int, rokuyo: Boolean = false): Boolean = mutex.withLock {
        val years = listOf(aroundYear - 1, aroundYear, aroundYear + 1)
        var allOk = true
        for (y in years) {
            if (!ensureYearBoth(y, rokuyo)) allOk = false
        }
        rebuildMap()
        allOk
    }

    /** Ensure the year containing [year]/[month] is available (for scroll into uncached months). */
    suspend fun ensureMonth(year: Int, month: Int, rokuyo: Boolean = false): Boolean = mutex.withLock {
        val ok = ensureYearBoth(year, rokuyo)
        rebuildMap()
        ok
    }

    /**
     * Rokuyo ON: Koyomi API is the main source (holidays-jp is still fetched: its names win).
     * Rokuyo OFF: holidays-jp is main; Koyomi is only asked when holidays-jp failed.
     * @return true if at least the main source (or its fallback) produced the year.
     */
    private suspend fun ensureYearBoth(year: Int, rokuyo: Boolean): Boolean {
        return if (rokuyo) {
            val k = koyomi.ensureYear(year)
            val h = ensureYearLocked(year)
            k || h
        } else {
            ensureYearLocked(year) || koyomi.ensureYear(year)
        }
    }

    private suspend fun ensureYearLocked(year: Int): Boolean {
        if (year in fetchedYears && yearFile(year).exists()) return true
        // Try disk cache first
        val file = yearFile(year)
        if (file.exists()) {
            fetchedYears.add(year)
            return true
        }
        return fetchYear(year)
    }

    private fun yearFile(year: Int) = File(cacheDir, "$year.json")

    private suspend fun fetchYear(year: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://holidays-jp.github.io/api/v1/$year/date.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                Log.w(TAG, "holiday fetch HTTP $code for $year")
                return@withContext false
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            // Validate JSON object
            JSONObject(body)
            yearFile(year).writeText(body)
            fetchedYears.add(year)
            true
        } catch (e: Exception) {
            Log.w(TAG, "holiday fetch failed for $year", e)
            false
        }
    }

    private fun rebuildMap() {
        val map = linkedMapOf<String, String>()
        cacheDir.listFiles()?.forEach { f ->
            if (!f.name.endsWith(".json")) return@forEach
            runCatching {
                val obj = JSONObject(f.readText())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.optString(k, "")
                }
            }
        }
        _holidays.value = map
    }

    /** Load any existing cache into memory without network. */
    suspend fun loadCached() = mutex.withLock {
        cacheDir.listFiles()?.forEach { f ->
            val name = f.name.removeSuffix(".json")
            name.toIntOrNull()?.let { fetchedYears.add(it) }
        }
        koyomi.loadCached()
        rebuildMap()
    }

    companion object {
        private const val TAG = "HolidayRepository"

        fun calendarYearMonth(cal: Calendar): Pair<Int, Int> =
            cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)

        fun todayLocal(): LocalDate = LocalDate.now()
    }
}
