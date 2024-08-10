package space.mori.chzzk_bot.common.events

class DiscordRegisterEvent(
    val user: String,
    val token: String,
): Event {
    val TAG = javaClass.simpleName
}