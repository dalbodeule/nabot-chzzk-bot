package space.mori.chzzk_bot.chatbot.chzzk

import io.github.cdimascio.dotenv.dotenv
import jdk.internal.net.http.common.Pair
import jdk.internal.net.http.common.Pair.pair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.GetUserEvents
import space.mori.chzzk_bot.common.events.GetUserType
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.LiveStatusService
import space.mori.chzzk_bot.common.services.UserService
import xyz.r2turntrue.chzzk4j.Chzzk
import xyz.r2turntrue.chzzk4j.ChzzkBuilder
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Connector {
    val chzzk: Chzzk = ChzzkBuilder()
        .withAuthorization(dotenv["NID_AUT"], dotenv["NID_SES"])
        .build()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun getChannel(channelId: String): ChzzkChannel? = chzzk.getChannel(channelId)
    fun getChannelAndUser(channelId: String): Pair<ChzzkChannel, User?>? = pair(chzzk.getChannel(channelId), UserService.getUser(channelId))


    init {
        logger.info("chzzk logged: ${chzzk.isLoggedIn} / ${chzzk.loggedUser?.nickname ?: "----"}")
        dispatcher.subscribe(GetUserEvents::class) {
            if (it.type == GetUserType.REQUEST) {
                CoroutineScope(Dispatchers.Default).launch {
                    val channel = getChannelAndUser(it.uid ?: "")
                    if(channel == null) dispatcher.post(GetUserEvents(
                        GetUserType.NOTFOUND, null, null, null, null
                    ))
                    else dispatcher.post(GetUserEvents(
                        GetUserType.RESPONSE,
                        channel.first.channelId,
                        channel.first.channelName,
                        LiveStatusService.getLiveStatus(channel.second!!)?.status ?: false,
                        channel.first.channelImageUrl
                    ))
                }
            }
        }
    }
}