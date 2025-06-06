package space.mori.chzzk_bot.chatbot.utils

import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import space.mori.chzzk_bot.chatbot.chzzk.dotenv
import space.mori.chzzk_bot.common.utils.IData
import space.mori.chzzk_bot.common.utils.client
import xyz.r2turntrue.chzzk4j.ChzzkClient
import java.io.IOException


data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val tokenType: String = "Bearer",
    val scope: String
)

fun ChzzkClient.refreshAccessToken(refreshToken: String): Pair<String, String> {
    val url = "https://openapi.chzzk.naver.com/auth/v1/token"
    val request = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(gson.toJson(mapOf(
            "grantType" to "refresh_token",
            "refreshToken" to refreshToken,
            "clientId" to dotenv["NAVER_CLIENT_ID"],
            "clientSecret" to dotenv["NAVER_CLIENT_SECRET"]
        )).toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()

    client.newCall(request).execute().use { response ->
        try {
            if(!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val body = response.body?.string()
            val data = gson.fromJson(body, object: TypeToken<IData<RefreshTokenResponse>>() {})

            return Pair(data.content.accessToken, data.content.refreshToken)
        } catch(e: Exception) {
            throw e
        }
    }
}