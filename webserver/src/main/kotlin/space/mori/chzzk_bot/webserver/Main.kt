package space.mori.chzzk_bot.webserver

import applicationHttpClient
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.mori.chzzk_bot.webserver.routes.*
import java.time.Duration

val dotenv = dotenv {
    ignoreIfMissing = true
}

const val naverMeAPIURL = "https://openapi.naver.com/v1/nid/me"

val redirects = mutableMapOf<String, String>()

val server = embeddedServer(Netty, port = 8080, ) {
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
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Get)
        allowHost("http://localhost:8080")
        allowHost(dotenv["FRONTEND"])
    }
    install(Sessions) {
        cookie<UserSession>("user_session", storage = SessionStorageMemory()) {}
    }
    install(Authentication) {
        oauth("auth-oauth-naver") {
            urlProvider = { "${dotenv["HOST"]}/auth/callback" }
            providerLookup = { OAuthServerSettings.OAuth2ServerSettings(
                name = "naver",
                authorizeUrl = "https://nid.naver.com/oauth2.0/authorize",
                accessTokenUrl = "https://nid.naver.com/oauth2.0/token",
                requestMethod = HttpMethod.Post,
                clientId = dotenv["NAVER_CLIENT_ID"],
                clientSecret = dotenv["NAVER_CLIENT_SECRET"],
                defaultScopes = listOf(""),
                extraAuthParameters = listOf(),
                onStateCreated = { call, state ->
                    //saves new state with redirect url value
                    call.request.queryParameters["redirectUrl"]?.let {
                        redirects[state] = it
                    }
                }
            )}
            client = applicationHttpClient
        }
    }
    routing {
        route("/auth") {
            authenticate("auth-oauth-naver") {
                get("/login") {

                }
                get("/callback") {
                    val currentPrincipal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    currentPrincipal?.let { principal ->
                        principal.state?.let { state ->
                            val userInfo: NaverAPI<NaverMeAPI> = applicationHttpClient.get(naverMeAPIURL) {
                                headers{
                                    append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                                }
                            }.body()

                            call.sessions.set(userInfo.response?.let { it1 ->
                                UserSession(state,
                                    it1.id, it1.nickname, it1.profile_image)
                            })

                            redirects[state]?.let { redirect ->
                                call.respondRedirect(redirect)
                                return@get
                            }
                        }
                    }
                    call.respondRedirect(dotenv["FRONTEND"])
                }
            }
            get("/logout") {
                call.sessions.clear<UserSession>()
            }
        }

        apiRoutes()
        apiSongRoutes()
        wsTimerRoutes()
        wsSongRoutes()
        wsSongListRoutes()
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

@Serializable
data class UserSession(
    val state: String,
    val id: String,
    val nickname: String,
    val profileImage: String
)

@Serializable
data class NaverMeAPI(
    val id: String,
    val nickname: String,
    val profile_image: String
)

@Serializable
data class NaverAPI<T>(val resultcode: String, val message: String, val response: T?)