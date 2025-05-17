package space.mori.chzzk_bot.chatbot.chzzk

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.ChzzkUserFindEvent
import space.mori.chzzk_bot.common.events.ChzzkUserReceiveEvent
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import xyz.r2turntrue.chzzk4j.ChzzkClient
import xyz.r2turntrue.chzzk4j.ChzzkClientBuilder
import xyz.r2turntrue.chzzk4j.auth.ChzzkLegacyLoginAdapter
import xyz.r2turntrue.chzzk4j.auth.ChzzkSimpleUserLoginAdapter
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import xyz.r2turntrue.chzzk4j.types.channel.live.ChzzkLiveDetail
import kotlin.getValue

val dotenv = dotenv {
    ignoreIfMissing = true
}

@OptIn(DelicateCoroutinesApi::class)
object Connector {
    val adapter = ChzzkLegacyLoginAdapter(dotenv["NID_AUT"], dotenv["NID_SES"])
    val client: ChzzkClient = ChzzkClientBuilder(dotenv["NAVER_CLIENT_ID"], dotenv["NAVER_CLIENT_SECRET"])
        .withLoginAdapter(adapter)
        .build()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun getChannel(channelId: String): ChzzkChannel? = client.fetchChannel(channelId)
    fun getLive(channelId: String): ChzzkLiveDetail? = client.fetchLiveDetail(channelId)

    init {
        logger.info("chzzk logged: ${client.isLoggedIn}")

        client.loginAsync().join()

        dispatcher.subscribe(ChzzkUserFindEvent::class) { event ->
            GlobalScope.launch {
                val user = getChannel(event.uid)

                dispatcher.post(ChzzkUserReceiveEvent(
                    find = user != null,
                    uid = user?.channelId,
                    nickname = user?.channelName,
                    isStreamOn = user?.isBroadcasting,
                    avatarUrl = user?.channelImageUrl
                ))
            }
        }
    }

    fun getClient(accessToken: String, refreshToken: String): ChzzkClient {
        val adapter = ChzzkSimpleUserLoginAdapter(accessToken, refreshToken)
        val client = ChzzkClientBuilder(dotenv["NAVER_CLIENT_ID"], dotenv["NAVER_CLIENT_SECRET"])
            .withLoginAdapter(adapter)
            .build()

        return client
    }


}