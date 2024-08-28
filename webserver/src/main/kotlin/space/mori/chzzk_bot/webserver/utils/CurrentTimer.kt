package space.mori.chzzk_bot.webserver.utils

import space.mori.chzzk_bot.common.events.TimerEvent
import space.mori.chzzk_bot.common.models.User
import java.util.concurrent.ConcurrentHashMap

object CurrentTimer {
    private val currentTimer = ConcurrentHashMap<String, TimerEvent>()

    fun setTimer(user: User, timer: TimerEvent?) {
        if(timer == null) {
            currentTimer.remove(user.token ?: "")
        } else {
            currentTimer[user.token ?: ""] = timer
        }
    }

    fun getTimer(user: User) = currentTimer[user.token ?: ""]
}