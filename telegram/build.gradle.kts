plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "dev.openclaw.voice.telegram"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Official TDLib API and engine, community binary packaging. See THIRD_PARTY.md.
    if (rootProject.file("native/official-tdlib.aar").exists())
        implementation(files(rootProject.file("native/official-tdlib.aar")))
    else implementation("io.github.tdlib-android:core:0.1.1")
}
