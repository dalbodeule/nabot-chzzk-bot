package space.mori.chzzk_bot.webserver

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import space.mori.chzzk_bot.webserver.routes.apiRoutes

val server = embeddedServer(Netty, port = 8080) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }
    routing {
        apiRoutes()
        swaggerUI("swagger-ui/index.html", "openapi/documentation.yaml") {
            options {
                version = "1.1.0"
            }
        }
    }
}

fun start() {
    server.start(wait = true)
}

fun stop() {
    server.stop()
}