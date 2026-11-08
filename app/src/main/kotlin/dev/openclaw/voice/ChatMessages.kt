package dev.openclaw.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.openclaw.voice.core.ChatMessage
import dev.openclaw.voice.core.Delivery

@Composable internal fun ChatMessages(
    messages: List<ChatMessage>, chatId: Long, openCount: Long,
    busy: Boolean, retry: (Long) -> Unit, modifier: Modifier = Modifier
) {
    key(chatId) {
        val listState = rememberLazyListState()
        // Index zero is always the newest message, anchored to the bottom. Loading older
        // history cannot move that anchor, even if the newest message id stays unchanged.
        LaunchedEffect(openCount, messages.lastOrNull()?.id) {
            if (messages.isNotEmpty()) listState.scrollToItem(0)
        }
        LazyColumn(modifier.testTag("chat-messages"), state = listState, reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages.asReversed(), key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth().testTag("message-${message.id}")
                    .background(if (message.outgoing) Color(0xFF23473F) else Color(0xFF22333E),
                        MaterialTheme.shapes.medium).padding(12.dp)) {
                    Text(if (message.outgoing) "Вы" else "Бот", style = MaterialTheme.typography.labelSmall)
                    Text(message.text)
                    if (message.outgoing) Text(when (message.delivery) {
                        Delivery.PENDING -> "В очереди Telegram…"
                        Delivery.SENT -> "Отправлено"
                        Delivery.FAILED -> "Ошибка отправки"
                    }, style = MaterialTheme.typography.labelSmall)
                    if (message.retryable) TextButton(onClick = { retry(message.id) }, enabled = !busy) {
                        Text("Повторить")
                    }
                }
            }
        }
    }
}
