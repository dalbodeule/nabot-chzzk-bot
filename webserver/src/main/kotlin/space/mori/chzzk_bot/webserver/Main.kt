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
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.UserRegisterEvent
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.webserver.routes.*
import space.mori.chzzk_bot.webserver.utils.DiscordRatelimits
import java.math.BigInteger
import java.security.SecureRandom
import java.time.Duration
import kotlin.getValue
import kotlin.time.toKotlinDuration

val dotenv = dotenv {
    ignoreIfMissing = true
}

val redirects = mutableMapOf<String, String>()

val server = embeddedServer(Netty, port = 8080, ) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15).toKotlinDuration()
        timeout = Duration.ofSeconds(100).toKotlinDuration()
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
        val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

        route("/auth") {
            // discord login
            authenticate("auth-oauth-discord") {
                get("/login/discord") {

                }
                get("/callback/discord") {
                    try {
                        val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                        val session = call.sessions.get<UserSession>()
                        val user = session?.id?.let { UserService.getUser(it) }

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
            get("/login") {
                val state = generateSecureRandomState()

                // 세션에 상태 값 저장
                call.sessions.set(UserSession(
                    state,
                    "",
                    listOf(),
                ))

                // OAuth 제공자의 인증 URL 구성
                val authUrl = URLBuilder("https://chzzk.naver.com/account-interlock").apply {
                    parameters.append("clientId", dotenv["NAVER_CLIENT_ID"]) // 비표준 파라미터 이름
                    parameters.append("redirectUri", "${dotenv["HOST"]}/auth/callback")
                    parameters.append("state", state)
                    // 추가적인 파라미터가 필요하면 여기에 추가
                }.build().toString()

                // 사용자에게 인증 페이지로 리다이렉트
                call.respondRedirect(authUrl)
            }
            get("/callback") {
                val receivedState = call.parameters["state"]
                val code = call.parameters["code"]

                // 세션에서 상태 값 가져오기
                val session = call.sessions.get<UserSession>()
                if (session == null || session.state != receivedState) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid state parameter")
                    return@get
                }

                if (code == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing code parameter")
                    return@get
                }
                try {
                    // Access Token 요청
                    val tokenRequest = TokenRequest(
                        grantType = "authorization_code",
                        state = session.state,
                        code = code,
                        clientId = dotenv["NAVER_CLIENT_ID"],
                        clientSecret = dotenv["NAVER_CLIENT_SECRET"]
                    )

                    val response = applicationHttpClient.post("https://chzzk.naver.com/auth/v1/token") {
                        contentType(ContentType.Application.Json)
                        setBody(tokenRequest)
                    }

                    val tokenResponse = response.body<TokenResponse>()

                    if(tokenResponse.content == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to obtain access token")
                        return@get
                    }

                    // Access Token 사용: 예를 들어, 사용자 정보 요청
                    val userInfo = getChzzkUser(tokenResponse.content.accessToken)

                    if(userInfo.content != null) {
                        var user = UserService.getUser(userInfo.content.channelId)

                        if(user == null) {
                            user = UserService.saveUser(userInfo.content.channelName , userInfo.content.channelId)
                        }

                        call.sessions.set(
                            UserSession(
                                session.state,
                                userInfo.content.channelId,
                                listOf()
                            )
                        )
                        UserService.setRefreshToken(user,
                            tokenResponse.content.accessToken,
                            tokenResponse.content.refreshToken ?: ""
                        )

                        dispatcher.post(UserRegisterEvent(user.token))

                        call.respondRedirect(getFrontendURL(""))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Failed to obtain access token")
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
    val discordGuildList: List<String>,

)

@Serializable
data class TokenRequest(
    val grantType: String,
    val state: String,
    val code: String,
    val clientId: String,
    val clientSecret: String
)

@Serializable
data class TokenResponse(
    val code: Int,
    val message: String?,
    val content: TokenResponseBody?
)

@Serializable
data class TokenResponseBody(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val refreshToken: String? = null
)

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

@Serializable
data class ChzzkMeApi(
    val channelId: String,
    val channelName: String,
    val nickname: String,
)

@Serializable
data class ChzzkApi<T>(
    val code: Int,
    val message: String?,
    val content: T?
)

suspend fun getChzzkUser(accessToken: String): ChzzkApi<ChzzkMeApi> {
    val response = applicationHttpClient.get("https://openapi.chzzk.naver.com/open/v1/users/me") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }

    return response.body<ChzzkApi<ChzzkMeApi>>()
}

fun generateSecureRandomState(): String {
    return BigInteger(130, SecureRandom()).toString(32)
}
