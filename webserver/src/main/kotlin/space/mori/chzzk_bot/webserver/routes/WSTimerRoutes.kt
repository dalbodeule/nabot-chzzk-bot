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
import space.mori.chzzk_bot.common.services.TimerConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.utils.CurrentTimer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
fun Routing.wsTimerRoutes() {
    environment.monitor.subscribe(ApplicationStopped) {
        songListScope.cancel()
    }
    val sessions = ConcurrentHashMap<String, ConcurrentLinkedQueue<WebSocketServerSession>>()
    val logger = LoggerFactory.getLogger("WSTimerRoutes")
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    val ackMap = ConcurrentHashMap<String, ConcurrentHashMap<WebSocketServerSession, CompletableDeferred<Boolean>>>()

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
        message: TimerResponse,
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

    fun broadcastMessage(uid: String, message: TimerResponse) {
        val userSessions = sessions[uid]
        userSessions?.forEach { session ->
            songListScope.launch {
                val success = sendWithRetry(session, message.copy(uid = uid))
                if (!success) {
                    logger.info("Removing session for user $uid due to repeated failures.")
                    removeSession(uid, session)
                }
            }
        }
    }

    webSocket("/timer/{uid}") {
        val uid = call.parameters["uid"]
        val user = uid?.let { UserService.getUser(it) }
        if (uid == null || user == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid UID"))
            return@webSocket
        }
        addSession(uid, this)
        val timer = CurrentTimer.getTimer(user)

        if (timer?.type == TimerType.STREAM_OFF) {
            songListScope.launch {
                sendSerialized(TimerResponse(TimerType.STREAM_OFF.value, null, uid))
            }
        } else {
            songListScope.launch {
                if(timer?.type == TimerType.STREAM_OFF) {
                    sendSerialized(TimerResponse(TimerType.STREAM_OFF.value, null, uid))
                } else {
                    if (timer == null) {
                        sendSerialized(
                            TimerResponse(
                                TimerConfigService.getConfig(user)?.option ?: TimerType.REMOVE.value,
                                null,
                                uid
                            )
                        )
                    } else {
                        sendSerialized(
                            TimerResponse(
                                timer.type.value,
                                timer.time,
                                uid
                            )
                        )
                    }
                }
            }
        }
        try {
            for (frame in incoming) {
                when(frame) {
                    is Frame.Text -> {
                        val text = frame.readText().trim()
                        if(text == "ping") {
                            send("pong")
                        } else {
                            val data = Json.decodeFromString<TimerRequest>(text)
                            if (data.type == TimerType.ACK.value) {
                                ackMap[data.uid]?.get(this)?.complete(true)
                                ackMap[data.uid]?.remove(this)
                            }
                        }
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } catch(e: ClosedReceiveChannelException) {
            logger.error("Error in WebSocket: ${e.message}")
        } finally {
            removeSession(uid, this)
            ackMap[uid]?.remove(this)
        }
    }

    dispatcher.subscribe(TimerEvent::class) {
        logger.debug("TimerEvent: {} / {}", it.uid, it.type)
        val user = UserService.getUser(it.uid)
        CurrentTimer.setTimer(user!!, it)
        songListScope.launch {
            broadcastMessage(it.uid, TimerResponse(it.type.value, it.time ?: "", it.uid))
        }
    }
}
@Serializable
data class TimerResponse(
    val type: Int,
    val time: String?,
    val uid: String
)
@Serializable
data class TimerRequest(
    val type: Int,
    val uid: String
)