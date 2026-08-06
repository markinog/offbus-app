package com.offbuss.data

import android.content.Context
import com.google.gson.Gson
import com.offbuss.model.Horarios
import com.offbuss.model.Route
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

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
     *
     * Os pontos vêm de assets/corrigir-linhas.json, a listagem oficial de
     * ida/volta da EMDEC: o primeiro campo da Triple é a "Ida" e o segundo é a
     * "Volta". Antes eles saíam do "nome_linha" do GeoJSON, que é sujo na
     * origem e trazia extremos errados. As abreviações do JSON (Term., Jd.,
     * Pq., Shop. …) foram escritas por extenso aqui, porque o nome aparece
     * inteiro na lista de resultados. Horários existem apenas para as 21
     * primeiras linhas.
     */
    private val linhasDisponiveis = mapOf(
        "101"    to Triple("Parque Universitário",           "Terminal Ouro Verde",                         false),
        "102"    to Triple("Recanto do Sol",                 "Terminal Ouro Verde",                         false),
        "105"    to Triple("Cohab I",                        "Terminal Ouro Verde",                         false),
        "106"    to Triple("Cohab II",                       "Terminal Ouro Verde",                         false),
        "108"    to Triple("Jardim Aeronave I",              "Terminal Ouro Verde",                         false),
        "109"    to Triple("DIC VI",                         "Terminal Ouro Verde",                         false),
        "110"    to Triple("Jardim Adhemar de Barros",       "Terminal Ouro Verde",                         false),
        "111"    to Triple("Jardim Aeronave II",             "Terminal Ouro Verde",                         false),
        "112"    to Triple("Jardim Planalto I",              "Terminal Ouro Verde",                         false),
        "113"    to Triple("Jardim Planalto II",             "Terminal Ouro Verde",                         false),
        "114"    to Triple("Corredor Central",               "Jardim Melina",                               true),
        "116"    to Triple("Shopping D. Pedro",              "Terminal Ouro Verde",                         true),
        "117"    to Triple("Corredor Central",               "DIC VI",                                      true),
        "118"    to Triple("Cambuí",                         "Terminal Ouro Verde",                         true),
        "119"    to Triple("Shopping D. Pedro",              "Terminal Ouro Verde",                         true),
        "120"    to Triple("Terminal Central",               "Terminal Ouro Verde",                         true),
        "122"    to Triple("Campinas Shopping",              "Terminal Vida Nova",                          true),
        "123"    to Triple("Terminal Ouro Verde",            "Terminal Campo Grande",                       true),
        "125"    to Triple("Shopping Iguatemi",              "Terminal Ouro Verde",                         true),
        "128"    to Triple("Terminal Ouro Verde",            "Terminal Vida Nova",                          true),
        "131"    to Triple("Corredor Central",               "Terminal Vida Nova",                          true),
        "132"    to Triple("Rodoviária",                     "Terminal Vida Nova",                          true),
        "133"    to Triple("Corredor Central",               "Vida Nova",                                   true),
        "134"    to Triple("Terminal Barão Geraldo",         "Terminal Ouro Verde",                         true),
        "135"    to Triple("Corredor Central",               "Jardim Filadélfia",                           true),
        "136"    to Triple("Terminal Central",               "Terminal Vida Nova",                          true),
        // Circular: o GeoJSON da 138 só traz features de sentido 1
        "138"    to Triple("Recanto do Sol I e II",          "Terminal Ouro Verde",                         false),
        "144"    to Triple("Jardim Capivari",                "Terminal Campos Elíseos",                     false),
        "150"    to Triple("Terminal Vila União",            "Terminal Sta. Lúcia",                         true),
        "152"    to Triple("Residencial Vila União II",      "Terminal Sta. Lúcia",                         false),
        "153"    to Triple("Rodoviária",                     "Terminal Vila União",                         true),
        "154"    to Triple("Corredor Central",               "Terminal Vila União",                         true),
        "161"    to Triple("Corredor Central",               "Souza Queiroz",                               true),
        "163"    to Triple("Terminal Central",               "Terminal Vila União",                         true),
        "164"    to Triple("Terminal Central",               "Terminal Vila União",                         true),
        "165"    to Triple("Jardim Paulicéia",               "Terminal Campos Elíseos",                     false),
        "171"    to Triple("Shopping D. Pedro",              "Campinas Shopping",                           true),
        "173"    to Triple("Centro",                         "Jardim São Vicente",                          true),
        "179"    to Triple("Terminal Mercado I",             "Jardim São Domingos",                         true),
        "180"    to Triple("Jardim Santo Antônio",           "Terminal Ouro Verde",                         false),
        "186"    to Triple("Terminal Central",               "Aeroporto de Viracopos / Jardim Santa Maria", true),
        "187"    to Triple("Aeroporto Viracopos",            "Jardim São Domingos",                         true),
        "188"    to Triple("Aeroporto Viracopos",            "Jardim Fernanda",                             true),
        "189"    to Triple("Terminal Central",               "Jardim Columbia",                             true),
        "190"    to Triple("Corredor Central",               "Jardim São Domingos",                         true),
        "191"    to Triple("Terminal Central",               "Jardim Fernanda",                             true),
        "192"    to Triple("Corredor Central",               "Vila Diva",                                   true),
        "193"    to Triple("Rodoviária",                     "Aeroporto Viracopos",                         true),
        "194"    to Triple("Terminal Central",               "Jardim Itaguaçu",                             true),
        "195"    to Triple("Terminal Central",               "Vila Palmeiras",                              true),
        "196"    to Triple("Aeroporto Viracopos",            "Terminal Ouro Verde",                         true),
        "197"    to Triple("Corredor Central",               "Jardim Marisa",                               true),
        "198"    to Triple("Aeroporto Viracopos",            "Terminal Ouro Verde",                         true),
        "199"    to Triple("Terminal Mercado I",             "Vida Nova",                                   true),
        "200"    to Triple("Jardim Novo Maracanã",           "Terminal Campo Grande",                       false),
        "201"    to Triple("Jardim Sta. Rosa",               "Terminal Campo Grande",                       false),
        "202"    to Triple("Parque Valença II",              "Novo Terminal Campo Grande",                  false),
        "203"    to Triple("Campina Grande",                 "Terminal Campo Grande",                       false),
        "204"    to Triple("Parque da Floresta",             "Terminal Campo Grande",                       false),
        "205"    to Triple("Terminal Ouro Verde",            "Terminal Campo Grande",                       true),
        "206"    to Triple("Santa Clara",                    "Terminal Campo Grande",                       false),
        "207"    to Triple("Jardim Lisa",                    "Terminal Campo Grande",                       false),
        "208"    to Triple("Jardim Maracanã",                "Terminal Campo Grande",                       false),
        "209"    to Triple("Chác. Cruz. do Sul",             "Terminal Campo Grande",                       false),
        "209.1"  to Triple("Chác. Cruz. do Sul",             "Terminal Campo Grande",                       false),
        "210"    to Triple("Terminal Barão Geraldo",         "Terminal Campo Grande",                       true),
        "211"    to Triple("Shopping Iguatemi",              "Terminal Campo Grande",                       true),
        "212"    to Triple("Corredor Central",               "Terminal Itajaí",                             true),
        "213"    to Triple("Rodoviária",                     "Terminal Itajaí",                             true),
        "214"    to Triple("Terminal Central",               "Terminal Itajaí",                             true),
        "215"    to Triple("Jardim Bassoli",                 "Terminal Campo Grande",                       false),
        "216"    to Triple("Residencial Col. das Nascentes", "Terminal Campo Grande",                       false),
        "217"    to Triple("Residencial São Luiz",           "Terminal Campo Grande",                       false),
        "218"    to Triple("Terminal Campo Grande",          "Terminal Itajaí",                             true),
        "220"    to Triple("Cambuí",                         "Terminal Campo Grande",                       true),
        "226"    to Triple("Residencial Sírius",             "Terminal Satélite Íris",                      false),
        "227"    to Triple("Jardim Florence II",             "Terminal Satélite Íris",                      false),
        "230"    to Triple("Parque dos Resedás",             "Jardim Ipaussurama",                          true),
        "231"    to Triple("Terminal Central",               "Terminal Satélite Íris",                      true),
        "232"    to Triple("Jardim Rossin",                  "Terminal Satélite Íris",                      false),
        "233"    to Triple("Residencial Cosmos",             "Terminal Satélite Íris",                      false),
        "239"    to Triple("PUCC II",                        "Terminal Ouro Verde",                         true),
        "240"    to Triple("Shopping D. Pedro",              "Jardim Garcia",                               true),
        "241"    to Triple("Terminal Mercado I",             "Vila Pe. Manoel da Nóbrega",                  true),
        "242"    to Triple("Terminal Mercado I",             "Jardim Miranda",                              true),
        "244"    to Triple("Rodoviária",                     "Shopping Dom Pedro",                          true),
        "249"    to Triple("Shopping Iguatemi",              "Parque dos Eucaliptos",                       true),
        "251"    to Triple("Terminal Mercado I",             "Parque Fazendinha",                           true),
        "252"    to Triple("Terminal Mercado I",             "Parque São Jorge",                            true),
        "253"    to Triple("Vila Boa Vista",                 "Swift",                                       true),
        "254"    to Triple("Terminal Mercado I",             "Terminal Pe. Anchieta",                       true),
        "255"    to Triple("Terminal Mercado I",             "Parque Via Norte",                            true),
        "256"    to Triple("Terminal Mercado I",             "Parque Fazendinha",                           true),
        "257"    to Triple("Terminal Mercado I",             "Parque São Jorge",                            true),
        "260"    to Triple("Shopping Iguatemi",              "Nova Aparecida",                              true),
        "261"    to Triple("Terminal Mercado I",             "Vila Réggio",                                 true),
        "263"    to Triple("Terminal Mercado I",             "Terminal Pe. Anchieta",                       true),
        "264"    to Triple("Corredor Central",               "Padre Anchieta",                              true),
        "265"    to Triple("Terminal Mercado I",             "Nova Aparecida",                              true),
        "266"    to Triple("Hospital das Clínicas",          "Parque São Jorge",                            true),
        "269"    to Triple("Terminal Barão Geraldo",         "Terminal Pe. Anchieta",                       true),
        "271"    to Triple("Terminal Mercado I",             "Jardim Pacaembú",                             true),
        "273"    to Triple("Terminal Mercado I",             "Jardim Eulina",                               true),
        "289"    to Triple("Terminal Mercado I",             "Itajaí",                                      true),
        "299"    to Triple("Terminal Mercado I",             "Nova Aparecida",                              true),
        "300"    to Triple("Terminal Barão Geraldo",         "San Conrado",                                 true),
        "309"    to Triple("Terminal Mercado I",             "Parque Cidade",                               true),
        "310"    to Triple("Corredor Central",               "Vila Olímpia",                                true),
        "311"    to Triple("Terminal Mercado I",             "Jardim Santa Mônica",                         true),
        "312"    to Triple("Terminal Mercado I",             "Vila Esperança",                              true),
        "313"    to Triple("Corredor Central",               "CDHU - Amarais",                              true),
        "314"    to Triple("Terminal Barão Geraldo",         "Terminal Pe. Anchieta",                       true),
        "315"    to Triple("Aeroporto Amarais",              "San Martin",                                  true),
        "316"    to Triple("Corredor Central",               "San Martín",                                  true),
        "317"    to Triple("Jardim São Marcos",              "Jardim São José",                             true),
        "318"    to Triple("Corredor Central",               "Jardim Mirassol",                             true),
        "319"    to Triple("Terminal Barão Geraldo",         "San Martín",                                  true),
        "320"    to Triple("Real Parque II",                 "Terminal Barão Geraldo",                      false),
        "321"    to Triple("Bq. das Palmeiras",              "Terminal Barão Geraldo",                      false),
        "322"    to Triple("Guara",                          "Terminal Barão Geraldo",                      false),
        "323"    to Triple("Vila Holândia",                  "Terminal Barão Geraldo",                      false),
        "324"    to Triple("Real Parque I",                  "Terminal Barão Geraldo",                      false),
        "325"    to Triple("Vila Sta. Isabel",               "Terminal Barão Geraldo",                      false),
        "328"    to Triple("Cidade Judiciária",              "Terminal Barão Geraldo",                      true),
        "329"    to Triple("Estação Cidade Judiciária",      "Terminal Barão Geraldo",                      true),
        "329.1"  to Triple("Cidade Judiciária",              "Terminal Barão Geraldo",                      true),
        "330"    to Triple("Corredor Central",               "UNICAMP",                                     true),
        "331"    to Triple("Rodoviária",                     "Terminal Barão Geraldo",                      true),
        "332"    to Triple("Rodoviária",                     "Hospital das Clínicas",                       true),
        "333"    to Triple("Corredor Central",               "Terminal Barão Geraldo",                      true),
        "336"    to Triple("Hospital das Clínicas",          "Terminal Barão Geraldo",                      false),
        "337"    to Triple("UNICAMP",                        "Terminal Barão Geraldo",                      false),
        "338"    to Triple("Shopping Iguatemi",              "Terminal Barão Geraldo",                      true),
        "339"    to Triple("Terminal Mercado I",             "Cidade Universitária",                        true),
        "341"    to Triple("Rodoviária",                     "Jardim São Gabriel",                          true),
        "342"    to Triple("Rodoviária",                     "Jardim Aliança",                              true),
        "344"    to Triple("Centro",                         "Shopping Prado",                              true),
        "345"    to Triple("Cidade Judiciária",              "Jardim Carlos Lourenço",                      true),
        "346"    to Triple("Centro",                         "Jardim Carlos Lourenço",                      true),
        "348"    to Triple("Cidade Judiciária",              "Vila Marieta",                                true),
        "349"    to Triple("Rodoviária",                     "Vila Formosa",                                true),
        "350"    to Triple("Cidade Judiciária",              "Gargantilha",                                 true),
        "351"    to Triple("Corredor Central",               "PUCC I",                                      true),
        "352"    to Triple("Terminal Mercado I",             "Bairro Bananal",                              true),
        "353"    to Triple("Est. Expedicionários",           "Alphaville",                                  true),
        "355"    to Triple("Terminal Mercado I",             "Vila Gênesis",                                true),
        "356"    to Triple("Estação Expedicionários",        "Parque dos Pomares",                          true),
        "357"    to Triple("Rodoviária",                     "PUCC",                                        true),
        "358"    to Triple("Cidade Judiciária",              "Rec. dos Dourados",                           true),
        "359"    to Triple("Cidade Judiciária",              "Jardim Esmeraldina",                          true),
        "360"    to Triple("Castelo",                        "Jardim Proença",                              true),
        "361"    to Triple("Circular Centro",                "Jardim Chapadão",                             true),
        "362"    to Triple("Rodoviária",                     "Shopping Iguatemi",                           true),
        "364"    to Triple("Rodoviária",                     "Jardim São Fernando",                         true),
        "366"    to Triple("Rodoviária",                     "Vila Orosimbo Maia",                          true),
        "367"    to Triple("Rodoviária",                     "Jardim Baronesa",                             true),
        "368"    to Triple("Rodoviária",                     "Jardim Itatiaia",                             true),
        "369"    to Triple("Est. Expedicionários",           "Parque Imperador",                            true),
        "371"    to Triple("Shopping D. Pedro",              "Shopping Prado",                              true),
        "375"    to Triple("Est. Expedicionários",           "Alphaville D. Pedro",                         true),
        "377"    to Triple("Shopping D. Pedro",              "Vila Marieta",                                true),
        "380"    to Triple("Cambuí",                         "Terminal Central",                            true),
        "381"    to Triple("Shopping D. Pedro",              "Shopping Iguatemi",                           true),
        "383"    to Triple("Rodoviária",                     "Leroy Merlin",                                true),
        "383.1"  to Triple("Shopping Iguatemi",              "Leroy Merlin",                                true),
        "384"    to Triple("Entreverdes",                    "Shopping Iguatemi",                           true),
        "385"    to Triple("Rodoviária",                     "Shopping Iguatemi",                           true),
        "386"    to Triple("Cambuí",                         "Rodoviária",                                  true),
        "389"    to Triple("Circular Centro",                "Estação Sousas",                              true),
        "390"    to Triple("Rodoviária",                     "Joaquim Egídio",                              true),
        "391"    to Triple("Est. Expedicionários",           "Nova Sousas",                                 true),
        "392"    to Triple("Estação Expedicionários",        "San Conrado",                                 true),
        "393"    to Triple("Estação Sousas",                 "Cabras",                                      true),
        "394"    to Triple("Est. Sousas",                    "Parque Jatibaia",                             true),
        "394.1"  to Triple("Estação Sousas",                 "Parque Jatibaia",                             true),
        "395"    to Triple("Notre Dame",                     "Est. Expedicionários",                        true),
        "396"    to Triple("Rodoviária",                     "Sousas",                                      true),
        "397"    to Triple("Rodoviária",                     "Gramado",                                     true),
        "398"    to Triple("Rodoviária",                     "Joaquim Egídio",                              true),
        "399"    to Triple("Terminal Mercado I",             "Joaquim Egídio",                              true),
        "401"    to Triple("Terminal Central",               "Chácaras São Martinho",                       true),
        "402"    to Triple("Terminal Central",               "Parque da Figueira",                          true),
        "403"    to Triple("Rodoviária",                     "Jardim Nova Europa",                          true),
        "404"    to Triple("Terminal Central",               "Jardim Maria Rosa",                           true),
        "405"    to Triple("Terminal Central",               "Jardim Petrópolis",                           true),
        "406"    to Triple("Jardim Petrópolis",              "Jardim Petrópolis",                           false),
        "408"    to Triple("Rodoviária",                     "Parque Jambeiro",                             true),
        "409"    to Triple("Rodoviária",                     "Parque Jambeiro",                             true),
        "410"    to Triple("Corredor Central",               "Parque das Camélias",                         true),
        "411"    to Triple("Jardim do Lago II",              "Terminal Central",                            true),
        "412"    to Triple("Terminal Central",               "Parque Eldorado",                             true),
        "413"    to Triple("Corredor Central",               "Jardim São José",                             true),
        "414"    to Triple("Parque Oziel",                   "Terminal Central",                            true),
        "415"    to Triple("Gleba B",                        "Terminal Central",                            true),
        "416"    to Triple("Rodoviária",                     "Jardim do Lago",                              true),
        "422"    to Triple("Terminal Central",               "Jardim Nova América",                         true),
        "429"    to Triple("Terminal Central",               "Swiss Park II",                               true),
        "430"    to Triple("Terminal Central",               "Swiss Park",                                  true),
        "431"    to Triple("Ref. Agrária",                   "Terminal Central",                            true),
        "432"    to Triple("Saltinho",                       "Terminal Central",                            true),
        "433"    to Triple("Terminal Central",               "Morada das Bandeiras",                        true),
        "434"    to Triple("Terminal Central",               "Jardim Nsa. Sra. de Lourdes",                 true),
        "489"    to Triple("Terminal Mercado I",             "Parque Jambeiro",                             true),
        "499"    to Triple("Centro",                         "Iporanga",                                    true),
        "502"    to Triple("Circular Centro",                "Circular Centro",                             false)
    )

    /**
     * Busca por número da linha, ponto inicial ou final (case-insensitive).
     *
     * Devolve a [Route] inteira, e não só o número, porque a lista de
     * resultados mostra os pontos ao lado da linha — sem eles o usuário não
     * teria como escolher entre as quatro linhas que atendem "Vida Nova".
     *
     * Termo vazio devolve lista vazia: casar substring com "" traria o índice
     * inteiro de uma vez, o que não é resultado de busca nenhum.
     */
    fun search(termo: String): List<Route> {
        val t = semAcento(termo)
        if (t.isEmpty()) return emptyList()

        return linhasDisponiveis.entries
            .filter { (linha, info) ->
                linha.contains(t) ||
                        semAcento(info.first).contains(t) ||
                        semAcento(info.second).contains(t)
            }
            .sortedBy { it.key.toDouble() }
            .map { (linha, info) -> Route(linha, info.first, info.second, info.third) }
    }

    /**
     * Minúsculas e sem acento, dos dois lados da comparação.
     *
     * O índice está cheio de "União", "Itajaí" e "Judiciária", e ninguém digita
     * acento no teclado do celular para buscar um ônibus.
     */
    private fun semAcento(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

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
     *
     * A numeração segue a posição da parada ao longo do traçado, e não a ordem
     * em que ela aparece no arquivo — ver [OrdenadorDeParadas].
     */
    fun loadGeoJsonPontos(linha: String, sentido: Int): String? {
        val json = readAsset("routes/${linha}_sentido${sentido}_pontos.geojson") ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        val doSentido = filtrarPorSentido(root, sentido) ?: return null

        val paradas = doSentido.optJSONArray("features") ?: return null
        val ordem = runCatching {
            val rota = readAsset("routes/${linha}_sentido${sentido}_rota.geojson")
                ?.let { JSONObject(it) }
                ?.let { filtrarPorSentido(it, sentido) }
                ?.optJSONArray("features")
            rota?.let { OrdenadorDeParadas.ordem(paradas, it) }
        }.getOrNull()

        return runCatching { numerarPontos(doSentido, ordem).toString() }.getOrNull()
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
     * Numera as paradas de 1 a n.
     *
     * [ordem] traz os índices na sequência do percurso, calculada a partir do
     * traçado. Quando é null — traçado inutilizável ou linha que refaz o mesmo
     * corredor nos dois sentidos — cai para a ordem do próprio arquivo.
     */
    private fun numerarPontos(root: JSONObject, ordem: List<Int>?): JSONObject {
        val features = root.getJSONArray("features")
        val sequencia = ordem?.takeIf { it.size == features.length() }
            ?: List(features.length()) { it }

        sequencia.forEachIndexed { posicao, indice ->
            val feature = features.getJSONObject(indice)
            val props   = feature.optJSONObject("properties")
                ?: JSONObject().also { feature.put("properties", it) }

            // Gravado como texto: o text-field do MapLibre espera string
            props.put("ordem", (posicao + 1).toString())
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