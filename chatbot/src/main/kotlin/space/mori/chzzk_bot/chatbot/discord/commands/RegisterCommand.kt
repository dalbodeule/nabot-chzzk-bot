package space.mori.chzzk_bot.chatbot.discord.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.chzzk.ChzzkHandler
import space.mori.chzzk_bot.chatbot.chzzk.Connector
import space.mori.chzzk_bot.chatbot.discord.CommandInterface
import space.mori.chzzk_bot.common.services.UserService

object RegisterCommand: CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name = "register"

    private val regex = """(?:.+chzzk\.naver\.com/)?([a-f0-9]{32})?(?:/live)?${'$'}""".toRegex()

    override val command =  Commands.slash(name, "치지직 계정을 등록합니다.")

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        event.hook.sendMessage("가입은 여기를 참고해주세요!\nhttps://nabot.mori.space/register").queue()

        /* val chzzkID = event.getOption("chzzk_id")?.asString
        if(chzzkID == null) {
            event.hook.sendMessage("치지직 계정은 필수 입력입니다.").queue()
            return
        }
        val matchResult = regex.find(chzzkID)
        val matchedChzzkId = matchResult?.groups?.get(1)?.value

        val chzzkChannel = matchedChzzkId?.let { Connector.getChannel(it) }
        if (chzzkChannel == null) {
            event.hook.sendMessage("치지직 계정을 찾을 수 없습니다.").queue()
            return
        }

        try {

            val user = UserService.saveUser(chzzkChannel.channelName, chzzkChannel.channelId, event.user.idLong)
            CoroutineScope(Dispatchers.Main).launch {
                ChzzkHandler.addUser(chzzkChannel, user)
            }
            event.hook.sendMessage("등록이 완료되었습니다. `${chzzkChannel.channelId}` - `${chzzkChannel.channelName}`")
        } catch(e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        } */
    }
}