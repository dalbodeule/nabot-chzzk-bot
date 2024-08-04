package space.mori.chzzk_bot.common.events

enum class SongType(var value: Int) {
    ADD(0),
    REMOVE(1),
    NEXT(2),

    STREAM_OFF(50)
}

class SongEvent(
    val uid: String,
    val type: SongType,
    val req_uid: String?,
    val name: String?,
    val author: String?,
    val time: Int?,
): Event {
    var TAG = javaClass.simpleName
}