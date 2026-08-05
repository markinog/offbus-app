package com.offbuss.utils

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Leitor dos tiles vetoriais direto do APK, sem cópia para o armazenamento.
 *
 * O app antes guardava o mapa base num MBTiles — um banco SQLite — e o
 * `SQLiteDatabase` só abre um caminho no sistema de arquivos, nunca um asset.
 * Daí a cópia dos ~272 MiB para o `filesDir` na primeira execução, que fazia a
 * primeira abertura demorar e ocupava o dobro de espaço no aparelho.
 *
 * Aqui os tiles vêm de um arquivo plano (`map.tiles`) com um índice de largura
 * fixa (`map.idx`), ambos gerados por `tools/mbtiles_para_arquivo.py`. Como os
 * dois são declarados em `noCompress`, ficam gravados no APK sem compressão e
 * em região contígua: `AssetManager.openFd` devolve o descritor do próprio APK
 * junto do deslocamento onde o asset começa, e a leitura vira um seek.
 *
 * Ver o cabeçalho do script para o layout dos dois arquivos.
 *
 * @param base deslocamento de `map.tiles` dentro do arquivo que [canal] abre —
 *   zero quando ele é um arquivo solto, `startOffset` quando está dentro do APK
 */
class TileArchive internal constructor(
    private val canal: FileChannel,
    private val base: Long,
    private val tamanhoDosTiles: Long,
    private val indice: ByteBuffer,
    private val totalDeEntradas: Int,
    private val aoFechar: Closeable?
) : Closeable {

    companion object {
        private const val MAGIC = 0x4F464642           // "OFFB" em big-endian
        private const val VERSAO = 1
        private const val TAMANHO_CABECALHO = 12
        private const val TAMANHO_ENTRADA = 16

        const val ASSET_TILES = "map.tiles"
        const val ASSET_INDICE = "map.idx"

        /**
         * Abre os assets do mapa embutidos no APK.
         *
         * @throws IOException se os assets faltarem, estiverem corrompidos ou
         *   tiverem sido comprimidos na hora de empacotar o APK — este último
         *   caso significa que `noCompress` perdeu a extensão `tiles`.
         */
        fun abrir(context: Context): TileArchive {
            val (indice, total) = validarIndice(
                context.assets.open(ASSET_INDICE).use { it.readBytes() }
            )

            val descritor = try {
                context.assets.openFd(ASSET_TILES)
            } catch (e: IOException) {
                // openFd só funciona com asset gravado sem compressão
                throw IOException(
                    "Não consegui abrir $ASSET_TILES por descritor. Confirme " +
                            "que \"tiles\" está em androidResources.noCompress.", e
                )
            }

            val canal = FileInputStream(descritor.fileDescriptor).channel
            return TileArchive(
                canal, descritor.startOffset, descritor.length,
                indice, total, descritor
            )
        }

        /**
         * Abre um par map.tiles/map.idx solto no sistema de arquivos.
         *
         * Existe para os testes: fora do Android não há `AssetManager`, e o que
         * precisa ser verificado é justamente o miolo — a busca no índice e a
         * leitura por offset, idênticos nos dois caminhos.
         */
        internal fun deArquivos(tiles: File, idx: File): TileArchive {
            val (indice, total) = validarIndice(idx.readBytes())
            val entrada = FileInputStream(tiles)
            return TileArchive(entrada.channel, 0L, tiles.length(), indice, total, entrada)
        }

        private fun validarIndice(bytes: ByteArray): Pair<ByteBuffer, Int> {
            if (bytes.size < TAMANHO_CABECALHO) {
                throw IOException("$ASSET_INDICE truncado (${bytes.size} bytes)")
            }

            val buffer = ByteBuffer.wrap(bytes)
            // O magic é lido em big-endian para casar com a ordem dos caracteres
            if (buffer.getInt(0) != MAGIC) {
                throw IOException("$ASSET_INDICE não começa com \"OFFB\"")
            }
            val versao = buffer.get(4).toInt()
            if (versao != VERSAO) {
                throw IOException("$ASSET_INDICE está na versão $versao, esperada $VERSAO")
            }

            // O resto do arquivo é little-endian, como o script grava
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val total = buffer.getInt(8)

            val esperado = TAMANHO_CABECALHO + total.toLong() * TAMANHO_ENTRADA
            if (total < 0 || bytes.size.toLong() != esperado) {
                throw IOException(
                    "$ASSET_INDICE declara $total entradas mas tem " +
                            "${bytes.size} bytes (esperado $esperado)"
                )
            }
            return buffer to total
        }

        /** Mesma composição de chave do script — as duas precisam bater. */
        internal fun chave(z: Int, x: Int, y: Int): Long =
            (z.toLong() shl 58) or (x.toLong() shl 29) or y.toLong()
    }

    /** Quantos tiles o arquivo contém — usado nos testes e no log de abertura. */
    val quantidadeDeTiles: Int get() = totalDeEntradas

    /**
     * Devolve o tile no esquema XYZ, ou null quando ele não existe.
     *
     * O blob sai como está no arquivo, ainda em gzip: quem descomprime é o
     * [TileServer], que já fazia isso quando a origem era o MBTiles.
     */
    fun tile(z: Int, x: Int, y: Int): ByteArray? {
        // Acima de z29 a chave transbordaria para os bits do zoom e devolveria
        // o tile errado em vez de nada
        if (z < 0 || x < 0 || y < 0 || z > 29 || x >= (1 shl 29) || y >= (1 shl 29)) return null

        val posicao = procurar(chave(z, x, y))
        if (posicao < 0) return null

        val entrada = TAMANHO_CABECALHO + posicao * TAMANHO_ENTRADA
        // Os campos são u32; a máscara evita que virem negativos
        val offset  = indice.getInt(entrada + 8).toLong()  and 0xFFFFFFFFL
        val tamanho = indice.getInt(entrada + 12).toLong() and 0xFFFFFFFFL

        if (tamanho == 0L) return ByteArray(0)
        if (offset + tamanho > tamanhoDosTiles) {
            throw IOException("Entrada $z/$x/$y aponta para fora de $ASSET_TILES")
        }

        val buffer = ByteBuffer.allocate(tamanho.toInt())
        var posicaoNoArquivo = base + offset
        // read posicional não move o cursor do canal: várias threads do
        // NanoHTTPD podem ler ao mesmo tempo sem disputar o descritor
        while (buffer.hasRemaining()) {
            val lidos = canal.read(buffer, posicaoNoArquivo)
            if (lidos <= 0) throw IOException("$ASSET_TILES terminou antes do tile $z/$x/$y")
            posicaoNoArquivo += lidos
        }
        return buffer.array()
    }

    /** Busca binária sobre o índice; devolve a posição da entrada ou -1. */
    private fun procurar(alvo: Long): Int {
        var baixo = 0
        var alto = totalDeEntradas - 1
        while (baixo <= alto) {
            val meio = (baixo + alto) ushr 1
            val chave = indice.getLong(TAMANHO_CABECALHO + meio * TAMANHO_ENTRADA)
            when {
                chave < alvo -> baixo = meio + 1
                chave > alvo -> alto = meio - 1
                else -> return meio
            }
        }
        return -1
    }

    override fun close() {
        // Fechar o descritor fecha o fd que o canal usa; a ordem inversa
        // deixaria o canal apontando para um fd já liberado
        runCatching { canal.close() }
        runCatching { aoFechar?.close() }
    }
}
