package space.mori.chzzk_bot.common.events

enum class GetUserType(var value: Int) {
    REQUEST(0),
    RESPONSE(1),
    NOTFOUND(2)
}

class GetUserEvents(
    val type: GetUserType,
    val uid: String?,
    val nickname: String?,
    val isStreamOn: Boolean?,
    val avatarUrl: String?,
): Event {
    var TAG = javaClass.simpleName
}