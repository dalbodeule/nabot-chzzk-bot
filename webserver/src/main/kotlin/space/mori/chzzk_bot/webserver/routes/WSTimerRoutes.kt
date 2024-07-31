package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.events.TimerType
import space.mori.chzzk_bot.common.services.UserService
import java.util.concurrent.ConcurrentHashMap

val logger = LoggerFactory.getLogger("WSTimerRoutes")

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

    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    dispatcher.subscribe(TimerEvent::class) {
        logger.debug("TimerEvent: {} / {}", it.uid, it.type)
        CoroutineScope(Dispatchers.Default).launch {
            sessions[it.uid]?.sendSerialized(TimerResponse(it.type, it.time ?: ""))
        }
    }
}

@Serializable
data class TimerResponse(
    val type: TimerType,
    val time: String?
)