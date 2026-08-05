package com.offbuss.utils

import android.content.Context
import android.util.Log
import java.io.File

object MapStyleHelper {

    private const val TAG = "MapStyleHelper"

    /** Nome do arquivo que as versões até a 1.0 copiavam para o `filesDir`. */
    private const val MBTILES_ANTIGO = "map.mbtiles"

    fun prepareOfflineStyle(context: Context): String =
        context.assets.open("map-style.json").bufferedReader().use { it.readText() }

    /**
     * Apaga o `map.mbtiles` que as versões antigas deixavam no `filesDir`.
     *
     * O mapa base agora é lido direto do APK, então essa cópia virou lixo — mas
     * um lixo de ~272 MiB, que continuaria ocupando o armazenamento de quem
     * atualizou por cima de uma instalação anterior.
     */
    fun limparCopiaAntiga(context: Context) {
        val antigo = File(context.filesDir, MBTILES_ANTIGO)
        if (!antigo.exists()) return

        val tamanho = antigo.length()
        if (antigo.delete()) {
            Log.i(TAG, "Removido $MBTILES_ANTIGO do filesDir (${tamanho / 1024 / 1024} MiB)")
        } else {
            Log.w(TAG, "Não consegui remover $MBTILES_ANTIGO do filesDir")
        }
    }
}
