package com.offbuss.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Testes do leitor do mapa base.
 *
 * A fixture em `src/test/resources/mapa/` foi gerada pelo próprio
 * `tools/mbtiles_para_arquivo.py` a partir do `fixture.mbtiles` que está ao
 * lado dela. Os dois andam juntos de propósito: o formato é combinado entre um
 * script Python e um leitor Kotlin, e nada além de um teste impede que um lado
 * mude sem o outro — ver [arquivoGeradoPeloScriptContinuaIgual].
 *
 * O conteúdo da fixture (y já em XYZ):
 *
 * | z  | x     | y     | conteúdo                               |
 * |----|-------|-------|----------------------------------------|
 * | 0  | 0     | 0     | gzip "raiz do mundo"                   |
 * | 5  | 12    | 19    | gzip "zoom cinco"                      |
 * | 10 | 391   | 570   | gzip "conteudo repetido"               |
 * | 10 | 392   | 570   | gzip "conteudo repetido" (mesmo blob)  |
 * | 14 | 12530 | 18262 | gzip "campinas centro"                 |
 * | 14 | 12531 | 18262 | vazio (0 bytes)                        |
 * | 14 | 12531 | 18263 | 1024 bytes binários, sem gzip          |
 */
class TileArchiveTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private lateinit var tiles: File
    private lateinit var idx: File

    @Before
    fun copiarFixture() {
        tiles = recurso("map.tiles")
        idx = recurso("map.idx")
    }

    private fun recurso(nome: String): File {
        val destino = pasta.newFile(nome)
        val bytes = checkNotNull(javaClass.getResourceAsStream("/mapa/$nome")) {
            "fixture /mapa/$nome não está no classpath de teste"
        }.use { it.readBytes() }
        destino.writeBytes(bytes)
        return destino
    }

    private fun abrir() = TileArchive.deArquivos(tiles, idx)

    private fun ByteArray.descomprimido(): String =
        GZIPInputStream(inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)

    @Test
    fun leTodosOsTilesDaFixture() {
        abrir().use { arquivo ->
            assertEquals(7, arquivo.quantidadeDeTiles)

            // Primeira e última entradas do índice — onde um erro de busca
            // binária aparece antes de qualquer outro
            assertEquals("raiz do mundo", arquivo.tile(0, 0, 0)!!.descomprimido())
            assertEquals(1024, arquivo.tile(14, 12531, 18263)!!.size)

            assertEquals("zoom cinco", arquivo.tile(5, 12, 19)!!.descomprimido())
            assertEquals("campinas centro", arquivo.tile(14, 12530, 18262)!!.descomprimido())
        }
    }

    @Test
    fun tilesDeduplicadosDevolvemOMesmoConteudo() {
        abrir().use { arquivo ->
            // As duas chaves apontam para o mesmo offset dentro do map.tiles
            val primeiro = arquivo.tile(10, 391, 570)
            val segundo = arquivo.tile(10, 392, 570)
            assertNotNull(primeiro)
            assertArrayEquals(primeiro, segundo)
            assertEquals("conteudo repetido", primeiro!!.descomprimido())
        }
    }

    @Test
    fun tileVazioNaoViraNulo() {
        abrir().use { arquivo ->
            // Um tile de 0 bytes existe e é diferente de "não existe":
            // devolver null aqui faria o TileServer responder 204 sem motivo
            val vazio = arquivo.tile(14, 12531, 18262)
            assertNotNull(vazio)
            assertEquals(0, vazio!!.size)
        }
    }

    @Test
    fun tileInexistenteDevolveNulo() {
        abrir().use { arquivo ->
            assertNull(arquivo.tile(9, 0, 0))            // zoom fora da fixture
            assertNull(arquivo.tile(14, 12530, 18263))   // x existe, y não
            assertNull(arquivo.tile(0, 0, 1))            // logo após a primeira entrada
            assertNull(arquivo.tile(14, 99999, 99999))   // depois da última entrada
        }
    }

    @Test
    fun coordenadaForaDaFaixaDevolveNulo() {
        abrir().use { arquivo ->
            // Sem essas guardas o deslocamento de bits transbordaria para os
            // bits do zoom e a busca acharia o tile de outra coordenada
            assertNull(arquivo.tile(-1, 0, 0))
            assertNull(arquivo.tile(0, -1, 0))
            assertNull(arquivo.tile(0, 0, -1))
            assertNull(arquivo.tile(30, 0, 0))
            assertNull(arquivo.tile(0, 1 shl 29, 0))
        }
    }

    @Test
    fun leituraConcorrenteDevolveSempreOMesmoBlob() {
        abrir().use { arquivo ->
            // O NanoHTTPD atende cada requisição numa thread: a leitura
            // posicional precisa aguentar isso sem sincronização
            val esperado = arquivo.tile(14, 12531, 18263)!!
            val erros = java.util.concurrent.atomic.AtomicInteger()

            (1..8).map {
                Thread {
                    repeat(200) {
                        if (!arquivo.tile(14, 12531, 18263)!!.contentEquals(esperado)) {
                            erros.incrementAndGet()
                        }
                    }
                }
            }.onEach { it.start() }.onEach { it.join() }

            assertEquals(0, erros.get())
        }
    }

    @Test
    fun indiceComMagicErradoNaoAbre() {
        idx.writeBytes(idx.readBytes().also { it[0] = 'X'.code.toByte() })

        val erro = assertThrows(IOException::class.java) { abrir() }
        assertTrue(erro.message!!.contains("OFFB"))
    }

    @Test
    fun indiceTruncadoNaoAbre() {
        // Uma entrada a menos do que o cabeçalho declara
        idx.writeBytes(idx.readBytes().copyOf(idx.length().toInt() - 16))

        val erro = assertThrows(IOException::class.java) { abrir() }
        assertTrue(erro.message!!.contains("entradas"))
    }

    @Test
    fun chaveOrdenaPorZoomDepoisPorXDepoisPorY() {
        // A busca binária só funciona se a ordem numérica das chaves for a
        // mesma que o script usa para ordenar as entradas
        assertTrue(TileArchive.chave(0, 0, 0) < TileArchive.chave(1, 0, 0))
        assertTrue(TileArchive.chave(5, 1, 999) < TileArchive.chave(5, 2, 0))
        assertTrue(TileArchive.chave(5, 2, 0) < TileArchive.chave(5, 2, 1))
    }

    /**
     * Roda o conversor de verdade e compara com a fixture versionada.
     *
     * É o que impede o formato do script e o do leitor de divergirem em
     * silêncio. Pulado quando não há python3 na máquina; no CI ele existe.
     */
    @Test
    fun arquivoGeradoPeloScriptContinuaIgual() {
        // O Gradle roda o teste com o diretório do módulo como cwd, mas isso
        // não é contrato: procurar nos dois níveis evita um falso vermelho
        val script = listOf("../tools", "tools")
            .map { File(it, "mbtiles_para_arquivo.py") }
            .firstOrNull { it.exists() }
        assumeTrue("script não encontrado a partir de ${File(".").absolutePath}", script != null)
        assumeTrue("python3 indisponível", temPython3())

        val mbtiles = recurso("fixture.mbtiles")
        val saida = pasta.newFolder("gerado")
        val processo = ProcessBuilder(
            "python3", script!!.absolutePath, mbtiles.absolutePath, saida.absolutePath
        ).redirectErrorStream(true).start()

        val log = processo.inputStream.bufferedReader().readText()
        assertTrue("conversor falhou:\n$log", processo.waitFor() == 0)

        assertArrayEquals(
            "map.tiles mudou — regenere a fixture ou reveja o script",
            tiles.readBytes(), File(saida, "map.tiles").readBytes()
        )
        assertArrayEquals(
            "map.idx mudou — o leitor e o script saíram de sincronia",
            idx.readBytes(), File(saida, "map.idx").readBytes()
        )
    }

    private fun temPython3(): Boolean = runCatching {
        ProcessBuilder("python3", "--version").start().waitFor() == 0
    }.getOrDefault(false)
}
