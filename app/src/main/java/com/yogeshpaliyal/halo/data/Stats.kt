package com.yogeshpaliyal.halo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors

/**
 * A small local tally of the switches made with Halo, for the Recap. Per day: total, which
 * apps were opened, the hour, the gesture used and back-and-forth pairs. Kept for 62 days
 * in Halo's private storage; it never leaves the phone and can be reset from the Recap.
 */
object Stats {
    enum class Kind { DIAL, TAP, DOUBLE }

    private const val FILE = "stats.json"
    private const val KEEP_DAYS = 62L
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Volatile
    private var lastKey: String? = null

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Records one switch to [key]. Returns at once; the write happens in the background. */
    fun record(context: Context, key: String, kind: Kind) {
        val app = context.applicationContext
        val previous = lastKey
        lastKey = key
        val today = LocalDate.now().toString()
        val hour = LocalTime.now().hour
        io.execute {
            synchronized(lock) {
                val root = read(app)
                val days = root.optJSONObject("days") ?: JSONObject().also { root.put("days", it) }
                val day = days.optJSONObject(today) ?: JSONObject().also { days.put(today, it) }
                day.put("t", day.optInt("t") + 1)
                val apps = day.optJSONObject("a") ?: JSONObject().also { day.put("a", it) }
                apps.put(key, apps.optInt(key) + 1)
                val hours = day.optJSONArray("h") ?: JSONArray(IntArray(24).toList()).also { day.put("h", it) }
                hours.put(hour, hours.optInt(hour) + 1)
                val kinds = day.optJSONArray("k") ?: JSONArray(listOf(0, 0, 0)).also { day.put("k", it) }
                kinds.put(kind.ordinal, kinds.optInt(kind.ordinal) + 1)
                if (previous != null && previous != key) {
                    // Unordered, so A→B and B→A count as the same duo.
                    val pk = listOf(previous, key).sorted().joinToString("|")
                    val pairs = day.optJSONObject("p") ?: JSONObject().also { day.put("p", it) }
                    pairs.put(pk, pairs.optInt(pk) + 1)
                }
                prune(days)
                runCatching { file(app).writeText(root.toString()) }
            }
        }
    }

    private fun read(context: Context): JSONObject =
        runCatching { JSONObject(file(context).readText()) }.getOrElse { JSONObject() }

    private fun prune(days: JSONObject) {
        val oldest = LocalDate.now().minusDays(KEEP_DAYS).toString()
        days.keys().asSequence().toList().filter { it < oldest }.forEach { days.remove(it) }
    }

    fun reset(context: Context) {
        synchronized(lock) { file(context).delete() }
        lastKey = null
    }

    /** Everything the Recap shows, for the last [days] days including today. */
    data class Summary(
        val days: Int,
        val total: Int,
        val activeDays: Int,
        val topApps: List<Pair<String, Int>>,
        val topPair: Triple<String, String, Int>?,
        val hours: IntArray,
        val kinds: IntArray,
        val streak: Int,
        val perDay: List<Pair<LocalDate, Int>>,
    ) {
        val peakHour: Int get() = hours.indices.maxByOrNull { hours[it] } ?: 0
    }

    /** Reads and adds up the tally. Call off the main thread. */
    fun summary(context: Context, days: Int): Summary = synchronized(lock) {
        val all = read(context).optJSONObject("days") ?: JSONObject()
        val today = LocalDate.now()
        val range = (0 until days).map { today.minusDays(it.toLong()) }.reversed()
        val apps = HashMap<String, Int>()
        val pairs = HashMap<String, Int>()
        val hours = IntArray(24)
        val kinds = IntArray(3)
        val perDay = range.map { date ->
            val d = all.optJSONObject(date.toString())
            if (d != null) {
                d.optJSONObject("a")?.let { a -> a.keys().forEach { k -> apps[k] = (apps[k] ?: 0) + a.optInt(k) } }
                d.optJSONObject("p")?.let { p -> p.keys().forEach { k -> pairs[k] = (pairs[k] ?: 0) + p.optInt(k) } }
                d.optJSONArray("h")?.let { h -> for (i in 0 until 24) hours[i] += h.optInt(i) }
                d.optJSONArray("k")?.let { k -> for (i in 0 until 3) kinds[i] += k.optInt(i) }
            }
            date to (d?.optInt("t") ?: 0)
        }
        // Days in a row with at least one switch, ending today (or yesterday if today is empty).
        var streak = 0
        var day = if ((all.optJSONObject(today.toString())?.optInt("t") ?: 0) > 0) today else today.minusDays(1)
        while ((all.optJSONObject(day.toString())?.optInt("t") ?: 0) > 0) {
            streak++
            day = day.minusDays(1)
        }
        val top = pairs.maxByOrNull { it.value }?.let { (k, v) ->
            val (a, b) = k.split("|").let { it[0] to it.getOrElse(1) { "" } }
            Triple(a, b, v)
        }
        Summary(
            days = days,
            total = perDay.sumOf { it.second },
            activeDays = perDay.count { it.second > 0 },
            topApps = apps.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            topPair = top?.takeIf { it.third >= 2 },
            hours = hours,
            kinds = kinds,
            streak = streak,
            perDay = perDay,
        )
    }
}
