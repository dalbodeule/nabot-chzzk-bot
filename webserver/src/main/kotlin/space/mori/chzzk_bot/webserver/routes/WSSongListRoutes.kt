import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.Logger
import io.ktor.websocket.*
import io.ktor.server.application.*
import io.ktor.websocket.Frame.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.models.SongList
import space.mori.chzzk_bot.common.models.SongLists.uid
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.SongListService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import space.mori.chzzk_bot.common.utils.getYoutubeVideo
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.CurrentSong
import space.mori.chzzk_bot.webserver.utils.SongListWebSocketManager

fun Route.wsSongListRoutes(songListWebSocketManager: SongListWebSocketManager) {
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    webSocket("/songlist") {
        val session = call.sessions.get<UserSession>()
        val uid = session?.id
        if (uid == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid session"))
            return@webSocket
        }

        try {
            songListWebSocketManager.addSession(uid, this)
            for (frame in incoming) {
                when (frame) {
                    is Text -> {
                        val text = frame.readText().trim()
                        if (text == SongListWebSocketManager.PING_MESSAGE) {
                            send(SongListWebSocketManager.PONG_MESSAGE)
                            songListWebSocketManager.handlePong(uid)
                        } else {
                            // Handle song requests
                            text.let { Json.decodeFromString<SongRequest>(it) }.let { data ->
                                val user = session.id.let { UserService.getUser(it) }
                                
                                if(user == null) {
                                    songListWebSocketManager.removeSession(uid)
                                    return@webSocket
                                }
                                
                                handleSongRequest(data, user, dispatcher, songListWebSocketManager.logger)
                            }.runCatching { songListWebSocketManager.logger.error("Failed to parse WebSocket message as SongRequest.") }
                        }
                    }

                    is Ping -> send(Pong(frame.data))
                    else -> songListWebSocketManager.logger.warn("Unsupported frame type received.")
                }
            }
        } catch (e: Exception) {
            songListWebSocketManager.logger.error("WebSocket error: ${e.message}")
        } finally {
            songListWebSocketManager.removeSession(uid)
        }
    }
}


suspend fun handleSongRequest(
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
                        CoroutineScope(Dispatchers.Default).launch {
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
                } catch (e: Exception) {
                    logger.debug("SongType.ADD Error: $uid $e")
                }
            }
        }
        SongType.REMOVE.value -> {
            data.url?.let { url ->
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
        SongType.NEXT.value -> {
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
