package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.getStreamInfo
import space.mori.chzzk_bot.webserver.UserSession

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
)

fun Routing.apiRoutes() {
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
            val user = getStreamInfo(uid)
            if(user.content == null) {
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
                call.respondText("No session found", status = HttpStatusCode.NotFound)
                return@get
            }
            println(session)
            val user = UserService.getUserWithNaverId(session.id.toLong())
            if(user == null) {
                call.respondText("No session found", status = HttpStatusCode.NotFound)
                return@get
            }
            val songConfig = SongConfigService.getConfig(user)
            val status = getStreamInfo(user.token)

            call.respond(HttpStatusCode.OK, GetSessionDTO(
                status.content!!.channel.channelId,
                status.content!!.channel.channelName,
                status.content!!.status == "OPEN",
                status.content!!.channel.channelImageUrl,
                songConfig.queueLimit,
                songConfig.personalLimit,
                songConfig.streamerOnly
            ))
        }
    }
}