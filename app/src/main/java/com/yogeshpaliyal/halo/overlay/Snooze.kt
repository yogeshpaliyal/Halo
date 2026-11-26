package com.yogeshpaliyal.halo.overlay

import androidx.annotation.StringRes
import com.yogeshpaliyal.halo.R

/**
 * The times offered by the snooze button at the bottom of the dial, left to right.
 * [OFF] turns the bubble off until it is switched back on in the app.
 */
enum class SnoozeChoice(val minutes: Int, @StringRes val short: Int, @StringRes val long: Int) {
    MIN_1(1, R.string.snooze_1m_short, R.string.snooze_1m),
    MIN_5(5, R.string.snooze_5m_short, R.string.snooze_5m),
    HOUR_1(60, R.string.snooze_1h_short, R.string.snooze_1h),
    HOUR_8(8 * 60, R.string.snooze_8h_short, R.string.snooze_8h),
    OFF(0, R.string.snooze_off_short, R.string.snooze_off),
}
