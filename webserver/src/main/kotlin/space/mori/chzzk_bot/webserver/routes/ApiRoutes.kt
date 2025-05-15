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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import space.mori.chzzk_bot.common.events.ChzzkUserFindEvent
import space.mori.chzzk_bot.common.events.ChzzkUserReceiveEvent

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

    suspend fun getChzzkUserWithId(uid: String): ChzzkUserReceiveEvent? {
        val completableDeferred = CompletableDeferred<ChzzkUserReceiveEvent>()
        dispatcher.subscribe(ChzzkUserReceiveEvent::class) { event ->
            if (event.uid == uid) {
                completableDeferred.complete(event)
            }
        }
        val user = withTimeoutOrNull(5000) {
            dispatcher.post(ChzzkUserFindEvent(uid))
            completableDeferred.await()
        }
        return user
    }

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
            val user = getChzzkUserWithId(uid)
            if (user?.find == false) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            } else {
                call.respond(HttpStatusCode.OK, GetUserDTO(
                    user?.uid ?: "",
                    user?.nickname ?: "",
                    user?.isStreamOn ?: false,
                    user?.avatarUrl ?: ""
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
            val status = getChzzkUserWithId(user.token)
            val returnUsers = mutableListOf<GetSessionDTO>()

            if(status == null) {
                call.respondText("No user found", status = HttpStatusCode.NotFound)
                return@get
            }

            if (user.username == "임시닉네임") {
                status.let { stats -> UserService.updateUser(user, stats.uid ?: "", stats.nickname ?: "") }
            }

            returnUsers.add(GetSessionDTO(
                status.uid ?: user.token,
                status.nickname ?: user.username,
                status.isStreamOn == true,
                status.avatarUrl ?: "",
                songConfig.queueLimit,
                songConfig.personalLimit,
                songConfig.streamerOnly,
                songConfig.disabled
            ))

            val subordinates = transaction {
                user.subordinates.toList()
            }
            returnUsers.addAll(subordinates.map {
                val subStatus = getChzzkUserWithId(it.token)
                return@map if (subStatus == null) {
                    null
                } else {
                    GetSessionDTO(
                        subStatus.uid ?: "",
                        subStatus.nickname ?: "",
                        subStatus.isStreamOn == true,
                        subStatus.avatarUrl ?: "",
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