package space.mori.chzzk_bot.common.events

data class CommandReloadEvent(
    val uid: String
): Event {
    val TAG = javaClass.simpleName
}