package com.yogeshpaliyal.halo.data

/** What a single tap on the bubble does. */
enum class TapAction(val label: String, val description: String) {
    PREVIOUS_APP("Previous app", "Jump back to the app you used last"),
    OPEN_MENU("Open menu", "Show the menu, then tap an app"),
    NOTHING("Do nothing", "Ignore taps"),
}

/** How long you hold the bubble before the menu opens. */
enum class HoldDelay(val label: String, val millis: Long) {
    SHORT("Short", 200),
    MEDIUM("Medium", 280),
    LONG("Long", 400),
}
