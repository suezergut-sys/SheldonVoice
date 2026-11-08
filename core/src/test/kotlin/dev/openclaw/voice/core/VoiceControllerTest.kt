package dev.openclaw.voice.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceControllerTest {
    private class Recorder : VoiceRecorder {
        var starts = 0; var stops = 0; var cancels = 0
        var failStart = false; var failStop = false
        override fun start() { starts++; check(!failStart) }
        override fun stop(): VoiceClip { stops++; check(!failStop); return VoiceClip("missing-test.ogg", 2) }
        override fun cancel() { cancels++ }
    }
    @Test fun `permission gates start and prevents duplicate recording`() {
        val r = Recorder(); val c = VoiceController(r) { 0 }
        c.start(false); assertEquals(0, r.starts)
        c.start(true); c.start(true)
        assertEquals(1, r.starts); assertEquals(RecordingPhase.RECORDING, c.state.value.phase)
    }
    @Test fun `timer uses monotonic elapsed time and ignores idle ticks`() {
        var time = 100L; val c = VoiceController(Recorder()) { time }
        c.start(true); time = 3600; c.tick(); assertEquals(3, c.state.value.seconds)
        c.cancel(); time = 5000; c.tick(); assertEquals(0, c.state.value.seconds)
    }
    @Test fun `background cancels live recording and never sends`() {
        val r = Recorder(); val c = VoiceController(r) { 0 }
        c.start(true); c.background()
        assertEquals(RecordingPhase.IDLE, c.state.value.phase); assertEquals(1, r.cancels)
    }
    @Test fun `start failure releases recorder`() {
        val r = Recorder().apply { failStart = true }; val c = VoiceController(r) { 0 }
        c.start(true)
        assertEquals(RecordingPhase.ERROR, c.state.value.phase); assertEquals(1, r.cancels)
    }
    @Test fun `short recording is rejected without sending`() = runTest {
        val r = Recorder(); val c = VoiceController(r) { 0 }; c.start(true)
        c.send { fail("Must not send") }
        assertEquals(RecordingPhase.ERROR, c.state.value.phase); assertEquals(0, r.stops)
    }
    @Test fun `stop failure cannot send invalid audio`() = runTest {
        var time = 0L; val r = Recorder().apply { failStop = true }; val c = VoiceController(r) { time }
        c.start(true); time = 2000
        c.send { fail("Must not send") }; assertEquals(RecordingPhase.ERROR, c.state.value.phase)
        assertNull(c.state.value.draft)
    }
    @Test fun `double tap sends once and remains sending until repository accepts`() = runTest {
        var time = 0L; val r = Recorder(); val c = VoiceController(r) { time }
        val accepted = CompletableDeferred<Unit>(); var sends = 0
        c.start(true); time = 2000
        val job = launch { c.send { sends++; accepted.await() } }; runCurrent()
        c.send { sends++ }; c.cancel(); c.background()
        assertEquals(1, sends); assertEquals(RecordingPhase.SENDING, c.state.value.phase)
        accepted.complete(Unit); job.join()
        assertEquals(RecordingPhase.IDLE, c.state.value.phase); assertEquals(1, r.stops)
    }
    @Test fun `failed request retains draft for explicit retry without another stop`() = runTest {
        var time = 0L; val r = Recorder(); val c = VoiceController(r) { time }
        c.start(true); time = 2000
        c.send { error("Offline") }
        assertEquals(RecordingPhase.ERROR, c.state.value.phase); assertNotNull(c.state.value.draft)
        c.send { assertEquals(2, it.duration) }
        assertEquals(RecordingPhase.IDLE, c.state.value.phase); assertEquals(1, r.stops)
    }
    @Test fun `cancel draft clears error and allows new recording`() = runTest {
        var time = 0L; val r = Recorder(); val c = VoiceController(r) { time }
        c.start(true); time = 2000; c.send { error("Offline") }; c.cancel(); c.start(true)
        assertEquals(RecordingPhase.RECORDING, c.state.value.phase); assertNull(c.state.value.draft)
    }
}
