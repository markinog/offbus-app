# CONTEXT.md — BusApp
> Coloque este arquivo na raiz do projeto Android Studio.
> Use com o Claude Code: `claude` → "leia o CONTEXT.md e me ajude com X"

---

## O que é o projeto

**BusApp** é um aplicativo Android nativo desenvolvido em Kotlin para consulta
offline de linhas de ônibus municipais de Campinas. Todos os dados (rotas,
paradas e horários) ficam embutidos no APK em `assets/`. Nenhuma chamada de
rede é feita em tempo de execução — o app funciona 100% sem internet.

É um MVP desenvolvido como Trabalho de Conclusão de Curso (TCC) no IFSP
Campus Campinas — Curso de Análise e Desenvolvimento de Sistemas.

---

## Stack e versões

| Componente | Versão |
|---|---|
| Language | Kotlin |
| Android Studio | Hedgehog+ |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| AGP | 9.0.1 |
| MapLibre Native Android | 11.8.4 |
| Kotlinx Coroutines | 1.9.0 |
| Lifecycle Runtime KTX | 2.8.7 |
| Gson | 2.11.0 |
| Material | 1.14.0 |
| Tiles de mapa | `.mbtiles` formato **Shortbread** (BBBike) |
| Dados de rota | GeoJSON coletados via `emdec_scraper.py` |

---

## Linhas implementadas

| Linha | Ponto Inicial | Ponto Final | Sentidos |
|---|---|---|---|
| 116 | Terminal Ouro Verde | Shopping Dom Pedro | 1 e 2 |
| 117 | Terminal Ouro Verde | Jardim Itatinga | 1 e 2 |
| 138 | Terminal Ouro Verde | Recanto do Sol I e II | 1 e 2 |

---

## Estrutura de arquivos do projeto

```
BusApp/
├── CONTEXT.md                          ← este arquivo
├── gradle/
│   └── libs.versions.toml              ← versões centralizadas
├── build.gradle.kts                    ← raiz (só alias, apply false)
└── app/
    ├── build.gradle.kts                ← dependências do app
    └── src/main/
        ├── AndroidManifest.xml         ← sem permissão de internet
        ├── java/com/busapp/
        │   ├── MainActivity.kt         ← tela principal
        │   ├── model/
        │   │   └── Route.kt            ← data classes Route e Horarios
        │   ├── data/
        │   │   └── RouteRepository.kt  ← lê assets, busca por linha
        │   └── utils/
        │       └── MapStyleHelper.kt   ← prepara estilo offline do MapLibre
        ├── res/
        │   ├── layout/
        │   │   ├── activity_main.xml   ← tela principal
        │   │   └── dialog_horarios.xml ← popup de horários
        │   ├── drawable/
        │   │   └── bg_search.xml       ← background do campo de busca
        │   └── values/
        │       └── strings.xml         ← app_name = "BusApp"
        └── assets/
            ├── map.mbtiles             ← tiles OSM offline (BBBike shortbread)
            ├── map-style.json          ← estilo vetorial shortbread
            ├── routes/
            │   ├── 116_sentido1_rota.geojson
            │   ├── 116_sentido1_pontos.geojson
            │   ├── 116_sentido2_rota.geojson
            │   ├── 116_sentido2_pontos.geojson
            │   ├── 117_sentido1_rota.geojson
            │   ├── 117_sentido1_pontos.geojson
            │   ├── 117_sentido2_rota.geojson
            │   ├── 117_sentido2_pontos.geojson
            │   ├── 138_sentido1_rota.geojson
            │   ├── 138_sentido1_pontos.geojson
            │   ├── 138_sentido2_rota.geojson
            │   └── 138_sentido2_pontos.geojson
            └── horarios/
                ├── 116_sentido1.json
                ├── 116_sentido2.json
                ├── 117_sentido1.json
                ├── 117_sentido2.json
                ├── 138_sentido1.json
                └── 138_sentido2.json
```

---

## Configuração de build

### gradle/libs.versions.toml

```toml
[versions]
agp              = "9.0.1"
kotlin           = "2.0.21"
coreKtx          = "1.18.0"
junit            = "4.13.2"
junitVersion     = "1.3.0"
espressoCore     = "3.7.0"
appcompat        = "1.7.1"
material         = "1.14.0"
activity         = "1.13.0"
constraintlayout = "2.2.1"
maplibre         = "11.8.4"
coroutines       = "1.9.0"
lifecycle        = "2.8.7"
gson             = "2.11.0"

[libraries]
androidx-core-ktx          = { group = "androidx.core",               name = "core-ktx",                   version.ref = "coreKtx"          }
junit                      = { group = "junit",                       name = "junit",                      version.ref = "junit"            }
androidx-junit             = { group = "androidx.test.ext",           name = "junit",                      version.ref = "junitVersion"     }
androidx-espresso-core     = { group = "androidx.test.espresso",      name = "espresso-core",              version.ref = "espressoCore"     }
androidx-appcompat         = { group = "androidx.appcompat",          name = "appcompat",                  version.ref = "appcompat"        }
material                   = { group = "com.google.android.material", name = "material",                   version.ref = "material"         }
androidx-activity          = { group = "androidx.activity",           name = "activity",                   version.ref = "activity"         }
androidx-constraintlayout  = { group = "androidx.constraintlayout",   name = "constraintlayout",           version.ref = "constraintlayout" }
maplibre-android           = { group = "org.maplibre.gl",             name = "android-sdk",                version.ref = "maplibre"         }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx",       name = "kotlinx-coroutines-android", version.ref = "coroutines"       }
androidx-lifecycle-runtime = { group = "androidx.lifecycle",          name = "lifecycle-runtime-ktx",      version.ref = "lifecycle"        }
gson                       = { group = "com.google.code.gson",        name = "gson",                       version.ref = "gson"             }

[plugins]
android-application = { id = "com.android.application",      version.ref = "agp"    }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### build.gradle.kts (raiz)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
}
```

### app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace  = "com.busapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.busapp"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("mbtiles", "geojson")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.maplibre.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.gson)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

> ⚠️ AGP 9.0.1 embute o plugin Kotlin — não declarar `kotlin.android` no
> bloco `plugins` do `app/build.gradle.kts`. O `kotlin { jvmToolchain(17) }`
> substitui o depreciado `kotlinOptions { jvmTarget = "17" }`.

---

## AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="BusApp"
        android:theme="@style/Theme.Material3.DayNight"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## Código Kotlin completo

### model/Route.kt

```kotlin
package com.busapp.model

data class Route(
    val linha: String,
    val pontoInicial: String,
    val pontoFinal: String,
    val temSentido2: Boolean = false
)

data class Horarios(
    val linha: String,
    val sentido: Int,
    val pontoPartida: String,
    val horarios: List<String>
)
```

---

### data/RouteRepository.kt

```kotlin
package com.busapp.data

import android.content.Context
import com.busapp.model.Horarios
import com.google.gson.Gson

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    private val linhasDisponiveis = mapOf(
        "116" to Triple("Terminal Ouro Verde", "Shopping Dom Pedro",    true),
        "117" to Triple("Terminal Ouro Verde", "Jardim Itatinga",       true),
        "138" to Triple("Terminal Ouro Verde", "Recanto do Sol I e II", true)
    )

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
```

---

### utils/MapStyleHelper.kt

```kotlin
package com.busapp.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

object MapStyleHelper {

    private const val TAG = "MapStyleHelper"

    fun prepareOfflineStyle(context: Context): String {
        val mbtilesFile = copyMbtilesToFilesDir(context)
        return buildStyleJson(context, mbtilesFile.absolutePath)
    }

    private fun copyMbtilesToFilesDir(context: Context): File {
        val dest = File(context.filesDir, "map.mbtiles")
        if (!dest.exists()) {
            Log.d(TAG, "Copiando map.mbtiles para filesDir...")
            context.assets.open("map.mbtiles").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Concluído: ${dest.absolutePath}")
        }
        return dest
    }

    private fun buildStyleJson(context: Context, mbtilePath: String): String {
        val template = context.assets.open("map-style.json")
            .bufferedReader().use { it.readText() }
        val json = JSONObject(template)
        json.getJSONObject("sources")
            .getJSONObject("mbtiles-source")
            .put("url", "mbtiles://$mbtilePath")
        return json.toString()
    }
}
```

---

### MainActivity.kt

```kotlin
package com.busapp

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.busapp.data.RouteRepository
import com.busapp.databinding.ActivityMainBinding
import com.busapp.utils.MapStyleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RouteRepository
    private var mapLibreMap: MapLibreMap? = null

    private var currentLinha   = ""
    private var currentSentido = 1
    private var mapReady       = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        binding    = ActivityMainBinding.inflate(layoutInflater)
        repository = RouteRepository(this)
        setContentView(binding.root)
        initMap(savedInstanceState)
        setupListeners()
    }

    private fun initMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { map ->
            mapLibreMap = map
            lifecycleScope.launch(Dispatchers.IO) {
                val styleJson = MapStyleHelper.prepareOfflineStyle(this@MainActivity)
                withContext(Dispatchers.Main) {
                    map.setStyle(Style.Builder().fromJson(styleJson)) {
                        mapReady = true
                    }
                }
            }
        }
    }

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

    private fun renderizarRota(geoJsonRota: String, geoJsonPontos: String?) {
        if (!mapReady) {
            Toast.makeText(this, "Mapa ainda carregando...", Toast.LENGTH_SHORT).show()
            return
        }
        mapLibreMap?.getStyle { style ->
            limparCamadas(style)
            style.addSource(GeoJsonSource("route-source", geoJsonRota))
            style.addLayer(
                LineLayer("route-layer", "route-source").withProperties(
                    PropertyFactory.lineColor("#E53935"),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round")
                )
            )
            if (geoJsonPontos != null) {
                style.addSource(GeoJsonSource("stops-source", geoJsonPontos))
                style.addLayer(
                    CircleLayer("stops-layer", "stops-source").withProperties(
                        PropertyFactory.circleColor("#1565C0"),
                        PropertyFactory.circleRadius(5f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
                )
            }
            enquadrarMapa(geoJsonRota)
        }
    }

    private fun limparCamadas(style: Style) {
        listOf("route-layer", "stops-layer").forEach { runCatching { style.removeLayer(it) } }
        listOf("route-source", "stops-source").forEach { runCatching { style.removeSource(it) } }
    }

    private fun enquadrarMapa(geojson: String) {
        try {
            val json     = JSONObject(geojson)
            val features = json.getJSONArray("features")
            if (features.length() == 0) return
            val geometry = features.getJSONObject(0).getJSONObject("geometry")
            val tipo     = geometry.getString("type")
            val bounds   = LatLngBounds.Builder()
            var count    = 0

            when (tipo) {
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
            if (count >= 2) {
                mapLibreMap?.post {
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mostrarHorarios() {
        val horarios = repository.loadHorarios(currentLinha, currentSentido)
        val (pontoInicial, pontoFinal) = repository.getPontos(currentLinha)
        val dialogView = layoutInflater.inflate(R.layout.dialog_horarios, null)
        val tvTitulo   = dialogView.findViewById<TextView>(R.id.tvLinhaTitulo)
        val tvPonto    = dialogView.findViewById<TextView>(R.id.tvPontoPartida)
        val llHorarios = dialogView.findViewById<LinearLayout>(R.id.llHorarios)
        val label = if (currentSentido == 1) "Ida" else "Volta"
        tvTitulo.text = "Linha $currentLinha — $label"
        if (horarios != null) {
            tvPonto.text = "Partida: ${horarios.pontoPartida}"
            horarios.horarios.forEach { h ->
                val tv = TextView(this).apply { text = h; textSize = 16f; setPadding(0, 8, 0, 8) }
                llHorarios.addView(tv)
            }
        } else {
            tvPonto.text = "$pontoInicial → $pontoFinal"
            llHorarios.addView(TextView(this).apply {
                text = "Horários não cadastrados"; textSize = 14f
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Horários de Partida")
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    override fun onStart()     { super.onStart();     binding.mapView.onStart()    }
    override fun onResume()    { super.onResume();    binding.mapView.onResume()   }
    override fun onPause()     { super.onPause();     binding.mapView.onPause()    }
    override fun onStop()      { super.onStop();      binding.mapView.onStop()     }
    override fun onDestroy()   { super.onDestroy();   binding.mapView.onDestroy()  }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory()}
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        binding.mapView.onSaveInstanceState(out)
    }
}
```

---

## Layouts XML

### res/layout/activity_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#ffffff">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:background="#1a1a2e"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:text="BusApp"
            android:textColor="#ffffff"
            android:textSize="18sp"
            android:textStyle="bold"/>
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Campinas · offline"
            android:textColor="#aaaaaa"
            android:textSize="12sp"/>
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="12dp"
        android:background="#ffffff">
        <EditText
            android:id="@+id/etSearch"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="48dp"
            android:hint="Número da linha, ponto inicial ou final"
            android:inputType="text"
            android:maxLines="1"
            android:paddingStart="12dp"
            android:paddingEnd="12dp"
            android:background="@drawable/bg_search"/>
        <ImageButton
            android:id="@+id/btnSearch"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginStart="8dp"
            android:src="@android:drawable/ic_menu_search"
            android:background="#e53935"
            android:contentDescription="Buscar"/>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/layoutSentido"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:paddingBottom="8dp"
        android:visibility="gone"
        android:background="#ffffff">
        <Button
            android:id="@+id/btnSentido1"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_marginEnd="4dp"
            android:text="⬆ Sentido 1 (Ida)"/>
        <Button
            android:id="@+id/btnSentido2"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_marginStart="4dp"
            android:text="⬇ Sentido 2 (Volta)"/>
    </LinearLayout>

    <Button
        android:id="@+id/btnItinerarios"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="12dp"
        android:layout_marginBottom="8dp"
        android:backgroundTint="#1a1a2e"
        android:text="Itinerários"
        android:visibility="gone"/>

    <org.maplibre.android.maps.MapView
        android:id="@+id/mapView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:visibility="gone"/>

</LinearLayout>
```

### res/layout/dialog_horarios.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">
    <TextView
        android:id="@+id/tvLinhaTitulo"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textStyle="bold"
        android:textColor="#1a1a2e"
        android:layout_marginBottom="4dp"/>
    <TextView
        android:id="@+id/tvPontoPartida"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="13sp"
        android:textColor="#666666"
        android:layout_marginBottom="12dp"/>
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content">
        <LinearLayout
            android:id="@+id/llHorarios"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"/>
    </ScrollView>
</LinearLayout>
```

### res/drawable/bg_search.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#f0f0eb"/>
    <corners android:radius="10dp"/>
</shape>
```

---

## assets/map-style.json

```json
{
  "version": 8,
  "name": "BusApp Offline",
  "sources": {
    "mbtiles-source": {
      "type": "vector",
      "url": "mbtiles:///PLACEHOLDER"
    }
  },
  "layers": [
    { "id": "background", "type": "background",
      "paint": { "background-color": "#f2efe9" } },
    { "id": "earth", "type": "fill",
      "source": "mbtiles-source", "source-layer": "earth",
      "paint": { "fill-color": "#f2efe9" } },
    { "id": "water", "type": "fill",
      "source": "mbtiles-source", "source-layer": "water_polygons",
      "paint": { "fill-color": "#a8d4e6" } },
    { "id": "roads-casing", "type": "line",
      "source": "mbtiles-source", "source-layer": "streets",
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#c9b99a",
        "line-width": ["interpolate",["linear"],["zoom"],10,2,15,8,18,16] } },
    { "id": "roads-fill", "type": "line",
      "source": "mbtiles-source", "source-layer": "streets",
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#ffffff",
        "line-width": ["interpolate",["linear"],["zoom"],10,1,15,5,18,12] } },
    { "id": "buildings", "type": "fill",
      "source": "mbtiles-source", "source-layer": "buildings",
      "minzoom": 14,
      "paint": { "fill-color": "#e0d8ce", "fill-outline-color": "#ccc0b2" } },
    { "id": "road-labels", "type": "symbol",
      "source": "mbtiles-source", "source-layer": "street_labels",
      "minzoom": 14,
      "layout": { "text-field": ["get","name"], "text-size": 11,
        "symbol-placement": "line", "text-padding": 10 },
      "paint": { "text-color": "#666666",
        "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "place-labels", "type": "symbol",
      "source": "mbtiles-source", "source-layer": "place_labels",
      "layout": { "text-field": ["get","name"],
        "text-size": ["interpolate",["linear"],["zoom"],8,10,14,13],
        "text-max-width": 8 },
      "paint": { "text-color": "#444444",
        "text-halo-color": "#ffffff", "text-halo-width": 2 } }
  ]
}
```

> ⚠️ O `PLACEHOLDER` é substituído em runtime pelo `MapStyleHelper.kt`
> com o caminho absoluto do `map.mbtiles` no `filesDir` do dispositivo.

---

## Problemas conhecidos e soluções

| Problema | Causa | Solução |
|---|---|---|
| Erro `Cannot add extension 'kotlin'` | AGP 9.0.1 embute o plugin Kotlin | Remover `alias(libs.plugins.kotlin.android)` do `app/build.gradle.kts` |
| Erro `kotlinOptions deprecated` | AGP 9.0.1 deprecou `kotlinOptions` | Substituir por `kotlin { jvmToolchain(17) }` fora do bloco `android {}` |
| Mapa sem cores (só linhas brancas) | `source-layer` errado no `map-style.json` | Usar os nomes corretos do schema Shortbread: `streets`, `water_polygons`, `place_labels`, `street_labels`, `buildings` |
| Rota não aparece / não enquadra | `fitBounds` falhando silenciosamente | Usar `mapLibreMap?.post {}` para garantir execução após renderização e checar `count >= 2` antes de chamar `animateCamera` |
| "OffBus" aparece na tela | `app_name` ou TextView sobrando | Checar `res/values/strings.xml` e `activity_main.xml` |

---

## Como adicionar uma nova linha

1. `python emdec_scraper.py 101`
2. `xcopy *.geojson → assets/routes/`
3. Criar `assets/horarios/101_sentido1.json` e `101_sentido2.json`
4. Adicionar em `RouteRepository.kt`:
   ```kotlin
   "101" to Triple("Terminal Ouro Verde", "Destino da Linha", true),
   ```
5. **Build → Rebuild Project → Run**

---

## Script Python (emdec_scraper.py)

```python
import requests, json, os, sys
from bs4 import BeautifulSoup

BASE_URL   = "http://www.emdec.com.br"
OUTPUT_DIR = r"C:\Users\leite\OneDrive\Documentos\teste_tcc"

def get_js_urls(linha, sentido):
    url = f"{BASE_URL}/itinerarios/?linha={linha}&sentido={sentido}"
    soup = BeautifulSoup(requests.get(url, timeout=10).text, "html.parser")
    rota_url = pontos_url = None
    for s in soup.find_all("script", src=True):
        src = s["src"]
        if "lin_itinerarios" in src:
            rota_url = BASE_URL + "/itinerarios/" + src.lstrip("/")
        elif "pto_itinerarios_pontos" in src:
            pontos_url = BASE_URL + "/itinerarios/" + src.lstrip("/")
    return rota_url, pontos_url

def extract_geojson(url):
    js = requests.get(url, timeout=10).text.strip()
    return json.loads(js[js.index("{"):js.rindex("}")+1])

def fetch_linha(linha):
    for sentido in [1, 2]:
        try:
            rota_url, pontos_url = get_js_urls(linha, sentido)
            if rota_url:
                with open(os.path.join(OUTPUT_DIR, f"{linha}_sentido{sentido}_rota.geojson"), "w", encoding="utf-8") as f:
                    json.dump(extract_geojson(rota_url), f, ensure_ascii=False, indent=2)
            if pontos_url:
                with open(os.path.join(OUTPUT_DIR, f"{linha}_sentido{sentido}_pontos.geojson"), "w", encoding="utf-8") as f:
                    json.dump(extract_geojson(pontos_url), f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"Erro sentido {sentido}: {e}")

if __name__ == "__main__":
    for linha in sys.argv[1:]:
        fetch_linha(linha)
```

Uso: `python emdec_scraper.py 116 117 138`