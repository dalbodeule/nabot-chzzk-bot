package space.mori.chzzk_bot.common.events

data class ChzzkUserFindEvent(
    val uid: String
): Event {
    val TAG = javaClass.simpleName
}