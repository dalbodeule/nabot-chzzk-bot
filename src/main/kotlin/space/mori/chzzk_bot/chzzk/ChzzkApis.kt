package space.mori.chzzk_bot.chzzk

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class IFollow(
    val code: Int = 200,
    val message: String? = null,
    val content: IFollowContent = IFollowContent()
)

data class IFollowContent(
    val userIdHash: String = "",
    val nickname: String = "",
    val profileImageUrl: String = "",
    val userRoleCode: String = "",
    val badge: String? = null,
    val title: String? = null,
    val verifiedMark: Boolean = false,
    val activityBadges: List<String> = emptyList(),
    val streamingProperty: Map<String, String> = mapOf(
        "following" to "",
        "nicknameColor" to ""
    )
)

val client = OkHttpClient()
val gson = Gson()

fun getFollowDate(chatID: String, userId: String) : IFollow {
    val url = "https://comm-api.game.naver.com/nng_main/v1/chats/$chatID/users/$userId/profile-card?chatType=STREAMING"
    val request = Request.Builder()
        .url(url)
        .build()

    client.newCall(request).execute().use { response ->
        try {
            if(!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val follow = gson.fromJson(response.body?.string(), IFollow::class.java)

            return follow
        } catch(e: Exception) {
            throw e
        }
    }
}