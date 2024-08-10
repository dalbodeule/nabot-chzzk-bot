package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.DiscordRegisterEvent
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.getRandomString
import space.mori.chzzk_bot.webserver.UserSession

fun Route.apiDiscordRoutes() {
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    route("/discord") {
        get("{uid}") {
            val uid = call.parameters["uid"]
            val session = call.sessions.get<UserSession>()
            if(uid == null) {
                call.respond(HttpStatusCode.BadRequest, "UID is required")
                return@get
            }
            val user = UserService.getUser(uid)
            if(user == null || user.naverId != session?.id || user.token == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@get
            }

            if (user.discord == null) {
                val randomString = getRandomString(8)

                CoroutineScope(Dispatchers.Default).launch {
                    dispatcher.post(DiscordRegisterEvent(
                        user.token!!,
                        randomString
                    ))
                }

                call.respond(HttpStatusCode.NotFound, DiscordRequireRegisterDTO(
                    user.token!!,
                    randomString
                ))
                return@get
            }


        }
    }
}

data class DiscordRequireRegisterDTO(
    val user: String,
    val passkey: String
)