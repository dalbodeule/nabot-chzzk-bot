package space.mori.chzzk_bot.chzzk

import org.slf4j.LoggerFactory
import xyz.r2turntrue.chzzk4j.Chzzk
import xyz.r2turntrue.chzzk4j.ChzzkBuilder
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import space.mori.chzzk_bot.dotenv

object Connector {
    val chzzk: Chzzk = ChzzkBuilder()
        .withAuthorization(dotenv["NID_AUT"], dotenv["NID_SES"])
        .build()
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getChannel(channelId: String): ChzzkChannel? = chzzk.getChannel(channelId)

    init {
        logger.info("chzzk logged: ${chzzk.isLoggedIn} / ${chzzk.loggedUser?.nickname ?: "----"}")
    }
}