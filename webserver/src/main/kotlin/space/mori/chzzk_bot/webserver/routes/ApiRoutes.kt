package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.UserRegisterEvent
import space.mori.chzzk_bot.common.services.SongConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.getStreamInfo
import space.mori.chzzk_bot.common.utils.getUserInfo
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

@Serializable
data class GetSettingDTO(
    val isBotDisabled: Boolean,
    val isBotMsgDisabled: Boolean,
)

@Serializable
data class RegisterChzzkUserDTO(
    val chzzkUrl: String
)

fun Routing.apiRoutes() {
    val chzzkIDRegex = """(?:.+chzzk\.naver\.com/)?([a-f0-9]{32})(?:.+)?""".toRegex()
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
            var user = UserService.getUserWithNaverId(session.id)
            if(user == null) {
                user = UserService.saveUser("임시닉네임", session.id)
            }
            val songConfig = SongConfigService.getConfig(user)
            val status = user.token?.let { it1 -> getStreamInfo(it1) }
            val returnUsers = mutableListOf<GetSessionDTO>()

            if (user.username == "임시닉네임") {
                status?.content?.channel?.let { it1 -> UserService.updateUser(user, it1.channelId, it1.channelName) }
            }

            if(status?.content == null) {
                call.respondText(user.naverId, status = HttpStatusCode.NotFound)
                return@get
            }

            returnUsers.add(GetSessionDTO(
                status.content!!.channel.channelId,
                status.content!!.channel.channelName,
                status.content!!.status == "OPEN",
                status.content!!.channel.channelImageUrl,
                songConfig.queueLimit,
                songConfig.personalLimit,
                songConfig.streamerOnly,
                songConfig.disabled
            ))

            val subordinates = transaction {
                user.subordinates.toList()
            }
            returnUsers.addAll(subordinates.map {
                val subStatus = it.token?.let { token -> ChzzkUserCache.getCachedUser(token) }
                return@map if (it.token == null || subStatus?.content == null) {
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
        post {
            val session = call.sessions.get<UserSession>()
            if(session == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@post
            }

            val body: RegisterChzzkUserDTO = call.receive()

            val user = UserService.getUserWithNaverId(session.id)
            if(user == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@post
            }

            val matchResult = chzzkIDRegex.find(body.chzzkUrl)
            val matchedChzzkId = matchResult?.groups?.get(1)?.value

            if (matchedChzzkId == null) {
                call.respondText("Invalid chzzk ID", status = HttpStatusCode.BadRequest)
                return@post
            }

            val status = getUserInfo(matchedChzzkId)
            if (status.content == null) {
                call.respondText("Invalid chzzk ID", status = HttpStatusCode.BadRequest)
                return@post
            }
            UserService.updateUser(
                user,
                status.content!!.channelId,
                status.content!!.channelName
            )
            call.respondText("Done!", status = HttpStatusCode.OK)

            CoroutineScope(Dispatchers.Default).launch {
                dispatcher.post(UserRegisterEvent(status.content!!.channelId))
            }
            return@post
        }
    }

    route("/settings") {
        get {
            val session = call.sessions.get<UserSession>()
            if(session == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@get
            }

            val user = UserService.getUserWithNaverId(session.id)
            if(user == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@get
            }

            call.respond(GetSettingDTO(
                user.isDisabled, user.isDisableStartupMsg
            ))
        }
        post {
            val session = call.sessions.get<UserSession>()
            if(session == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@post
            }

            val body: GetSettingDTO = call.receive()

            val user = UserService.getUserWithNaverId(session.id)
            if(user == null) {
                call.respondText("No session found", status = HttpStatusCode.Unauthorized)
                return@post
            }

            UserService.setIsDisabled(user, body.isBotDisabled)
            UserService.setIsStartupDisabled(user, body.isBotMsgDisabled)

            call.respond(GetSettingDTO(
                user.isDisabled, user.isDisableStartupMsg
            ))
        }
    }
}