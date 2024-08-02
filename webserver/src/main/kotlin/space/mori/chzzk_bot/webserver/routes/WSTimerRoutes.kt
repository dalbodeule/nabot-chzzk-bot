package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.services.UserService
import java.util.concurrent.ConcurrentHashMap

val logger: Logger = LoggerFactory.getLogger("WSTimerRoutes")

fun Routing.wsTimerRoutes() {
    val sessions = ConcurrentHashMap<String, WebSocketServerSession>()
    val status = ConcurrentHashMap<String, TimerType>()

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
        if(status[uid] == TimerType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                sendSerialized(TimerResponse(TimerType.STREAM_OFF.value, ""))
            }
        }

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
        status[it.uid] = it.type
        CoroutineScope(Dispatchers.Default).launch {
            sessions[it.uid]?.sendSerialized(TimerResponse(it.type.value, it.time ?: ""))
        }
    }
}

@Serializable
data class TimerResponse(
    val type: Int,
    val time: String?
)