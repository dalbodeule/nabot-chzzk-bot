package space.mori.chzzk_bot.webserver.utils

import applicationHttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import space.mori.chzzk_bot.common.utils.logger
import space.mori.chzzk_bot.webserver.DiscordGuildListAPI
import space.mori.chzzk_bot.webserver.GuildRole
import space.mori.chzzk_bot.webserver.dotenv
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object DiscordGuildCache {
    private val cache = ConcurrentHashMap<String, CachedGuilds>()
    private const val EXP_SECONDS = 600L
    private val mutex = Mutex()

    suspend fun getCachedGuilds(guildId: String): Guild? {
        val now = Instant.now()
        val guild = cache[guildId]

        if(guild == null || guild.timestamp.plusSeconds(EXP_SECONDS).isBefore(now) || !guild.isBotAvailable) {
            mutex.withLock {
                if(guild == null || guild.timestamp.plusSeconds(EXP_SECONDS).isBefore(now) || !guild.isBotAvailable) {
                    fetchAllGuilds()
                }
                if(guild?.guild?.roles?.isEmpty() == true) {
                    guild.guild.roles = fetchGuildRoles(guildId)
                }
            }
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

    private suspend fun fetchGuildRoles(guildId: String): List<GuildRole> {
        if(DiscordRatelimits.isLimited()) {
            delay(DiscordRatelimits.getRateReset())
        }
        val result = applicationHttpClient.get("https://discord.com/api/guilds/${guildId}/roles") {
            headers {
                append(HttpHeaders.Authorization, "Bot ${dotenv["DISCORD_TOKEN"]}")
            }
        }

        val rateLimit = result.headers["X-RateLimit-Limit"]?.toIntOrNull()
        val remaining = result.headers["X-RateLimit-Remaining"]?.toIntOrNull()
        val resetAfter = result.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull()?.toLong()?.plus(1L)

        DiscordRatelimits.setRateLimit(rateLimit, remaining, resetAfter)

        return result.body<List<GuildRole>>()
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
                        Guild(it.id, it.name, it.icon, it.banner, it.roles ?: emptyList()),
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
    var roles: List<GuildRole> = emptyList(),
)