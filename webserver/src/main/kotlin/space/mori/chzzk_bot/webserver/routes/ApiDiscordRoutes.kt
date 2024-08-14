package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.DiscordGuildCache

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
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val guild = DiscordGuildCache.getCachedGuilds(user.liveAlertGuild.toString())
            if(guild == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(HttpStatusCode.OK, guild)
            return@get
        }
        get("/guild/{gid}") {
            val gid = call.parameters["gid"]
            val session = call.sessions.get<UserSession>()
            if(gid == null) {
                call.respond(HttpStatusCode.BadRequest, "GID is required")
                return@get
            }
            if(session == null) {
                call.respond(HttpStatusCode.BadRequest, "Session is required")
                return@get
            }
            val user = UserService.getUserWithNaverId(session.id)
            if(user == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@get
            }

            val guild = DiscordGuildCache.getCachedGuilds(gid)
            if(guild == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(HttpStatusCode.OK, guild)
            return@get
        }
        get {
            val session = call.sessions.get<UserSession>()
            if(session == null) {
                call.respond(HttpStatusCode.BadRequest, "Session is required")
                return@get
            }
            val user = UserService.getUserWithNaverId(session.id)
            if(user == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@get
            }

            call.respond(HttpStatusCode.OK, DiscordGuildCache.getCachedGuilds(session.discordGuildList))
            return@get
        }
    }
}