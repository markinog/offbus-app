package com.offbuss.utils

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.URLDecoder
import java.util.zip.GZIPInputStream

class TileServer(private val context: Context) : NanoHTTPD("127.0.0.1", PORT) {

    companion object {
        const val PORT = 7777
        private const val TAG = "TileServer"
    }

    private var archive: TileArchive? = null

    fun openAndStart() {
        val aberto = TileArchive.abrir(context)
        archive = aberto
        start(SOCKET_READ_TIMEOUT, true)
        Log.d(TAG, "Server started on port $PORT (${aberto.quantidadeDeTiles} tiles)")
    }

    fun stopServer() {
        stop()
        archive?.close()
        archive = null
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = try { URLDecoder.decode(session.uri, "UTF-8") } catch (e: Exception) { session.uri }
        val parts = uri.removePrefix("/").split("/")

        return when {
            parts.size == 4 && parts[0] == "tiles" -> serveTile(parts)
            parts.size >= 3 && parts[0] == "fonts" -> serveFont(uri)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun serveTile(parts: List<String>): Response {
        val z = parts[1].toIntOrNull() ?: return noContent()
        val x = parts[2].toIntOrNull() ?: return noContent()
        // O MapLibre pede "/tiles/14/9298/5671" — sem extensão, mas um estilo
        // editado à mão pode trazer ".pbf", e aí o toIntOrNull devolveria null
        val y = parts[3].substringBefore('.').toIntOrNull() ?: return noContent()

        // A inversão TMS→XYZ é feita na conversão do MBTiles, não mais aqui
        var data = runCatching { archive?.tile(z, x, y) }
            .onFailure { Log.w(TAG, "Falha lendo o tile $z/$x/$y", it) }
            .getOrNull() ?: return noContent()

        // Descomprime gzip — o MapLibre espera PBF puro
        if (data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
            data = GZIPInputStream(data.inputStream()).use { it.readBytes() }
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/x-protobuf",
            data.inputStream(),
            data.size.toLong()
        )
    }

    private fun serveFont(uri: String): Response {
        // uri = "/fonts/noto-sans/0-255.pbf"  →  asset = "fonts/noto-sans/0-255.pbf"
        val assetPath = uri.removePrefix("/")
        return try {
            val data = context.assets.open(assetPath).readBytes()
            Log.d(TAG, "Font: $assetPath (${data.size} bytes)")
            newFixedLengthResponse(
                Response.Status.OK,
                "application/x-protobuf",
                data.inputStream(),
                data.size.toLong()
            )
        } catch (e: Exception) {
            // Retorna 200 vazio em vez de 404 — evita falha em cascata no estilo do MapLibre
            Log.w(TAG, "Font not found: $assetPath")
            newFixedLengthResponse(Response.Status.OK, "application/x-protobuf",
                ByteArray(0).inputStream(), 0)
        }
    }

    private fun noContent() = newFixedLengthResponse(
        Response.Status.NO_CONTENT, "application/x-protobuf", null, 0
    )
}
