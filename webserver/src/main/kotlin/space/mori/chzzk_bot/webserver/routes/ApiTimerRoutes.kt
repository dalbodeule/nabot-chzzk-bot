package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import space.mori.chzzk_bot.common.events.TimerType
import space.mori.chzzk_bot.common.services.TimerConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.UserSession

fun Routing.apiTimerRoutes() {
    route("/timerapi") {
        get("/{uid}") {
            val uid = call.parameters["uid"]
            val session = call.sessions.get<UserSession>()
            if(uid == null) {
                call.respond(HttpStatusCode.BadRequest, "UID is required")
                return@get
            }
            val user = UserService.getUser(uid)
            if(user == null || user.naverId != session?.id) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@get
            }

            val timerConfig = TimerConfigService.getConfig(user)

            call.respond(HttpStatusCode.OK, TimerResponseDTO(timerConfig?.option ?: 0))
        }

        put("/{uid}") {
            val uid = call.parameters["uid"]
            val session = call.sessions.get<UserSession>()
            val request = call.receive<TimerRequestDTO>()
            if(uid == null) {
                call.respond(HttpStatusCode.BadRequest, "UID is required")
                return@put
            }
            val user = UserService.getUser(uid)
            if(user == null || user.naverId != session?.id) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@put
            }

            TimerConfigService.saveOrUpdateConfig(user, TimerType.entries[request.option])
            call.respond(HttpStatusCode.OK)
        }
    }
}

@Serializable
data class TimerRequestDTO(
    val option: Int
)

@Serializable
data class TimerResponseDTO(
    val option: Int
)
