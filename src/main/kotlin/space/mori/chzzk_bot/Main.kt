package space.mori.chzzk_bot

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.discord.Discord

val dotenv = dotenv()
val logger: Logger = LoggerFactory.getLogger("main")

fun main(args: Array<String>) {
    val discord = Discord()

    discord.enable()
}