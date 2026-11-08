package dev.openclaw.voice.telegram

import android.content.Context
import android.os.Build
import dev.openclaw.voice.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdTelegramRepository(context: Context) : TelegramRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vault = SecureVault(context)
    private val root = File(context.noBackupFilesDir, "telegram").apply { mkdirs() }
    private val outbox = File(root, "outbox").apply { mkdirs() }
    private val mutable = MutableStateFlow(TelegramState(
        chatId = vault.get("chat_id").toLongOrNull() ?: 0, bot = vault.get("bot")))
    override val state = mutable.asStateFlow()
    private val events = Channel<TdApi.Object>(Channel.UNLIMITED)
    private val timeline = MessageTimeline()
    private val client: Client
    init {
        System.loadLibrary("tdjni")
        Client.execute(TdApi.SetLogVerbosityLevel(0))
        Client.execute(TdApi.SetLogStream(TdApi.LogStreamEmpty()))
        client = Client.create({ events.trySend(it) }, { mutable.update { it.copy(error = "Ошибка TDLib") } },
            { mutable.update { it.copy(error = "Ошибка соединения TDLib") } })
        scope.launch {
            for (event in events) try { update(event) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "Ошибка Telegram") } }
        }
    }
    private suspend fun <T : TdApi.Object> call(request: TdApi.Function<T>): T = suspendCancellableCoroutine { continuation ->
        client.send(request, { result ->
            if (continuation.isActive) {
                if (result is TdApi.Error) continuation.resumeWithException(IllegalStateException(safeError(result)))
                else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(result as T)
                }
            }
        }, { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Ошибка TDLib")) })
    }
    private fun safeError(error: TdApi.Error): String = when {
        error.code == 406 -> "Запрос не выполнен"
        error.code == 429 -> "Слишком много запросов. Попробуйте позже"
        error.message.contains("PHONE_CODE_INVALID") -> "Неверный код подтверждения"
        error.message.contains("PASSWORD_HASH_INVALID") -> "Неверный пароль"
        error.message.contains("PHONE_NUMBER_INVALID") -> "Неверный номер телефона"
        error.message.contains("USERNAME") -> "Бот с таким username не найден"
        error.code == 401 -> "Требуется авторизация"
        else -> "Telegram: ошибка ${error.code}. Проверьте соединение и повторите действие"
    }
    override suspend fun configure(apiId: Int, apiHash: String) {
        require(apiId > 0 && apiHash.matches(Regex("[a-fA-F0-9]{32}"))) { "Укажите корректные api_id и api_hash" }
        vault.put("api_id", apiId.toString()); vault.put("api_hash", apiHash)
        parameters()
    }
    private suspend fun parameters() {
        val id = vault.get("api_id").toIntOrNull()
        if (id == null) { mutable.update { it.copy(auth = Auth.CREDENTIALS) }; return }
        call(TdApi.SetTdlibParameters().apply {
            databaseDirectory = File(root, "db").absolutePath
            filesDirectory = File(root, "files").absolutePath
            databaseEncryptionKey = vault.databaseKey()
            useMessageDatabase = true; useChatInfoDatabase = true; useFileDatabase = true
            useSecretChats = false; apiId = id; apiHash = vault.get("api_hash")
            systemLanguageCode = "ru"; deviceModel = Build.MODEL
            systemVersion = Build.VERSION.RELEASE; applicationVersion = "0.1.0"
        })
    }
    override suspend fun phone(value: String) { call(TdApi.SetAuthenticationPhoneNumber(value.trim(), null)) }
    override suspend fun code(value: String) { call(TdApi.CheckAuthenticationCode(value.trim())) }
    override suspend fun password(value: String) { call(TdApi.CheckAuthenticationPassword(value)) }
    override suspend fun selectBot(username: String) {
        val name = username.trim().removePrefix("@")
        require(name.matches(Regex("[A-Za-z0-9_]{5,32}"))) { "Введите username бота без ссылки" }
        val chat = call(TdApi.SearchPublicChat(name))
        val userId = (chat.type as? TdApi.ChatTypePrivate)?.userId ?: error("Нужен личный чат с ботом")
        require(call(TdApi.GetUser(userId)).type is TdApi.UserTypeBot) { "Этот аккаунт не является ботом" }
        if (state.value.chatId != 0L) call(TdApi.CloseChat(state.value.chatId))
        vault.put("chat_id", chat.id.toString()); vault.put("bot", name)
        synchronized(timeline) {
            timeline.clear()
            mutable.update { it.copy(chatId = chat.id, bot = name, messages = emptyList(), error = null) }
        }
        sync()
    }
    override suspend fun startBot() {
        val id = state.value.chatId
        val chat = call(TdApi.GetChat(id))
        val user = (chat.type as TdApi.ChatTypePrivate).userId
        call(TdApi.SendBotStartMessage(user, id, ""))
    }
    override suspend fun sync() {
        val id = state.value.chatId
        if (id == 0L || state.value.auth != Auth.READY) return
        call(TdApi.OpenChat(id))
        var from = 0L
        // TDLib may return a short first page while its local cache warms up.
        repeat(5) {
            val page = call(TdApi.GetChatHistory(id, from, 0, 50, false)).messages
            if (page.isEmpty()) return
            page.forEach { merge(it) }
            val next = page.last().id
            if (next == from) return
            from = next
        }
    }
    override suspend fun sendVoice(path: String, duration: Int) {
        val id = state.value.chatId
        require(id != 0L && state.value.auth == Auth.READY) { "Сначала выберите бота" }
        // TDLib owns a durable copy once accepted; it can finish upload after process restart.
        val durable = File(outbox, "${UUID.randomUUID()}.ogg")
        File(path).copyTo(durable)
        try {
            val result = call(TdApi.SendMessage().apply {
                chatId = id
                inputMessageContent = TdApi.InputMessageVoiceNote(
                    TdApi.InputVoiceNote(TdApi.InputFileLocal(durable.absolutePath), duration, byteArrayOf()),
                    TdApi.FormattedText("", emptyArray()), null)
            })
            merge(result)
            // Keep accepted audio in private storage: TDLib can still need it for retry.
        } catch (e: Exception) {
            if (e !is CancellationException) durable.delete()
            throw e
        }
    }
    override suspend fun retry(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId }
        require(message?.retryable == true) { "Это сообщение нельзя повторить" }
        call(TdApi.ResendMessages().apply { chatId = state.value.chatId; messageIds = longArrayOf(messageId) })
            .messages.forEach { merge(it, messageId, explicitRetry = true) }
        sync()
    }
    private fun merge(message: TdApi.Message, oldId: Long = message.id, explicitRetry: Boolean = false) {
        if (message.chatId != state.value.chatId) return
        val delivery = when (message.sendingState) {
            is TdApi.MessageSendingStatePending -> Delivery.PENDING
            is TdApi.MessageSendingStateFailed -> Delivery.FAILED
            else -> Delivery.SENT
        }
        val text = when (val content = message.content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessageVoiceNote -> "Голосовое · ${content.voiceNote.duration} с"
            else -> "[Нетекстовое сообщение]"
        }
        val model = ChatMessage(message.id, text, message.isOutgoing, message.date, delivery,
            (message.sendingState as? TdApi.MessageSendingStateFailed)?.canRetry == true)
        synchronized(timeline) {
            if (message.chatId != state.value.chatId) return
            val updated = timeline.merge(model, oldId, explicitRetry)
            mutable.update { it.copy(messages = updated) }
        }
    }
    private suspend fun update(value: TdApi.Object) {
        when (value) {
            is TdApi.UpdateAuthorizationState -> when (val auth = value.authorizationState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> parameters()
                is TdApi.AuthorizationStateWaitPhoneNumber -> mutable.update { it.copy(auth = Auth.PHONE) }
                is TdApi.AuthorizationStateWaitCode -> mutable.update { it.copy(auth = Auth.CODE,
                    authHint = "Код: ${auth.codeInfo.type.javaClass.simpleName.removePrefix("AuthenticationCodeType")}") }
                is TdApi.AuthorizationStateWaitPassword -> mutable.update { it.copy(auth = Auth.PASSWORD, authHint = auth.passwordHint) }
                is TdApi.AuthorizationStateReady -> { mutable.update { it.copy(auth = Auth.READY, error = null) }; scope.launch { runCatching { sync() } } }
                is TdApi.AuthorizationStateClosed -> mutable.update { it.copy(auth = Auth.CLOSED) }
                else -> mutable.update { it.copy(auth = Auth.UNSUPPORTED,
                    authHint = "Этот способ входа пока не поддерживается: ${auth.javaClass.simpleName.removePrefix("AuthorizationState")}") }
            }
            is TdApi.UpdateConnectionState -> {
                val ready = value.state is TdApi.ConnectionStateReady
                val wasReady = state.value.connected
                mutable.update { it.copy(connected = ready) }
                if (ready && !wasReady) scope.launch { runCatching { sync() } }
            }
            is TdApi.UpdateNewMessage -> merge(value.message)
            is TdApi.UpdateMessageSendSucceeded -> merge(value.message, value.oldMessageId)
            is TdApi.UpdateMessageSendFailed -> merge(value.message, value.oldMessageId)
            is TdApi.UpdateMessageContent -> if (value.chatId == state.value.chatId) {
                merge(call(TdApi.GetMessage(value.chatId, value.messageId)))
            }
            is TdApi.UpdateDeleteMessages -> if (value.chatId == state.value.chatId && value.isPermanent)
                synchronized(timeline) { mutable.update { it.copy(messages = timeline.delete(value.messageIds)) } }
        }
    }
}
