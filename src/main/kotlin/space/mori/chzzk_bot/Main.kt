package space.mori.chzzk_bot

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.chzzk.ChzzkHandler
import space.mori.chzzk_bot.chatbot.discord.Discord
import space.mori.chzzk_bot.chatbot.chzzk.Connector as ChzzkConnector
import space.mori.chzzk_bot.common.Connector
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.webserver.start
import space.mori.chzzk_bot.webserver.stop
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

val dotenv = dotenv {
    ignoreIfMissing = true
}
val logger: Logger = LoggerFactory.getLogger("main")

fun main(args: Array<String>) {
    val dispatcher = module {
        single { CoroutinesEventBus() }
    }
    startKoin {
        modules(dispatcher)
    }

    val discord = Discord()

    val connector = Connector
    val chzzkConnector = ChzzkConnector
    val chzzkHandler = ChzzkHandler

    discord.enable()
    chzzkHandler.enable()
    chzzkHandler.runStreamInfo()
    start()

    if(dotenv.get("RUN_AGENT", "false").toBoolean()) {
        runBlocking {
            delay(TimeUnit.MINUTES.toMillis(1))
            exitProcess(0)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        stop()

        chzzkHandler.stopStreamInfo()
        chzzkHandler.disable()
        discord.disable()
        connector.dataSource.close()
    })
}