package ro.parapanta.vant.model

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
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
    val ref = "ABCD"[i] + r.toString()
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

fun xlsxParts(list: List<Flight>): List<Pair<String, String>> {
    fun h(s: String) = Str(s, 1)
    val sorted = list.sortedWith(compareBy({ it.date }, { it.id }))
    val n = sorted.size
    val total = sorted.sumOf { it.minutes }
    val s1 = listOf(listOf<Cell>(h("Data"), h("Locație"), h("Timp în aer"), h("Minute"))) +
        sorted.map { listOf(Num(excelDate(it.date).toString(), 2), Str(it.site), Num(days(it.minutes), 3), Num(it.minutes.toString())) } +
        listOf(listOf(h("Total"), Str(""), Formula("SUM(C2:C${n + 1})", days(total), 4), Formula("SUM(D2:D${n + 1})", total.toString(), 1)))
    val s2 = listOf(listOf<Cell>(h("Locație"), h("Zile de zbor"), h("Timp în aer"), h("Ultima zi"))) +
        bySite(list).map { listOf(Str(it.site), Num(it.days.toString()), Num(days(it.minutes), 3), Num(excelDate(it.last).toString(), 2)) }
    val ns = "http://schemas.openxmlformats.org"
    val x = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    val ws = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    return listOf(
        "[Content_Types].xml" to x + "<Types xmlns=\"$ns/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"$ws\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"$ws\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>",
        "_rels/.rels" to x + "<Relationships xmlns=\"$ns/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$ns/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
        "xl/workbook.xml" to x + "<workbook xmlns=\"$ns/spreadsheetml/2006/main\" xmlns:r=\"$ns/officeDocument/2006/relationships\"><sheets><sheet name=\"Zile de zbor\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Pe locații\" sheetId=\"2\" r:id=\"rId2\"/></sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>",
        "xl/_rels/workbook.xml.rels" to x + "<Relationships xmlns=\"$ns/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$ns/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"$ns/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"$ns/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>",
        "xl/styles.xml" to x + "<styleSheet xmlns=\"$ns/spreadsheetml/2006/main\"><numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"dd.mm.yyyy\"/><numFmt numFmtId=\"165\" formatCode=\"[h]:mm\"/></numFmts><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"5\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>",
        "xl/worksheets/sheet1.xml" to sheet(listOf(12, 26, 12, 10), s1),
        "xl/worksheets/sheet2.xml" to sheet(listOf(26, 13, 12, 12), s2),
    )
}

fun flightsXlsx(list: List<Flight>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        xlsxParts(list).forEach { (name, xml) ->
            z.putNextEntry(ZipEntry(name))
            z.write(xml.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
    }
    return out.toByteArray()
}
