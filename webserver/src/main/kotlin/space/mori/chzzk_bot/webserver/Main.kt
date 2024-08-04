package space.mori.chzzk_bot.webserver

import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
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
import space.mori.chzzk_bot.webserver.routes.wsSongRoutes
import space.mori.chzzk_bot.webserver.routes.wsTimerRoutes
import java.time.Duration

val server = embeddedServer(Netty, port = 8080) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

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
        wsTimerRoutes()
        wsSongRoutes()
        swaggerUI("swagger-ui/index.html", "openapi/documentation.yaml") {
            options {
                version = "1.2.0"
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