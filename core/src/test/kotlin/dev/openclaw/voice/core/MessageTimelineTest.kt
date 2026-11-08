package dev.openclaw.voice.core

import org.junit.Assert.*
import org.junit.Test

class MessageTimelineTest {
    private fun message(id: Long, status: Delivery) = ChatMessage(id, "Voice", true, 100, status)
    @Test fun `accepted stays pending until server delivery update`() {
        val timeline = MessageTimeline()
        assertEquals(Delivery.PENDING, timeline.merge(message(-1, Delivery.PENDING)).single().delivery)
        val sent = timeline.merge(message(10, Delivery.SENT), -1)
        assertEquals(10L, sent.single().id); assertEquals(Delivery.SENT, sent.single().delivery)
    }
    @Test fun `late accepted callback cannot resurrect temporary message`() {
        val timeline = MessageTimeline()
        timeline.merge(message(10, Delivery.SENT), -1)
        assertEquals(listOf(message(10, Delivery.SENT)), timeline.merge(message(-1, Delivery.PENDING)))
    }
    @Test fun `failed update survives stale pending and retry can succeed`() {
        val timeline = MessageTimeline()
        timeline.merge(message(-1, Delivery.PENDING))
        timeline.merge(message(-1, Delivery.FAILED))
        assertEquals(Delivery.FAILED, timeline.merge(message(-1, Delivery.PENDING)).single().delivery)
        assertEquals(Delivery.SENT, timeline.merge(message(20, Delivery.SENT), -1).single().delivery)
    }
    @Test fun `history upsert deduplicates and deletion cannot be resurrected`() {
        val timeline = MessageTimeline()
        timeline.merge(message(1, Delivery.SENT)); timeline.merge(message(1, Delivery.SENT))
        assertTrue(timeline.delete(longArrayOf(1)).isEmpty())
        assertTrue(timeline.merge(message(1, Delivery.SENT)).isEmpty())
    }
    @Test fun `explicit retry can return failed message to pending`() {
        val timeline = MessageTimeline()
        timeline.merge(message(-1, Delivery.FAILED))
        assertEquals(Delivery.PENDING, timeline.merge(message(-1, Delivery.PENDING), explicitRetry = true).single().delivery)
    }
    @Test fun `switching bot clears prior dialog and retained ids`() {
        val timeline = MessageTimeline()
        timeline.merge(message(1, Delivery.SENT)); timeline.clear()
        assertEquals(listOf(message(2, Delivery.SENT)), timeline.merge(message(2, Delivery.SENT)))
    }
    @Test fun `history is bounded and ordered`() {
        val timeline = MessageTimeline()
        var result = emptyList<ChatMessage>()
        (300L downTo 1L).forEach { result = timeline.merge(message(it, Delivery.SENT)) }
        assertEquals(250, result.size); assertEquals(51L, result.first().id)
    }
}
