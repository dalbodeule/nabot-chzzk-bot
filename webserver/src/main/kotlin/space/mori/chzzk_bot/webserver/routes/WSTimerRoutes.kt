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
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.services.TimerConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.utils.CurrentTimer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

fun Routing.wsTimerRoutes() {
    val sessions = ConcurrentHashMap<String, ConcurrentLinkedQueue<WebSocketServerSession>>()
    val logger = LoggerFactory.getLogger("WSTimerRoutes")

    fun addSession(uid: String, session: WebSocketServerSession) {
        sessions.computeIfAbsent(uid) { ConcurrentLinkedQueue() }.add(session)
    }

    fun removeSession(uid: String, session: WebSocketServerSession) {
        sessions[uid]?.remove(session)
        if(sessions[uid]?.isEmpty() == true) {
            sessions.remove(uid)
        }
    }

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

        addSession(uid, this)
        val timer = CurrentTimer.getTimer(user)

        if(timer?.type == TimerType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                sendSerialized(TimerResponse(TimerType.STREAM_OFF.value, null))
            }
        } else {
            CoroutineScope(Dispatchers.Default).launch {
                if (timer == null) {
                    sendSerialized(
                        TimerResponse(
                            TimerConfigService.getConfig(user)?.option ?: TimerType.REMOVE.value,
                            null
                        )
                    )
                } else {
                    sendSerialized(
                        TimerResponse(
                            timer.type.value,
                            timer.time
                        )
                    )
                }
            }
        }

        try {
            for (frame in incoming) {
                when(frame) {
                    is Frame.Text -> {
                        if(frame.readText().trim() == "ping") {
                            send("pong")
                        }
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {

                    }
                }
            }
        } catch(e: ClosedReceiveChannelException) {
            logger.error("Error in WebSocket: ${e.message}")
            removeSession(uid, this)
        }
    }

    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    dispatcher.subscribe(TimerEvent::class) {
        logger.debug("TimerEvent: {} / {}", it.uid, it.type)
        val user = UserService.getUser(it.uid)
        CurrentTimer.setTimer(user!!, it)
        CoroutineScope(Dispatchers.Default).launch {
            sessions[it.uid]?.forEach { ws ->
                ws.sendSerialized(TimerResponse(it.type.value, it.time ?: ""))
            }
        }
    }
}

@Serializable
data class TimerResponse(
    val type: Int,
    val time: String?
)