package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.utils.getStreamInfo

@Serializable
data class GetUserDTO(
    val uid: String,
    val nickname: String,
    val isStreamOn: Boolean,
    val avatarUrl: String,
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
            call.respondText("Require UID", status = HttpStatusCode.NotFound)
        }
    }
    route("/session/{sid}") {
        get {
            val sid = call.parameters["sid"]
            if(sid == null) {
                call.respondText("Require SID", status = HttpStatusCode.NotFound)
                return@get
            }
            val user = SongConfigService.getUserByToken(sid)
            if(user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            } else {
                val chzzkUser = getStreamInfo(user.token)
                call.respond(HttpStatusCode.OK, GetUserDTO(
                    chzzkUser.content!!.channel.channelId,
                    chzzkUser.content!!.channel.channelName,
                    chzzkUser.content!!.status == "OPEN",
                    chzzkUser.content!!.channel.channelImageUrl
                ))
            }
        }
    }
    route("/session") {
        get {
            call.respondText("Require SID", status = HttpStatusCode.NotFound)
        }
    }
}