package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import space.mori.chzzk_bot.common.events.Event
import space.mori.chzzk_bot.common.events.TimerType
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.events.EventDispatcher
import space.mori.chzzk_bot.common.events.EventHandler
import java.util.concurrent.ConcurrentHashMap

fun Routing.wsTimerRoutes() {
    val sessions = ConcurrentHashMap<String, WebSocketServerSession>()

    webSocket("/timer/{uid}") {
        val uid = call.parameters["uid"]
        val user = uid?.let { UserService.getUser(it) }
        if (uid == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid UID"))
            return@webSocket
        }
        if (user == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid UID"))
            return@webSocket
        }

        sessions[uid] = this

        try {
            for (frame in incoming) {
                when(frame) {
                    is Frame.Text -> {

                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {

                    }
                }
            }
        } catch(_: ClosedReceiveChannelException) {

        } finally {
            sessions.remove(uid)
        }
    }

    run {
        val dispatcher = EventDispatcher

        dispatcher.register(TimerEvent::class.java, object : EventHandler<TimerEvent> {
            override fun handle(event: TimerEvent) {
                CoroutineScope(Dispatchers.IO).launch {
                    sessions[event.uid]?.sendSerialized(TimerResponse(event.type, event.time ?: ""))
                }
            }
        })
    }
}

enum class TimerType {
    UPTIME, TIMER
}

class TimerEvent(
    val uid: String,
    val type: TimerType,
    val time: String?
): Event

@Serializable
data class TimerResponse(
    val type: TimerType,
    val time: String?
)