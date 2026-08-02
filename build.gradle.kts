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
            // Bundles native/deps + config so the packaged app is self-contained
            // (no reliance on the process's working directory). Populated by
            // stageAppResources below; read back via AppConfig.resolvePath and
            // NativeLoader using the compose.application.resources.dir property.
            appResourcesRootDir.set(layout.projectDirectory.dir("appResources"))
        }
    }
}

// Compile the native JNI bridge (dbcppp + SocketCAN) before Kotlin classes so
// the .so is present at runtime. Idempotent: build.sh no-ops when up to date.
val buildNative = tasks.register<Exec>("buildNative") {
    workingDir = file("native")
    val isWindows = System.getProperty("os.name")
        .lowercase()
        .contains("windows")
    if (isWindows) {
        commandLine("cmd", "/c", "buildNative.bat")
    } else {
        commandLine("bash", "build.sh")
    }
    // Compile the JNI bridge against the same JDK that runs Gradle (the
    // provisioned JDK 11), so its headers are found.
    environment("JAVA_HOME", System.getProperty("java.home"))
    inputs.dir("native/src")
    inputs.file("native/CMakeLists.txt")
    outputs.dir("native/build")
}

// run -> classes -> compileKotlin, so hooking compileKotlin also covers `run`.
tasks.named("compileKotlin") { dependsOn(buildNative) }

// Stages the freshly built native bridge (+ its runtime deps) and the config/
// DBC+YAML files into appResources/, the tree that appResourcesRootDir copies
// into the packaged app. A raw jpackage app image has no working directory
// containing native/ or config/, so without this NativeLoader/AppConfig can't
// find anything once installed — only `./gradlew run` (cwd = project root)
// happened to work.
val stageAppResources = tasks.register<Sync>("stageAppResources") {
    dependsOn(buildNative)
    val isWindows = System.getProperty("os.name")
        .lowercase()
        .contains("windows")
    val hostArch = System.getProperty("os.arch").lowercase().let {
        if (it.contains("aarch64") || it.contains("arm64")) "aarch64" else "x86_64"
    }
    into(layout.projectDirectory.dir("appResources"))
    from("config") { into("common/config") }
    if (isWindows) {
        from("native/build") { include("vecunative.dll"); into("windows/native") }
        from("native/prebuilt/windows-x86_64") {
            include("libdbcppp.dll", "libc++.dll", "libunwind.dll")
            into("windows/native")
        }
    } else {
        from("native/build") { include("libvecunative.so"); into("linux/native") }
        from("native/prebuilt/linux-$hostArch") { include("libdbcppp.so*"); into("linux/native") }
    }
}

// Needed both for `run` (dev) and for packaging (installed app has no source
// tree to fall back on). The compose plugin's own `prepareAppResources` task
// reads appResourcesRootDir straight off disk, so it must run strictly after
// ours writes into it (same directory — an implicit-dependency validation
// error otherwise).
tasks.matching {
    it.name == "run" || it.name == "createDistributable" || it.name == "prepareAppResources" ||
        it.name.startsWith("package")
}.configureEach { dependsOn(stageAppResources) }

// Headless end-to-end pipeline check (no CAN bus, no UI). `./gradlew selfTest`.
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs the headless DBC/rules/encode-decode self test."
    dependsOn("classes")
    mainClass.set("com.vecu.SelfTestKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}
