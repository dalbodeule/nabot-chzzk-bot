package space.mori.chzzk_bot.chzzk

import io.github.cdimascio.dotenv.dotenv
import xyz.r2turntrue.chzzk4j.Chzzk
import xyz.r2turntrue.chzzk4j.ChzzkBuilder

object Connector {
    private val dotenv = dotenv()
    val chzzk: Chzzk = ChzzkBuilder()
        .withAuthorization(dotenv["NID_AUT"], dotenv["NID_SES"])
        .build()

    fun getChannel(channelId: String) = chzzk.getChannel(channelId)
}