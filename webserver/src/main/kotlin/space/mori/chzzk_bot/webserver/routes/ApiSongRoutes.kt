package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import space.mori.chzzk_bot.common.services.SongListService
import space.mori.chzzk_bot.common.services.UserService

fun Routing.songRoutes() {
    route("/songs/{uid}") {
        get {
            val uid = call.parameters["uid"]
            val user = uid?.let { it1 -> UserService.getUser(it1) }
            if (user == null) {
                call.respondText("No user found", status = HttpStatusCode.NotFound)
                return@get
            }

            val songs = SongListService.getSong(user)
            call.respond(songs)
        }
    }
    route("/songs") {
        get {
            call.respondText("Require UID", status= HttpStatusCode.BadRequest)
        }
    }
}