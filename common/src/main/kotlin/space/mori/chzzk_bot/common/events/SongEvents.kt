package space.mori.chzzk_bot.common.events

import space.mori.chzzk_bot.common.utils.YoutubeVideo

enum class SongType(var value: Int) {
    ADD(0),
    REMOVE(1),
    NEXT(2),

    STREAM_OFF(50)
}

class SongEvent(
    val uid: String,
    val type: SongType,
    val reqUid: String?,
    val current: YoutubeVideo? = null,
    val next: YoutubeVideo? = null,
    val delUrl: String? = null,
): Event {
    var TAG = javaClass.simpleName
}