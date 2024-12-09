package space.mori.chzzk_bot.common.events

data class BotEnabledEvent(
    val chzzkId: String,
    val isDisabled: Boolean,
): Event {
    val TAG = javaClass.simpleName
}