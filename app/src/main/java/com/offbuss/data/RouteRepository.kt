package com.offbuss.data

import android.content.Context
import com.google.gson.Gson
import com.offbuss.model.Horarios
import org.json.JSONArray
import org.json.JSONObject

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Índice de linhas disponíveis no app.
     * Para adicionar uma nova linha: copie os .geojson para assets/routes/,
     * crie o .json de horários em assets/horarios/ e adicione uma entrada aqui.
     *
     * O terceiro campo da Triple indica se a linha tem volta. Só marque true
     * quando o GeoJSON realmente trouxer features com "sentido": 2 — as linhas
     * circulares têm apenas o sentido 1, e o botão de volta ficaria vazio.
     */
    private val linhasDisponiveis = mapOf(
        "101" to Triple("Parque Universitário",  "Parque Universitário",   false),
        "102" to Triple("Recanto do Sol",        "Recanto do Sol",         false),
        "105" to Triple("Cohab I",               "Cohab I",                false),
        "106" to Triple("Cohab II",              "Cohab II",               false),
        "108" to Triple("Jardim Aeronave I",     "Jardim Aeronave I",      false),
        "109" to Triple("DIC VI",                "Vila Aeroporto",         false),
        "110" to Triple("Adhemar de Barros",     "Adhemar de Barros",      false),
        "111" to Triple("Jardim Aeronave II",    "Jardim Aeronave II",     false),
        "112" to Triple("Jardim Planalto I",     "Jardim Planalto I",      false),
        "113" to Triple("Jardim Planalto II",    "Jardim Planalto II",     false),
        "114" to Triple("Jardim Melina",         "Corredor Central",       true),
        "116" to Triple("Terminal Ouro Verde",   "Shopping Dom Pedro",     true),
        "117" to Triple("Terminal Ouro Verde",   "Jardim Itatinga",        true),
        "122" to Triple("Terminal Vida Nova",    "Campinas Shopping",      true),
        "123" to Triple("Terminal Campo Grande", "Terminal Ouro Verde",    true),
        "125" to Triple("Terminal Ouro Verde",   "Shopping Iguatemi",      true),
        "128" to Triple("Vida Nova",             "Terminal Mercado I",     true),
        "131" to Triple("Vida Nova",             "Terminal Mercado I",     true),
        "133" to Triple("Vida Nova",             "Vida Nova",              true),
        "134" to Triple("Terminal Ouro Verde",   "Terminal Ouro Verde",    true),
        // Circular: o GeoJSON da 138 só traz features de sentido 1
        "138" to Triple("Terminal Ouro Verde",   "Recanto do Sol I e II",  false)
    )

    /** Busca por número da linha, ponto inicial ou final (case-insensitive) */
    fun search(termo: String): List<String> {
        val t = termo.trim().lowercase()
        return linhasDisponiveis.entries
            .filter { (linha, info) ->
                linha.contains(t) ||
                        info.first.lowercase().contains(t) ||
                        info.second.lowercase().contains(t)
            }
            .map { it.key }
            .sorted()
    }

    fun temSentido2(linha: String): Boolean =
        linhasDisponiveis[linha]?.third ?: false

    fun getPontos(linha: String): Pair<String, String> {
        val info = linhasDisponiveis[linha] ?: return Pair("", "")
        return Pair(info.first, info.second)
    }

    fun loadGeoJsonRota(linha: String, sentido: Int): String? {
        val json = readAsset("routes/${linha}_sentido${sentido}_rota.geojson") ?: return null
        // GeoJSON inesperado: devolve o arquivo cru em vez de sumir com a rota
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        return filtrarPorSentido(root, sentido)?.toString()
    }

    /**
     * Lê as paradas do sentido e injeta a propriedade "ordem" em cada feature,
     * usada como rótulo numérico no mapa.
     */
    fun loadGeoJsonPontos(linha: String, sentido: Int): String? {
        val json = readAsset("routes/${linha}_sentido${sentido}_pontos.geojson") ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        val doSentido = filtrarPorSentido(root, sentido) ?: return null
        return runCatching { numerarPontos(doSentido).toString() }.getOrNull()
    }

    /**
     * Mantém só as features do sentido pedido.
     *
     * Os arquivos da EMDEC trazem ida e volta no mesmo GeoJSON, cada feature
     * marcada com a propriedade "sentido". Sem este filtro o mapa desenha os
     * dois trajetos sobrepostos e as paradas de ambos.
     *
     * Devolve null quando o sentido não existe no arquivo — caso das linhas
     * circulares, que só têm sentido 1.
     */
    private fun filtrarPorSentido(root: JSONObject, sentido: Int): JSONObject? {
        val features = root.optJSONArray("features") ?: return null
        val filtradas = JSONArray()

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            if (feature.optJSONObject("properties")?.optInt("sentido", -1) == sentido) {
                filtradas.put(feature)
            }
        }

        if (filtradas.length() == 0) return null
        return root.put("features", filtradas)
    }

    /**
     * Numera as paradas na ordem em que aparecem no arquivo, que é a ordem de
     * viagem: dentro de cada sentido a EMDEC entrega ptseqlinha crescente, e a
     * volta já começa onde a ida termina.
     */
    private fun numerarPontos(root: JSONObject): JSONObject {
        val features = root.getJSONArray("features")

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val props   = feature.optJSONObject("properties")
                ?: JSONObject().also { feature.put("properties", it) }

            // Gravado como texto: o text-field do MapLibre espera string
            props.put("ordem", (i + 1).toString())
        }
        return root
    }

    fun loadHorarios(linha: String, sentido: Int): Horarios? {
        val json = readAsset("horarios/${linha}_sentido${sentido}.json") ?: return null
        return runCatching { gson.fromJson(json, Horarios::class.java) }.getOrNull()
    }

    private fun readAsset(path: String): String? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
}