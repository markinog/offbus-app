package com.offbuss.data

import android.content.Context
import com.google.gson.Gson
import com.offbuss.model.Horarios

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Índice de linhas disponíveis no app.
     * Para adicionar uma nova linha: copie os .geojson para assets/routes/,
     * crie o .json de horários em assets/horarios/ e adicione uma entrada aqui.
     */
    private val linhasDisponiveis = mapOf(
        "101" to Triple("Parque Universitário",  "Parque Universitário",   true),
        "102" to Triple("Recanto do Sol",        "Recanto do Sol",         true),
        "105" to Triple("Cohab I",               "Cohab I",                true),
        "106" to Triple("Cohab II",              "Cohab II",               true),
        "108" to Triple("Jardim Aeronave I",     "Jardim Aeronave I",      true),
        "109" to Triple("DIC VI",                "Vila Aeroporto",         true),
        "110" to Triple("Adhemar de Barros",     "Adhemar de Barros",      true),
        "111" to Triple("Jardim Aeronave II",    "Jardim Aeronave II",     true),
        "112" to Triple("Jardim Planalto I",     "Jardim Planalto I",      true),
        "113" to Triple("Jardim Planalto II",    "Jardim Planalto II",     true),
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
        "138" to Triple("Terminal Ouro Verde",   "Recanto do Sol I e II",  true)
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

    fun loadGeoJsonRota(linha: String, sentido: Int): String? =
        readAsset("routes/${linha}_sentido${sentido}_rota.geojson")

    fun loadGeoJsonPontos(linha: String, sentido: Int): String? =
        readAsset("routes/${linha}_sentido${sentido}_pontos.geojson")

    fun loadHorarios(linha: String, sentido: Int): Horarios? {
        val json = readAsset("horarios/${linha}_sentido${sentido}.json") ?: return null
        return runCatching { gson.fromJson(json, Horarios::class.java) }.getOrNull()
    }

    private fun readAsset(path: String): String? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
}