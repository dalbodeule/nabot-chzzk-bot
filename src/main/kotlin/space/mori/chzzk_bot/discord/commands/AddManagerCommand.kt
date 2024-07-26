package space.mori.chzzk_bot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.discord.CommandInterface
import space.mori.chzzk_bot.services.ManagerService
import space.mori.chzzk_bot.services.UserService

object AddManagerCommand : CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name: String = "addmanager"
    override val command = Commands.slash(name, "매니저를 추가합니다.")
        .addOptions(OptionData(OptionType.USER, "user", "추가할 유저를 선택하세요.", true))

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        val manager = event.getOption("user")?.asUser

        if(manager == null) {
            event.hook.sendMessage("유저는 필수사항입니다.").queue()
            return
        }
        if(manager.idLong == event.user.idLong) {
            event.hook.sendMessage("자신은 매니저로 설정할 수 없습니다.").queue()
            return
        }

        val user = UserService.getUser(event.user.idLong)
        if(user == null) {
            event.hook.sendMessage("치지직 계정을 찾을 수 없습니다.").queue()
            return
        }

        try {
            ManagerService.saveManager(user, manager.idLong, manager.effectiveName)
            event.hook.sendMessage("등록이 완료되었습니다. ${manager.effectiveName}").queue()
        } catch (e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}