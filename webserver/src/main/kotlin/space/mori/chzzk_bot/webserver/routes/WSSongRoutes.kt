package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

val songScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
fun Routing.wsSongRoutes() {
    environment.monitor.subscribe(ApplicationStopped) {
        songScope.cancel()
    }
    val sessions = ConcurrentHashMap<String, ConcurrentLinkedQueue<WebSocketServerSession>>()
    val status = ConcurrentHashMap<String, SongType>()
    val logger = LoggerFactory.getLogger("WSSongRoutes")
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    val ackMap = ConcurrentHashMap<String, ConcurrentHashMap<WebSocketServerSession, CompletableDeferred<Boolean>>>()

    fun addSession(uid: String, session: WebSocketServerSession) {
        sessions.computeIfAbsent(uid) { ConcurrentLinkedQueue() }.add(session)
    }

    fun removeSession(uid: String, session: WebSocketServerSession) {
        sessions[uid]?.remove(session)
        if (sessions[uid]?.isEmpty() == true) {
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
                session.sendSerialized(message)
                val ackDeferred = CompletableDeferred<Boolean>()
                ackMap.computeIfAbsent(message.uid) { ConcurrentHashMap() }[session] = ackDeferred
                val ackReceived = withTimeoutOrNull(delayMillis) { ackDeferred.await() } ?: false
                if (ackReceived) {
                    ackMap[message.uid]?.remove(session)
                    return true
                } else {
                    attempt++
                    logger.warn("ACK not received for message to ${message.uid} on attempt $attempt.")
                }
            } catch (e: Exception) {
                attempt++
                logger.info("Failed to send message on attempt $attempt. Retrying in $delayMillis ms.")
                e.printStackTrace()
                delay(delayMillis)
            }
        }
        return false
    }

    fun broadcastMessage(userId: String, message: SongResponse) {
        val userSessions = sessions[userId]
        userSessions?.forEach { session ->
            songScope.launch {
                val success = sendWithRetry(session, message)
                if (!success) {
                    logger.info("Removing session for user $userId due to repeated failures.")
                    removeSession(userId, session)
                }
            }
        }
    }

    webSocket("/song/{uid}") {
        logger.info("WebSocket connection attempt received")
        val uid = call.parameters["uid"]
        val user = uid?.let { UserService.getUser(it) }
        if (uid == null || user == null) {
            logger.warn("Invalid UID: $uid")
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid UID"))
            return@webSocket
        }
        try {
            addSession(uid, this)
            logger.info("WebSocket connection established for user: $uid")

            // Start heartbeat
            val heartbeatJob = songScope.launch {
                while (true) {
                    try {
                        send(Frame.Ping(ByteArray(0)))
                        delay(30000) // 30 seconds
                    } catch (e: Exception) {
                        logger.error("Heartbeat failed for user $uid", e)
                        break
                    }
                }
            }

            if (status[uid] == SongType.STREAM_OFF) {
                songScope.launch {
                    sendSerialized(
                        SongResponse(
                            SongType.STREAM_OFF.value,
                            uid,
                            null,
                            null,
                            null,
                        )
                    )
                }
            }
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText().trim()
                            if (text == "ping") {
                                send("pong")
                            } else {
                                val data = Json.decodeFromString<SongRequest>(text)
                                if (data.type == SongType.ACK.value) {
                                    ackMap[data.uid]?.get(this)?.complete(true)
                                    ackMap[data.uid]?.remove(this)
                                }
                            }
                        }

                        is Frame.Ping -> send(Frame.Pong(frame.data))
                        else -> {}
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                logger.error("WebSocket connection closed for user $uid: ${e.message}")
            } catch (e: Exception) {
                logger.error("Unexpected error in WebSocket for user $uid", e)
            } finally {
                logger.info("Cleaning up WebSocket connection for user $uid")
                removeSession(uid, this)
                ackMap[uid]?.remove(this)
                heartbeatJob.cancel()
            }
        } catch(e: Exception) {
            logger.error("Unexpected error in WebSocket for user $uid", e)
        }
    }

    dispatcher.subscribe(SongEvent::class) {
        logger.debug("SongEvent: {} / {} {}", it.uid, it.type, it.current?.name)
        songScope.launch {
            broadcastMessage(
                it.uid, SongResponse(
                    it.type.value,
                    it.uid,
                    it.reqUid,
                    it.current?.toSerializable(),
                    it.next?.toSerializable(),
                    it.delUrl
                )
            )
        }
    }
    dispatcher.subscribe(TimerEvent::class) {
        if (it.type == TimerType.STREAM_OFF) {
            songScope.launch {
                broadcastMessage(
                    it.uid, SongResponse(
                        it.type.value,
                        it.uid,
                        null,
                        null,
                        null,
                    )
                )
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
