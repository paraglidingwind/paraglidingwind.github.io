package ro.parapanta.vant.model

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.floor
import kotlin.math.roundToInt
import java.util.zip.ZipOutputStream

/*
 * Export Excel (.xlsx) construit direct (XML + zip), fără biblioteci.
 * Aceeași structură și același XML ca xlsxParts() din pagina web.
 * Stiluri: 1 = antet (bold), 2 = dată zz.ll.aaaa, 3 = durată [h]:mm, 4 = durată bold.
 */
private sealed interface Cell
private data class Str(val v: String, val s: Int = 0) : Cell
private data class Num(val v: String, val s: Int = 0) : Cell
private data class Formula(val f: String, val v: String, val s: Int) : Cell

private fun xmlEsc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun excelDate(d: String): Long = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.parse(d))
private fun days(min: Int) = (min / 1440.0).toString()

private fun row(r: Int, cells: List<Cell>) = "<row r=\"$r\">" + cells.mapIndexed { i, c ->
    val ref = "ABCDEFG"[i] + r.toString()
    fun st(s: Int) = if (s != 0) " s=\"$s\"" else ""
    when (c) {
        is Formula -> "<c r=\"$ref\" s=\"${c.s}\"><f>${c.f}</f><v>${c.v}</v></c>"
        is Str -> "<c r=\"$ref\" t=\"inlineStr\"${st(c.s)}><is><t>${xmlEsc(c.v)}</t></is></c>"
        is Num -> "<c r=\"$ref\"${st(c.s)}><v>${c.v}</v></c>"
    }
}.joinToString("") + "</row>"

private fun sheet(widths: List<Int>, rows: List<List<Cell>>) =
    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>" +
        "<cols>" + widths.mapIndexed { i, w -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("") + "</cols>" +
        "<sheetData>" + rows.mapIndexed { i, c -> row(i + 1, c) }.joinToString("") + "</sheetData></worksheet>"

fun xlsxParts(list: List<Flight>, sites: List<Site>): List<Pair<String, String>> {
    fun h(s: String) = Str(s, 1)
    val sorted = list.sortedWith(compareBy({ it.date }, { it.id }))
    val n = sorted.size
    val total = sorted.sumOf { it.minutes }
    val s1 = listOf(listOf<Cell>(h("Data"), h("Locație"), h("Timp în aer"), h("Minute"))) +
        sorted.map { listOf(Num(excelDate(it.date).toString(), 2), Str(it.site), Num(days(it.minutes), 3), Num(it.minutes.toString())) } +
        listOf(listOf(h("Total"), Str(""), Formula("SUM(C2:C${n + 1})", days(total), 4), Formula("SUM(D2:D${n + 1})", total.toString(), 1)))
    val s2 = listOf(listOf<Cell>(h("Locație"), h("Zile de zbor"), h("Timp în aer"), h("Ultima zi"))) +
        bySite(list).map { listOf(Str(it.site), Num(it.days.toString()), Num(days(it.minutes), 3), Num(excelDate(it.last).toString(), 2)) }
    val s3 = listOf(listOf<Cell>(h("Nume"), h("Latitudine"), h("Longitudine"), h("Altitudine"), h("Direcții bune"), h("Marginal"), h("Holfuy"))) +
        sites.map { st ->
            fun d(v: Int) = st.o.mapIndexedNotNull { i, x -> if (x == v) DIRS[i] else null }.joinToString(", ")
            listOf(Str(st.n), Num(st.lat.toString()), Num(st.lon.toString()), st.alt?.let { Num(it.toString()) } ?: Str(""),
                Str(d(2)), Str(d(1)), when { st.hf > 0 -> Num(st.hf.toString()); st.hf == 0 -> Str("fără"); else -> Str("") })
        }
    val ns = "http://schemas.openxmlformats.org"
    val x = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    val ws = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    return listOf(
        "[Content_Types].xml" to x + "<Types xmlns=\"$ns/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"$ws\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"$ws\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"$ws\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>",
        "_rels/.rels" to x + "<Relationships xmlns=\"$ns/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$ns/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
        "xl/workbook.xml" to x + "<workbook xmlns=\"$ns/spreadsheetml/2006/main\" xmlns:r=\"$ns/officeDocument/2006/relationships\"><sheets><sheet name=\"Zile de zbor\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Pe locații\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"Situri\" sheetId=\"3\" r:id=\"rId3\"/></sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>",
        "xl/_rels/workbook.xml.rels" to x + "<Relationships xmlns=\"$ns/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$ns/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"$ns/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"$ns/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/><Relationship Id=\"rId4\" Type=\"$ns/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>",
        "xl/styles.xml" to x + "<styleSheet xmlns=\"$ns/spreadsheetml/2006/main\"><numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"dd.mm.yyyy\"/><numFmt numFmtId=\"165\" formatCode=\"[h]:mm\"/></numFmts><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"5\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>",
        "xl/worksheets/sheet1.xml" to sheet(listOf(12, 26, 12, 10), s1),
        "xl/worksheets/sheet2.xml" to sheet(listOf(26, 13, 12, 12), s2),
        "xl/worksheets/sheet3.xml" to sheet(listOf(24, 11, 11, 11, 18, 18, 9), s3),
    )
}

fun flightsXlsx(list: List<Flight>, sites: List<Site>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        xlsxParts(list, sites).forEach { (name, xml) ->
            z.putNextEntry(ZipEntry(name))
            z.write(xml.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
    }
    return out.toByteArray()
}

/* ---------- import ----------
 * Citește un export (al nostru sau re-salvat din Excel: sharedStrings, compresie, celule numerice).
 * Același algoritm ca readXlsx() din pagina web. Rândurile fără dată/locație/timp valid sunt ignorate
 * (antetul și rândul „Total” cad singure).
 */
data class XlsxImport(val flights: List<Flight>, val sites: List<Site>)

private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|lt|gt|quot|apos|amp);")
private fun unescape(t: String) = ENTITY.replace(t) { m ->
    val e = m.groupValues[1]
    when {
        e.startsWith("#x") -> String(Character.toChars(e.drop(2).toInt(16)))
        e.startsWith("#") -> String(Character.toChars(e.drop(1).toInt()))
        else -> mapOf("lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "amp" to "&")[e]!!
    }
}
private val DOT = setOf(RegexOption.DOT_MATCHES_ALL)
private fun texts(x: String) = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", DOT).findAll(x).joinToString("") { unescape(it.groupValues[1]) }
private fun attr(tag: String, name: String) = Regex("\\s${Regex.escape(name)}=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

private fun cells(xml: String, shared: List<String>): List<Map<Int, String>> =
    Regex("<row\\b([^>]*?)(?:/>|>(.*?)</row>)", DOT).findAll(xml).map { r ->
        val map = mutableMapOf<Int, String>()
        var pos = 0
        Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", DOT).findAll(r.groupValues[2]).forEach { c ->
            val attrs = c.groupValues[1]
            val body = c.groupValues[2]
            val ref = Regex("\\br=\"([A-Z]+)\\d*\"").find(attrs)?.groupValues?.get(1)
            val col = ref?.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) }?.minus(1) ?: pos
            pos = col + 1
            val t = Regex("\\bt=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
            val v = Regex("<v>(.*?)</v>", DOT).find(body)?.groupValues?.get(1)?.let(::unescape)
            map[col] = when (t) { "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }; "inlineStr" -> texts(body); else -> v } ?: ""
        }
        map
    }.toList()

private val BASE = LocalDate.of(1899, 12, 30)
fun parseExcelDate(v: String): String? {
    val s = v.trim()
    s.toDoubleOrNull()?.let { n -> return if (n > 1000 && n < 200000) BASE.plusDays(floor(n).toLong()).toString() else null }
    val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s)?.groupValues?.let { listOf(it[1], it[2], it[3]) }
        ?: Regex("^(\\d{1,2})[./](\\d{1,2})[./](\\d{4})").find(s)?.groupValues?.let { listOf(it[3], it[2], it[1]) }
        ?: return null
    return runCatching { LocalDate.of(m[0].toInt(), m[1].toInt(), m[2].toInt()).toString() }.getOrNull()
}
fun parseExcelMinutes(minutes: String?, duration: String?): Int? {
    minutes?.trim()?.toDoubleOrNull()?.let { return it.roundToInt() }
    val d = duration?.trim() ?: return null
    d.toDoubleOrNull()?.let { return if (it < 10) (it * 1440).roundToInt() else null }
    return Regex("^(\\d+):(\\d{1,2})").find(d)?.groupValues?.let { it[1].toInt() * 60 + it[2].toInt() }
}
private val DIR_IN = mapOf("N" to 0, "NE" to 1, "E" to 2, "SE" to 3, "S" to 4, "SV" to 5, "SW" to 5, "V" to 6, "W" to 6, "NV" to 7, "NW" to 7)
private fun dirSet(v: String?) = (v ?: "").uppercase().split(Regex("[,;\\s]+")).mapNotNull { DIR_IN[it] }.toSet()

fun readXlsx(bytes: ByteArray): XlsxImport? {
    val files = mutableMapOf<String, String>()
    runCatching {
        ZipInputStream(bytes.inputStream()).use { z ->
            generateSequence { z.nextEntry }.forEach { e ->
                if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) files[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
    }.onFailure { return null }
    val wb = files["xl/workbook.xml"] ?: return null
    val rels = Regex("<Relationship\\b[^>]*>").findAll(files["xl/_rels/workbook.xml.rels"] ?: "")
        .associate { attr(it.value, "Id") to attr(it.value, "Target") }
    fun sheet(name: String): String? {
        val tag = Regex("<sheet\\b[^>]*>").findAll(wb).firstOrNull { unescape(attr(it.value, "name") ?: "") == name }?.value ?: return null
        val target = rels[attr(tag, "r:id")] ?: return null
        return files[if (target.startsWith("/")) target.drop(1) else "xl/$target"]
    }
    val shared = files["xl/sharedStrings.xml"]?.let { x ->
        Regex("<si\\b[^>]*>(.*?)</si>", DOT).findAll(x).map { texts(it.groupValues[1]) }.toList()
    } ?: emptyList()
    val days = sheet("Zile de zbor") ?: return null
    val flights = cells(days, shared).mapNotNull { r ->
        val date = parseExcelDate(r[0] ?: "") ?: return@mapNotNull null
        val site = (r[1] ?: "").trim().ifEmpty { return@mapNotNull null }
        val min = parseExcelMinutes(r[3], r[2]) ?: return@mapNotNull null
        if (min > 0) Flight("", date, site, min) else null
    }
    val sites = sheet("Situri")?.let { x ->
        cells(x, shared).mapNotNull { r ->
            val name = (r[0] ?: "").trim().ifEmpty { return@mapNotNull null }
            val lat = r[1]?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = r[2]?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            val good = dirSet(r[4])
            val marg = dirSet(r[5])
            val hf = (r[6] ?: "").trim().let { h -> h.toDoubleOrNull()?.roundToInt() ?: if (h.equals("fără", true)) 0 else -1 }
            Site(name, lat, lon, r[3]?.trim()?.toDoubleOrNull()?.roundToInt(), List(8) { if (it in good) 2 else if (it in marg) 1 else 0 }, hf)
        }
    } ?: emptyList()
    return XlsxImport(flights, sites)
}

/** Adaugă doar zilele care lipsesc (aceeași dată, locație și durată = deja există). */
fun mergeFlights(existing: List<Flight>, imported: List<Flight>, newId: () -> String): Pair<List<Flight>, Int> {
    fun k(f: Flight) = "${f.date}|${f.site}|${f.minutes}"
    val have = existing.groupingBy(::k).eachCount().toMutableMap()
    val add = imported.filter { f ->
        val c = have[k(f)] ?: 0
        if (c > 0) { have[k(f)] = c - 1; false } else true
    }.map { it.copy(id = newId()) }
    return (existing + add) to add.size
}

/** Adaugă doar siturile care lipsesc (aceleași coordonate = deja există). */
fun mergeSites(existing: List<Site>, imported: List<Site>): Pair<List<Site>, Int> {
    val keys = existing.map { it.key }.toMutableSet()
    val add = imported.filter { keys.add(it.key) }
    return (existing + add) to add.size
}

fun importMessage(days: Int, sites: Int): String = when {
    days == 0 && sites == 0 -> "Nimic nou: totul exista deja."
    else -> "Am adăugat " + listOfNotNull(
        if (days > 0) (if (days == 1) "1 zi de zbor" else "$days zile de zbor") else null,
        if (sites > 0) (if (sites == 1) "1 sit" else "$sites situri") else null,
    ).joinToString(" și ") + "."
}
