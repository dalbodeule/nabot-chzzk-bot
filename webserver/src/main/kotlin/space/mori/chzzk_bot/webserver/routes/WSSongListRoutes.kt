package space.mori.chzzk_bot.webserver.routes

import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.models.SongList
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.SongListService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import space.mori.chzzk_bot.common.utils.getYoutubeVideo
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.CurrentSong
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

fun Routing.wsSongListRoutes() {
    val logger = LoggerFactory.getLogger("WSSongListRoutes")
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    val songListScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Manage all active sessions
    val sessionHandlers = ConcurrentHashMap<String, SessionHandler>()

    // Handle application shutdown
    environment.monitor.subscribe(ApplicationStopped) {
        sessionHandlers.values.forEach {
            songListScope.launch {
                it.close(CloseReason(CloseReason.Codes.NORMAL, "Server shutting down"))
            }
        }
    }

    // WebSocket endpoint
    webSocket("/songlist") {
        val session = call.sessions.get<UserSession>()
        val user: User? = session?.id?.let { UserService.getUser(it) }

        if (user == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid SID"))
            return@webSocket
        }

        val uid = user.token

        // Ensure only one session per user
        sessionHandlers[uid]?.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Another session is already active."))

        val handler = SessionHandler(uid, this, dispatcher, logger)
        sessionHandlers[uid] = handler

        // Initialize session
        handler.initialize()

        // Listen for incoming frames
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> handler.handleTextFrame(frame.readText())
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> Unit
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.info("Session closed: ${e.message}")
        } catch (e: IOException) {
            logger.error("IO error: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error: ${e.message}")
        } finally {
            sessionHandlers.remove(uid)
            handler.close(CloseReason(CloseReason.Codes.NORMAL, "Session ended"))
        }
    }

    // Subscribe to SongEvents
    dispatcher.subscribe(SongEvent::class) { event ->
        val handler = sessionHandlers[event.uid]
        songListScope.launch {
            handler?.sendSongResponse(event)
        }
    }

    // Subscribe to TimerEvents
    dispatcher.subscribe(TimerEvent::class) { event ->
        if (event.type == TimerType.STREAM_OFF) {
            val handler = sessionHandlers[event.uid]
            songListScope.launch {
                handler?.sendTimerOff()
            }
        }
    }
}

class SessionHandler(
    private val uid: String,
    private val session: WebSocketServerSession,
    private val dispatcher: CoroutinesEventBus,
    private val logger: Logger
) {
    private val ackMap = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val sessionMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun initialize() {
        // Send initial status if needed,
        // For example, send STREAM_OFF if applicable
        // This can be extended based on your requirements
    }

    suspend fun handleTextFrame(text: String) {
        if (text.trim() == "ping") {
            session.send("pong")
            return
        }

        val data = try {
            Json.decodeFromString<SongRequest>(text)
        } catch (e: Exception) {
            logger.warn("Failed to decode SongRequest: ${e.message}")
            return
        }

        when (data.type) {
            SongType.ACK.value -> handleAck(data.uid)
            else -> handleSongRequest(data)
        }
    }

    private fun handleAck(requestUid: String) {
        ackMap[requestUid]?.complete(true)
        ackMap.remove(requestUid)
    }

    private fun handleSongRequest(data: SongRequest) {
        scope.launch {
            SongRequestProcessor.process(data, uid, dispatcher, this@SessionHandler, logger)
        }
    }

    suspend fun sendSongResponse(event: SongEvent) {
        val response = SongResponse(
            type = event.type.value,
            uid = event.uid,
            reqUid = event.reqUid,
            current = event.current?.toSerializable(),
            next = event.next?.toSerializable(),
            delUrl = event.delUrl
        )
        sendWithRetry(response)
    }

    suspend fun sendTimerOff() {
        val response = SongResponse(
            type = TimerType.STREAM_OFF.value,
            uid = uid,
            reqUid = null,
            current = null,
            next = null,
            delUrl = null
        )
        sendWithRetry(response)
    }

    private suspend fun sendWithRetry(res: SongResponse, maxRetries: Int = 5, delayMillis: Long = 3000L) {
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                session.sendSerialized(res)
                val ackDeferred = CompletableDeferred<Boolean>()
                ackMap[res.uid] = ackDeferred

                val ackReceived = withTimeoutOrNull(5000L) { ackDeferred.await() } ?: false
                if (ackReceived) {
                    logger.debug("ACK received for message to $uid on attempt $attempt.")
                    return
                } else {
                    logger.warn("ACK not received for message to $uid on attempt $attempt.")
                }
            } catch (e: IOException) {
                logger.warn("Failed to send message to $uid on attempt $attempt: ${e.message}")
                if (e is WebSocketException) {
                    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "WebSocket error"))
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Unexpected error while sending message to $uid on attempt $attempt: ${e.message}")
            }

            attempt++
            delay(delayMillis)
        }

        logger.error("Failed to send message to $uid after $maxRetries attempts.")
    }

    suspend fun close(reason: CloseReason) {
        try {
            session.close(reason)
        } catch (e: Exception) {
            logger.warn("Error closing session: ${e.message}")
        }
    }
}

object SongRequestProcessor {
    private val songMutex = Mutex()

    suspend fun process(
        data: SongRequest,
        uid: String,
        dispatcher: CoroutinesEventBus,
        handler: SessionHandler,
        logger: Logger
    ) {
        val user = UserService.getUser(uid) ?: return

        when (data.type) {
            SongType.ADD.value -> handleAdd(data, user, dispatcher, handler, logger)
            SongType.REMOVE.value -> handleRemove(data, user, dispatcher, logger)
            SongType.NEXT.value -> handleNext(user, dispatcher, logger)
            else -> {
                // Handle other types if necessary
            }
        }
    }

    private suspend fun handleAdd(
        data: SongRequest,
        user: User,
        dispatcher: CoroutinesEventBus,
        handler: SessionHandler,
        logger: Logger
    ) {
        val url = data.url ?: return
        val youtubeVideo = getYoutubeVideo(url) ?: run {
            logger.warn("Failed to fetch YouTube video for URL: $url")
            return
        }

        songMutex.withLock {
            SongListService.saveSong(
                user,
                user.token,
                url,
                youtubeVideo.name,
                youtubeVideo.author,
                youtubeVideo.length,
                user.username
            )
        }

        dispatcher.post(
            SongEvent(
                uid = user.token,
                type = SongType.ADD,
                reqUid = user.token,
                current = CurrentSong.getSong(user),
                next = youtubeVideo
            )
        )
    }

    private suspend fun handleRemove(
        data: SongRequest,
        user: User,
        dispatcher: CoroutinesEventBus,
        logger: Logger
    ) {
        val url = data.url ?: return

        songMutex.withLock {
            val songs = SongListService.getSong(user)
            val exactSong = songs.firstOrNull { it.url == url }
            if (exactSong != null) {
                SongListService.deleteSong(user, exactSong.uid, exactSong.name)
            }
        }

        dispatcher.post(
            SongEvent(
                uid = user.token,
                type = SongType.REMOVE,
                delUrl = url,
                reqUid = null,
                current = null,
                next = null,
            )
        )
    }

    private suspend fun handleNext(
        user: User,
        dispatcher: CoroutinesEventBus,
        logger: Logger
    ) {
        var song: SongList? = null
        var youtubeVideo: YoutubeVideo? = null

        songMutex.withLock {
            val songList = SongListService.getSong(user)
            if (songList.isNotEmpty()) {
                song = songList[0]
                SongListService.deleteSong(user, song.uid, song.name)
            }
        }

        song?.let {
            youtubeVideo = YoutubeVideo(
                it.url,
                it.name,
                it.author,
                it.time
            )
        }

        dispatcher.post(
            SongEvent(
                uid = user.token,
                type = SongType.NEXT,
                current = null,
                next = youtubeVideo,
                reqUid = null,
                delUrl = null
            )
        )

        CurrentSong.setSong(user, youtubeVideo)
    }
}

@Serializable
data class SongRequest(
    val type: Int,
    val uid: String,
    val url: String? = null,
    val maxQueue: Int? = null,
    val maxUserLimit: Int? = null,
    val isStreamerOnly: Boolean? = null,
    val remove: Int? = null,
    val isDisabled: Boolean? = null
)
