package space.mori.chzzk_bot.common.events

data class UserRegisterEvent(
    val chzzkId: String
): Event {
    val TAG = javaClass.simpleName
}