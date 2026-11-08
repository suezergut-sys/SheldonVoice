package dev.openclaw.voice

import android.app.Application
import dev.openclaw.voice.telegram.TdTelegramRepository

class VoiceApplication : Application() {
    val repository by lazy { TdTelegramRepository(this) }
}
