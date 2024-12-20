package space.mori.chzzk_bot.webserver

import applicationHttpClient
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.routes.*
import space.mori.chzzk_bot.webserver.utils.DiscordRatelimits
import wsSongListRoutes
import java.time.Duration
import kotlin.time.toKotlinDuration

val dotenv = dotenv {
    ignoreIfMissing = true
}

const val naverMeAPIURL = "https://openapi.naver.com/v1/nid/me"

val redirects = mutableMapOf<String, String>()

val server = embeddedServer(Netty, port = 8080, ) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15).toKotlinDuration()
        timeout = Duration.ofSeconds(15).toKotlinDuration()
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
    install(Sessions) {
        cookie<UserSession>("user_session", storage = MariadbSessionStorage()) {}
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
        oauth("auth-oauth-discord") {
            urlProvider = { "${dotenv["HOST"]}/auth/callback/discord" }
            providerLookup = { OAuthServerSettings.OAuth2ServerSettings(
                name = "discord",
                authorizeUrl = "https://discord.com/oauth2/authorize",
                accessTokenUrl = "https://discord.com/api/oauth2/token",
                clientId = dotenv["DISCORD_CLIENT_ID"],
                clientSecret = dotenv["DISCORD_CLIENT_SECRET"],
                requestMethod = HttpMethod.Post,
                defaultScopes = listOf(),
                extraAuthParameters = listOf(
                    Pair("permissions", "826781943872"),
                    Pair("response_type", "code"),
                    Pair("integration_type", "0"),
                    Pair("scope", "guilds bot identify")
                ),
                onStateCreated = { call, state ->
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
            // discord login
            authenticate("auth-oauth-discord") {
                get("/login/discord") {

                }
                get("/callback/discord") {
                    try {
                        val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                        val session = call.sessions.get<UserSession>()
                        val user = session?.id?.let { UserService.getUserWithNaverId(it) }

                        if(principal != null && session != null && user != null) {
                            try {
                                val accessToken = principal.accessToken
                                val userInfo = getDiscordUser(accessToken)
                                val guilds = getUserGuilds(accessToken)

                                userInfo?.user?.id?.toLong()?.let { id -> UserService.updateUser(user, id) }

                                call.sessions.set(UserSession(
                                    session.state,
                                    session.id,
                                    guilds.filter {
                                        it.owner
                                    }.map { it.id }
                                ))

                                redirects[principal.state]?.let { redirect ->
                                    call.respondRedirect(redirect)
                                    return@get
                                }

                                call.respondRedirect(getFrontendURL(""))
                            } catch(e: Exception) {
                                println(e.toString())
                                call.respondRedirect(getFrontendURL(""))
                            }
                        } else {
                            call.respondRedirect(getFrontendURL(""))
                        }
                    } catch(e: Exception) {
                        println(e.stackTrace)
                    }
                }
            }

            // naver login
            authenticate("auth-oauth-naver") {
                get("/login") {

                }
                get("/callback") {
                    val currentPrincipal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    currentPrincipal?.let { principal ->
                        principal.state?.let { state ->
                            val userInfo: NaverAPI<NaverMeAPI> = applicationHttpClient.get(naverMeAPIURL) {
                                headers {
                                    append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                                }
                            }.body()

                            call.sessions.set(userInfo.response?.let { profile ->
                                UserSession(state, profile.id, listOf())
                            })

                            redirects[state]?.let { redirect ->
                                call.respondRedirect(redirect)
                                return@get
                            }
                        }
                    }
                    call.respondRedirect(getFrontendURL(""))
                }
            }

            // common: logout
            get("/logout") {
                call.sessions.clear<UserSession>()
                call.response.status(HttpStatusCode.OK)
                return@get
            }
        }

        apiRoutes()
        apiSongRoutes()
        apiCommandRoutes()
        apiTimerRoutes()
        apiDiscordRoutes()

        wsTimerRoutes()
        wsSongRoutes()
        wsSongListRoutes()

        swaggerUI("swagger-ui/index.html", "openapi/documentation.yaml") {
            options {
                version = "1.2.0"
            }
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Get)
        allowHost(dotenv["FRONTEND"] ?: "localhost:3000", schemes=listOf("https"))
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }
}

fun start() {
    server.start(wait = true)
}

fun stop() {
    server.stop()
}

fun getFrontendURL(path: String)
    = "${if(dotenv["FRONTEND_HTTPS"].toBoolean()) "https://" else "http://" }${dotenv["FRONTEND"]}${path}"

@Serializable
data class UserSession(
    val state: String,
    val id: String,
    val discordGuildList: List<String>
)

@Serializable
data class NaverMeAPI(
    val id: String
)

@Serializable
data class NaverAPI<T>(val resultcode: String, val message: String, val response: T?)

@Serializable
data class DiscordMeAPI(
    val application: DiscordApplicationAPI,
    val scopes: List<String>,
    val user: DiscordUserAPI
)

@Serializable
data class DiscordApplicationAPI(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val hook: Boolean,
    val bot_public: Boolean,
    val bot_require_code_grant: Boolean,
    val verify_key: String
)

@Serializable
data class DiscordUserAPI(
    val id: String,
    val username: String,
    val avatar: String,
    val discriminator: String,
    val global_name: String,
    val public_flags: Int
)

@Serializable
data class DiscordGuildListAPI(
    val id: String,
    val name: String,
    val icon: String?,
    val banner: String?,
    val owner: Boolean,
    val permissions: Int,
    val features: List<String>,
    val roles: List<GuildRole>?
)

@Serializable
data class GuildRole(
    val id: String,
    val name: String,
    val color: Int,
    val mentionable: Boolean,
)

enum class ChannelType(val value: Int) {
    GUILD_TEXT(0),
    DM(1),
    GUILD_VOICE(2),
    GROUP_DM(3),
    GUILD_CATEGORY(4),
    GUILD_ANNOUNCEMENT(5),
    ANNOUNCEMENT_THREAD(10),
    PUBLIC_THREAD(11),
    PRIVATE_THREAD(12),
    GUILD_STAGE_VOICE(13),
    GUILD_DIRECTORY(14),
    GUILD_FORUM(15),
    GUILD_MEDIA(16)
}

@Serializable
data class GuildChannel(
    val id: String,
    val type: Int,
    val name: String?
)

suspend fun getDiscordUser(accessToken: String): DiscordMeAPI? {
    if(DiscordRatelimits.isLimited()) {
        delay(DiscordRatelimits.getRateReset())
    }

    val response: HttpResponse = applicationHttpClient.get("https://discord.com/api/oauth2/@me") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }

    val rateLimit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
    val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
    val resetAfter = response.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull()?.toLong()

    DiscordRatelimits.setRateLimit(rateLimit, remaining, resetAfter)

    return response.body<DiscordMeAPI?>()
}

suspend fun getUserGuilds(accessToken: String): List<DiscordGuildListAPI> {
    if(DiscordRatelimits.isLimited()) {
        delay(DiscordRatelimits.getRateReset())
    }

    val response = applicationHttpClient.get("https://discord.com/api/users/@me/guilds") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }

    val rateLimit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
    val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
    val resetAfter = response.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull()?.toLong()

    DiscordRatelimits.setRateLimit(rateLimit, remaining, resetAfter)

    return response.body<List<DiscordGuildListAPI>>()
}