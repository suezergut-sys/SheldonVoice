# TDLib и зависимости

Telegram engine и Java API: официальная TDLib, https://github.com/tdlib/td (Boost Software License 1.0). Приложение использует `org.drinkless.tdlib.Client` напрямую. Bot API и сторонний backend не используются.

Для сборки без локального NDK используется фиксированный Maven Central артефакт `io.github.tdlib-android:core:0.1.1`. Это **community distribution, не официальный Maven-релиз Telegram**. AAR содержит generated Java API и `libtdjni.so` для arm64-v8a, armeabi-v7a, x86_64, x86; вспомогательный ktx-слой не используется. Обёртка указана здесь явно, а не выдаётся за официальный артефакт.

Исходная библиотека: https://github.com/tdlib/td.
Официальная инструкция: https://github.com/tdlib/td/blob/master/example/android/README.md.
Maven артефакт: https://repo.maven.apache.org/maven2/io/github/tdlib-android/core/0.1.1/.

SHA-256 проверенного core-0.1.1.aar: `1ec6d6c6c83b655349d8a91182145cdd5400392246136ad79432253ad6004106`. Gradle dependency verification фиксирует хеши артефактов в `gradle/verification-metadata.xml`; это контроль неизменности скачанного артефакта, а не доказательство его происхождения.

## Замена на собственную сборку

На Linux/macOS/WSL с Bash, JDK, C++ compiler, PHP, perl, gperf и Android NDK следуйте официальному Android example. Собирайте интерфейс **Java**, а не JSON: нужны `Client.java`, `TdApi.java` и `libtdjni.so` из одной и той же ревизии.

Для воспроизводимой исходной точки можно использовать upstream commit `175048b69616ebc81b59ce71aa42abedbab9fe54` (проверен при разработке 20.10.2026); при замене API возможны изменения, запускайте все проверки заново. Ревизия community AAR не установлена, она не заявляется равной этому commit.

Официальный `example/android/Dockerfile` позволяет собрать архив в Docker:

```bash
git clone https://github.com/tdlib/td.git
cd td
git checkout 175048b69616ebc81b59ce71aa42abedbab9fe54
cd example/android
docker build --build-arg COMMIT_HASH=175048b69616ebc81b59ce71aa42abedbab9fe54 \
  --build-arg ANDROID_NDK_VERSION=27.2.12479018 --output tdlib .
```

Упакуйте результат стандартным Android library module в `native/official-tdlib.aar`: package `org.drinkless.tdlib`, Java sources из `tdlib/java`, JNI файлы в `src/main/jniLibs/<abi>/libtdjni.so`, consumer keep rules `-keep class org.drinkless.tdlib.** { *; }`. При наличии этого файла Gradle автоматически использует его вместо Maven AAR. Для 16 KB устройств проверьте ELF и zip alignment по Android NDK guidance; один факт наличия `.so` не подтверждает совместимость.

Остальные зависимости: AndroidX/Compose (Apache 2.0), Kotlin/Coroutines (Apache 2.0), JUnit 4 (EPL 1.0). Их версии зафиксированы в Gradle. Нативные лицензии и уведомления upstream необходимо сохранить при самостоятельной переупаковке и выпуске.
