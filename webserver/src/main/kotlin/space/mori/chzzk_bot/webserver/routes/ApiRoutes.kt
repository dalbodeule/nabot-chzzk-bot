package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.apiRoutes() {
    route("/") {
        get {
            call.respondText("Hello World!", status = HttpStatusCode.OK)
        }
    }
    route("/health") {
        get {
            call.respondText("OK", status= HttpStatusCode.OK)
        }
    }
}