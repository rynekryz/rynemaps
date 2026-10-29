package com.rynekryz.rynemaps.util

import android.location.Location
import com.rynekryz.rynemaps.data.GeoPoint
import java.util.Locale
import kotlin.math.roundToInt

fun Location.toGeo() = GeoPoint(latitude, longitude)

private val imperialCountries = setOf("US", "GB", "LR", "MM")

fun formatDistance(meters: Double): String {
    val locale = Locale.getDefault()
    return if (locale.country in imperialCountries) {
        val feet = meters * 3.28084
        if (feet < 500) "${(feet / 10).roundToInt() * 10} ft"
        else String.format(locale, "%.1f mi", meters / 1609.344)
    } else {
        if (meters < 1000) "${(meters / 10).roundToInt() * 10} m"
        else String.format(locale, "%.1f km", meters / 1000)
    }
}

fun formatDuration(seconds: Double): String {
    val minutes = maxOf(1, (seconds / 60).roundToInt())
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h hr" else "$h hr $m min"
}
