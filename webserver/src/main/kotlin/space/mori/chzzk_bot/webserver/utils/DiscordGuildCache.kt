package space.mori.chzzk_bot.webserver.utils

import applicationHttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.webserver.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object DiscordGuildCache {
    private val cache = ConcurrentHashMap<String, CachedGuilds>()
    private const val EXP_SECONDS = 600L
    private val mutex = Mutex()
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getCachedGuilds(guildId: String): Guild? {
        val now = Instant.now()
        var guild = cache[guildId]

        if(guild == null || guild.timestamp.plusSeconds(EXP_SECONDS).isBefore(now) || !guild.isBotAvailable) {
            mutex.withLock {
                if(guild == null || guild!!.timestamp.plusSeconds(EXP_SECONDS).isBefore(now) || !guild!!.isBotAvailable) {
                    fetchAllGuilds()
                    guild = cache[guildId]
                }
            }
        }

        try {
            if(guild == null) return null
            if (guild!!.guild.roles.isEmpty()) {
                val roles = fetchGuildRoles(guildId)
                guild!!.guild.roles.addAll(roles)
            }
            if (guild!!.guild.channel.isEmpty()) {
                val channels = fetchGuildChannels(guildId)
                guild!!.guild.channel.addAll(channels)
            }
        } catch(e: Exception) {
            logger.info("guild fetch is failed. ${e.stackTraceToString()}")
            return null
        }
        return cache[guildId]?.guild
    }

    suspend fun getCachedGuilds(guildId: List<String>): List<Guild> {
        return guildId.mapNotNull { getCachedGuilds(it) }
    }

    private suspend fun fetchGuilds(beforeGuildId: String? = null): List<DiscordGuildListAPI> {
        if(DiscordRatelimits.isLimited()) {
            delay(DiscordRatelimits.getRateReset())
        }
        val result = applicationHttpClient.get("https://discord.com/api/users/@me/guilds") {
            headers {
                append(HttpHeaders.Authorization, "Bot ${dotenv["DISCORD_TOKEN"]}")
            }
            parameter("limit", 200)
            if (beforeGuildId != null) {
                parameter("before", beforeGuildId)
            }
        }

        val rateLimit = result.headers["X-RateLimit-Limit"]?.toIntOrNull()
        val remaining = result.headers["X-RateLimit-Remaining"]?.toIntOrNull()
        val resetAfter = result.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull()?.toLong()?.plus(1L)

        DiscordRatelimits.setRateLimit(rateLimit, remaining, resetAfter)

        return result.body<List<DiscordGuildListAPI>>()
    }

    private suspend fun fetchGuildRoles(guildId: String): MutableList<GuildRole> {
        if(DiscordRatelimits.isLimited()) {
            delay(DiscordRatelimits.getRateReset())
        }
        try {
            val result = applicationHttpClient.get("https://discord.com/api/guilds/${guildId}/roles") {
                headers {
                    append(HttpHeaders.Authorization, "Bot ${dotenv["DISCORD_TOKEN"]}")
                }
            }

            val rateLimit = result.headers["X-RateLimit-Limit"]?.toIntOrNull()
            val remaining = result.headers["X-RateLimit-Remaining"]?.toIntOrNull()
            val resetAfter = result.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull()?.toLong()?.plus(1L)

            DiscordRatelimits.setRateLimit(rateLimit, remaining, resetAfter)

            if (result.status != HttpStatusCode.OK) {
                logger.error("Failed to fetch data from Discord API. Status: ${result.status} ${result.bodyAsText()}")
                return mutableListOf()
            }

            val parsed = result.body<MutableList<GuildRole>>()

            return parsed
        } catch(e: Exception) {
            logger.info("fetchGuildRoles error: ${e.stackTraceToString()}")
            return mutableListOf()
        }
    }

    private suspend fun fetchGuildChannels(guildId: String): MutableList<GuildChannel> {
        if(DiscordRatelimits.isLimited()) {
            delay(DiscordRatelimits.getRateReset())
        }
        try {
            val result = applicationHttpClient.get("https://discord.com/api/guilds/${guildId}/channels") {
                headers {
                    append(HttpHeaders.Authorization, "Bot ${dotenv["DISCORD_TOKEN"]}")
                }
            }

            val rateLimit = result.headers["X-RateLimit-Limit"]?.toIntOrNull()
            val remaining = result.headers["X-RateLimit-Remaining"]?.toIntOrNull()
            val resetAfter = result.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull()?.toLong()?.plus(1L)

            DiscordRatelimits.setRateLimit(rateLimit, remaining, resetAfter)

            if (result.status != HttpStatusCode.OK) {
                logger.error("Failed to fetch data from Discord API. Status: ${result.status} ${result.bodyAsText()}")
                return mutableListOf()
            }

            val parsed = result.body<List<GuildChannel>>().filter { it.type == ChannelType.GUILD_TEXT.value }.toMutableList()

            return parsed
        } catch(e: Exception) {
            logger.info("fetchGuildRoles error: ${e.stackTraceToString()}")
            return mutableListOf()
        }
    }

    private suspend fun fetchAllGuilds() {
        var lastGuildId: String? = null
        while (true) {
            try {
                val guilds = fetchGuilds(lastGuildId)
                if (guilds.isEmpty()) {
                    break
                }

                guilds.forEach {
                    cache[it.id] = CachedGuilds(
                        Guild(it.id, it.name, it.icon, it.banner, it.roles?.toMutableList() ?: mutableListOf(), mutableListOf()),
                        Instant.now().plusSeconds(EXP_SECONDS),
                        true
                    )
                }
                lastGuildId = guilds.last().id
                if(guilds.size <= 200) break
            } catch(e: Exception) {
                logger.info("Exception in discord caches. ${e.stackTraceToString()}")
                return
            }
        }
    }

    fun addGuild(guilds: Map<String, Guild>) {
        cache.putAll(guilds.map {
            it.key to CachedGuilds(it.value, Instant.now().plusSeconds(EXP_SECONDS))
        })
    }
}

data class CachedGuilds(
    val guild: Guild,
    val timestamp: Instant = Instant.now(),
    val isBotAvailable: Boolean = false,
)

@Serializable
data class Guild(
    val id: String,
    val name: String,
    val icon: String?,
    val banner: String?,
    var roles: MutableList<GuildRole>,
    var channel: MutableList<GuildChannel>
)