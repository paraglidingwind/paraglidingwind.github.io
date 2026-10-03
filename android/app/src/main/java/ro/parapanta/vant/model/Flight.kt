package ro.parapanta.vant.model

import kotlinx.serialization.Serializable

/** O zi de zbor pe o locație. [date] = AAAA-LL-ZZ. Salvat doar pe telefon. Același format ca pe web. */
@Serializable
data class Flight(val id: String, val date: String, val site: String, val minutes: Int)

data class MonthGroup(val key: String, val flights: List<Flight>) {
    val minutes get() = flights.sumOf { it.minutes }
}

data class SiteTotal(val site: String, val days: Int, val minutes: Int, val last: String)

fun fmtDur(m: Int): String {
    val h = m / 60
    val r = m % 60
    return if (h > 0) (if (r > 0) "$h h $r min" else "$h h") else "$r min"
}

fun zile(n: Int) = if (n == 1) "1 zi" else "$n zile"

/** Zilele de zbor grupate pe luni, cele mai recente primele; opțional doar pentru o locație. */
fun byMonth(flights: List<Flight>, site: String? = null): List<MonthGroup> =
    flights.filter { site == null || it.site == site }
        .sortedWith(compareByDescending<Flight> { it.date }.thenByDescending { it.id })
        .groupBy { it.date.take(7) }
        .map { (k, v) -> MonthGroup(k, v) }

/** Totaluri pe locații, ordonate după timpul în aer. */
fun bySite(flights: List<Flight>): List<SiteTotal> =
    flights.groupBy { it.site }
        .map { (s, v) -> SiteTotal(s, v.size, v.sumOf { it.minutes }, v.maxOf { it.date }) }
        .sortedByDescending { it.minutes }

/** Mesajul de eroare pentru formular, sau null dacă datele sunt bune. Aceleași mesaje ca pe web. */
fun validateFlight(site: String, date: String?, h: Int, m: Int, today: String): String? = when {
    site.isBlank() -> "Alege sau scrie locația."
    date == null || !Regex("""\d{4}-\d{2}-\d{2}""").matches(date) -> "Alege data."
    date > today -> "Data nu poate fi în viitor."
    h < 0 || m < 0 || m > 59 -> "Minutele trebuie să fie între 0 și 59."
    h * 60 + m <= 0 -> "Scrie cât timp ai stat în aer."
    else -> null
}
