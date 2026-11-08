package dev.openclaw.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openclaw.voice.core.*

class MainActivity : ComponentActivity() {
    private val model: VoiceViewModel by viewModels()
    private fun microphoneGranted() = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)) model.armLauncher()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF91E9CE),
                background = Color(0xFF101A20), surface = Color(0xFF18262D))) {
                Surface(Modifier.fillMaxSize()) {
                    VoiceScreen(model)
                }
            }
        }
    }
    override fun onResume() { super.onResume(); model.resume(microphoneGranted()) }
    override fun onPause() { model.pause(); super.onPause() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER))
            model.armLauncher()
    }
}

@Composable private fun VoiceScreen(model: VoiceViewModel) {
    val tg by model.telegram.collectAsStateWithLifecycle()
    val voice by model.recording.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val granted by model.microphone.collectAsStateWithLifecycle()
    val chatOpenCount by model.chatOpenCount.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.permissionResult(it)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column { Text("SheldonVoice", style = MaterialTheme.typography.titleLarge)
                Text(if (tg.connected) "Telegram подключён" else "Ожидание сети…", style = MaterialTheme.typography.labelMedium) }
            if (tg.auth == Auth.READY && !settings) TextButton(onClick = model::openSettings,
                enabled = voice.phase != RecordingPhase.SENDING) { Text("Бот") }
        }
        (error ?: tg.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            tg.auth == Auth.CREDENTIALS -> Credentials(busy, model::configure)
            tg.auth in listOf(Auth.PHONE, Auth.CODE, Auth.PASSWORD) -> Authentication(tg, busy, model::auth)
            tg.auth == Auth.STARTING -> Text("Запуск защищённой сессии…")
            tg.auth != Auth.READY -> Text(tg.authHint.ifBlank { "Сессия закрыта. Перезапустите приложение" })
            tg.chatId == 0L || settings -> BotSettings(tg.bot, busy, model::bot,
                if (tg.chatId != 0L) model::closeSettings else null)
            else -> {
                Text("@${tg.bot}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = model::sync, enabled = !busy) { Text("Обновить") }
                    TextButton(onClick = model::startBot, enabled = !busy) { Text("Отправить /start") }
                }
                ChatMessages(tg.messages, tg.chatId, chatOpenCount, busy, model::retry,
                    Modifier.weight(1f).fillMaxWidth())
                if (!granted) {
                    Text("При открытии приложения запись начнётся автоматически. В фоне запись отменяется.")
                    Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Разрешить микрофон") }
                } else {
                    Text(when (voice.phase) {
                        RecordingPhase.RECORDING -> "● Идёт запись"
                        RecordingPhase.SENDING -> "Передача в Telegram…"
                        RecordingPhase.ERROR -> voice.error ?: "Ошибка записи"
                        RecordingPhase.IDLE -> "Готов к записи"
                    }, color = if (voice.phase == RecordingPhase.RECORDING) Color(0xFFFF9A8B) else MaterialTheme.colorScheme.primary)
                    if (voice.phase == RecordingPhase.RECORDING) Text(
                        "%02d:%02d".format(voice.seconds / 60, voice.seconds % 60), fontSize = 48.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (voice.phase) {
                            RecordingPhase.RECORDING -> {
                                OutlinedButton(onClick = model::cancel, Modifier.weight(1f)) { Text("Отменить") }
                                Button(onClick = model::send, Modifier.weight(1f), enabled = voice.seconds >= 1) { Text("Отправить") }
                            }
                            RecordingPhase.IDLE -> Button(onClick = model::start, Modifier.fillMaxWidth()) { Text("Записать голосовое") }
                            RecordingPhase.ERROR -> {
                                OutlinedButton(onClick = model::cancel) { Text("Сбросить") }
                                if (voice.draft != null) Button(onClick = model::send) { Text("Повторить") }
                            }
                            RecordingPhase.SENDING -> CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Credentials(busy: Boolean, submit: (String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    Text("Подключите свой Telegram API", style = MaterialTheme.typography.headlineSmall)
    Text("Получите api_id и api_hash на my.telegram.org → API development tools. Данные сохраняются зашифрованными на этом телефоне.")
    OutlinedTextField(id, { id = it }, label = { Text("api_id") }, singleLine = true)
    OutlinedTextField(hash, { hash = it }, label = { Text("api_hash") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation())
    Button(onClick = { submit(id, hash); hash = "" }, enabled = !busy && id.isNotBlank() && hash.isNotBlank()) { Text("Продолжить") }
}
@Composable private fun Authentication(tg: TelegramState, busy: Boolean, submit: (String) -> Unit) {
    var value by remember(tg.auth) { mutableStateOf("") }
    val label = when (tg.auth) { Auth.PHONE -> "Телефон (+7…)"; Auth.CODE -> "Код подтверждения"; else -> "Пароль 2FA" }
    Text(label, style = MaterialTheme.typography.headlineSmall)
    if (tg.authHint.isNotBlank()) Text(tg.authHint)
    OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true,
        visualTransformation = if (tg.auth == Auth.PASSWORD) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
    Button(onClick = { submit(value); value = "" }, enabled = !busy && value.isNotBlank()) { Text("Войти") }
}
@Composable private fun BotSettings(initial: String, busy: Boolean, submit: (String) -> Unit, close: (() -> Unit)?) {
    var bot by remember { mutableStateOf(initial) }
    Text("Ваш OpenClaw-бот", style = MaterialTheme.typography.headlineSmall)
    Text("Введите username уже настроенного бота. Проверьте его перед отправкой личных сообщений.")
    OutlinedTextField(bot, { bot = it }, label = { Text("@username") }, singleLine = true)
    Button(onClick = { submit(bot) }, enabled = !busy && bot.isNotBlank()) { Text("Выбрать бота") }
    close?.let { TextButton(onClick = it) { Text("Назад") } }
}
