package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        setContent {
            PantallaPrincipal()
        }
    }
}

// ==============================================
// 📊 DATOS DEL ROUTER — SOLO LO NECESARIO
// ==============================================
data class DatosRouter(
    val conectado: Boolean = false,
    val ip: String = "—",
    val cpu: Int = 0,
    val ram: Int = 0,
    val subida: String = "— Mbps",
    val bajada: String = "— Mbps",
    val error: String = ""
)

// ==============================================
// 🔌 API MIKROTIK — LECTURA DE DATOS
// ==============================================
object MikrotikAPI {
    var ultimoError = ""

    private suspend fun hacerPeticion(
        ip: String,
        puerto: Int,
        usuario: String,
        clave: String,
        recurso: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:$puerto/rest$recurso"
            val conexion = URL(url).openConnection() as HttpURLConnection
            conexion.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                val credenciales = Base64.encodeToString(
                    "$usuario:$clave".toByteArray(),
                    Base64.NO_WRAP
                )
                setRequestProperty("Authorization", "Basic $credenciales")
            }
            val codigo = conexion.responseCode
            if (codigo == 401) {
                ultimoError = "❌ Usuario o contraseña incorrectos"
                return@withContext null
            }
            if (codigo != 200) {
                ultimoError = "❌ Error HTTP $codigo"
                return@withContext null
            }
            val respuesta = conexion.inputStream.bufferedReader().use { it.readText() }
            conexion.disconnect()
            respuesta
        } catch (e: Exception) {
            ultimoError = "❌ ${e.message ?: "Sin conexión"}"
            null
        }
    }

    suspend fun obtenerTodo(ip: String, puerto: Int, usuario: String, clave: String): DatosRouter {
        ultimoError = ""
        return withContext(Dispatchers.IO) {
            var puertoUsado = 8080
            var respuesta: String? = null
            listOf(puerto, 8080, 80).forEach { p ->
                respuesta = hacerPeticion(ip, p, usuario, clave, "/system/resource")
                if (respuesta != null) {
                    puertoUsado = p
                    return@forEach
                }
            }
            if (respuesta == null) return@withContext DatosRouter(conectado = false, error = ultimoError)

            var cpu = 0
            var ram = 0
            try {
                val map = parsearJsonSimple(respuesta!!.trim().removeSurrounding("[", "]"))
                map["cpu-load"]?.toIntOrNull()?.let { cpu = it }
                map["free-memory"]?.toLongOrNull()?.let { libre ->
                    val total = map["total-memory"]?.toLongOrNull() ?: 1
                    ram = ((total - libre) * 100 / total).toInt()
                }
            } catch (e: Exception) {
                ultimoError = "Error al leer recursos"
            }

            // Leer tráfico de Ether1 para velocidades
            var subida = "— Mbps"
            var bajada = "— Mbps"
            hacerPeticion(ip, puertoUsado, usuario, clave, "/interface/ether1/monitor")?.let { trafico ->
                try {
                    val m = parsearJsonSimple(trafico.trim().removeSurrounding("[", "]"))
                    val rx = m["rx-bits-per-second"]?.toLongOrNull() ?: 0
                    val tx = m["tx-bits-per-second"]?.toLongOrNull() ?: 0
                    bajada = if (rx > 1_000_000) "%.1f Mbps".format(rx / 1_000_000f) else "$rx bps"
                    subida = if (tx > 1_000_000) "%.1f Mbps".format(tx / 1_000_000f) else "$tx bps"
                } catch (e: Exception) {}
            }

            DatosRouter(
                conectado = true,
                ip = ip,
                cpu = cpu,
                ram = ram,
                subida = subida,
                bajada = bajada
            )
        }
    }

    private fun parsearJsonSimple(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        json.trim().removeSurrounding("{", "}").split(",").forEach { par ->
            val partes = par.split(":", limit = 2)
            if (partes.size == 2) {
                val clave = partes[0].trim().removeSurrounding("\"")
                val valor = partes[1].trim().removeSurrounding("\"")
                map[clave] = valor
            }
        }
        return map
    }
}

// ============== CONFIGURACIÓN ==============
class MikrotikConfig(context: Context) {
    private val prefs = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)
    data class Config(
        val ip: String = "",
        val usuario: String = "admin",
        val clave: String = ""
    )
    fun cargar() = Config(
        ip = prefs.getString("ip", "") ?: "",
        usuario = prefs.getString("usuario", "admin") ?: "admin",
        clave = prefs.getString("clave", "") ?: ""
    )
    fun guardar(config: Config) = prefs.edit()
        .putString("ip", config.ip)
        .putString("usuario", config.usuario)
        .putString("clave", config.clave)
        .apply()
}
lateinit var configMikrotik: MikrotikConfig

// ============== VENTANA CONFIG ==============
@Composable
fun VentanaConfig(onCerrar: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configMikrotik.cargar() }
    var ip by remember { mutableStateOf(config.ip) }
    var usuario by remember { mutableStateOf(config.usuario) }
    var clave by remember { mutableStateOf(config.clave) }

    Dialog(onDismissRequest = onCerrar) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text("⚙️ CONFIGURACIÓN RB750Gr3", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP MikroTik") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("192.168.88.1") }
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (ip.isBlank()) return@Button
                            configMikrotik.guardar(MikrotikConfig.Config(ip, usuario, clave))
                            Toast.makeText(contexto, "✅ Guardado", Toast.LENGTH_SHORT).show()
                            onCerrar()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))
                    ) { Text("💾 GUARDAR") }
                    Button(onClick = onCerrar, Modifier.weight(1f)) { Text("CERRAR") }
                }
            }
        }
    }
}

// ============== PANTALLA PRINCIPAL — IGUAL A LA IMAGEN ✅ ==============
@Composable
fun PantallaPrincipal() {
    var abrirConfig by remember { mutableStateOf(false) }
    var datosRouter by remember { mutableStateOf(DatosRouter()) }
    var cargando by remember { mutableStateOf(false) }
    val config = remember { configMikrotik.cargar() }

    // Actualización cada 3 segundos
    LaunchedEffect(config.ip) {
        if (config.ip.isBlank()) return@LaunchedEffect
        while (isActive) {
            cargando = true
            datosRouter = MikrotikAPI.obtenerTodo(config.ip, 8080, config.usuario, config.clave)
            cargando = false
            delay(3000)
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(20.dp)
        ) {
            // TÍTULO
            Text(
                "🎟️ PAOYHAN TICKETS",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                modifier = Modifier.padding(vertical = 20.dp)
            )

            // ==============================
            // 📡 TARJETA RB750Gr3 — IGUAL A LA IMAGEN
            // ==============================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(Color(0xFFFFF3E0)), // Fondo naranja claro igual a la imagen
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // NOMBRE + ÍCONO CONFIGURACIÓN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📡 RB750Gr3",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        IconButton(onClick = { abrirConfig = true }) {
                            Icon(Icons.Default.Settings, "Configurar", tint = Color(0xFF6366F1), modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // MENSAJE SI NO HAY IP CONFIGURADA
                    if (config.ip.isBlank()) {
                        Text(
                            "⚠️ Toca el ícono ⚙️ para configurar la IP",
                            fontSize = 15.sp,
                            color = Color.Gray
                        )
                    }
                    // CONECTANDO
                    else if (!datosRouter.conectado) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄 Conectando...", fontSize = 15.sp, color = Color(0xFFE65100))
                            if (cargando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).padding(start = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    // ✅ CONECTADO — MOSTRAR TODOS LOS DATOS
                    else {
                        DatoFila("IP", datosRouter.ip)
                        Spacer(Modifier.height(12.dp))
                        DatoFila("💻 CPU", "${datosRouter.cpu}%")
                        Spacer(Modifier.height(12.dp))
                        DatoFila("💾 RAM", "${datosRouter.ram}%")
                        Spacer(Modifier.height(12.dp))
                        DatoFila("⬆️ SUBIDA", datosRouter.subida)
                        Spacer(Modifier.height(12.dp))
                        DatoFila("⬇️ BAJADA", datosRouter.bajada)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }

        if (abrirConfig) VentanaConfig { abrirConfig = false }
    }
}

// ============== FILA DE DATOS ==============
@Composable
fun DatoFila(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(etiqueta, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF424242))
        Text(valor, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
    }
}
