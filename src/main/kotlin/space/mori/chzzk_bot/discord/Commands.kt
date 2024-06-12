package space.mori.chzzk_bot.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.reflections.Reflections

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Command

interface CommandInterface {
    val name: String
    fun run(event: SlashCommandInteractionEvent, bot: JDA): Unit
    val command: CommandData
}

fun getCommands(): List<CommandInterface> {
    val commandList = mutableListOf<CommandInterface>()

    val packageName = "space.mori.chzzk_bot.discord.commands"
    val reflections = Reflections(packageName)
    val annotatedClasses = reflections.getTypesAnnotatedWith(Command::class.java)

    for(clazz in annotatedClasses) {
        val obj = clazz.kotlin.objectInstance
        if(obj is CommandInterface) {
            commandList.add(obj)
        } else {
            throw IllegalStateException("${clazz.name} is not a CommandInterface")
        }
    }

    return commandList.toList()
}