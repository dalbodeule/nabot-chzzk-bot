package space.mori.chzzk_bot.chzzk

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class IData<T>(
    val code: Int = 200,
    val message: String? = null,
    val content: T
)

// Follows
data class IFollowContent(
    val userIdHash: String = "",
    val nickname: String = "",
    val profileImageUrl: String = "",
    val userRoleCode: String = "",
    val badge: Badge? = null,
    val title: Title? = null,
    val verifiedMark: Boolean = false,
    val activityBadges: List<Badge> = emptyList(),
    val streamingProperty: StreamingProperty = StreamingProperty()
)

data class Badge(
    val badgeNo: Int?,
    val badgeId: String?,
    val imageUrl: String?,
    val title: String?,
    val description: String?,
    val activated: Boolean?
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

// Stream info
data class IStreamInfo(
    val liveId: Int = 0,
    val liveTitle: String = "",
    val status: String = "",
    val liveImageUrl: String = "",
    val defaultThumbnailImageUrl: String? = null,
    val concurrentUserCount: Int = 0,
    val accumulateCount: Int = 0,
    val openDate: String = "",
    val closeDate: String = "",
    val adult: Boolean = false,
    val clipActive: Boolean = false,
    val tags: List<String> = emptyList(),
    val chatChannelId: String = "",
    val categoryType: String = "",
    val liveCategory: String = "",
    val liveCategoryValue: String = "",
    val chatActive: Boolean = true,
    val chatAvailableGroup: String = "",
    val paidPromotion: Boolean = false,
    val chatAvailableCondition: String = "",
    val minFollowerMinute: Int = 0,
    val livePlaybackJson: String = "",
    val p2pQuality: List<Any> = emptyList(),
    val channel: Channel = Channel(),
    val livePollingStatusJson: String = "",
    val userAdultStatus: String? = null,
    val chatDonationRankingExposure: Boolean = true,
    val adParameter: AdParameter = AdParameter()
)

data class Channel(
    val channelId: String = "",
    val channelName: String = "",
    val channelImageUrl: String = "",
    val verifiedMark: Boolean = false
)

data class AdParameter(
    val tag: String = ""
)

// OkHttpClient에 Interceptor 추가
val client = OkHttpClient.Builder()
    .addNetworkInterceptor { chain ->
        chain.proceed(
            chain.request()
                .newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()
        )
    }
    .build()
val gson = Gson()

fun getFollowDate(chatID: String, userId: String) : IData<IFollowContent> {
    val url = "https://comm-api.game.naver.com/nng_main/v1/chats/$chatID/users/$userId/profile-card?chatType=STREAMING"
    val request = Request.Builder()
        .url(url)
        .build()

    client.newCall(request).execute().use { response ->
        try {
            if(!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val body = response.body?.string()
            val follow = gson.fromJson(body, object: TypeToken<IData<IFollowContent>>() {})

            return follow
        } catch(e: Exception) {
            println(e.stackTrace)
            throw e
        }
    }
}

fun getStreamInfo(userId: String) : IData<IStreamInfo> {
    val url = "https://api.chzzk.naver.com/service/v2/channels/${userId}/live-detail"
    val request = Request.Builder()
        .url(url)
        .build()

    client.newCall(request).execute().use { response ->
        try {
            if(!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val body = response.body?.string()
            val follow = gson.fromJson(body, object: TypeToken<IData<IStreamInfo>>() {})

            return follow
        } catch(e: Exception) {
            throw e
        }
    }
}
