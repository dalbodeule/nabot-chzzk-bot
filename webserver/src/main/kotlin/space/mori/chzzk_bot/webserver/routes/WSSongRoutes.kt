package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

fun Routing.wsSongRoutes() {
    val sessions = ConcurrentHashMap<String, ConcurrentLinkedQueue<WebSocketServerSession>>()
    val status = ConcurrentHashMap<String, SongType>()
    val logger = LoggerFactory.getLogger("WSSongRoutes")

    fun addSession(uid: String, session: WebSocketServerSession) {
        sessions.computeIfAbsent(uid) { ConcurrentLinkedQueue() }.add(session)
    }

    fun removeSession(uid: String, session: WebSocketServerSession) {
        sessions[uid]?.remove(session)
        if(sessions[uid]?.isEmpty() == true) {
            sessions.remove(uid)
        }
    }

    suspend fun sendWithRetry(
        session: WebSocketServerSession,
        message: SongResponse,
        maxRetries: Int = 3,
        delayMillis: Long = 2000L
    ): Boolean {
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                session.sendSerialized(message)  // 메시지 전송 시도
                return true  // 성공하면 true 반환
            } catch (e: Exception) {
                attempt++
                logger.info("Failed to send message on attempt $attempt. Retrying in $delayMillis ms.")
                e.printStackTrace()
                delay(delayMillis)  // 재시도 전 대기
            }
        }
        return false  // 재시도 실패 시 false 반환
    }

    fun broadcastMessage(userId: String, message: SongResponse) {
        val userSessions = sessions[userId]

        userSessions?.forEach { session ->
            CoroutineScope(Dispatchers.Default).launch {
                val success = sendWithRetry(session, message)
                if (!success) {
                    println("Removing session for user $userId due to repeated failures.")
                    userSessions.remove(session)  // 실패 시 세션 제거
                }
            }
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
                ))
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
        }
    }

    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    dispatcher.subscribe(SongEvent::class) {
        logger.debug("SongEvent: {} / {} {}", it.uid, it.type, it.current?.name)
        CoroutineScope(Dispatchers.Default).launch {
            broadcastMessage(it.uid, SongResponse(
                it.type.value,
                it.uid,
                it.reqUid,
                it.current?.toSerializable(),
                it.next?.toSerializable(),
                it.delUrl
            ))
        }
    }
    dispatcher.subscribe(TimerEvent::class) {
        if(it.type == TimerType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                broadcastMessage(it.uid, SongResponse(
                    it.type.value,
                    it.uid,
                    null,
                    null,
                    null,
                ))
            }
        }
    }
}

@Serializable
data class SerializableYoutubeVideo(
    val url: String,
    val name: String,
    val author: String,
    val length: Int
)

fun YoutubeVideo.toSerializable() = SerializableYoutubeVideo(url, name, author, length)

@Serializable
data class SongResponse(
    val type: Int,
    val uid: String,
    val reqUid: String?,
    val current: SerializableYoutubeVideo? = null,
    val next: SerializableYoutubeVideo? = null,
    val delUrl: String? = null
)
