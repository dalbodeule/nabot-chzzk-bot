package space.mori.chzzk_bot.common.utils

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun getUptime(streamOnTime: LocalDateTime): String {
    val currentTime = LocalDateTime.now()

    val hours = ChronoUnit.HOURS.between(streamOnTime, currentTime)
    val minutes = ChronoUnit.MINUTES.between(streamOnTime?.plusHours(hours), currentTime)
    val seconds = ChronoUnit.SECONDS.between(streamOnTime?.plusHours(hours)?.plusMinutes(minutes), currentTime)

    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}