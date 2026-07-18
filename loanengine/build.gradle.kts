plugins {
    // No version here on purpose: AGP 9's built-in Kotlin already puts KGP on the
    // build classpath, and re-declaring a version fails plugin resolution.
    id("org.jetbrains.kotlin.jvm")
}

// Pure Kotlin/JVM module: no Android dependencies allowed here.
// Targets Java 11 to stay consumable by :app without requiring an extra JDK toolchain.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
