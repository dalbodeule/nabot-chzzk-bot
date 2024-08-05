package space.mori.chzzk_bot.webserver.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.SongListService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.getYoutubeVideo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

fun Routing.wsSongListRoutes() {
    val sessions = ConcurrentHashMap<String, ConcurrentLinkedQueue<WebSocketServerSession>>()
    val status = ConcurrentHashMap<String, SongType>()
    val logger = LoggerFactory.getLogger("WSSongListRoutes")

    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun addSession(uid: String, session: WebSocketServerSession) {
        sessions.computeIfAbsent(uid) { ConcurrentLinkedQueue() }.add(session)
    }

    fun removeSession(uid: String, session: WebSocketServerSession) {
        sessions[uid]?.remove(session)
        if(sessions[uid]?.isEmpty() == true) {
            sessions.remove(uid)
        }
    }

    webSocket("/songlist/{sid}") {
        val sid = call.parameters["sid"]
        val session = sid?.let { SongConfigService.getConfig(it) }
        val user = sid?.let {SongConfigService.getUserByToken(sid) }
        if (sid == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid SID"))
            return@webSocket
        }
        if (user == null || session == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid SID"))
            return@webSocket
        }

        addSession(sid, this)

        if(status[sid] == SongType.STREAM_OFF) {
            CoroutineScope(Dispatchers.Default).launch {
                sendSerialized(SongResponse(
                    SongType.STREAM_OFF.value,
                    user.token,
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
                        val data = frame.readText().let { Json.decodeFromString<SongRequest>(it) }

                        if(data.maxQueue != null && data.maxQueue > 0) SongConfigService.updateQueueLimit(user, data.maxQueue)
                        if(data.maxUserLimit != null && data.maxUserLimit > 0) SongConfigService.updatePersonalLimit(user, data.maxUserLimit)
                        if(data.isStreamerOnly != null) SongConfigService.updateStreamerOnly(user, data.isStreamerOnly)

                        if(data.url != null) {
                            val youtubeVideo = getYoutubeVideo(data.url)
                            if(youtubeVideo != null) {
                                CoroutineScope(Dispatchers.Default).launch {
                                    SongListService.saveSong(user, user.token, data.url, youtubeVideo.name, youtubeVideo.author, youtubeVideo.length, user.username)
                                    dispatcher.post(SongEvent(
                                        user.token,
                                        SongType.ADD,
                                        user.token,
                                        user.username,
                                        youtubeVideo.name,
                                        youtubeVideo.author,
                                        youtubeVideo.length
                                    ))
                                }
                            }
                        }
                        if(data.remove != null && data.remove > 0) {
                            val songs = SongListService.getSong(user)
                            if(songs.size < data.remove) {
                                val song = songs[data.remove]
                                SongListService.deleteSong(user, song.uid, song.name)

                                dispatcher.post(
                                    SongEvent(
                                        user.token,
                                        SongType.REMOVE,
                                        user.token,
                                        user.username,
                                        song.name,
                                        song.author,
                                        0
                                    )
                                )
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
            removeSession(sid, this)
        }
    }

    dispatcher.subscribe(SongEvent::class) {
        logger.debug("SongEvent: {} / {} {}", it.uid, it.type, it.name)
        CoroutineScope(Dispatchers.Default).launch {
            val user = UserService.getUser(it.uid)
            if(user != null) {
                val session = SongConfigService.getConfig(user)
                sessions[session.token]?.forEach { ws ->
                    ws.sendSerialized(
                        SongResponse(
                            it.type.value,
                            it.uid,
                            it.reqUid,
                            it.name,
                            it.author,
                            it.time
                        )
                    )
                }
            }
        }
    }
    dispatcher.subscribe(TimerEvent::class) {
        if(it.type == TimerType.STREAM_OFF) {
            val user = UserService.getUser(it.uid)
            SongConfigService.updateSession(user!!, null)
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
data class SongRequest(
    val type: Int,
    val uid: String,
    val url: String?,
    val maxQueue: Int?,
    val maxUserLimit: Int?,
    val isStreamerOnly: Boolean?,
    val remove: Int?
)