package com.offbuss

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.offbus.R
import com.offbus.databinding.ActivityMainBinding
import com.offbuss.data.RouteRepository
import com.offbuss.utils.MapStyleHelper
import com.offbuss.utils.TileServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RouteRepository
    private var mapLibreMap: MapLibreMap? = null

    private var currentLinha   = ""
    private var currentSentido = 1
    private var mapReady       = false
    private var pendingRota:   String? = null
    private var pendingPontos: String? = null
    private var tileServer:    TileServer? = null

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

    private fun initMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { map ->
            mapLibreMap = map
            lifecycleScope.launch(Dispatchers.IO) {
                // 1. Copia mbtiles se necessário e lê o estilo
                val styleJson = MapStyleHelper.prepareOfflineStyle(this@MainActivity)
                // 2. Abre o banco e inicia o servidor HTTP local
                val server = TileServer(MapStyleHelper.getMbtilesFile(this@MainActivity), this@MainActivity)
                server.openAndStart()
                tileServer = server
                withContext(Dispatchers.Main) {
                    map.setStyle(Style.Builder().fromJson(styleJson)) {
                        mapReady = true
                        val rota   = pendingRota
                        val pontos = pendingPontos
                        if (rota != null) {
                            pendingRota   = null
                            pendingPontos = null
                            renderizarRota(rota, pontos)
                        }
                    }
                }
            }
        }
    }

    // ─── Listeners ───────────────────────────────────────────────────
    private fun setupListeners() {
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
    }

    // ─── Busca ───────────────────────────────────────────────────────
    private fun buscarLinha(termo: String) {
        val resultados = repository.search(termo)
        when {
            resultados.isEmpty() -> Toast.makeText(
                this, "Linha \"$termo\" não encontrada", Toast.LENGTH_LONG).show()

            resultados.size == 1 -> exibirResultado(resultados.first())

            else -> AlertDialog.Builder(this)
                .setTitle("Selecione a linha")
                .setItems(resultados.toTypedArray()) { _, i -> exibirResultado(resultados[i]) }
                .show()
        }
    }

    private fun exibirResultado(linha: String) {
        currentLinha   = linha
        currentSentido = 1

        binding.layoutSentido.visibility  = View.VISIBLE
        binding.btnSentido2.visibility    =
            if (repository.temSentido2(linha)) View.VISIBLE else View.GONE
        binding.btnItinerarios.visibility = View.VISIBLE
        binding.mapView.visibility        = View.VISIBLE

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
                        PropertyFactory.lineColor("#2196F3"),
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
                        PropertyFactory.circleColor("#2196F3"),
                        PropertyFactory.circleRadius(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(10, 3f),
                                Expression.stop(13, 5f),
                                Expression.stop(15, 9f),
                                Expression.stop(17, 14f),
                                Expression.stop(19, 18f)
                            )
                        ),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(13, 1.5f),
                                Expression.stop(15, 2f),
                                Expression.stop(17, 3f)
                            )
                        )
                    )
                )

                // Número da parada na ordem do trajeto — só a partir do z15,
                // quando a bolinha já é grande o bastante para comportá-lo
                style.addLayer(
                    SymbolLayer("stops-number-layer", "stops-source").withProperties(
                        PropertyFactory.textField(Expression.get("ordem")),
                        // Único fontstack embarcado em assets/fonts/
                        PropertyFactory.textFont(arrayOf("noto-sans")),
                        PropertyFactory.textSize(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(15, 9f),
                                Expression.stop(17, 13f),
                                Expression.stop(19, 16f)
                            )
                        ),
                        PropertyFactory.textColor("#FFFFFF"),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true)
                    ).apply { minZoom = 15f }
                )
            }

            enquadrarMapa(geoJsonRota)
        }
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
        val llHorarios = dialogView.findViewById<LinearLayout>(R.id.llHorarios)

        val label = if (currentSentido == 1) "Ida" else "Volta"
        tvTitulo.text = "Linha $currentLinha — $label"

        val corPrimaria  = getColor(com.offbus.R.color.text_primary)
        val corSecundaria = getColor(com.offbus.R.color.text_secondary)

        if (horarios != null) {
            tvPonto.text = "Partida: ${horarios.pontoPartida}"
            horarios.horarios.forEach { h ->
                val tv = TextView(this).apply {
                    text     = h
                    textSize = 15f
                    setTextColor(corPrimaria)
                    setPadding(0, 10, 0, 10)
                }
                llHorarios.addView(tv)
            }
        } else {
            tvPonto.text = "$pontoInicial → $pontoFinal"
            llHorarios.addView(TextView(this).apply {
                text     = "Horários não cadastrados"
                textSize = 14f
                setTextColor(corSecundaria)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Horários de Partida")
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

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
