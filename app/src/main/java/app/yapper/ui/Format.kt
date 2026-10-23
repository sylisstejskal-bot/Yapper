package app.yapper.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("h:mm a")
private val dayFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy")
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

fun formatClock(epoch: Long): String {
    return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(clockFormat)
}

fun formatDayLabel(epoch: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormat)
    }
}

fun formatMonth(epoch: Long): String {
    return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(monthFormat)
}

fun sameDay(a: Long, b: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val left = Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
    val right = Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
    return left == right
}
