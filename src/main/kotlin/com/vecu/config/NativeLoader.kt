package com.vecu.config

import java.io.File

/**
 * Loads the single native bridge (`libvecunative.so`) that backs both
 * [com.vecu.dbc.DbcNative] and [com.vecu.can.SocketCanNative]. Idempotent.
 */
object NativeLoader {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        // Explicit override wins (e.g. -Dvecu.native.lib=/abs/path).
        System.getProperty("vecu.native.lib")?.let {
            System.load(File(it).absolutePath)
            loaded = true
            return
        }
        val found = AppConfig.NATIVE_LIB_CANDIDATES
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?: error(
                "native library not found; looked in " +
                    AppConfig.NATIVE_LIB_CANDIDATES.joinToString() +
                    " (run the Gradle 'buildNative' task or native/build.sh)",
            )
        System.load(found.absolutePath)
        loaded = true
    }
}
