package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.models.SongList
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.SongListService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import space.mori.chzzk_bot.common.utils.getYoutubeVideo
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.CurrentSong
import java.util.concurrent.ConcurrentHashMap

fun Routing.wsSongListRoutes() {
    val sessions = ConcurrentHashMap<String, WebSocketServerSession>()
    val status = ConcurrentHashMap<String, SongType>()
    val logger = LoggerFactory.getLogger("WSSongListRoutes")

    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun addSession(uid: String, session: WebSocketServerSession) {
        if(sessions[uid] != null) {
            CoroutineScope(Dispatchers.Default).launch {
                sessions[uid]?.close(
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY,
                    "Duplicated sessions."
                ))
            }
        }
        sessions[uid] = session
    }

    fun removeSession(uid: String) {
        sessions.remove(uid)
    }

    suspend fun sendWithRetry(uid: String, res: SongResponse, maxRetries: Int = 5, delayMillis: Long = 3000L) {
        var attempt = 0
        var sentSuccessfully = false

        while (attempt < maxRetries && !sentSuccessfully) {
            val ws = sessions[uid]
            try {
                // Attempt to send the message
                ws?.sendSerialized(res)
                sentSuccessfully = true // If no exception, mark as sent successfully
                logger.debug("Message sent successfully on attempt $attempt")
            } catch (e: Exception) {
                attempt++
                logger.warn("Failed to send message on attempt $attempt. Retrying in $delayMillis ms.")
                logger.warn(e.stackTraceToString())

                // Wait before retrying
                delay(delayMillis)
            }
        }

        if (!sentSuccessfully) {
            logger.error("Failed to send message after $maxRetries attempts.")
        }
    }

    webSocket("/songlist") {
        val session = call.sessions.get<UserSession>()
        val user = session?.id?.let { UserService.getUserWithNaverId( it ) }
        if (user == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid SID"))
            return@webSocket
        }

        val uid = user.token

        addSession(uid!!, this)

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
            removeSession(uid)
        }

        try {
            for (frame in incoming) {
                when(frame) {
                    is Frame.Text -> {
                        if (frame.readText() == "ping") {
                            send("pong")
                        } else {
                            val data = frame.readText().let { Json.decodeFromString<SongRequest>(it) }

                            if (data.maxQueue != null && data.maxQueue > 0) SongConfigService.updateQueueLimit(
                                user,
                                data.maxQueue
                            )
                            if (data.maxUserLimit != null && data.maxUserLimit > 0) SongConfigService.updatePersonalLimit(
                                user,
                                data.maxUserLimit
                            )
                            if (data.isStreamerOnly != null) SongConfigService.updateStreamerOnly(
                                user,
                                data.isStreamerOnly
                            )
                            if (data.isDisabled != null) SongConfigService.updateDisabled(user, data.isDisabled)

                            if (data.type == SongType.ADD.value && data.url != null) {
                                try {
                                    val youtubeVideo = getYoutubeVideo(data.url)
                                    if (youtubeVideo != null) {
                                        CoroutineScope(Dispatchers.Default).launch {
                                            SongListService.saveSong(
                                                user,
                                                user.token!!,
                                                data.url,
                                                youtubeVideo.name,
                                                youtubeVideo.author,
                                                youtubeVideo.length,
                                                user.username
                                            )
                                            dispatcher.post(
                                                SongEvent(
                                                    user.token!!,
                                                    SongType.ADD,
                                                    user.token,
                                                    user.username,
                                                    null,
                                                    youtubeVideo
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.debug("SongType.ADD Error: $uid $e")
                                }
                            } else if (data.type == SongType.REMOVE.value && data.url != null) {
                                dispatcher.post(
                                    SongEvent(
                                        user.token!!,
                                        SongType.REMOVE,
                                        null,
                                        null,
                                        null,
                                        null,
                                        data.url
                                    )
                                )
                            } else if (data.type == SongType.NEXT.value) {
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
                                        user.token!!,
                                        SongType.NEXT,
                                        song?.uid,
                                        song?.reqName,
                                        youtubeVideo
                                    )
                                )

                                CurrentSong.setSong(user, youtubeVideo)
                            }
                        }
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {

                    }
                }
            }
        } catch(e: ClosedReceiveChannelException) {
            logger.error("Error in WebSocket: ${e.message}")
        } finally {
            removeSession(uid)
        }
    }

    dispatcher.subscribe(SongEvent::class) {
        logger.debug("SongEvent: {} / {} {}", it.uid, it.type, it.current?.name)
        CoroutineScope(Dispatchers.Default).launch {
            val user = UserService.getUser(it.uid)
            if(user != null) {
                user.token?.let { token ->
                    sendWithRetry(
                        token, SongResponse(
                            it.type.value,
                            it.uid,
                            it.reqUid,
                            it.current?.toSerializable(),
                            it.next?.toSerializable(),
                            it.delUrl
                        ))
                }
            }
        }
    }
    dispatcher.subscribe(TimerEvent::class) {
        if(it.type == TimerType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                val user = UserService.getUser(it.uid)
                if(user != null) {
                    user.token?.let { token ->
                        sendWithRetry(
                            token, SongResponse(
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