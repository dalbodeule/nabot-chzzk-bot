package space.mori.chzzk_bot.chatbot.chzzk

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import xyz.r2turntrue.chzzk4j.ChzzkClient
import xyz.r2turntrue.chzzk4j.ChzzkClientBuilder
import xyz.r2turntrue.chzzk4j.auth.ChzzkOauthLoginAdapter
import xyz.r2turntrue.chzzk4j.auth.ChzzkSimpleUserLoginAdapter
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Connector {
    val client: ChzzkClient = ChzzkClientBuilder(dotenv["NAVER_CLIENT_ID"], dotenv["NAVER_CLIENT_SECRET"])
        .build()
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getChannel(channelId: String): ChzzkChannel? = client.fetchChannel(channelId)

    init {
        logger.info("chzzk logged: ${client.isLoggedIn}")

        client.loginAsync().join()
    }

    fun getClient(accessToken: String, refreshToken: String): ChzzkClient {
        val adapter = ChzzkSimpleUserLoginAdapter(accessToken, refreshToken)
        val client = ChzzkClientBuilder(dotenv["NAVER_CLIENT_ID"], dotenv["NAVER_CLIENT_SECRET"])
            .withLoginAdapter(adapter)
            .build()

        return client
    }
}