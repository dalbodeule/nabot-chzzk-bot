package space.mori.chzzk_bot.chatbot.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData

interface CommandInterface {
    val name: String
    fun run(event: SlashCommandInteractionEvent, bot: JDA): Unit
    val command: CommandData
}
