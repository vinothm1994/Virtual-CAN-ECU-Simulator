package com.vecu.config

import java.io.File
import java.util.Locale

/**
 * Loads the native bridge (`libvecunative.so` / `vecunative.dll`) that backs
 * [com.vecu.dbc.DbcNative] and the CAN drivers. The dbcppp dependency is loaded
 * first so the OS resolves it by name (needed on Windows; harmless on Linux).
 * Idempotent.
 */
object NativeLoader {
    @Volatile
    private var loaded = false

    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")

    private val platform: String = run {
        val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
        val a = if (arch.contains("aarch64") || arch.contains("arm64")) "aarch64" else "x86_64"
        if (isWindows) "windows-x86_64" else "linux-$a"
    }

    private val bridgeName = if (isWindows) "vecunative.dll" else "libvecunative.so"

    // Set by the compose plugin (both `run` and the packaged app) to the merged
    // appResources/ tree (see build.gradle.kts' stageAppResources task). A
    // packaged app has no working directory containing native/, so this is the
    // only reliable way to find the bridge once installed.
    private val resourcesDir: File? =
        System.getProperty("compose.application.resources.dir")?.let(::File)

    // Dependencies to load (from the bridge's own directory) before the bridge,
    // in order, so the OS resolves them by name. On Windows this includes the
    // shared C++ runtime so both DLLs use one runtime instance.
    private val deps: List<String> = if (isWindows) {
        // Order matters: libc++ needs libunwind; dbcppp needs both.
        listOf("libunwind.dll", "libc++.dll", "libdbcppp.dll")
    } else {
        listOf("libdbcppp.so.3.8.0")
    }

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return

        // Explicit override wins (e.g. -Dvecu.native.lib=/abs/path/to/bridge).
        val override = System.getProperty("vecu.native.lib")?.let { File(it) }
        val bridge = override ?: findBridge()
            ?: error(
                "native library '$bridgeName' not found (looked in the app resources dir, " +
                    "native/build and native/prebuilt/$platform). Run the Gradle 'buildNative' " +
                    "task, or ensure the prebuilt bridge for $platform is present.",
            )

        // Load dependencies from the bridge's own directory first (in order),
        // so the bridge's imports resolve by name.
        for (name in deps) {
            val dep = File(bridge.parentFile, name)
            if (dep.exists()) System.load(dep.absolutePath)
        }

        System.load(bridge.absolutePath)
        loaded = true
    }

    private fun findBridge(): File? {
        val candidates = listOfNotNull(
            resourcesDir?.let { File(it, "native/$bridgeName") },
            File("native/build/$bridgeName"),
            File("native/prebuilt/$platform/$bridgeName"),
            File("../native/build/$bridgeName"),
            File(bridgeName),
        )
        return candidates.firstOrNull { it.exists() }
    }
}
