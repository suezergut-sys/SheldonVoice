package dev.openclaw.voice

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openclaw.voice.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as VoiceApplication).repository
    val telegram = repository.state
    private val controller = VoiceController(AndroidVoiceRecorder(app), SystemClock::elapsedRealtime)
    val recording = controller.state
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val settings = MutableStateFlow(false)
    val chatOpenCount = MutableStateFlow(0L)
    private var foreground = false
    val microphone = MutableStateFlow(false)
    private var autoArmed = false
    init {
        viewModelScope.launch { telegram.collect { maybeAutoStart() } }
        viewModelScope.launch {
            while (isActive) {
                delay(250); controller.tick()
                if (recording.value.seconds >= 600) { controller.cancel(); error.value = "Достигнут лимит записи 10 минут" }
            }
        }
    }
    fun resume(granted: Boolean, launcher: Boolean = false) {
        foreground = true; microphone.value = granted
        chatOpenCount.value++
        if (launcher) autoArmed = true
        maybeAutoStart()
        if (telegram.value.auth == Auth.READY) viewModelScope.launch { runCatching { repository.sync() } }
    }
    fun pause() { foreground = false; controller.background() }
    fun permissionResult(granted: Boolean) {
        microphone.value = granted
        if (!granted) error.value = "Для записи разрешите доступ к микрофону в настройках Android"
        maybeAutoStart()
    }
    private fun maybeAutoStart() {
        if (autoArmed && eligible()) { autoArmed = false; controller.start(true) }
    }
    fun armLauncher() { autoArmed = true }
    private fun eligible() = foreground && microphone.value && !settings.value && !busy.value &&
        telegram.value.auth == Auth.READY && telegram.value.chatId != 0L
    fun start() { autoArmed = false; controller.start(eligible()) }
    fun cancel() { autoArmed = false; controller.cancel() }
    fun openSettings() { autoArmed = false; controller.background(); settings.value = true }
    fun closeSettings() { settings.value = false }
    fun send() {
        if (!foreground) return
        viewModelScope.launch { controller.send { repository.sendVoice(it.path, it.duration) } }
    }
    fun configure(id: String, hash: String) = action { repository.configure(id.toIntOrNull() ?: 0, hash.trim()) }
    fun auth(value: String) = action {
        when (telegram.value.auth) {
            Auth.PHONE -> repository.phone(value)
            Auth.CODE -> repository.code(value)
            Auth.PASSWORD -> repository.password(value)
            else -> Unit
        }
    }
    fun bot(value: String) = action { repository.selectBot(value); settings.value = false }
    fun startBot() = action { repository.startBot() }
    fun sync() = action { repository.sync() }
    fun retry(id: Long) = action { repository.retry(id) }
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { withTimeout(45_000) { block() } }
            catch (_: TimeoutCancellationException) { error.value = "Нет ответа Telegram. Проверьте интернет и обновите диалог" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Не удалось выполнить действие" }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { controller.cancel() }
}
