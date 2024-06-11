package space.mori.chzzk_bot.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.build.Commands
import space.mori.chzzk_bot.dotenv
import space.mori.chzzk_bot.logger
import kotlin.concurrent.thread

class Discord {
    lateinit var bot: JDA
    var guild: Guild? = null

    internal fun enable() {
        try {
            val thread = thread {
                bot = JDABuilder.createDefault(dotenv["DISCORD_TOKEN"])
                    .setActivity(Activity.playing("치지직 보는중"))
                    .build().awaitReady()

                guild = bot.getGuildById(dotenv["GUILD_ID"])

                bot.updateCommands().addCommands(
                    Commands.slash("ping", "Pong!")
                ).queue()

                if (guild == null) {
                    logger.info("No guild found!")
                    this.disable()
                }
            }
        } catch(e: Exception) {
            logger.info("Could not enable Discord!")
            logger.debug(e.stackTraceToString())
        }
    }

    internal fun disable() {
        try {
            bot.shutdown()
        } catch(e: Exception) {
            logger.info("Error while shutting down Discord!")
            logger.debug(e.stackTraceToString())
        }
    }
}