# Проверка 0.1.1 — 21.10.2026

- Debug APK 0.1.1 собран; package `dev.openclaw.voice`, versionCode 2, label SheldonVoice.
- Сертификат старого APK 0.1.0 и нового совпадает: SHA-256 `1329c4638a57777b90429bf0eff9df67c2865bf95ebb8b97f45ef4fad77eadd0`.
- Установка 0.1.1 через `adb install -r` поверх исходного APK 0.1.0 успешна. Проверочный файл в приватном `no_backup` и ранее выданное разрешение RECORD_AUDIO сохранились.
- SecureVault, TdTelegramRepository и VoiceApplication побайтово совпадают с исходниками архива 0.1.0: формат и расположение сессии не менялись.
- 16 JVM-тестов; 4 Android Compose-теста успешно прошли на эмуляторе API 36: старт у последнего сообщения, поступление старой истории, возвращение из середины диалога, конец длинного ответа и новый входящий ответ (первые два сценария объединены в один тест).
- Android Lint: ошибок нет; остаются предупреждения о новых версиях зависимостей и квадратной форме исходной картинки для значка.
- Gradle UTP runner на этом Windows-окружении завершился инфраструктурной ошибкой `Failed to receive the UTP test results`. Те же собранные тесты запущены напрямую через `adb shell am instrument -w -r dev.openclaw.voice.test/androidx.test.runner.AndroidJUnitRunner`: **OK (4 tests)**.
- Реальная Telegram-сессия пользователя при обновлении не использовалась. Проверены совместимость APK и сохранность приватных данных; отзыв сессии на стороне Telegram от приложения не зависит.

# Проверка 0.1.0 — 20.10.2026

Команда, выполненная на Windows с JDK 21 и Android SDK 36:

```powershell
.\gradlew.bat -PbuildRoot=C:/Temp/sheldonvoice-build :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Результат: **BUILD SUCCESSFUL**.

- `core:test`: 16 тестов, 0 ошибок (9 VoiceController + 7 MessageTimeline).
- `app:testDebugUnitTest`: NO-SOURCE; Android UI unit tests не заявляются.
- `app:lintDebug`: 0 ошибок; 8 предупреждений об обновлениях зависимостей, лишнем v26 qualifier и monochrome icon.
- `app:assembleDebug`: APK включает все 4 ABI, minSdk 29, targetSdk 36.
- Gradle dependency verification SHA-256 включена; финальная сборка прошла без режима перезаписи metadata.
- `apksigner verify --verbose`: подпись APK проверена, APK Signature Scheme v2.
- `zipalign -c -P 16 4`: проверка выравнивания APK выполнена успешно.
- ELF PT_LOAD у `libtdjni.so` для arm64-v8a и x86_64 имеет выравнивание 16384 байт. Это статическая проверка; запуск на физическом 16 KB устройстве не выполнялся.
- APK установлен через adb в эмулятор API 36 (Android 16), запущен через MAIN/LAUNCHER. Отображается экран ввода api_id/api_hash; Java/JNI TDLib загрузились, AndroidRuntime crash отсутствует.
- На первом запуске не запрашивается микрофон до перехода к соответствующему действию пользователя.
- У APK разрешения INTERNET и RECORD_AUDIO; AndroidX дополнительно объявляет внутреннее signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, которое не запрашивает доступ к пользовательским данным.

Реальный Telegram login, 2FA, микрофон телефона и end-to-end ответ настроенного OpenClaw не проверены: они требуют личного аккаунта и credentials пользователя. Не вводите их в чат — настройте на телефоне. Сценарии ручной приёмки перечислены в README.

Сборка непосредственно из исходного пути с кириллицей компилирует APK, но JVM test worker не находит скомпилированные тестовые классы из-за кодировки argument file. Параметр `buildRoot` решает это без переноса исходников; все 16 тестов успешно выполнены в ASCII-каталоге результатов.
