package dev.openclaw.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import dev.openclaw.voice.core.VoiceClip
import dev.openclaw.voice.core.VoiceRecorder
import java.io.File
import java.util.UUID
import kotlin.math.ceil

class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L
    init {
        // Incomplete recordings from a killed process are never sent automatically.
        File(context.noBackupFilesDir, "recordings").listFiles()?.forEach { it.delete() }
    }
    @Suppress("DEPRECATION")
    override fun start() {
        check(recorder == null)
        val directory = File(context.noBackupFilesDir, "recordings").apply { mkdirs() }
        file = File(directory, "${UUID.randomUUID()}.ogg")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = next
        next.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioChannels(1); setAudioSamplingRate(48000); setAudioEncodingBitRate(32000)
            setOutputFile(file!!.absolutePath)
            prepare(); start()
        }
        started = SystemClock.elapsedRealtime()
    }
    override fun stop(): VoiceClip {
        val current = checkNotNull(recorder)
        try { current.stop() } finally { current.release(); recorder = null }
        val complete = checkNotNull(file)
        check(complete.length() > 0) { "Пустая запись" }
        file = null
        return VoiceClip(complete.absolutePath, ceil((SystemClock.elapsedRealtime() - started) / 1000.0).toInt())
    }
    override fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        file?.delete(); file = null
    }
}
