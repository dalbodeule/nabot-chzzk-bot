package space.mori.chzzk_bot.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import space.mori.chzzk_bot.dotenv
import org.slf4j.LoggerFactory

class Discord: ListenerAdapter() {
    private lateinit var bot: JDA
    private var guild: Guild? = null
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val commands = getCommands()

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        commands.find { it.name == event.name }?.run(event, bot)
    }

    internal fun enable() {
        val thread = Thread {
            try {
                bot = JDABuilder.createDefault(dotenv["DISCORD_TOKEN"])
                    .setActivity(Activity.playing("치지직 보는중"))
                    .addEventListeners(this)
                    .build().awaitReady()

                guild = bot.getGuildById(dotenv["GUILD_ID"])

                bot.updateCommands()
                    .addCommands(* commands.map { it.command }.toTypedArray())
                    .onSuccess { logger.info("Command update success!") }
                    .queue()


                if (guild == null) {
                    logger.info("No guild found!")
                    this.disable()
                }
            } catch (e: Exception) {
                logger.info("Could not enable Discord!")
                logger.debug(e.stackTraceToString())
            }
        }
        thread.start()
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