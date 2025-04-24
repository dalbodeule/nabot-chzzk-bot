package space.mori.chzzk_bot.webserver.routes

import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.Logger
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.models.SongList
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.SongListService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import space.mori.chzzk_bot.common.utils.getYoutubeVideo
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.CurrentSong
import java.util.concurrent.ConcurrentHashMap

val songListScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
fun Routing.wsSongListRoutes() {
    val sessions = ConcurrentHashMap<String, WebSocketServerSession>()
    val status = ConcurrentHashMap<String, SongType>()
    val logger = LoggerFactory.getLogger("WSSongListRoutes")
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    val sessionMutex = Mutex()

    environment.monitor.subscribe(ApplicationStopped) {
        songListScope.cancel()
    }

    val ackMap = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    suspend fun addSession(uid: String, session: WebSocketServerSession) {
        val oldSession = sessionMutex.withLock {
            val old = sessions[uid]
            sessions[uid] = session
            old
        }
        if(oldSession != null) {
            songListScope.launch {
                try {
                    oldSession.close(CloseReason(
                        CloseReason.Codes.VIOLATED_POLICY, "Another session is already active."))
                } catch(e: Exception) {
                    logger.warn("Error closing old session: ${e.message}")
                }
            }
        }
    }

    suspend fun removeSession(uid: String) {
        sessionMutex.withLock {
            sessions.remove(uid)
        }
    }

    suspend fun waitForAck(ws: WebSocketServerSession, expectedUid: String): Boolean {
        val ackDeferred = CompletableDeferred<Boolean>()
        ackMap[expectedUid] = ackDeferred
        return try {
            withTimeoutOrNull(5000L) { ackDeferred.await() } ?: false
        } catch (e: CancellationException) {
            false
        } finally {
            ackMap.remove(expectedUid)
        }
    }

    suspend fun sendWithRetry(uid: String, res: SongResponse, maxRetries: Int = 5, delayMillis: Long = 3000L) {
        var attempt = 0
        var sentSuccessfully = false
        while (attempt < maxRetries && !sentSuccessfully) {
            val ws: WebSocketServerSession? = sessionMutex.withLock { sessions[uid] }
            if (ws == null) {
                logger.debug("No active session for $uid. Retrying in $delayMillis ms.")
                delay(delayMillis)
                attempt++
                continue
            }
            try {
                ws.sendSerialized(res)
                val ackReceived = waitForAck(ws, res.uid)
                if (ackReceived) {
                    logger.debug("ACK received for message to $uid on attempt $attempt.")
                    sentSuccessfully = true
                } else {
                    logger.warn("ACK not received for message to $uid on attempt $attempt.")
                    attempt++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                logger.warn("Failed to send message to $uid on attempt $attempt: ${e.message}")
                if (e is WebSocketException || e is IOException) {
                    removeSession(uid)
                }
            }
            if (!sentSuccessfully && attempt < maxRetries) {
                delay(delayMillis)
            }
        }
        if (!sentSuccessfully) {
            logger.error("Failed to send message to $uid after $maxRetries attempts.")
        }
    }

    webSocket("/songlist") {
        val session = call.sessions.get<UserSession>()
        val user = session?.id?.let { UserService.getUser(it) }
        if (user == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid SID"))
            return@webSocket
        }
        val uid = user.token
        addSession(uid, this)
        if (status[uid] == SongType.STREAM_OFF) {
            songListScope.launch {
                try {
                    sendSerialized(SongResponse(
                        SongType.STREAM_OFF.value,
                        uid,
                        null,
                        null,
                        null,
                    ))
                } catch (e: Exception) {
                    logger.warn("Error sending STREAM_OFF: ${e.message}")
                } finally {
                    removeSession(uid)
                }
            }
            return@webSocket
        }
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        if (text.trim() == "ping") {
                            send("pong")
                        } else {
                            val data = Json.decodeFromString<SongRequest>(text)
                            if (data.type == SongType.ACK.value) {
                                ackMap[data.uid]?.complete(true)
                            } else {
                                handleSongRequest(data, user, dispatcher, logger)
                            }
                        }
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.error("WebSocket connection closed: ${e.message}")
        } catch (e: Exception) {
            logger.error("Error in WebSocket: ${e.message}")
        } finally {
            removeSession(uid)
            ackMap.remove(uid)
        }
    }

    dispatcher.subscribe(SongEvent::class) {
        logger.debug("SongEvent: {} / {} {}", it.uid, it.type, it.current?.name)
        songListScope.launch {
            try {
                val user = UserService.getUser(it.uid)
                if (user != null) {
                    sendWithRetry(
                        user.token, SongResponse(
                            it.type.value,
                            it.uid,
                            it.reqUid,
                            it.current?.toSerializable(),
                            it.next?.toSerializable(),
                            it.delUrl
                        )
                    )
                }
            } catch(e: Exception) {
                logger.error("Error handling song event: ${e.message}")
            }
        }
    }
    dispatcher.subscribe(TimerEvent::class) {
        if (it.type == TimerType.STREAM_OFF) {
            songListScope.launch {
                try {
                    val user = UserService.getUser(it.uid)
                    if (user != null) {
                        sendWithRetry(
                            user.token, SongResponse(
                                it.type.value,
                                it.uid,
                                null,
                                null,
                                null,
                            )
                        )
                    }
                } catch(e: Exception) {
                    logger.error("Error handling timer event: ${e.message}")
                }
            }
        }
    }
}
// 노래 처리를 위한 Mutex 추가
private val songMutex = Mutex()
fun handleSongRequest(
    data: SongRequest,
    user: User,
    dispatcher: CoroutinesEventBus,
    logger: Logger
) {
    if (data.maxQueue != null && data.maxQueue > 0) SongConfigService.updateQueueLimit(user, data.maxQueue)
    if (data.maxUserLimit != null && data.maxUserLimit > 0) SongConfigService.updatePersonalLimit(user, data.maxUserLimit)
    if (data.isStreamerOnly != null) SongConfigService.updateStreamerOnly(user, data.isStreamerOnly)
    if (data.isDisabled != null) SongConfigService.updateDisabled(user, data.isDisabled)
    when (data.type) {
        SongType.ADD.value -> {
            data.url?.let { url ->
                try {
                    val youtubeVideo = getYoutubeVideo(url)
                    if (youtubeVideo != null) {
                        songListScope.launch {
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
                                dispatcher.post(
                                    SongEvent(
                                        user.token,
                                        SongType.ADD,
                                        user.token,
                                        CurrentSong.getSong(user),
                                        youtubeVideo
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("SongType.ADD Error: ${user.token} $e")
                }
            }
        }
        SongType.REMOVE.value -> {
            data.url?.let { url ->
                songListScope.launch {
                    songMutex.withLock {
                        val songs = SongListService.getSong(user)
                        val exactSong = songs.firstOrNull { it.url == url }
                        if (exactSong != null) {
                            SongListService.deleteSong(user, exactSong.uid, exactSong.name)
                        }
                        dispatcher.post(
                            SongEvent(
                                user.token,
                                SongType.REMOVE,
                                null,
                                null,
                                null,
                                url
                            )
                        )
                    }
                }
            }
        }
        SongType.NEXT.value -> {
            songListScope.launch {
                songMutex.withLock {
                    val songList = SongListService.getSong(user)
                    var song: SongList? = null
                    var youtubeVideo: YoutubeVideo? = null
                    if (songList.isNotEmpty()) {
                        song = songList[0]
                        SongListService.deleteSong(user, song.uid, song.name)
                    }
                    song?.let {
                        youtubeVideo = YoutubeVideo(
                            song.url,
                            song.name,
                            song.author,
                            song.time
                        )
                    }
                    dispatcher.post(
                        SongEvent(
                            user.token,
                            SongType.NEXT,
                            song?.uid,
                            youtubeVideo
                        )
                    )
                    CurrentSong.setSong(user, youtubeVideo)
                }
            }
        }
    }
}

@Serializable
data class SongRequest(
    val type: Int,
    val uid: String,
    val url: String?,
    val maxQueue: Int?,
    val maxUserLimit: Int?,
    val isStreamerOnly: Boolean?,
    val remove: Int?,
    val isDisabled: Boolean?,
)