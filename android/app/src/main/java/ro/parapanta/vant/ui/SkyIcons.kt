package ro.parapanta.vant.ui

// Generat de build.py din data/sky_icons.json. Nu edita manual.
data class IconPart(val d: String, val role: String, val fill: Boolean)

val SKY_ICONS: Map<String, List<IconPart>> = mapOf(
    "sun" to listOf(IconPart("M12 7.5 A4.5 4.5 0 1 0 12 16.5 A4.5 4.5 0 1 0 12 7.5 Z", "sun", true), IconPart("M12 2 V4.2 M12 19.8 V22 M2 12 H4.2 M19.8 12 H22 M4.9 4.9 L6.5 6.5 M17.5 17.5 L19.1 19.1 M4.9 19.1 L6.5 17.5 M17.5 6.5 L19.1 4.9", "sun", false)),
    "moon" to listOf(IconPart("M19.5 14.6 A7.8 7.8 0 1 1 9.4 4.5 A6.2 6.2 0 0 0 19.5 14.6 Z", "ink", false)),
    "partly" to listOf(IconPart("M6.03 10.77 A3.5 3.5 0 1 1 12 8.3", "sun", false), IconPart("M8.5 2.6 V3.9 M2.6 8.3 H3.9 M4.3 4.1 L5.2 5 M12.7 4.1 L11.8 5", "sun", false), IconPart("M9.5 20 H17.7 A3.4 3.4 0 0 0 18.2 13.23 A4.6 4.6 0 0 0 9.3 12.3 A3.85 3.85 0 0 0 9.5 20 Z", "ink", false)),
    "cloud" to listOf(IconPart("M7 19 H17.2 A4.3 4.3 0 0 0 17.8 10.44 A5.8 5.8 0 0 0 6.6 9.9 A4.6 4.6 0 0 0 7 19 Z", "ink", false)),
    "overcast" to listOf(IconPart("M5.2 14.2 A3.6 3.6 0 0 1 6.4 7.3 A5 5 0 0 1 15.6 6.4", "ink", false), IconPart("M8 20.5 H17.5 A3.6 3.6 0 0 0 18 13.33 A5 5 0 0 0 8.2 12.4 A4.05 4.05 0 0 0 8 20.5 Z", "ink", false)),
    "cumulus" to listOf(IconPart("M3.5 19 H20.5", "ink", false), IconPart("M5.5 19 A3 3 0 0 1 6.5 13.2 A3.6 3.6 0 0 1 11.5 8.5 A4 4 0 0 1 18 12 A3.6 3.6 0 0 1 18.5 19", "ink", false), IconPart("M20 3.6 A1.9 1.9 0 1 0 20 7.4 A1.9 1.9 0 1 0 20 3.6 Z", "sun", true)),
    "cirrus" to listOf(IconPart("M2.5 11 C6.5 8 10 13 15 10 M4.5 15.5 C8.5 13 12.5 17.5 20.5 14 M2.5 20 C5.5 18.5 8.5 21 12.5 19.5", "ink", false), IconPart("M18 3.6 A2.3 2.3 0 1 0 18 8.2 A2.3 2.3 0 1 0 18 3.6 Z", "sun", true)),
    "fog" to listOf(IconPart("M4 7 H20 M6 11 H18 M4 15 H20 M7 19 H17", "ink", false)),
    "rain" to listOf(IconPart("M7 15 H17.2 A4.3 4.3 0 0 0 17.8 6.44 A5.8 5.8 0 0 0 6.6 5.9 A4.6 4.6 0 0 0 7 15 Z", "ink", false), IconPart("M8.5 18 L7.5 21 M12.5 18 L11.5 21 M16.5 18 L15.5 21", "water", false)),
    "thunder" to listOf(IconPart("M7 15 H17.2 A4.3 4.3 0 0 0 17.8 6.44 A5.8 5.8 0 0 0 6.6 5.9 A4.6 4.6 0 0 0 7 15 Z", "ink", false), IconPart("M13 14.5 L10.5 18.5 H13.5 L11.5 22.5", "sun", false)),
    "snow" to listOf(IconPart("M7 15 H17.2 A4.3 4.3 0 0 0 17.8 6.44 A5.8 5.8 0 0 0 6.6 5.9 A4.6 4.6 0 0 0 7 15 Z", "ink", false), IconPart("M8 18.5 V18.6 M12 19.5 V19.6 M16 18.5 V18.6 M10 22 V22.1 M14 22 V22.1", "water", false)),
)
