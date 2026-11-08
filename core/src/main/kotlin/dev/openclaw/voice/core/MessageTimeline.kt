package dev.openclaw.voice.core

/** Delivery updates may beat the original send callback. A retired temp id cannot resurrect. */
class MessageTimeline {
    private val retired = LinkedHashSet<Long>()
    private val messages = linkedMapOf<Long, ChatMessage>()
    @Synchronized fun merge(message: ChatMessage, oldId: Long = message.id, explicitRetry: Boolean = false): List<ChatMessage> {
        if (message.id in retired) return snapshot()
        if (oldId != message.id) {
            retired.add(oldId); messages.remove(oldId)
            if (retired.size > 1000) retired.remove(retired.first())
        }
        val current = messages[message.id]
        // A stale pending response must never overwrite a terminal delivery update.
        if (explicitRetry || current == null || current.delivery == Delivery.PENDING || message.delivery != Delivery.PENDING)
            messages[message.id] = message
        val result = snapshot().takeLast(250)
        messages.keys.retainAll(result.map { it.id }.toSet())
        return result
    }
    @Synchronized fun clear() { messages.clear(); retired.clear() }
    @Synchronized fun delete(ids: LongArray): List<ChatMessage> {
        ids.forEach { messages.remove(it); retired.add(it) }
        return snapshot()
    }
    private fun snapshot() = messages.values.sortedWith(compareBy({ it.date }, { it.id }))
}
