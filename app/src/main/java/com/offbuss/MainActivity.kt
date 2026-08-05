package com.offbuss

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.offbus.R
import com.offbus.databinding.ActivityMainBinding
import com.offbus.databinding.ItemLinhaBinding
import com.offbuss.data.RouteRepository
import com.offbuss.model.Route
import com.offbuss.utils.MapStyleHelper
import com.offbuss.utils.TileServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.text.HtmlCompat
import androidx.core.widget.doAfterTextChanged
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "MainActivity"

        /** Raio da área de toque das paradas, em pixels de tela. */
        const val TOLERANCIA_TOQUE_PX = 24f

        /** Altura de cada item da lista de resultados — casada com item_linha.xml. */
        const val ALTURA_ITEM_DP = 56

        /**
         * Quantos resultados a lista mostra antes de precisar rolar.
         *
         * Sem esse teto uma busca ampla empilharia dezenas de linhas e
         * empurraria o mapa para fora da tela: a lista fica no mesmo
         * LinearLayout vertical.
         */
        const val MAX_SUGESTOES_VISIVEIS = 5

        /** Quantas buscas exibem a dica das paradas antes de ela parar de aparecer. */
        const val MAX_DICAS = 2

        /** Formulário de contribuição com a pesquisa do TCC. */
        const val FORMULARIO_TCC = "https://forms.gle/UqjAV5m4uQm2AYpx7"
    }

    /**
     * Azul escuro do traçado e das paradas — o mesmo `btn_outline` que
     * contorna o campo de busca, para o mapa falar a língua da interface.
     *
     * Lido de colors.xml em vez de repetir o hex aqui: mexer na paleta da UI
     * passa a arrastar o mapa junto.
     */
    private fun corDoTrajeto() = getColor(R.color.btn_outline)

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RouteRepository
    private var mapLibreMap: MapLibreMap? = null

    private var currentLinha   = ""
    private var currentSentido = 1
    private var mapReady       = false
    private var pendingRota:   String? = null
    private var pendingPontos: String? = null
    private var tileServer:    TileServer? = null

    /** Quantas vezes a dica das paradas já apareceu — o teto é [MAX_DICAS]. */
    private var dicasExibidas  = 0
    /** Marca que a próxima rota renderizada deve vir acompanhada da dica. */
    private var dicaPendente   = false

    // ─── onCreate ────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        binding    = ActivityMainBinding.inflate(layoutInflater)
        repository = RouteRepository(this)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        initMap(savedInstanceState)
        setupListeners()
    }

    /**
     * Convite para responder o formulário da pesquisa, atrás do botão "Avalie!".
     *
     * O link vem como HTML porque o MaterialAlertDialog não ativa o toque em
     * links sozinho: sem o LinkMovementMethod, a âncora fica só com cara de
     * link e não abre o navegador.
     */
    private fun mostrarConviteDoTcc() {
        val mensagem = HtmlCompat.fromHtml(
            "Lembre-se de avaliar este TCC!<br><br>" +
                    "<a href=\"$FORMULARIO_TCC\">$FORMULARIO_TCC</a>",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        val dialog = MaterialAlertDialogBuilder(this)
            .setMessage(mensagem)
            .setPositiveButton("Fechar", null)
            .show()

        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
            LinkMovementMethod.getInstance()
    }

    /**
     * Sobe o mapa offline.
     *
     * Não há mais cópia de arquivo aqui: o [TileServer] lê os tiles por offset
     * direto do APK, então o que resta é abrir um descritor e o índice — alguns
     * milissegundos, contra as dezenas de segundos que os 272 MiB do
     * `map.mbtiles` levavam para chegar ao `filesDir` na primeira execução.
     */
    private fun initMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { map ->
            mapLibreMap = map
            configurarCliqueNasParadas(map)
            lifecycleScope.launch(Dispatchers.IO) {
                // Devolve os ~272 MiB que as versões antigas deixaram no filesDir
                MapStyleHelper.limparCopiaAntiga(this@MainActivity)

                val preparo = runCatching {
                    val estilo = MapStyleHelper.prepareOfflineStyle(this@MainActivity)
                    val server = TileServer(this@MainActivity)
                    server.openAndStart()
                    tileServer = server
                    estilo
                }

                withContext(Dispatchers.Main) {
                    val styleJson = preparo.getOrElse { erro ->
                        // Sem os assets do mapa não há o que renderizar; avisar é
                        // melhor que deixar a tela em branco para sempre
                        Log.e(TAG, "Falha ao preparar o mapa offline", erro)
                        esconderCarregando()
                        Toast.makeText(
                            this@MainActivity,
                            "Não consegui abrir o mapa offline",
                            Toast.LENGTH_LONG
                        ).show()
                        return@withContext
                    }

                    map.setStyle(Style.Builder().fromJson(styleJson)) {
                        mapReady = true
                        val rota   = pendingRota
                        val pontos = pendingPontos
                        if (rota != null) {
                            pendingRota   = null
                            pendingPontos = null
                            renderizarRota(rota, pontos)
                        } else {
                            esconderCarregando()
                        }
                    }
                }
            }
        }
    }

    private fun esconderCarregando() {
        binding.pbMapa.visibility = View.GONE
    }

    // ─── Toque nas paradas ───────────────────────────────────────────
    private fun configurarCliqueNasParadas(map: MapLibreMap) {
        map.addOnMapClickListener { latLng ->
            val tela = map.projection.toScreenLocation(latLng)
            // Área de toque maior que a bolinha, para não exigir precisão do dedo
            val alvo = RectF(
                tela.x - TOLERANCIA_TOQUE_PX, tela.y - TOLERANCIA_TOQUE_PX,
                tela.x + TOLERANCIA_TOQUE_PX, tela.y + TOLERANCIA_TOQUE_PX
            )
            // A camada só existe depois que uma linha é buscada
            val paradas = runCatching { map.queryRenderedFeatures(alvo, "stops-layer") }
                .getOrDefault(emptyList())

            val parada = paradas.firstOrNull()
            if (parada != null) {
                mostrarInfoParada(parada)
                true
            } else {
                false
            }
        }
    }

    /**
     * Lê uma propriedade como texto.
     *
     * O GeoJSON da EMDEC traz campos presentes porém nulos — 218 paradas têm
     * "linhas": null. Nesses casos getStringProperty chama getAsString() sobre
     * um JsonNull e lança UnsupportedOperationException, então o valor precisa
     * ser checado antes de converter.
     */
    private fun Feature.textoOuNulo(nome: String): String? =
        runCatching { getProperty(nome)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()

    private fun Feature.inteiroOuNulo(nome: String): Int? =
        runCatching { getProperty(nome)?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()

    private fun mostrarInfoParada(parada: Feature) {
        val numero = parada.textoOuNulo("ordem")

        val sentido = when (parada.inteiroOuNulo("sentido")) {
            1    -> "Ida"
            2    -> "Volta"
            else -> null
        }

        val texto = buildString {
            append("Sentido: ").append(sentido ?: "não informado")
            append("\n\nLinhas que atendem esta parada:\n")
            append(formatarLinhas(parada.textoOuNulo("linhas")))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (numero != null) "Parada $numero" else "Parada")
            .setMessage(texto)
            .setPositiveButton("Fechar", null)
            .show()
    }

    /** "116,119 [15/04/2026]" → "116, 119" mais a data de atualização. */
    private fun formatarLinhas(bruto: String?): String {
        if (bruto.isNullOrBlank()) return "não informado"

        val numeros = bruto.substringBefore("[")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (numeros.isEmpty()) return bruto

        val data = bruto.substringAfter("[", "").substringBefore("]").trim()
        val lista = numeros.joinToString(", ")
        return if (data.isEmpty()) lista else "$lista\n\nDados de $data"
    }

    // ─── Listeners ───────────────────────────────────────────────────
    private fun setupListeners() {
        // A lista de resultados acompanha o que está sendo digitado
        binding.etSearch.doAfterTextChanged { texto ->
            atualizarSugestoes(texto?.toString().orEmpty())
        }

        binding.btnSearch.setOnClickListener {
            val termo = binding.etSearch.text.toString().trim()
            if (termo.isEmpty()) {
                Toast.makeText(this, "Digite o número ou ponto da linha",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            buscarLinha(termo)
        }

        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            binding.btnSearch.performClick(); true
        }

        binding.btnSentido1.setOnClickListener {
            if (currentSentido != 1) { currentSentido = 1; trocarSentido() }
        }
        binding.btnSentido2.setOnClickListener {
            if (currentSentido != 2) { currentSentido = 2; trocarSentido() }
        }
        binding.btnItinerarios.setOnClickListener { mostrarHorarios() }
        binding.fabAvaliar.setOnClickListener { mostrarConviteDoTcc() }
    }

    /**
     * Fecha o teclado e tira o foco do campo de busca.
     *
     * O manifesto usa adjustResize, então recolher o teclado devolve a altura
     * ao mapa. O clearFocus evita o cursor piscando sobre um campo já usado.
     */
    private fun esconderTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.etSearch.clearFocus()
    }

    // ─── Busca ───────────────────────────────────────────────────────
    /**
     * Reflete o termo digitado na lista de resultados.
     *
     * Não abre linha nenhuma sozinha, nem quando sobra um resultado só: a
     * escolha é sempre um toque do usuário, senão digitar "116" carregaria o
     * mapa antes de ele terminar de escrever "1163".
     */
    private fun atualizarSugestoes(termo: String) {
        val resultados = repository.search(termo)
        if (resultados.isEmpty()) esconderSugestoes() else mostrarSugestoes(resultados)
    }

    private fun mostrarSugestoes(resultados: List<Route>) {
        val container = binding.containerSugestoes
        container.removeAllViews()

        resultados.forEach { rota ->
            val item = ItemLinhaBinding.inflate(layoutInflater, container, false)
            item.tvNumeroLinha.text = rota.linha
            item.tvPontosLinha.text = descricaoDosPontos(rota)
            item.root.setOnClickListener { exibirResultado(rota.linha) }
            container.addView(item.root)
        }

        // Teto de altura: o resto do que foi encontrado vem pela rolagem
        val visiveis = minOf(resultados.size, MAX_SUGESTOES_VISIVEIS)
        binding.scrollSugestoes.apply {
            layoutParams = layoutParams.also { it.height = dp(ALTURA_ITEM_DP) * visiveis }
            scrollTo(0, 0)
            visibility = View.VISIBLE
        }
    }

    private fun esconderSugestoes() {
        binding.scrollSugestoes.visibility = View.GONE
        binding.containerSugestoes.removeAllViews()
    }

    /**
     * "Terminal Vida Nova / Campinas Shopping".
     *
     * Quando os dois extremos coincidem — o caso das circulares, como a 133 —
     * repetir o nome só polui, então mostra apenas uma vez.
     */
    private fun descricaoDosPontos(rota: Route): String =
        if (rota.pontoInicial.equals(rota.pontoFinal, ignoreCase = true)) rota.pontoInicial
        else "${rota.pontoInicial} / ${rota.pontoFinal}"

    private fun buscarLinha(termo: String) {
        val resultados = repository.search(termo)
        if (resultados.isEmpty()) {
            Toast.makeText(this, "Linha \"$termo\" não encontrada", Toast.LENGTH_LONG).show()
            return
        }
        // A lista já está na tela; a lupa só tira o teclado da frente dela
        esconderTeclado()
        mostrarSugestoes(resultados)
    }

    private fun exibirResultado(linha: String) {
        currentLinha   = linha
        currentSentido = 1
        // Agendada aqui e mostrada só quando a rota estiver desenhada
        if (dicasExibidas < MAX_DICAS) dicaPendente = true

        // Busca sem resultado é o único caso que mantém o teclado aberto,
        // para o usuário corrigir o termo sem um toque a mais
        esconderTeclado()
        // A lista cumpriu seu papel — o espaço volta para o mapa
        esconderSugestoes()

        binding.layoutSentido.visibility  = View.VISIBLE
        binding.btnSentido2.visibility    =
            if (repository.temSentido2(linha)) View.VISIBLE else View.GONE
        binding.btnItinerarios.visibility = View.VISIBLE
        binding.mapView.visibility        = View.VISIBLE
        // A dica cede o espaço para o mapa na primeira busca
        binding.tvDicaBusca.visibility    = View.GONE
        // O estilo ainda pode estar carregando: sem isso a rota fica em
        // pendingRota e o usuário encara um retângulo vazio sem explicação
        if (!mapReady) binding.pbMapa.visibility = View.VISIBLE

        atualizarBotoesSentido()
        carregarSentido()
    }

    // ─── Sentido ─────────────────────────────────────────────────────
    private fun trocarSentido() {
        atualizarBotoesSentido()
        carregarSentido()
    }

    private fun atualizarBotoesSentido() {
        binding.btnSentido1.isEnabled = currentSentido != 1
        binding.btnSentido2.isEnabled = currentSentido != 2
    }

    private fun carregarSentido() {
        val rota   = repository.loadGeoJsonRota(currentLinha, currentSentido)
        val pontos = repository.loadGeoJsonPontos(currentLinha, currentSentido)
        if (rota != null) {
            renderizarRota(rota, pontos)
        } else {
            Toast.makeText(this, "Dados do sentido $currentSentido não disponíveis",
                Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Mapa ────────────────────────────────────────────────────────
    private fun renderizarRota(geoJsonRota: String, geoJsonPontos: String?) {
        if (!mapReady) {
            pendingRota   = geoJsonRota
            pendingPontos = geoJsonPontos
            return
        }
        mapLibreMap?.getStyle { style ->
            // Rota — atualiza source existente ou cria na primeira vez
            val rotaSource = style.getSource("route-source") as? GeoJsonSource
            if (rotaSource != null) {
                rotaSource.setGeoJson(geoJsonRota)
            } else {
                style.addSource(GeoJsonSource("route-source", geoJsonRota))
                style.addLayer(
                    LineLayer("route-layer", "route-source").withProperties(
                        PropertyFactory.lineColor(corDoTrajeto()),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round")
                    )
                )
            }

            // Paradas — usa GeoJSON vazio quando não há paradas no sentido
            val stopsJson = geoJsonPontos ?: """{"type":"FeatureCollection","features":[]}"""
            val stopsSource = style.getSource("stops-source") as? GeoJsonSource
            if (stopsSource != null) {
                stopsSource.setGeoJson(stopsJson)
            } else {
                style.addSource(GeoJsonSource("stops-source", stopsJson))

                // A bolinha cresce com o zoom para o número caber e ficar legível
                style.addLayer(
                    CircleLayer("stops-layer", "stops-source").withProperties(
                        PropertyFactory.circleColor(corDoTrajeto()),
                        PropertyFactory.circleRadius(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(8, 3f),
                                Expression.stop(11, 5f),
                                Expression.stop(13, 8f),
                                Expression.stop(15, 13f),
                                Expression.stop(17, 18f)
                            )
                        ),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(11, 1.5f),
                                Expression.stop(13, 2f),
                                Expression.stop(15, 3f)
                            )
                        )
                    )
                )

                // Número da parada na ordem do trajeto — a partir do z14,
                // quando a bolinha já é grande o bastante para comportá-lo
                style.addLayer(
                    SymbolLayer("stops-number-layer", "stops-source").withProperties(
                        PropertyFactory.textField(Expression.get("ordem")),
                        // Único fontstack embarcado em assets/fonts/
                        PropertyFactory.textFont(arrayOf("noto-sans")),
                        PropertyFactory.textSize(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(13, 9f),
                                Expression.stop(15, 12f),
                                Expression.stop(17, 15f)
                            )
                        ),
                        PropertyFactory.textColor("#FFFFFF"),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true)
                    ).apply { minZoom = 13f }
                )
            }

            esconderCarregando()
            enquadrarMapa(geoJsonRota)
            mostrarDicaDoMapa()
        }
    }

    /**
     * Dica sobre o toque nas paradas, nas duas primeiras linhas escolhidas.
     *
     * Quem dispara é a escolha na lista de resultados, não a renderização em
     * si: alternar entre ida e volta também passa por aqui e gastaria as duas
     * exibições sem o usuário ter buscado uma segunda linha.
     *
     * Só faz sentido depois que há um trajeto na tela: antes da busca o mapa
     * fica oculto e não há parada nenhuma para tocar.
     */
    private fun mostrarDicaDoMapa() {
        if (!dicaPendente) return
        dicaPendente = false
        dicasExibidas++

        MaterialAlertDialogBuilder(this)
            .setMessage("Dica: clique em um dos pontos azuis no mapa para obter informações detalhadas!")
            .setPositiveButton("Entendi", null)
            .show()
    }

    private fun enquadrarMapa(geojson: String) {
        try {
            val json     = JSONObject(geojson)
            val features = json.getJSONArray("features")
            if (features.length() == 0) return

            val bounds = LatLngBounds.Builder()
            var count  = 0

            // Percorre todas as features: um sentido pode vir quebrado em trechos
            for (f in 0 until features.length()) {
                val geometry = features.getJSONObject(f).optJSONObject("geometry") ?: continue

                when (geometry.getString("type")) {
                    "LineString" -> {
                        val coords = geometry.getJSONArray("coordinates")
                        for (i in 0 until coords.length()) {
                            val c = coords.getJSONArray(i)
                            bounds.include(LatLng(c.getDouble(1), c.getDouble(0)))
                            count++
                        }
                    }
                    "MultiLineString" -> {
                        val lines = geometry.getJSONArray("coordinates")
                        for (i in 0 until lines.length()) {
                            val line = lines.getJSONArray(i)
                            for (j in 0 until line.length()) {
                                val c = line.getJSONArray(j)
                                bounds.include(LatLng(c.getDouble(1), c.getDouble(0)))
                                count++
                            }
                        }
                    }
                }
            }

            if (count >= 2) {
                binding.mapView.post {
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── Dialog de horários ──────────────────────────────────────────
    private fun mostrarHorarios() {
        val horarios = repository.loadHorarios(currentLinha, currentSentido)
        val (pontoInicial, pontoFinal) = repository.getPontos(currentLinha)

        val dialogView = layoutInflater.inflate(R.layout.dialog_horarios, null)
        val tvTitulo   = dialogView.findViewById<TextView>(R.id.tvLinhaTitulo)
        val tvPonto    = dialogView.findViewById<TextView>(R.id.tvPontoPartida)
        val glHorarios = dialogView.findViewById<GridLayout>(R.id.glHorarios)

        val label = if (currentSentido == 1) "Ida" else "Volta"
        tvTitulo.text = "Linha $currentLinha — $label"

        if (horarios != null) {
            tvPonto.text = "Partida: ${horarios.pontoPartida}"
            horarios.horarios.forEach { glHorarios.addView(cartaoDeHorario(it), celula()) }
        } else {
            tvPonto.text = "$pontoInicial → $pontoFinal"
            val aviso = TextView(this).apply {
                text     = "Horários não cadastrados"
                textSize = 14f
                setTextColor(getColor(com.offbus.R.color.text_secondary))
            }
            // Ocupa a largura inteira em vez de espremer numa das três colunas
            glHorarios.addView(aviso, celula(colunas = 3))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Horários de Partida")
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun cartaoDeHorario(hora: String) = TextView(this).apply {
        text     = hora
        textSize = 14f
        gravity  = Gravity.CENTER
        setTextColor(getColor(com.offbus.R.color.text_primary))
        setBackgroundResource(R.drawable.bg_horario)
        setPadding(0, dp(10), 0, dp(10))
    }

    /** Célula de largura igual na grade, com respiro entre os cartões. */
    private fun celula(colunas: Int = 1) = GridLayout.LayoutParams().apply {
        width       = 0
        height      = GridLayout.LayoutParams.WRAP_CONTENT
        columnSpec  = GridLayout.spec(GridLayout.UNDEFINED, colunas, 1f)
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    // ─── Ciclo de vida do MapView (obrigatório) ──────────────────────
    override fun onStart()    { super.onStart();    binding.mapView.onStart()   }
    override fun onResume()   { super.onResume();   binding.mapView.onResume()  }
    override fun onPause()    { super.onPause();    binding.mapView.onPause()   }
    override fun onStop()     { super.onStop();     binding.mapView.onStop()    }
    override fun onDestroy()  { super.onDestroy();  binding.mapView.onDestroy(); tileServer?.stopServer() }
    override fun onLowMemory(){ super.onLowMemory();binding.mapView.onLowMemory()}
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        binding.mapView.onSaveInstanceState(out)
    }
}
