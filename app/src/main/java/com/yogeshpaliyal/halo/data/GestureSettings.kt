package com.yogeshpaliyal.halo.data

import androidx.annotation.StringRes
import com.yogeshpaliyal.halo.R

/** What a tap or double tap on the bubble does. */
enum class TapAction(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    PREVIOUS_APP(R.string.tap_previous, R.string.tap_previous_desc),
    SECOND_APP(R.string.tap_second, R.string.tap_second_desc),
    OPEN_MENU(R.string.tap_menu, R.string.tap_menu_desc),
    NOTHING(R.string.tap_nothing, R.string.tap_nothing_desc),
}

/** How long you hold the bubble before the menu opens. */
enum class HoldDelay(@StringRes val labelRes: Int, val millis: Long) {
    SHORT(R.string.delay_short, 200),
    MEDIUM(R.string.delay_medium, 280),
    LONG(R.string.delay_long, 400),
}

/** Size of the resting bubble. */
enum class BubbleSize(@StringRes val labelRes: Int, val dp: Int) {
    SMALL(R.string.size_small, 44),
    MEDIUM(R.string.size_medium, 52),
    LARGE(R.string.size_large, 62),
}
