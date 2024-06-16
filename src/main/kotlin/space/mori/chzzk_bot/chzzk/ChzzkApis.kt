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
    val badge: Badge? = null,
    val title: Title? = null,
    val verifiedMark: Boolean = false,
    val activityBadges: List<String> = emptyList(),
    val streamingProperty: StreamingProperty = StreamingProperty()
)

data class Badge(
    val imageUrl: String = ""
)

data class Title(
    val name: String = "",
    val color: String = ""
)

data class StreamingProperty(
    val following: Following? = Following(),
    val nicknameColor: NicknameColor = NicknameColor()
)

data class Following(
    val followDate: String? = null
)

data class NicknameColor(
    val colorCode: String = ""
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
            val body = response.body?.string()
            val follow = gson.fromJson(body, IFollow::class.java)

            return follow
        } catch(e: Exception) {
            throw e
        }
    }
}