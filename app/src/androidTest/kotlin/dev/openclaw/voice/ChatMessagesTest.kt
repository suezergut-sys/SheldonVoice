package dev.openclaw.voice

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.openclaw.voice.core.ChatMessage
import dev.openclaw.voice.core.Delivery
import org.junit.Rule
import org.junit.Test

class ChatMessagesTest {
    @get:Rule val compose = createComposeRule()
    private fun messages(range: IntRange) = range.map {
        ChatMessage(it.toLong(), "Message $it", false, it, Delivery.SENT)
    }
    @Test fun opensAtLatestAndStaysThereWhenOlderHistoryLoads() {
        val history = mutableStateOf(emptyList<ChatMessage>())
        compose.setContent { MaterialTheme {
            ChatMessages(history.value, 1, 0, false, {}, Modifier.height(300.dp))
        } }
        compose.runOnIdle { history.value = messages(51..100) }
        compose.onNodeWithText("Message 100").assertIsDisplayed()
        compose.runOnIdle { history.value = messages(1..100) }
        compose.onNodeWithText("Message 100").assertIsDisplayed()
    }
    @Test fun reopeningReturnsFromOldHistoryToLatest() {
        val opening = mutableStateOf(0L)
        compose.setContent { MaterialTheme {
            ChatMessages(messages(1..100), 1, opening.value, false, {}, Modifier.height(300.dp))
        } }
        compose.onNodeWithTag("chat-messages").performScrollToIndex(30)
        compose.runOnIdle { opening.value++ }
        compose.onNodeWithText("Message 100").assertIsDisplayed()
    }
    @Test fun longLatestMessageOpensAtItsBottom() {
        val latest = ChatMessage(100, (1..80).joinToString("\n") { "Line $it" }, true, 100, Delivery.SENT)
        compose.setContent { MaterialTheme {
            ChatMessages(messages(1..99) + latest, 1, 0, false, {}, Modifier.height(300.dp))
        } }
        compose.onNodeWithText("Отправлено").assertIsDisplayed()
    }
    @Test fun incomingReplyScrollsToLatest() {
        val history = mutableStateOf(messages(1..100))
        compose.setContent { MaterialTheme {
            ChatMessages(history.value, 1, 0, false, {}, Modifier.height(300.dp))
        } }
        compose.runOnIdle { history.value = messages(1..101) }
        compose.onNodeWithText("Message 101").assertIsDisplayed()
    }
}
