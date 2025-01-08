package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.UserSession
import space.mori.chzzk_bot.webserver.utils.DiscordGuildCache

fun Route.apiDiscordRoutes() {
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    route("/discord") {
        get("/{uid}") {
            val uid = call.parameters["uid"]
            val session = call.sessions.get<UserSession>()
            if(uid == null) {
                call.respond(HttpStatusCode.BadRequest, "UID is required")
                return@get
            }
            val user = UserService.getUser(uid)
            if(user?.token == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@get
            }

            val managers = transaction {
                user.managers.toList()
            }
            if(!managers.any { it.token == session?.id } && user.token != session?.id) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@get
            }

            if (user.discord == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(HttpStatusCode.OK, GuildSettings(
                user.liveAlertGuild.toString(),
                user.liveAlertChannel.toString(),
                user.liveAlertMessage
            ))
            return@get
        }
        post("/{uid}") {
            val uid = call.parameters["uid"]
            val session = call.sessions.get<UserSession>()
            val body: GuildSettings = call.receive()
            if(uid == null) {
                call.respond(HttpStatusCode.BadRequest, "UID is required")
                return@post
            }
            val user = UserService.getUser(uid)
            if(user?.token == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@post
            }

            val managers = transaction {
                user.managers.toList()
            }
            if(!managers.any { it.token == session?.id } && user.token != session?.id) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@post
            }

            UserService.updateLiveAlert(user, body.guildId?.toLong() ?: 0L, body.channelId?.toLong() ?: 0L, body.message)
            call.respond(HttpStatusCode.OK)
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
            val user = UserService.getUser(session.id)
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
        get("/guilds") {
            val session = call.sessions.get<UserSession>()
            if(session == null) {
                call.respond(HttpStatusCode.BadRequest, "Session is required")
                return@get
            }

            call.respond(HttpStatusCode.OK, DiscordGuildCache.getCachedGuilds(session.discordGuildList))
            return@get
        }
    }
}

@Serializable
data class GuildSettings(
    val guildId: String?,
    val channelId: String?,
    val message: String? = null,
)