package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent {
            PantallaPrincipal()
        }
    }
}

val db = FirebaseDatabase.getInstance().reference

// ==============================================
// 📊 DATOS DEL ROUTER — SOLO RB750Gr3
// ==============================================
data class DatosRouter(
    val conectado: Boolean = false,
    val cpu: Int = 0,
    val ram: Int = 0,
    val temperatura: String = "—",
    val subida: String = "— Mbps",
    val bajada: String = "— Mbps",
    val clientes: List<ClienteLAN> = emptyList(),
    val error: String = ""
)

data class ClienteLAN(
    val ip: String,
    val mac: String,
    val nombre: String = "",
    val interfaz: String = ""
)

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

    suspend fun probarConexion(ip: String, puerto: Int, usuario: String, clave: String): Boolean {
        ultimoError = ""
        listOf(puerto, 8080, 80).forEach { p ->
            if (hacerPeticion(ip, p, usuario, clave, "/system/resource") != null) return true
        }
        return false
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
            var temperatura = "—"
            try {
                val map = parsearJsonSimple(respuesta!!.trim().removeSurrounding("[", "]"))
                map["cpu-load"]?.toIntOrNull()?.let { cpu = it }
                map["free-memory"]?.toLongOrNull()?.let { libre ->
                    val total = map["total-memory"]?.toLongOrNull() ?: 1
                    ram = ((total - libre) * 100 / total).toInt()
                }
                map["temperature"]?.let { temperatura = "$it°C" }
                if (temperatura == "—") map["board-temperature1"]?.let { temperatura = "$it°C" }
            } catch (e: Exception) {
                ultimoError = "Error al leer recursos"
            }

            val clientes = mutableListOf<ClienteLAN>()
            hacerPeticion(ip, puertoUsado, usuario, clave, "/ip/arp")?.let { respArp ->
                parsearListaJson(respArp).forEach { map ->
                    if (map["address"] != null && map["mac-address"] != null) {
                        clientes.add(ClienteLAN(
                            ip = map["address"]!!,
                            mac = map["mac-address"]!!,
                            nombre = map["host-name"] ?: "",
                            interfaz = map["interface"] ?: "—"
                        ))
                    }
                }
            }
            hacerPeticion(ip, puertoUsado, usuario, clave, "/ip/dhcp-server/lease")?.let { respDhcp ->
                parsearListaJson(respDhcp).forEach { map ->
                    if (map["active-address"] != null && map["active-mac-address"] != null) {
                        clientes.add(ClienteLAN(
                            ip = map["active-address"]!!,
                            mac = map["active-mac-address"]!!,
                            nombre = map["host-name"] ?: map["comment"] ?: "",
                            interfaz = map["interface"] ?: "—"
                        ))
                    }
                }
            }

            DatosRouter(
                conectado = true,
                cpu = cpu,
                ram = ram,
                temperatura = temperatura,
                clientes = clientes.distinctBy { it.ip }
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

    private fun parsearListaJson(json: String): List<Map<String, String>> {
        val lista = mutableListOf<Map<String, String>>()
        val contenido = json.trim().removeSurrounding("[", "]")
        if (contenido.isBlank()) return lista
        var i = 0
        while (i < contenido.length) {
            val inicio = contenido.indexOf("{", i)
            if (inicio == -1) break
            val fin = contenido.indexOf("}", inicio).takeIf { it != -1 } ?: contenido.length
            lista.add(parsearJsonSimple(contenido.substring(inicio, fin + 1)))
            i = fin + 1
        }
        return lista
    }
}

// ============== CONFIGURACIÓN — SIMPLE, SIN ID ==============
class MikrotikConfig(context: Context) {
    private val prefs = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)
    data class Config(
        val ip: String = "",
        val puerto: String = "8080",
        val usuario: String = "admin",
        val clave: String = "",
        val dns: String = ""
    )
    fun cargar() = Config(
        ip = prefs.getString("ip", "") ?: "",
        puerto = "8080",
        usuario = prefs.getString("usuario", "admin") ?: "admin",
        clave = prefs.getString("clave", "") ?: "",
        dns = prefs.getString("dns", "") ?: ""
    )
    fun guardar(config: Config) = prefs.edit()
        .putString("ip", config.ip)
        .putString("usuario", config.usuario)
        .putString("clave", config.clave)
        .putString("dns", config.dns)
        .apply()
}
lateinit var configMikrotik: MikrotikConfig

// ============== TICKET — IGUAL, SIN CAMBIOS ==============
data class Ticket(
    val codigo: String = "",
    val monto: Float = 0f,
    val minutos: Int = 0,
    val tiempoStr: String = "",
    val fecha: String = "",
    val estado: String = "CREADO",
    val tiempoRestanteSeg: Int = 0,
    val velocidadSubida: String = "— Mbps",
    val velocidadBajada: String = "— Mbps",
    val ipUsuario: String = "Sin asignar",
    val macUsuario: String = "Sin asignar",
    val fotoBase64: String = ""
)

class TicketManager(context: Context) {
    private val archivo = context.filesDir.resolve("tickets_guardados.txt")
    fun cargar(): MutableList<Ticket> = mutableListOf<Ticket>().apply {
        try {
            if (!archivo.exists()) return@apply
            archivo.bufferedReader().use { reader ->
                reader.lineSequence().forEach { linea ->
                    val datos = linea.split("|")
                    if (datos.size >= 11) {
                        add(Ticket(
                            datos[0],
                            datos[1].toFloatOrNull() ?: 0f,
                            datos[2].toIntOrNull() ?: 0,
                            datos[3],
                            datos[4],
                            datos[5],
                            datos[6].toIntOrNull() ?: 0,
                            datos[7],
                            datos[8],
                            datos[9],
                            datos.getOrNull(10) ?: "",
                            datos.getOrNull(11) ?: ""
                        ))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    fun guardar(tickets: List<Ticket>) {
        try {
            val escritor = archivo.bufferedWriter()
            tickets.forEach { t ->
                escritor.append("${t.codigo}|${t.monto}|${t.minutos}|${t.tiempoStr}|${t.fecha}|${t.estado}|${t.tiempoRestanteSeg}|${t.velocidadSubida}|${t.velocidadBajada}|${t.ipUsuario}|${t.macUsuario}|${t.fotoBase64}")
                escritor.newLine()
            }
            escritor.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()

fun generarCodigoQR(texto: String, tamano: Int = 300): Bitmap {
    val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    return Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565).apply {
        for (x in 0 until tamano) {
            for (y in 0 until tamano) {
                setPixel(x, y, if (matriz[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
    }
}

// ============== VENTANA CONFIG — SIMPLE, 1 SOLO ROUTER ==============
@Composable
fun VentanaConfig(onCerrar: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configMikrotik.cargar() }
    var ip by remember { mutableStateOf(config.ip) }
    var usuario by remember { mutableStateOf(config.usuario) }
    var clave by remember { mutableStateOf(config.clave) }
    var dns by remember { mutableStateOf(config.dns) }
    var mensajeEstado by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCerrar) {
        Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text("⚙️ CONFIGURACIÓN — RB750Gr3", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
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
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(20.dp))

                mensajeEstado?.let {
                    Text(
                        it,
                        fontSize = 14.sp,
                        color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (ip.isBlank()) {
                                mensajeEstado = "❌ Ingrese la IP"
                                return@Button
                            }
                            probando = true
                            mensajeEstado = "🔄 Conectando..."
                            CoroutineScope(Dispatchers.IO).launch {
                                val ok = MikrotikAPI.probarConexion(ip, 8080, usuario, clave)
                                withContext(Dispatchers.Main) {
                                    mensajeEstado = if (ok) "✅ CONECTADO" else MikrotikAPI.ultimoError
                                    probando = false
                                }
                            }
                        },
                        enabled = !probando,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (probando) "⏳" else "🧪 PROBAR")
                    }

                    Button(
                        onClick = {
                            if (ip.isBlank()) {
                                mensajeEstado = "❌ IP obligatoria"
                                return@Button
                            }
                            configMikrotik.guardar(MikrotikConfig.Config(ip, "8080", usuario, clave, dns))
                            mensajeEstado = "✅ Guardado"
                            Toast.makeText(contexto, "Guardado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))
                    ) {
                        Text("💾 GUARDAR")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onCerrar,
                    Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color(0xFF818CF8))
                ) {
                    Text("CERRAR")
                }
            }
        }
    }
}

// ============== SECCIÓN CLIENTES — IGUAL ==============
@Composable
fun SeccionClientesLAN(datosRouter: DatosRouter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Color(0xFFF3E5F5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("💻 CLIENTES CONECTADOS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
            Spacer(Modifier.height(12.dp))

            if (!datosRouter.conectado) {
                Text("⚠️ Conecta al router para ver clientes", color = Color.Gray, fontSize = 14.sp)
            } else if (datosRouter.clientes.isEmpty()) {
                Text("📭 Sin clientes conectados", color = Color.Gray, fontSize = 14.sp)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.28f))
                    Text("MAC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.35f))
                    Text("ETHER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.15f))
                    Text("NOMBRE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.22f))
                }
                Spacer(Modifier.height(8.dp))
                datosRouter.clientes.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.ip, fontSize = 11.sp, modifier = Modifier.weight(0.28f))
                        Text(c.mac, fontSize = 11.sp, modifier = Modifier.weight(0.35f))
                        Text(c.interfaz, fontSize = 11.sp, modifier = Modifier.weight(0.15f))
                        Text(c.nombre.ifBlank { "—" }, fontSize = 11.sp, modifier = Modifier.weight(0.22f))
                    }
                }
            }
        }
    }
}

@Composable
fun BotonPestana(
    texto: String,
    colorFondo: Color,
    modifier: Modifier = Modifier,
    alPresionar: () -> Unit
) {
    Button(
        onClick = alPresionar,
        modifier = modifier.height(55.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colorFondo)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ============== PANTALLA PRINCIPAL — SOLO RB750Gr3 ==============
@Composable
fun PantallaPrincipal() {
    var abrirConfig by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var datosRouter by remember { mutableStateOf(DatosRouter()) }
    var cargando by remember { mutableStateOf(false) }

    val config = remember { configMikrotik.cargar() }

    LaunchedEffect(config.ip) {
        if (config.ip.isBlank()) return@LaunchedEffect
        while (isActive) {
            cargando = true
            datosRouter = MikrotikAPI.obtenerTodo(config.ip, 8080, config.usuario, config.clave)
            cargando = false
            delay(3000)
        }
    }

    val creados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val activos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val vencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🎟️ PAOYHAN TICKETS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // ==============================
                // 📡 SOLO RB750Gr3 — SIN OTROS ROUTERS
                // ==============================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(Color(0xFFFFF3E0))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
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

                        Spacer(modifier = Modifier.height(16.dp))

                        if (config.ip.isBlank()) {
                            Text("⚠️ Toca el ícono ⚙️ para configurar la IP", fontSize = 15.sp, color = Color.Gray)
                        } else if (!datosRouter.conectado) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔄 Conectando...", fontSize = 15.sp, color = Color(0xFFE65100))
                                if (cargando) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(start = 8.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else {
                            Text("🌐 IP: ${config.ip}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💻 CPU", fontSize = 13.sp, color = Color.Gray)
                                    Text("${datosRouter.cpu}%", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💾 RAM", fontSize = 13.sp, color = Color.Gray)
                                    Text("${datosRouter.ram}%", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌡️ TEMP", fontSize = 13.sp, color = Color.Gray)
                                    Text(datosRouter.temperatura, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                SeccionClientesLAN(datosRouter = datosRouter)

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { abrirCreados = true },
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))
                ) {
                    Text("📋 TICKETS CREADOS ($creados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonPestana("🟢 ACTIVOS ($activos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos = true }
                    BotonPestana("🔴 VENCIDOS ($vencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos = true }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        if (abrirConfig) VentanaConfig { abrirConfig = false }
        if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { Text("Creados", modifier = Modifier.padding(24.dp)) }
        if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { Text("Activos", modifier = Modifier.padding(24.dp)) }
        if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { Text("Vencidos", modifier = Modifier.padding(24.dp)) }
    }
}
