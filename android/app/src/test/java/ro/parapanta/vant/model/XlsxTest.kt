package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

class XlsxTest {
    private val sites = listOf(
        Site("Liteni", 46.6129, 23.435, 895, listOf(0, 1, 2, 2, 1, 0, 0, 0)),
        Site("Făureni", 46.9301, 23.5512, null, listOf(0, 0, 0, 0, 2, 2, 1, 0), hf = 0),
        Site("Rimetea E", 46.4543, 23.5373, 1176, listOf(0, 1, 2, 2, 1, 0, 0, 0), hf = 243),
    )
    private val fl = listOf(
        Flight("a1", "2026-10-03", "Liteni", 95), Flight("a2", "2026-09-27", "Rimetea E", 40),
        Flight("a3", "2026-09-14", "Liteni", 180), Flight("a4", "2026-08-30", "Agriș S", 25),
    )

    @Test fun dates() {
        assertEquals(36526L, excelDate("2000-01-01"))
        assertEquals(46298L, excelDate("2026-10-03"))
    }

    @Test fun validZip() {
        val bytes = flightsXlsx(fl, sites)
        val names = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { names += it.name } }
        assertEquals(8, names.size)
        assertTrue("xl/worksheets/sheet1.xml" in names)
        // Fișierul pentru comparația cu exportul de pe web (vezi verificarea din proiect).
        File("build/xlsx-test.xlsx").writeBytes(bytes)
    }

    @Test fun content() {
        val s1 = xlsxParts(fl, sites).first { it.first == "xl/worksheets/sheet1.xml" }.second
        assertTrue(s1.contains("<c r=\"A2\" s=\"2\"><v>46264</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>Agriș S</t></is></c>"))
        assertTrue(s1.contains("<f>SUM(D2:D5)</f><v>340</v>"))
        val s2 = xlsxParts(fl, sites).first { it.first == "xl/worksheets/sheet2.xml" }.second
        assertTrue(s2.contains("<t>Liteni</t></is></c><c r=\"B2\"><v>2</v></c>"))
    }

    @Test fun roundTrip() {
        val back = readXlsx(flightsXlsx(fl, sites))!!
        assertEquals(fl.map { Triple(it.date, it.site, it.minutes) }.sortedBy { it.first }, back.flights.map { Triple(it.date, it.site, it.minutes) })
        assertEquals(sites, back.sites)
    }

    /** Un fișier cum îl salvează Excel: șiruri în sharedStrings, rând gol, dată scrisă ca text, durată fără minute. */
    @Test fun excelStyleFile() {
        val ns = "http://schemas.openxmlformats.org"
        val files = mapOf(
            "xl/workbook.xml" to "<workbook xmlns:r=\"$ns/officeDocument/2006/relationships\"><sheets><sheet name=\"Zile de zbor\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships><Relationship Id=\"rId1\" Type=\"x\" Target=\"/xl/worksheets/sheet1.xml\"/></Relationships>",
            "xl/sharedStrings.xml" to "<sst><si><t>Data</t></si><si><t xml:space=\"preserve\">Liteni </t></si><si><r><t>Agri</t></r><r><t>ș &amp; co</t></r></si><si><t>2.9.2026</t></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet><sheetData>" +
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>" +
                "<row r=\"2\" spans=\"1:4\"><c r=\"A2\" s=\"2\"><v>46298</v></c><c r=\"B2\" t=\"s\"><v>1</v></c><c r=\"C2\" s=\"3\"><v>6.5972222222222224E-2</v></c><c r=\"D2\"><v>95</v></c></row>" +
                "<row r=\"3\" spans=\"1:4\"/>" +
                "<row r=\"4\"><c r=\"A4\" t=\"s\"><v>3</v></c><c r=\"B4\" t=\"s\"><v>2</v></c><c r=\"C4\" t=\"str\"><v>1:20</v></c></row>" +
                "</sheetData></worksheet>",
        )
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> files.forEach { (n, x) -> z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(x.toByteArray()); z.closeEntry() } }
        val r = readXlsx(out.toByteArray())!!
        assertEquals(listOf(Flight("", "2026-10-03", "Liteni", 95), Flight("", "2026-09-02", "Agriș & co", 80)), r.flights)
        assertEquals(emptyList<Site>(), r.sites)
    }

    @Test fun notAnXlsx() = assertEquals(null, readXlsx("nu e un fișier excel".toByteArray()))

    @Test fun mergeSkipsDuplicates() {
        var n = 0
        val existing = listOf(Flight("x", "2026-10-03", "Liteni", 95))
        val imported = listOf(Flight("", "2026-10-03", "Liteni", 95), Flight("", "2026-10-03", "Liteni", 95), Flight("", "2026-09-14", "Liteni", 180))
        val (all, added) = mergeFlights(existing, imported) { "id${n++}" }
        assertEquals(2, added)
        assertEquals(3, all.size)
        val (again, added2) = mergeFlights(all, imported) { "id${n++}" }
        assertEquals(0, added2); assertEquals(3, again.size)
        assertEquals(1, mergeSites(sites.take(2), sites).second)
        assertEquals("Am adăugat 2 zile de zbor și 1 sit.", importMessage(2, 1))
        assertEquals("Nimic nou: totul exista deja.", importMessage(0, 0))
    }

    /** Exportul nostru deschis și re-salvat de altă aplicație (openpyxl): XML rescris complet, comprimat. */
    @Test fun resavedFile() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("resaved.xlsx")!!.readBytes()
        val r = readXlsx(bytes)!!
        assertEquals(fl.map { Triple(it.date, it.site, it.minutes) }.sortedBy { it.first }, r.flights.map { Triple(it.date, it.site, it.minutes) })
        assertEquals(sites, r.sites)
    }
}
