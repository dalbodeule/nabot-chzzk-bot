package space.mori.chzzk_bot

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.discord.Discord
import space.mori.chzzk_bot.chzzk.Connector as ChzzkConnector
import java.util.concurrent.TimeUnit

val dotenv = dotenv()
val logger: Logger = LoggerFactory.getLogger("main")

fun main(args: Array<String>) {
    val discord = Discord()

    Connector
    ChzzkConnector

    discord.enable()

    if(dotenv.get("RUN_AGENT", "false").toBoolean()) {
        runBlocking {
            delay(TimeUnit.MINUTES.toMillis(1))
            discord.disable()
        }
    }
}