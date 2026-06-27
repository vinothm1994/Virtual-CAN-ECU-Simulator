import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.10.2"
}

group = "com.vecu"
version = "0.1.0"

// Target JVM 11 bytecode for broad compatibility; build on a JDK 17+.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.yaml:snakeyaml:2.2")
}

compose.desktop {
    application {
        mainClass = "com.vecu.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "vecu-sim"
            packageVersion = "1.0.0"
            description = "Virtual CAN ECU Simulator"
            linux {
                packageName = "vecu-sim"
                appCategory = "Development"
            }
        }
    }
}

// Compile the native JNI bridge (dbcppp + SocketCAN) before Kotlin classes so
// the .so is present at runtime. Idempotent: build.sh no-ops when up to date.
val buildNative = tasks.register<Exec>("buildNative") {
    workingDir = file("native")
    commandLine("bash", "build.sh")
    // Compile the JNI bridge against the same JDK that runs Gradle (the
    // provisioned JDK 11), so its headers are found.
    environment("JAVA_HOME", System.getProperty("java.home"))
    inputs.dir("native/src")
    inputs.file("native/CMakeLists.txt")
    outputs.dir("native/build")
}

// run -> classes -> compileKotlin, so hooking compileKotlin also covers `run`.
tasks.named("compileKotlin") { dependsOn(buildNative) }

// Headless end-to-end pipeline check (no CAN bus, no UI). `./gradlew selfTest`.
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs the headless DBC/rules/encode-decode self test."
    dependsOn("classes")
    mainClass.set("com.vecu.SelfTestKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}
