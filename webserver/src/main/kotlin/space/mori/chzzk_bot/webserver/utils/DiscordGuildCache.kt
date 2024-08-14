package space.mori.chzzk_bot.webserver.utils

import applicationHttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import space.mori.chzzk_bot.webserver.DiscordGuildListAPI
import space.mori.chzzk_bot.webserver.dotenv
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object DiscordGuildCache {
    private val cache = ConcurrentHashMap<String, CachedGuilds>()
    private const val EXP_SECONDS = 600L

    private suspend fun getCachedGuilds(guildId: String): Guild? {
        val now = Instant.now()

        if(cache.isEmpty() || !cache.containsKey(guildId) || cache[guildId]!!.timestamp.plusSeconds(EXP_SECONDS).isBefore(now)) {
            fetchAllGuilds()
        }
        return cache[guildId]?.guild
    }

    suspend fun getCachedGuilds(guildId: List<String>): List<Guild> {
        return guildId.mapNotNull { getCachedGuilds(it) }
    }

    private suspend fun fetchGuilds(beforeGuildId: String? = null, limit: Int = 100): List<DiscordGuildListAPI> {
        val result = applicationHttpClient.get("https://discord.com/api/users/@me/guilds") {
            headers {
                append(HttpHeaders.Authorization, "Bot ${dotenv["DISCORD_TOKEN"]}")
            }
            parameter("limit", limit)
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

    private suspend fun fetchAllGuilds() {
        var lastGuildId: String? = null
        while (true) {
            while(DiscordRatelimits.isLimited()) {
                delay(DiscordRatelimits.getRateReset().takeIf { it > 0 } ?: 5000L)
            }
            val guilds = fetchGuilds(lastGuildId)
            if (guilds.isEmpty()) {
                break
            }

            guilds.forEach {
                cache[it.id] = CachedGuilds(
                    Guild(it.id, it.name, it.icon, it.banner)
                )
            }
            lastGuildId = guilds.last().id
        }
    }

    fun addGuild(guilds: Map<String, Guild>) {
        cache.putAll(guilds.map {
            it.key to CachedGuilds(it.value, Instant.now())
        })
    }
}

data class CachedGuilds(
    val guild: Guild,
    val timestamp: Instant = Instant.now()
)

@Serializable
data class Guild(
    val id: String,
    val name: String,
    val icon: String?,
    val banner: String?,
)