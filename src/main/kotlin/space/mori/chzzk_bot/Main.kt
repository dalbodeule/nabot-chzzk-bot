package space.mori.chzzk_bot

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chzzk.ChzzkHandler
import space.mori.chzzk_bot.discord.Discord
import space.mori.chzzk_bot.chzzk.Connector as ChzzkConnector
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

val dotenv = dotenv {
    ignoreIfMissing = true
}
val logger: Logger = LoggerFactory.getLogger("main")

val discord = Discord()

val connector = Connector
val chzzkConnector = ChzzkConnector
val chzzkHandler = ChzzkHandler

fun main(args: Array<String>) {
    discord.enable()
    chzzkHandler.enable()

    if(dotenv.get("RUN_AGENT", "false").toBoolean()) {
        runBlocking {
            delay(TimeUnit.MINUTES.toMillis(1))
            exitProcess(0)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        chzzkHandler.disable()
        discord.disable()
        connector.dataSource.close()
    })
}