package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.ChzzkUserCache

@Serializable
data class GetUserDTO(
    val uid: String,
    val nickname: String,
    val isStreamOn: Boolean,
    val avatarUrl: String
)

@Serializable
data class GetSessionDTO(
    val uid: String,
    val nickname: String,
    val isStreamOn: Boolean,
    val avatarUrl: String,
    val maxQueueSize: Int,
    val maxUserSize: Int,
    val isStreamerOnly: Boolean,
    val isDisabled: Boolean
)

fun Routing.apiRoutes() {
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    route("/") {
        get {
            call.respondText("Hello World!", status =
                HttpStatusCode.OK)
        }
    }
    route("/health") {
        get {
            call.respondText("OK", status= HttpStatusCode.OK)
        }
    }

    route("/user/{uid}") {
        get {
            val uid = call.parameters["uid"]
            if(uid == null) {
                call.respondText("Require UID", status = HttpStatusCode.NotFound)
                return@get
            }
            val user = ChzzkUserCache.getCachedUser(uid)
            if(user?.content == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            } else {
                call.respond(HttpStatusCode.OK, GetUserDTO(
                    user.content!!.channel.channelId,
                    user.content!!.channel.channelName,
                    user.content!!.status == "OPEN",
                    user.content!!.channel.channelImageUrl
                ))
            }
        }
    }
    route("/user") {
        get {
            val session = call.sessions.get<UserSession>()

            if(session == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@get
            }
            var user = UserService.getUser(session.id)
            if(user == null) {
                user = UserService.saveUser("임시닉네임", session.id)
            }
            val songConfig = SongConfigService.getConfig(user)
            val status = ChzzkUserCache.getCachedUser(session.id)
            val returnUsers = mutableListOf<GetSessionDTO>()

            if(status == null) {
                call.respondText("No user found", status = HttpStatusCode.NotFound)
                return@get
            }

            if (user.username == "임시닉네임") {
                status.content?.channel?.let { it1 -> UserService.updateUser(user, it1.channelId, it1.channelName) }
            }

            returnUsers.add(GetSessionDTO(
                status.content?.channel?.channelId ?: user.token,
                status.content?.channel?.channelName ?: user.token,
                status.content?.status == "OPEN",
                status.content?.channel?.channelImageUrl ?: "",
                songConfig.queueLimit,
                songConfig.personalLimit,
                songConfig.streamerOnly,
                songConfig.disabled
            ))

            val subordinates = transaction {
                user.subordinates.toList()
            }
            returnUsers.addAll(subordinates.map {
                val subStatus = ChzzkUserCache.getCachedUser(it.token)
                return@map if (subStatus?.content == null) {
                    null
                } else {
                    GetSessionDTO(
                        subStatus.content!!.channel.channelId,
                        subStatus.content!!.channel.channelName,
                        subStatus.content!!.status == "OPEN",
                        subStatus.content!!.channel.channelImageUrl,
                        0,
                        0,
                        false,
                        false
                    )
                }
            }.filterNotNull())

            call.respond(HttpStatusCode.OK, returnUsers)
        }
    }
}