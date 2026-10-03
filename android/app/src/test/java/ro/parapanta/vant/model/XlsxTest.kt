package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

class XlsxTest {
    private val fl = listOf(
        Flight("a1", "2026-10-03", "Liteni", 95), Flight("a2", "2026-09-27", "Rimetea E", 40),
        Flight("a3", "2026-09-14", "Liteni", 180), Flight("a4", "2026-08-30", "Agriș S", 25),
    )

    @Test fun dates() {
        assertEquals(36526L, excelDate("2000-01-01"))
        assertEquals(46298L, excelDate("2026-10-03"))
    }

    @Test fun validZip() {
        val bytes = flightsXlsx(fl)
        val names = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { names += it.name } }
        assertEquals(7, names.size)
        assertTrue("xl/worksheets/sheet1.xml" in names)
        // Fișierul pentru comparația cu exportul de pe web (vezi verificarea din proiect).
        File("build/xlsx-test.xlsx").writeBytes(bytes)
    }

    @Test fun content() {
        val s1 = xlsxParts(fl).first { it.first == "xl/worksheets/sheet1.xml" }.second
        assertTrue(s1.contains("<c r=\"A2\" s=\"2\"><v>46264</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>Agriș S</t></is></c>"))
        assertTrue(s1.contains("<f>SUM(D2:D5)</f><v>340</v>"))
        val s2 = xlsxParts(fl).first { it.first == "xl/worksheets/sheet2.xml" }.second
        assertTrue(s2.contains("<t>Liteni</t></is></c><c r=\"B2\"><v>2</v></c>"))
    }
}
