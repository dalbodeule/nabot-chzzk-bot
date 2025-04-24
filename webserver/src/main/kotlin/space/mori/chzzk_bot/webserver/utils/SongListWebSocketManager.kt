package space.mori.chzzk_bot.webserver.utils

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.util.logging.Logger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import space.mori.chzzk_bot.common.events.SongType
import space.mori.chzzk_bot.webserver.routes.SongResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class SongListWebSocketManager(internal val logger: Logger) {
    companion object {
        private const val ACK_TIMEOUT_MS = 5000L
        private const val ACK_CHECK_INTERVAL_MS = 100L
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_DELAY_MS = 3000L
        internal const val PING_MESSAGE = "ping"
        internal const val PONG_MESSAGE = "pong"
        private const val KEEP_ALIVE_INTERVAL_MS = 30000L // 30초 간격으로 핑-퐁 메시지 전송
    }

    private val sessions = ConcurrentHashMap<String, WebSocketServerSession>()
    private val status = ConcurrentHashMap<String, SongType>()
    private val lastActivity = ConcurrentHashMap<String, Long>()
    private val pendingAcks = ConcurrentHashMap<String, Boolean>()

    // 핑-퐁 상태 관리를 위한 맵
    private val pingStatus = ConcurrentHashMap<String, Boolean>()
    private val pingJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    init {
        // 정기적으로 비활성 연결을 체크하는 백그라운드 작업
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    checkInactiveSessions()
                    delay(60000) // 1분마다 체크
                } catch (e: Exception) {
                    logger.error("비활성 세션 체크 중 오류 발생: ${e.message}")
                }
            }
        }
    }

    suspend fun addSession(uid: String, session: WebSocketServerSession) {
        mutex.lock()
        try {
            sessions[uid] = session
            lastActivity[uid] = System.currentTimeMillis()

            // 핑-퐁 작업 시작
            startPingPongJob(uid, session)

            logger.info("웹소켓 세션 추가됨: $uid")
        } finally {
            mutex.unlock()
        }
    }

    suspend fun removeSession(uid: String) {
        mutex.lock()
        try {
            sessions.remove(uid)
            status.remove(uid)
            lastActivity.remove(uid)
            pendingAcks.remove(uid)

            // 핑-퐁 작업 중지
            stopPingPongJob(uid)

            logger.info("웹소켓 세션 제거됨: $uid")
        } finally {
            mutex.unlock()
        }
    }

    private fun startPingPongJob(uid: String, session: WebSocketServerSession) {
        pingStatus[uid] = true
        pingJobs[uid] = CoroutineScope(Dispatchers.IO).launch {
            while (pingStatus[uid] == true && sessions[uid] != null) {
                try {
                    session.send(Frame.Text(PING_MESSAGE))
                    logger.debug("핑 메시지 전송: $uid")

                    // 응답 대기 (타임아웃 처리는 메시지 수신부에서)
                    mutex.lock()
                    try {
                        lastActivity[uid] = System.currentTimeMillis()
                    } finally {
                        mutex.unlock()
                    }

                    delay(KEEP_ALIVE_INTERVAL_MS)
                } catch (e: Exception) {
                    logger.error("핑-퐁 작업 중 오류 발생: ${e.message}")

                    // 연결에 문제가 있으면 세션을 제거
                    if (e is ClosedReceiveChannelException || e is java.io.IOException) {
                        logger.warn("연결 문제로 세션 제거: $uid")
                        removeSession(uid)
                        break
                    }

                    delay(5000) // 오류 발생 시 잠시 대기 후 재시도
                }
            }
        }
    }

    private fun stopPingPongJob(uid: String) {
        pingStatus[uid] = false
        pingJobs[uid]?.cancel()
        pingJobs.remove(uid)
    }

    // 비활성 세션 점검 및 제거
    private suspend fun checkInactiveSessions() {
        val currentTime = System.currentTimeMillis()
        val inactiveTimeout = 3 * KEEP_ALIVE_INTERVAL_MS // 3번의 핑-퐁 주기 이상 응답이 없으면 비활성으로 간주

        val inactiveSessions = lastActivity.entries
            .filter { currentTime - it.value > inactiveTimeout }
            .map { it.key }

        mutex.lock()
        try {
            inactiveSessions.forEach { uid ->
                logger.warn("비활성 세션 감지됨. 제거 중: $uid (마지막 활동: ${(currentTime - lastActivity[uid]!!) / 1000}초 전)")

                try {
                    sessions[uid]?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "비활성 연결 감지"))
                } catch (e: Exception) {
                    logger.error("비활성 세션 닫기 실패: ${e.message}")
                } finally {
                    removeSession(uid)
                }
            }
        } finally {
            mutex.unlock()
        }

        if (inactiveSessions.isNotEmpty()) {
            logger.info("총 ${inactiveSessions.size}개의 비활성 세션이 제거됨")
        }
    }

    // 활동 기록 업데이트
    suspend fun updateActivity(uid: String) {
        mutex.lock()
        try {
            lastActivity[uid] = System.currentTimeMillis()
        } finally {
            mutex.unlock()
        }
    }

    // 퐁 메시지 처리
    suspend fun handlePong(uid: String) {
        updateActivity(uid)
        logger.debug("퐁 메시지 수신: $uid")
    }

    suspend fun waitForAck(ws: WebSocketServerSession): Boolean {
        val sessionId = ws.hashCode().toString()
        mutex.lock()
        try {
            pendingAcks[sessionId] = false
        } finally {
            mutex.unlock()
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < ACK_TIMEOUT_MS) {
            mutex.lock()
            try {
                if (pendingAcks[sessionId] == true) {
                    pendingAcks.remove(sessionId)
                    return true
                }
            } finally {
                mutex.unlock()
            }
            delay(ACK_CHECK_INTERVAL_MS)
        }

        mutex.lock()
        try {
            pendingAcks.remove(sessionId)
        } finally {
            mutex.unlock()
        }
        return false
    }

    suspend fun acknowledgeMessage(sessionId: String) {
        mutex.lock()
        try {
            pendingAcks[sessionId] = true
        } finally {
            mutex.unlock()
        }
    }

    suspend fun sendWithRetry(uid: String, response: SongResponse) {
        val session = sessions[uid] ?: run {
            logger.warn("세션을 찾을 수 없음: $uid")
            return
        }

        var attempts = 0
        val jsonResponse = Json.encodeToString(SongResponse.serializer(), response)

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                session.send(Frame.Text(jsonResponse))
                updateActivity(uid)

                if (waitForAck(session)) {
                    logger.debug("메시지 전송 성공 (시도 ${attempts + 1}): $uid")
                    return
                } else {
                    logger.warn("확인 응답 없음 (시도 ${attempts + 1}): $uid")
                }
            } catch (e: Exception) {
                logger.error("메시지 전송 실패 (시도 ${attempts + 1}): ${e.message}")

                if (e is ClosedReceiveChannelException || e is java.io.IOException) {
                    logger.warn("연결 끊김으로 인한 세션 제거: $uid")
                    removeSession(uid)
                    return
                }
            }

            attempts++
            if (attempts < MAX_RETRY_ATTEMPTS) {
                delay(RETRY_DELAY_MS)
            }
        }

        logger.error("최대 재시도 횟수 초과 후 메시지 전송 실패: $uid")
        // 여러 번 실패 후 연결이 불안정한 것으로 판단하여 세션 제거
        removeSession(uid)
    }

    suspend fun getUserStatus(uid: String): SongType? {
        mutex.lock()
        return try {
            status[uid]
        } finally {
            mutex.unlock()
        }
    }

    suspend fun updateUserStatus(uid: String, songType: SongType) {
        mutex.lock()
        try {
            status[uid] = songType
        } finally {
            mutex.unlock()
        }
    }
}