package space.mori.chzzk_bot.common.events

data class ChzzkUserReceiveEvent(
    val find: Boolean = true,
    val uid: String? = null,
    val nickname: String? = null,
    val isStreamOn: Boolean? = null,
    val avatarUrl: String? = null,
): Event {
    val TAG = javaClass.simpleName
}