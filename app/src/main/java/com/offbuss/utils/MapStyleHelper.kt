package com.offbuss.utils

import android.content.Context
import android.util.Log
import java.io.File

object MapStyleHelper {

    private const val TAG = "MapStyleHelper"

    fun prepareOfflineStyle(context: Context): String {
        copyMbtilesToFilesDir(context)
        return context.assets.open("map-style.json").bufferedReader().use { it.readText() }
    }

    fun getMbtilesFile(context: Context): File = File(context.filesDir, "map.mbtiles")

    private fun copyMbtilesToFilesDir(context: Context) {
        val dest = File(context.filesDir, "map.mbtiles")
        if (!dest.exists()) {
            Log.d(TAG, "Copiando map.mbtiles para filesDir (primeira execução)...")
            context.assets.open("map.mbtiles").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Concluído: ${dest.absolutePath}")
        }
    }
}
