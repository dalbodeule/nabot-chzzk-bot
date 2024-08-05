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
import space.mori.chzzk_bot.common.services.UserService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

fun Routing.wsSongRoutes() {
    val sessions = ConcurrentHashMap<String, ConcurrentLinkedQueue<WebSocketServerSession>>()
    val status = ConcurrentHashMap<String, SongType>()
    val logger = LoggerFactory.getLogger(this.javaClass.name)

    fun addSession(uid: String, session: WebSocketServerSession) {
        sessions.computeIfAbsent(uid) { ConcurrentLinkedQueue() }.add(session)
    }

    fun removeSession(uid: String, session: WebSocketServerSession) {
        sessions[uid]?.remove(session)
        if(sessions[uid]?.isEmpty() == true) {
            sessions.remove(uid)
        }
    }

    webSocket("/song/{uid}") {
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

        if(status[uid] == SongType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                sendSerialized(SongResponse(
                    SongType.STREAM_OFF.value,
                    uid,
                    null,
                    null,
                    null,
                    null
                ))
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
        } catch(e: ClosedReceiveChannelException) {
            logger.error("Error in WebSocket: ${e.message}")
        } finally {
            removeSession(uid, this)
        }
    }

    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    dispatcher.subscribe(SongEvent::class) {
        logger.debug("SongEvent: {} / {} {}", it.uid, it.type, it.name)
        CoroutineScope(Dispatchers.Default).launch {
            sessions[it.uid]?.forEach { ws ->
                ws.sendSerialized(SongResponse(
                    it.type.value,
                    it.uid,
                    it.reqUid,
                    it.name,
                    it.author,
                    it.time
                ))
            }
        }
    }
    dispatcher.subscribe(TimerEvent::class) {
        if(it.type == TimerType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                sessions[it.uid]?.forEach { ws ->
                    ws.sendSerialized(SongResponse(
                        it.type.value,
                        it.uid,
                        null,
                        null,
                        null,
                        null,
                    ))
                }
            }
        }
    }
}

@Serializable
data class SongResponse(
    val type: Int,
    val uid: String,
    val reqUid: String?,
    val name: String?,
    val author: String?,
    val time: Int?
)
