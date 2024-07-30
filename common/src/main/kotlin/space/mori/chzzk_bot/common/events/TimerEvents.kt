package space.mori.chzzk_bot.common.events

enum class TimerType {
    UPTIME, TIMER, REMOVE
}

class TimerEvent(
    val uid: String,
    val type: TimerType,
    val time: String?
): Event