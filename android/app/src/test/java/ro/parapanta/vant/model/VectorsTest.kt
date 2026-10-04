package ro.parapanta.vant.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Aceleași cazuri ca tests/core.test.mjs (web), din data/test_vectors/. Dacă un caz pică aici, regulile au divergat. */
class VectorsTest {
    private fun load(name: String): JsonObject =
        Json.parseToJsonElement(File("../../data/test_vectors/$name.json").readText()).jsonObject

    private fun JsonObject.d(k: String) = this[k]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull
    private fun JsonObject.s(k: String) = this[k]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    private fun hour(e: JsonElement?): Hour? {
        if (e == null || e is JsonNull) return null
        val o = e.jsonObject
        return Hour(o.d("ws"), o.d("wd"), o.d("wg"), o.d("pr"), o.d("cc"), o.d("cape"), o.d("tt"), o.d("w8"), o.d("d8"),
            o.i("code"), o.d("lo"), o.d("mi"), o.d("hi"), o.i("day"), o.d("li"))
    }

    private fun thresholds(o: JsonObject?) = Thresholds(
        good = o?.d("good") ?: 5.0, marg = o?.d("marg") ?: 7.0, calm = o?.d("calm") ?: 1.0, gust = o?.d("gust") ?: 9.0,
        spread = o?.d("spread") ?: 4.0, rain = o?.d("rain") ?: 0.2, from = o?.i("from") ?: 8, to = o?.i("to") ?: 20,
    )

    private fun merged(base: JsonObject, over: JsonElement?) =
        JsonObject(base["settings"]!!.jsonObject + (over?.jsonObject ?: emptyMap()))

    private fun site(v: JsonObject, c: JsonObject) =
        Site(c.s("site")!!, 0.0, 0.0, null, v["sites"]!!.jsonObject[c.s("site")!!]!!.jsonArray.map { it.jsonPrimitive.intOrNull ?: 0 })

    private fun status(s: String) = Status.valueOf(s.uppercase())

    @Test fun rate() {
        val v = load("rate")
        for (e in v["cases"]!!.jsonArray) {
            val c = e.jsonObject
            val next = c["next"]?.jsonArray?.map { hour(it) } ?: emptyList()
            val r = rate(site(v, c), hour(c["w"]), thresholds(merged(v, c["settings"])), next)
            assertEquals(c.s("name"), status(c.s("st")!!), r.status)
            c["warn"]?.let { w -> assertEquals(c.s("name") + " (avertizări)", w.jsonArray.map { it.jsonPrimitive.content }.sorted(), r.warn.sorted()) }
        }
    }

    @Test fun summary() {
        val v = load("summary")
        for (e in v["cases"]!!.jsonArray) {
            val c = e.jsonObject
            val hours = c["hours"]!!.jsonArray.map { it.jsonPrimitive.content }
            val extra = c["extra"]?.jsonArray?.map { hour(it) } ?: emptyList()
            val r = summarize(site(v, c), hours, c["w"]!!.jsonArray.map { hour(it) }, thresholds(merged(v, c["settings"])), extra)
            c["sts"]?.let { s -> assertEquals(c.s("name"), s.jsonArray.map { status(it.jsonPrimitive.content) }, r.statuses) }
            assertEquals(c.s("name"), status(c.s("st")!!), r.status)
            assertEquals(c.s("name"), c.s("text"), r.text)
        }
    }

    @Test fun sky() {
        for (e in load("sky")["cases"]!!.jsonArray) {
            val c = e.jsonObject
            val k = sky(hour(c["w"])!!)
            if ("icon" in c) assertEquals(c.s("name"), c.s("icon"), k.icon)
            if ("label" in c) assertEquals(c.s("name"), c.s("label"), k.label)
            if ("type" in c) assertEquals(c.s("name"), c.s("type"), k.type)
        }
    }

    @Test fun coords() {
        for (e in load("coords")["cases"]!!.jsonArray) {
            val c = e.jsonObject
            val out = c["out"]!!.let { if (it is JsonNull) null else it.jsonArray.map { x -> x.jsonPrimitive.doubleOrNull!! } }
            assertEquals(c.s("in"), out?.let { it[0] to it[1] }, parseCoords(c.s("in")!!))
        }
    }

    @Test fun validate() {
        val v = load("validate")
        val today = v.s("today")!!
        for (e in v["flight"]!!.jsonArray) {
            val c = e.jsonObject
            assertEquals(c.toString(), c.s("err"), validateFlight(c.s("site")!!, c.s("date"), c.i("h")!!, c.i("m")!!, today))
        }
        for (e in v["settings"]!!.jsonArray) {
            val c = e.jsonObject
            assertEquals(c.toString(), c.s("err"), validateThresholds(thresholds(c["s"]!!.jsonObject)))
        }
    }

    /** Profilurile Kotlin = PROFILES din core.js (citite din sursa web), toate valide. */
    @Test fun profiles() {
        val js = File("../../src/core.js").readText()
        for ((key, name) in listOf("elev" to "Elev / începător", "pilot" to "Pilot", "avansat" to "Avansat")) {
            val m = Regex("""$key:\s*\{good:(\S+?), marg:(\S+?), calm:(\S+?), gust:(\S+?),\s*spread:(\S+?), rain:(\S+?)\}""").find(js)!!
            val (g, mg, c, gu, sp, r) = m.destructured
            assertEquals(name, Thresholds(good = g.toDouble(), marg = mg.toDouble(), calm = c.toDouble(), gust = gu.toDouble(), spread = sp.toDouble(), rain = r.toDouble()), PROFILES[name])
            assertEquals(name, null, validateThresholds(PROFILES[name]!!))
        }
        assertEquals("Pilot", profileOf(Thresholds()))
    }
}
