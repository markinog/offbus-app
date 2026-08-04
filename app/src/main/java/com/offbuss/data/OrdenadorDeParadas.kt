package com.offbuss.data

import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Reordena as paradas de um sentido pela posição real ao longo do traçado.
 *
 * A sequência em que as paradas aparecem no GeoJSON da EMDEC — e o campo
 * ptseqlinha, que segue essa mesma sequência — nem sempre acompanha o
 * percurso: há linhas em que a numeração salta entre ruas vizinhas. Aqui cada
 * parada é projetada sobre a geometria da rota e ordenada pela distância
 * percorrida até ela.
 *
 * Quando o traçado não serve de referência confiável, devolve null para o
 * chamador manter a ordem original do arquivo.
 */
internal object OrdenadorDeParadas {

    /** Acima disso a parada está longe demais do traçado para confiar na projeção. */
    private const val DESVIO_MAXIMO_M = 300.0

    /**
     * Quando as paradas somam bem mais que o traçado, a linha percorre o mesmo
     * corredor nos dois sentidos e a EMDEC desenhou a geometria uma vez só.
     * Projetar colapsaria ida e volta num corredor único, intercalando as
     * paradas — nesse caso a ordem original do arquivo é a melhor informação.
     */
    private const val FATOR_VAI_E_VOLTA = 1.5

    /** Distância entre a primeira e a última parada para tratar como circuito. */
    private const val FECHAMENTO_CIRCUITO_M = 1200.0

    private class Coord(val lon: Double, val lat: Double)

    /**
     * @param paradas features de parada, já filtradas pelo sentido
     * @param rota    features de traçado, já filtradas pelo sentido
     * @return índices de [paradas] na ordem do percurso, ou null se não der
     *         para ordenar com segurança
     */
    fun ordem(paradas: JSONArray, rota: JSONArray): List<Int>? {
        if (paradas.length() < 3) return null

        val pontos = paradas.pontosDeParada() ?: return null
        val caminho = costurar(rota.segmentos())
        if (caminho.size < 2) return null

        val acumulado = acumulado(caminho)
        val comprimentoRota = acumulado.last()
        if (comprimentoRota <= 0.0) return null

        // Linha que refaz o mesmo corredor: a geometria cobre metade do percurso
        if (somaConsecutivas(pontos) > comprimentoRota * FATOR_VAI_E_VOLTA) return null

        val projecoes = pontos.map { projetar(it, caminho, acumulado) }
        if (projecoes.any { it.desvio > DESVIO_MAXIMO_M }) return null

        var ordem = pontos.indices.sortedBy { projecoes[it].distancia }

        // A polilinha costurada pode ter ficado ao contrário: a origem declarada
        // pela EMDEC (menor ptseqlinha) é usada só para decidir de que ponta contar
        val origem = paradas.indiceDaOrigem()
        var posicao = ordem.indexOf(origem)
        if (posicao > ordem.size / 2) {
            ordem = ordem.reversed()
            posicao = ordem.indexOf(origem)
        }

        // Em circuito o começo é arbitrário: gira a sequência para largar da origem
        if (posicao > 0) {
            val fechamento = distancia(pontos[ordem.first()], pontos[ordem.last()])
            if (fechamento > FECHAMENTO_CIRCUITO_M) return null
            ordem = ordem.subList(posicao, ordem.size) + ordem.subList(0, posicao)
        }
        return ordem
    }

    // ─── Extração do GeoJSON ─────────────────────────────────────────────

    private fun JSONArray.pontosDeParada(): List<Coord>? {
        val saida = ArrayList<Coord>(length())
        for (i in 0 until length()) {
            val c = optJSONObject(i)
                ?.optJSONObject("geometry")
                ?.optJSONArray("coordinates") ?: return null
            if (c.length() < 2) return null
            saida.add(Coord(c.getDouble(0), c.getDouble(1)))
        }
        return saida
    }

    /** Índice da parada com menor ptseqlinha — a origem segundo a EMDEC. */
    private fun JSONArray.indiceDaOrigem(): Int {
        var indice = 0
        var menor = Double.MAX_VALUE
        for (i in 0 until length()) {
            val seq = optJSONObject(i)?.optJSONObject("properties")
                ?.optDouble("ptseqlinha", Double.MAX_VALUE) ?: Double.MAX_VALUE
            if (seq < menor) {
                menor = seq
                indice = i
            }
        }
        return indice
    }

    /** Achata LineString e MultiLineString numa lista de trechos. */
    private fun JSONArray.segmentos(): List<List<Coord>> {
        val saida = mutableListOf<List<Coord>>()
        for (i in 0 until length()) {
            val geometria = optJSONObject(i)?.optJSONObject("geometry") ?: continue
            val coordenadas = geometria.optJSONArray("coordinates") ?: continue

            when (geometria.optString("type")) {
                "LineString" -> coordenadas.comoTrecho()?.let { saida.add(it) }
                "MultiLineString" ->
                    for (j in 0 until coordenadas.length()) {
                        coordenadas.optJSONArray(j)?.comoTrecho()?.let { saida.add(it) }
                    }
            }
        }
        return saida
    }

    private fun JSONArray.comoTrecho(): List<Coord>? {
        val trecho = ArrayList<Coord>(length())
        for (i in 0 until length()) {
            val c = optJSONArray(i) ?: continue
            if (c.length() >= 2) trecho.add(Coord(c.getDouble(0), c.getDouble(1)))
        }
        return if (trecho.size >= 2) trecho else null
    }

    // ─── Geometria ───────────────────────────────────────────────────────

    /**
     * Encadeia os trechos num caminho único.
     *
     * Os trechos vêm fora de ordem e podem estar invertidos, então a cada passo
     * anexa-se o trecho cuja ponta esteja mais perto — testando as duas
     * orientações e as duas extremidades do caminho já montado.
     */
    private fun costurar(segmentos: List<List<Coord>>): List<Coord> {
        if (segmentos.isEmpty()) return emptyList()

        val restantes = segmentos.toMutableList()
        val maior = restantes.maxByOrNull { comprimento(it) }!!
        restantes.remove(maior)
        var caminho = maior

        while (restantes.isNotEmpty()) {
            var escolhido = restantes[0]
            var noFim = true
            var invertido = false
            var menor = Double.MAX_VALUE

            for (trecho in restantes) {
                for (inverter in booleanArrayOf(false, true)) {
                    val t = if (inverter) trecho.asReversed() else trecho
                    val dFim = distancia(caminho.last(), t.first())
                    if (dFim < menor) { menor = dFim; escolhido = trecho; noFim = true; invertido = inverter }
                    val dInicio = distancia(t.last(), caminho.first())
                    if (dInicio < menor) { menor = dInicio; escolhido = trecho; noFim = false; invertido = inverter }
                }
            }

            restantes.remove(escolhido)
            val t = if (invertido) escolhido.asReversed() else escolhido
            caminho = if (noFim) caminho + t else t + caminho
        }
        return caminho
    }

    private class Projecao(val distancia: Double, val desvio: Double)

    /** Distância percorrida até o ponto do caminho mais próximo da parada. */
    private fun projetar(parada: Coord, caminho: List<Coord>, acumulado: DoubleArray): Projecao {
        var melhorDistancia = 0.0
        var melhorDesvio = Double.MAX_VALUE

        for (i in 0 until caminho.size - 1) {
            val a = caminho[i]
            val b = caminho[i + 1]
            val dx = b.lon - a.lon
            val dy = b.lat - a.lat
            val den = dx * dx + dy * dy

            val t = if (den == 0.0) 0.0
                    else (((parada.lon - a.lon) * dx + (parada.lat - a.lat) * dy) / den)
                        .coerceIn(0.0, 1.0)

            val projetado = Coord(a.lon + t * dx, a.lat + t * dy)
            val desvio = distancia(parada, projetado)
            if (desvio < melhorDesvio) {
                melhorDesvio = desvio
                melhorDistancia = acumulado[i] + distancia(a, projetado)
            }
        }
        return Projecao(melhorDistancia, melhorDesvio)
    }

    private fun acumulado(caminho: List<Coord>): DoubleArray {
        val acc = DoubleArray(caminho.size)
        for (i in 1 until caminho.size) {
            acc[i] = acc[i - 1] + distancia(caminho[i - 1], caminho[i])
        }
        return acc
    }

    private fun comprimento(trecho: List<Coord>): Double {
        var total = 0.0
        for (i in 0 until trecho.size - 1) total += distancia(trecho[i], trecho[i + 1])
        return total
    }

    private fun somaConsecutivas(pontos: List<Coord>): Double {
        var total = 0.0
        for (i in 0 until pontos.size - 1) total += distancia(pontos[i], pontos[i + 1])
        return total
    }

    /** Distância aproximada em metros — equirretangular, suficiente na escala da cidade. */
    private fun distancia(a: Coord, b: Coord): Double {
        val dx = (b.lon - a.lon) * 111320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
        val dy = (b.lat - a.lat) * 110540.0
        return hypot(dx, dy)
    }
}
