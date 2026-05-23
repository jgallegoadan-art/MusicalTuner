package com.example.musicaltuner

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import kotlin.math.log2
import kotlin.math.roundToInt

// 1. Estructura para definir un instrumento
data class Instrumento(val nombre: String, val notasCuerdas: List<String>)

class MainActivity : ComponentActivity() {
    private var dispatcher: AudioDispatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaAfinador(
                        onIniciarAudio = { onFrecuenciaDetectada ->
                            iniciarEscuchaMicrofono(onFrecuenciaDetectada)
                        },
                        onDetenerAudio = { detenerMicrofono() }
                    )
                }
            }
        }
    }

    private fun iniciarEscuchaMicrofono(onFrecuenciaDetectada: (Float) -> Unit) {
        detenerMicrofono()
        val sampleRate = 22050
        val bufferSize = 1024
        val overlap = 0

        try {
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, overlap)
            val pdh = PitchDetectionHandler { result, _ ->
                val pitch = result.pitch
                if (pitch != -1f) {
                    runOnUiThread { onFrecuenciaDetectada(pitch) }
                }
            }
            val processor = PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.YIN, sampleRate.toFloat(), bufferSize, pdh)
            dispatcher?.addAudioProcessor(processor)
            java.lang.Thread(dispatcher, "Audio Engine").start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun detenerMicrofono() {
        dispatcher?.stop()
        dispatcher = null
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerMicrofono()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAfinador(onIniciarAudio: ((Float) -> Unit) -> Unit, onDetenerAudio: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val permisoConcedido = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO)

    var tienePermiso by remember { mutableStateOf(permisoConcedido) }
    var herciosDetectados by remember { mutableFloatStateOf(0.0f) }
    var notaDetectada by remember { mutableStateOf("---") }

    // --- BASE DE DATOS DE INSTRUMENTOS ---
    val listaInstrumentos = remember {
        listOf(
            Instrumento("Modo Libre (Detectar todo)", emptyList()),
            Instrumento("Guitarra (Clásica/Flamenca/Acústica/Eléctrica)", listOf("E2", "A2", "D3", "G3", "B3", "E4")),
            Instrumento("Ukulele", listOf("G4", "C4", "E4", "A4")),
            Instrumento("Banjo (5 cuerdas)", listOf("G4", "D3", "G3", "B3", "D4")),
            Instrumento("Guitarra de 3 cuerdas (Open G)", listOf("G2", "D3", "G3")),
            Instrumento("Kalimba (17 teclas Do)", listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "F5", "G5", "A5", "B5", "C6", "D6", "E6"))
        )
    }
    var instrumentoSeleccionado by remember { mutableStateOf(listaInstrumentos[0]) }
    var menuDesplegado by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> tienePermiso = isGranted }

    LaunchedEffect(tienePermiso) {
        if (tienePermiso) {
            onIniciarAudio { hz ->
                herciosDetectados = hz
                notaDetectada = traducirHzANota(hz)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onDetenerAudio() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Cambiado a Top para dejar espacio al menú
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (!tienePermiso) {
            Text("Necesitamos acceso al micrófono para poder afinar.", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Conceder Permiso")
            }
        } else {
            // 🔄 SELECTOR VISUAL DE INSTRUMENTOS
            ExposedDropdownMenuBox(
                expanded = menuDesplegado,
                onExpandedChange = { menuDesplegado = !menuDesplegado }
            ) {
                OutlinedTextField(
                    value = instrumentoSeleccionado.nombre,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Instrumento") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuDesplegado) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = menuDesplegado,
                    onDismissRequest = { menuDesplegado = false }
                ) {
                    listaInstrumentos.forEach { instrumento ->
                        DropdownMenuItem(
                            text = { Text(instrumento.nombre) },
                            onClick = {
                                instrumentoSeleccionado = instrumento
                                menuDesplegado = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Nombre del modo o instrumento activo en grande
            Text(
                text = if (instrumentoSeleccionado.notasCuerdas.isEmpty()) "Modo Libre" else "Afinando...",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Nota e Idioma
            Text(text = notaDetectada, fontSize = 40.sp, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Hercios
            Text(text = "${String.format(java.util.Locale.US, "%.1f", herciosDetectados)} Hz", fontSize = 24.sp)

            Spacer(modifier = Modifier.height(48.dp))

            // Mostrar las notas objetivo si hay un instrumento seleccionado
            if (instrumentoSeleccionado.notasCuerdas.isNotEmpty()) {
                Text("Notas del instrumento:", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = instrumentoSeleccionado.notasCuerdas.joinToString("   "),
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text("Silba o toca una cuerda...", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

fun traducirHzANota(frecuencia: Float): String {
    if (frecuencia < 16.35f) return "---"
    val nombresNotasEEUU = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val nombresNotasES = arrayOf(
        "Do", "Do# / Reb", "Re", "Re# / Mib", "Mi", "Fa",
        "Fa# / Solb", "Sol", "Sol# / Lab", "La", "La# / Sib", "Si"
    )
    val notaInterna = (12 * log2(frecuencia / 440.0) + 69).roundToInt()
    val indiceNota = notaInterna % 12
    val octava = (notaInterna / 12) - 1

    return if (indiceNota in 0..11) {
        "${nombresNotasEEUU[indiceNota]}$octava - (${nombresNotasES[indiceNota]})"
    } else {
        "(___) - (___)"
    }
}