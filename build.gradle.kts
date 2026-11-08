plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Optional ASCII build path for Windows JDK argument-file encoding on non-ASCII checkouts.
subprojects {
    providers.gradleProperty("buildRoot").orNull?.let { destination ->
        layout.buildDirectory.set(file("$destination/$name"))
    }
}
