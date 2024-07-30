package space.mori.chzzk_bot.chatbot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.chzzk.ChzzkHandler
import space.mori.chzzk_bot.chatbot.chzzk.Connector
import space.mori.chzzk_bot.chatbot.discord.CommandInterface
import space.mori.chzzk_bot.common.services.CommandService
import space.mori.chzzk_bot.common.services.ManagerService
import space.mori.chzzk_bot.common.services.UserService

object UpdateCommand : CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name: String = "update"
    override val command = Commands.slash(name, "명령어를 수정합니다.")
        .addOptions(OptionData(OptionType.STRING, "label", "수정할 명령어를 입력하세요.", true))
        .addOptions(OptionData(OptionType.STRING, "content", "표시될 텍스트를 입력하세요.", true))
        .addOptions(OptionData(OptionType.STRING, "fail_content", "카운터 업데이트 실패시 표시될 텍스트를 입력하세요.", false))

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        val label = event.getOption("label")?.asString
        val content = event.getOption("content")?.asString
        val failContent = event.getOption("fail_content")?.asString

        if(label == null || content == null) {
            event.hook.sendMessage("명령어와 텍스트는 필수 입력입니다.").queue()
            return
        }

        var user = UserService.getUser(event.user.idLong)
        val manager = event.guild?.idLong?.let { ManagerService.getUser(it, event.user.idLong) }
        if(user == null && manager == null) {
            event.hook.sendMessage("당신은 이 명령어를 사용할 수 없습니다.").queue()
            return
        }

        if (manager != null) {
            user = manager.user
            ManagerService.updateManager(user, event.user.idLong, event.user.effectiveName)
        }

        val chzzkChannel = Connector.getChannel(user!!.token)

        try {
            CommandService.updateCommand(user, label, content, failContent ?: "")
            chzzkChannel?.let { ChzzkHandler.reloadCommand(it) }
            event.hook.sendMessage("등록이 완료되었습니다. $label = $content").queue()
        } catch (e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}