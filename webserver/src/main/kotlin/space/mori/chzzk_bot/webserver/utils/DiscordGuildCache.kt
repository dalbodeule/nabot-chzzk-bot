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

    suspend fun getCachedGuilds(guildId: String): Guild? {
        val now = Instant.now()

        return if(cache.isNotEmpty() && cache[guildId]?.timestamp?.plusSeconds(EXP_SECONDS)?.isAfter(now) == false) {
            cache[guildId]?.guild
        } else {
            fetchAllGuilds()
            cache[guildId]?.guild
        }
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

        return result.body<List<DiscordGuildListAPI>>()
    }

    private suspend fun fetchAllGuilds() {
        var lastGuildId: String? = null
        while (true) {
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
            delay(1500)
        }
    }

    fun addGuild(guilds: Map<String, CachedGuilds>) {
        cache.putAll(guilds)
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