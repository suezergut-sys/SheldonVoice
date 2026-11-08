package dev.openclaw.voice.core

import kotlinx.coroutines.flow.StateFlow

enum class Auth { STARTING, CREDENTIALS, PHONE, CODE, PASSWORD, READY, UNSUPPORTED, CLOSED }
enum class Delivery { PENDING, SENT, FAILED }
data class ChatMessage(val id: Long, val text: String, val outgoing: Boolean,
    val date: Int, val delivery: Delivery, val retryable: Boolean = false)
data class TelegramState(val auth: Auth = Auth.STARTING, val authHint: String = "",
    val chatId: Long = 0, val bot: String = "", val connected: Boolean = false,
    val messages: List<ChatMessage> = emptyList(), val error: String? = null)
interface TelegramRepository {
    val state: StateFlow<TelegramState>
    suspend fun configure(apiId: Int, apiHash: String)
    suspend fun phone(value: String)
    suspend fun code(value: String)
    suspend fun password(value: String)
    suspend fun selectBot(username: String)
    suspend fun startBot()
    suspend fun sync()
    suspend fun sendVoice(path: String, duration: Int)
    suspend fun retry(messageId: Long)
}

data class VoiceClip(val path: String, val duration: Int)
interface VoiceRecorder {
    fun start()
    fun stop(): VoiceClip
    fun cancel()
}

enum class RecordingPhase { IDLE, RECORDING, SENDING, ERROR }
data class RecordingState(val phase: RecordingPhase = RecordingPhase.IDLE,
    val seconds: Int = 0, val error: String? = null, val draft: VoiceClip? = null)

/** Called on one UI dispatcher; the state changes before the first suspension. */
class VoiceController(private val recorder: VoiceRecorder, private val clock: () -> Long) {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = mutable
    private var startedAt = 0L
    fun start(allowed: Boolean) {
        if (!allowed || mutable.value.phase != RecordingPhase.IDLE) return
        try {
            recorder.start()
            startedAt = clock()
            mutable.value = RecordingState(RecordingPhase.RECORDING)
        } catch (_: Exception) {
            recorder.cancel()
            mutable.value = RecordingState(RecordingPhase.ERROR, error = "Не удалось открыть микрофон")
        }
    }
    fun tick() {
        if (mutable.value.phase == RecordingPhase.RECORDING)
            mutable.value = mutable.value.copy(seconds = ((clock() - startedAt) / 1000).toInt().coerceAtLeast(0))
    }
    fun cancel() {
        if (mutable.value.phase == RecordingPhase.SENDING) return
        recorder.cancel()
        mutable.value.draft?.let { java.io.File(it.path).delete() }
        mutable.value = RecordingState()
    }
    fun background() { if (mutable.value.phase == RecordingPhase.RECORDING) cancel() }
    suspend fun send(sender: suspend (VoiceClip) -> Unit) {
        val old = mutable.value
        if (old.phase != RecordingPhase.RECORDING && old.draft == null) return
        if (old.phase == RecordingPhase.SENDING) return
        var clip = old.draft
        mutable.value = old.copy(phase = RecordingPhase.SENDING, error = null)
        try {
            if (clip == null) {
                require(clock() - startedAt >= 1000) { "Слишком короткая запись (минимум 1 секунда)" }
                clip = recorder.stop()
            }
            mutable.value = mutable.value.copy(draft = clip)
            sender(clip)
            java.io.File(clip.path).delete()
            mutable.value = RecordingState()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            recorder.cancel()
            mutable.value = RecordingState(RecordingPhase.ERROR, error = e.message ?: "Не удалось отправить", draft = clip)
        }
    }
}
