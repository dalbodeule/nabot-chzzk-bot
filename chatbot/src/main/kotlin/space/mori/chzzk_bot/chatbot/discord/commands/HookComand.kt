package space.mori.chzzk_bot.chatbot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.discord.CommandInterface
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.DiscordRegisterEvent
import space.mori.chzzk_bot.common.services.UserService
import java.util.concurrent.ConcurrentHashMap

object HookComand: CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name = "hook"
    override val command =  Commands.slash(name, "디스코드 계정과 서버를 등록합니다.")
        .addOptions(OptionData(OptionType.STRING, "token", "디스코드 연동 토큰을 입력해주세요."))

    // key: Token, value: ChzzkID
    private val hookMap = ConcurrentHashMap<String, String>()
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    init {
        dispatcher.subscribe(DiscordRegisterEvent::class) {
            hookMap[it.token] = it.user
        }
    }

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        val token = event.getOption("token")?.asString
        if(token == null) {
            event.hook.sendMessage("Token은 필수 입력입니다.").queue()
            return
        }

        val user = UserService.getUser(hookMap[token] ?: "")

        if (user == null) {
            event.hook.sendMessage("치지직 계정을 찾을 수 없습니다.").queue()
            return
        }

        if(event.guild == null) {
            event.hook.sendMessage("이 명령어는 디스코드 서버 안에서 실행해야 합니다.").queue()
            return
        }

        try {
            UserService.updateUser(user, event.user.idLong)
            UserService.updateLiveAlert(user, event.guild!!.idLong, event.channelIdLong, "")
            hookMap.remove(token)
            event.hook.sendMessage("등록이 완료되었습니다. `${user.username}`")
        } catch(e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}