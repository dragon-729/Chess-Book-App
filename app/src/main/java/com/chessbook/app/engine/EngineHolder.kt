package com.chessbook.app.engine

import android.content.Context
import java.io.File

/** Picks Stockfish if a native binary was bundled, otherwise the built-in fallback engine. */
object EngineHolder {
    @Volatile private var instance: ResilientEngine? = null

    fun get(context: Context): ResilientEngine =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(context: Context): ResilientEngine {
        val lib = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        val builtin = BuiltinEngine()
        return if (lib.exists() && lib.canExecute()) {
            ResilientEngine(UciEngine(lib, "Stockfish"), builtin)
        } else {
            ResilientEngine(builtin, builtin)
        }
    }
}
