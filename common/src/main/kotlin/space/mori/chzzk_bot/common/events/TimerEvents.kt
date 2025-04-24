package space.mori.chzzk_bot.common.events

enum class TimerType(var value: Int) {
    UPTIME(0),
    TIMER(1),
    REMOVE(2),

    STREAM_OFF(50),
    ACK(51)
}

class TimerEvent(
    val uid: String,
    val type: TimerType,
    val time: String?
): Event {
    var TAG = javaClass.simpleName
}