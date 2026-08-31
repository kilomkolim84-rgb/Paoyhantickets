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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

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
// 🔧 CONEXIÓN MIKROTIK + TRAER TODOS LOS DATOS
// ==============================================
data class DatosRouter(
    val conectado: Boolean = false,
    val cpu: String = "—",
    val ram: String = "—",
    val temperatura: String = "—",
    val subida: String = "— Mbps",
    val bajada: String = "— Mbps",
    val clientes: List<ClienteLAN> = emptyList(),
    val error: String = ""
)

data class ClienteLAN(
    val ip: String,
    val mac: String,
    val nombre: String = ""
)

object MikrotikAPI {
    var ultimoError = ""

    private suspend fun login(ip: String, puerto: Int, usuario: String, clave: String, entrada: InputStream, salida: OutputStream): Boolean {
        enviarComando(salida, "/login", "=name=$usuario", "=password=$clave")
        val resp = leerRespuesta(entrada)
        return resp.any { it == "!done" }
    }

    suspend fun probarConexion(ip: String, puerto: Int, usuario: String, clave: String): Boolean {
        ultimoError = ""
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ip, puerto)
                socket.soTimeout = 5000
                val entrada = socket.getInputStream()
                val salida = socket.getOutputStream()
                val ok = login(ip, puerto, usuario, clave, entrada, salida)
                socket.close()
                ok
            } catch (e: Exception) {
                ultimoError = e.message ?: "Sin conexión"
                false
            }
        }
    }

    suspend fun obtenerTodo(ip: String, puerto: Int, usuario: String, clave: String): DatosRouter {
        ultimoError = ""
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ip, puerto)
                socket.soTimeout = 8000
                val entrada = socket.getInputStream()
                val salida = socket.getOutputStream()

                if (!login(ip, puerto, usuario, clave, entrada, salida)) {
                    socket.close()
                    return@withContext DatosRouter(conectado = false, error = "Error de login")
                }

                // 1. RECURSOS: CPU, RAM, TEMPERATURA
                enviarComando(salida, "/system/resource/print")
                val respRecursos = leerRespuesta(entrada)

                // 2. CLIENTES CONECTADOS (ARP + DHCP)
                enviarComando(salida, "/ip/arp/print", "?=dynamic=yes")
                val respArp = leerRespuesta(entrada)

                enviarComando(salida, "/ip/dhcp-server/lease/print")
                val respLeases = leerRespuesta(entrada)

                socket.close()

                // PARSEAR RECURSOS
                var cpu = "—"
                var ram = "—"
                var temperatura = "—"
                for (linea in respRecursos) {
                    when {
                        linea.startsWith("=cpu-load=") -> cpu = linea.substringAfter("=")
                        linea.startsWith("=free-memory=") -> {
                            val libre = linea.substringAfter("=").toLongOrNull() ?: 0
                            val total = respRecursos.find { it.startsWith("=total-memory=") }?.substringAfter("=")?.toLongOrNull() ?: 1
                            val porcentaje = ((total - libre) * 100) / total
                            ram = "$porcentaje%"
                        }
                        linea.startsWith("=temperature=") -> temperatura = linea.substringAfter("=") + "°C"
                    }
                }

                // PARSEAR CLIENTES
                val clientes = mutableListOf<ClienteLAN>()
                for (linea in respArp + respLeases) {
                    if (linea.contains("=address=") && linea.contains("=mac-address=")) {
                        val ipMatch = Regex("=address=([\\d.]+)").find(linea)?.groupValues?.get(1)
                        val macMatch = Regex("=mac-address=([\\w:]+)").find(linea)?.groupValues?.get(1)
                        val nombreMatch = Regex("=host-name=([^=]+)").find(linea)?.groupValues?.get(1)
                        if (ipMatch != null && macMatch != null) {
                            clientes.add(ClienteLAN(ip = ipMatch, mac = macMatch, nombre = nombreMatch ?: ""))
                        }
                    }
                }

                DatosRouter(
                    conectado = true,
                    cpu = cpu,
                    ram = ram,
                    temperatura = temperatura,
                    subida = "— Mbps",
                    bajada = "— Mbps",
                    clientes = clientes.distinctBy { it.ip }
                )
            } catch (e: Exception) {
                ultimoError = e.message ?: "Error al leer datos"
                DatosRouter(conectado = false, error = ultimoError)
            }
        }
    }

    private fun enviarComando(salida: OutputStream, vararg partes: String) {
        partes.forEach { cmd ->
            val bytes = cmd.toByteArray(Charsets.UTF_8)
            escribirLongitud(salida, bytes.size)
            salida.write(bytes)
        }
        escribirLongitud(salida, 0)
        salida.flush()
    }

    private fun escribirLongitud(salida: OutputStream, len: Int) {
        when {
            len < 0x80 -> salida.write(len)
            len < 0x4000 -> {
                salida.write(0x80 or (len shr 8))
                salida.write(len and 0xFF)
            }
            len < 0x200000 -> {
                salida.write(0xC0 or (len shr 16))
                salida.write((len shr 8) and 0xFF)
                salida.write(len and 0xFF)
            }
            else -> {
                salida.write(0xE0 or (len shr 24))
                salida.write((len shr 16) and 0xFF)
                salida.write((len shr 8) and 0xFF)
                salida.write(len and 0xFF)
            }
        }
    }

    private fun leerRespuesta(entrada: InputStream): List<String> {
        val resp = mutableListOf<String>()
        while (true) {
            val len = leerLongitud(entrada)
            if (len == 0) break
            val datos = ByteArray(len)
            entrada.read(datos)
            resp.add(String(datos, Charsets.UTF_8))
        }
        return resp
    }

    private fun leerLongitud(entrada: InputStream): Int {
        var b = entrada.read()
        return when {
            b < 0x80 -> b
            b < 0xC0 -> ((b and 0x7F) shl 8) or entrada.read()
            b < 0xE0 -> ((b and 0x3F) shl 16) or (entrada.read() shl 8) or entrada.read()
            b < 0xF0 -> ((b and 0x1F) shl 24) or (entrada.read() shl 16) or (entrada.read() shl 8) or entrada.read()
            else -> 0
        }
    }
}

// ============== CONFIGURACIÓN ==============
class MikrotikConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)

    data class Config(
        val ip: String = "",
        val puerto: String = "8728",
        val usuario: String = "admin",
        val clave: String = "",
        val dns: String = ""
    )

    fun cargar(id: Int): Config {
        return Config(
            ip = prefs.getString("r${id}_ip", "") ?: "",
            puerto = "8728",
            usuario = prefs.getString("r${id}_usuario", "admin") ?: "admin",
            clave = prefs.getString("r${id}_clave", "") ?: "",
            dns = prefs.getString("r${id}_dns", "") ?: ""
        )
    }

    fun guardar(id: Int, config: Config) {
        prefs.edit()
            .putString("r${id}_ip", config.ip)
            .putString("r${id}_usuario", config.usuario)
            .putString("r${id}_clave", config.clave)
            .putString("r${id}_dns", config.dns)
            .apply()
    }
}

lateinit var configMikrotik: MikrotikConfig

// ============== TICKET ==============
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

// ============== GESTOR TICKETS ==============
class TicketManager(context: Context) {
    private val archivo = File(context.filesDir, "tickets_guardados.txt")

    fun cargar(): MutableList<Ticket> {
        val lista = mutableListOf<Ticket>()
        try {
            if (!archivo.exists()) return lista
            val lector = BufferedReader(InputStreamReader(FileInputStream(archivo)))
            var linea: String?
            while (lector.readLine().also { linea = it } != null) {
                val datos = linea!!.split("|")
                if (datos.size >= 11) {
                    lista.add(
                        Ticket(
                            codigo = datos[0],
                            monto = datos[1].toFloatOrNull() ?: 0f,
                            minutos = datos[2].toIntOrNull() ?: 0,
                            tiempoStr = datos[3],
                            fecha = datos[4],
                            estado = datos[5],
                            tiempoRestanteSeg = datos[6].toIntOrNull() ?: 0,
                            velocidadSubida = datos[7],
                            velocidadBajada = datos[8],
                            ipUsuario = datos[9],
                            macUsuario = datos.getOrNull(10) ?: "",
                            fotoBase64 = datos.getOrNull(11) ?: ""
                        )
                    )
                }
            }
            lector.close()
        } catch (e: Exception) { e.printStackTrace() }
        return lista
    }

    fun guardar(tickets: List<Ticket>) {
        try {
            val escritor = BufferedWriter(OutputStreamWriter(FileOutputStream(archivo)))
            tickets.forEach { t ->
                escritor.write("${t.codigo}|${t.monto}|${t.minutos}|${t.tiempoStr}|${t.fecha}|${t.estado}|${t.tiempoRestanteSeg}|${t.velocidadSubida}|${t.velocidadBajada}|${t.ipUsuario}|${t.macUsuario}|${t.fotoBase64}")
                escritor.newLine()
            }
            escritor.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()
val listaClientesLAN = mutableStateListOf<ClienteLAN>()

fun base64ABitmap(base64: String): Bitmap? {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

fun formatearTiempo(segundos: Int): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

// ============== VENTANA CONFIGURACIÓN ==============
@Composable
fun VentanaConfigRouter(routerId: Int, nombreRouter: String, onCerrar: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configMikrotik.cargar(routerId) }

    var ip by remember { mutableStateOf(config.ip) }
    var usuario by remember { mutableStateOf(config.usuario) }
    var clave by remember { mutableStateOf(config.clave) }
    var dns by remember { mutableStateOf(config.dns) }
    var mensajeEstado by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCerrar) {
        Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚙️ CONFIGURACIÓN\n$nombreRouter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(ip, { ip = it }, label = { Text("IP Mikrotik") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("192.168.88.1") })
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(dns, { dns = it }, label = { Text("DNS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(20.dp))

                mensajeEstado?.let { Text(it, fontSize = 14.sp, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)) }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (ip.isBlank()) { mensajeEstado = "❌ Ingrese la IP"; return@Button }
                            probando = true; mensajeEstado = "🔄 Conectando..."
                            CoroutineScope(Dispatchers.IO).launch {
                                val ok = MikrotikAPI.probarConexion(ip, 8728, usuario, clave)
                                withContext(Dispatchers.Main) {
                                    mensajeEstado = if (ok) "✅ CONECTADO — MikroTik respondió correctamente" else "❌ ERROR: ${MikrotikAPI.ultimoError}"
                                    probando = false
                                }
                            }
                        },
                        enabled = !probando, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    ) { Text(if (probando) "⏳..." else "🧪 PROBAR", fontSize = 15.sp) }

                    Button(
                        onClick = {
                            if (ip.isBlank()) { mensajeEstado = "❌ IP obligatoria"; return@Button }
                            configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, "8728", usuario, clave, dns))
                            mensajeEstado = "✅ Guardado"
                            Toast.makeText(contexto, "Configuración guardada", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))
                    ) { Text("💾 GUARDAR", fontSize = 15.sp) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF818CF8))) { Text("CERRAR", fontSize = 15.sp) }
            }
        }
    }
}

// ============== TARJETA ROUTER ==============
@Composable
fun TarjetaRouter(nombre: String, modelo: String, routerId: Int, seleccionado: Boolean, alSeleccionar: () -> Unit, alConfigurar: () -> Unit) {
    val config = remember { configMikrotik.cargar(routerId) }
    Card(onClick = alSeleccionar, modifier = Modifier.width(160.dp).height(145.dp), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(if (seleccionado) Color(0xFFE3F2FD) else Color.White),
        border = if (seleccionado) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Box {
            IconButton(onClick = alConfigurar, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(Icons.Default.Settings, null, tint = Color(0xFF6366F1))
            }
            Column(modifier = Modifier.padding(top = 32.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(modelo, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Text("IP: ${config.ip.ifBlank { "Sin config" }}", fontSize = 11.sp)
                Text("Puerto: 8728", fontSize = 11.sp)
            }
        }
    }
}

// ============== SECCIÓN CLIENTES ==============
@Composable
fun SeccionClientesLAN(datosRouter: DatosRouter) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFF3E5F5))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("💻 CLIENTES CONECTADOS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
            Spacer(modifier = Modifier.height(12.dp))

            if (!datosRouter.conectado) {
                Text("⚠️ Conecta al router para ver clientes", color = Color.Gray, fontSize = 14.sp)
            } else if (datosRouter.clientes.isEmpty()) {
                Text("📭 Sin clientes conectados", color = Color.Gray, fontSize = 14.sp)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.35f))
                    Text("MAC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.45f))
                    Text("NOMBRE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.20f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                datosRouter.clientes.forEach { c ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.ip, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
                        Text(c.mac, fontSize = 12.sp, modifier = Modifier.weight(0.45f))
                        Text(c.nombre.ifBlank { "—" }, fontSize = 12.sp, modifier = Modifier.weight(0.20f))
                    }
                }
            }
        }
    }
}

// ============== BOTÓN PESTAÑA ==============
@Composable
fun BotonPestana(texto: String, colorFondo: Color, modifier: Modifier = Modifier, alPresionar: () -> Unit) {
    Button(onClick = alPresionar, modifier = modifier.height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = colorFondo)) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ============== QR ==============
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

// ============== PANTALLA PRINCIPAL ==============
@Composable
fun PantallaPrincipal() {
    var routerSeleccionado by remember { mutableStateOf(1) }
    var abrirConfig1 by remember { mutableStateOf(false) }
    var abrirConfig2 by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var datosRouter by remember { mutableStateOf(DatosRouter()) }
    var cargandoDatos by remember { mutableStateOf(false) }

    val configActual = remember(routerSeleccionado) { configMikrotik.cargar(routerSeleccionado) }

    // CARGAR DATOS AUTOMÁTICAMENTE
    LaunchedEffect(routerSeleccionado, configActual.ip) {
        if (configActual.ip.isNotBlank()) {
            cargandoDatos = true
            datosRouter = MikrotikAPI.obtenerTodo(configActual.ip, 8728, configActual.usuario, configActual.clave)
            cargandoDatos = false
        }
    }

    val creados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val activos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val vencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp)) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎟️ PAOYHAN TICKETS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical = 16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TarjetaRouter("📡 Router #1", "RB750Gr3", 1, routerSeleccionado == 1, { routerSeleccionado = 1 }, { abrirConfig1 = true })
                    TarjetaRouter("📡 Router #2", "RB3011", 2, routerSeleccionado == 2, { routerSeleccionado = 2 }, { abrirConfig2 = true })
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 📊 TARJETA DE ESTADO EN VIVO
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(if (datosRouter.conectado) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊 ESTADO EN VIVO — Router #$routerSeleccionado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (datosRouter.conectado) Color(0xFF2E7D32) else Color(0xFFE65100))
                            if (cargandoDatos) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(start = 8.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (!datosRouter.conectado) {
                            Text("⚠️ Configura IP y contraseña → GUARDAR para ver datos", color = Color.Gray, fontSize = 14.sp)
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💻 CPU", fontSize = 12.sp, color = Color.Gray)
                                    Text("${datosRouter.cpu}%", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💾 RAM", fontSize = 12.sp, color = Color.Gray)
                                    Text(datosRouter.ram, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌡️ TEMP", fontSize = 12.sp, color = Color.Gray)
                                    Text(datosRouter.temperatura, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📤 SUBIDA", fontSize = 12.sp, color = Color.Gray)
                                    Text(datosRouter.subida, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📥 BAJADA", fontSize = 12.sp, color = Color.Gray)
                                    Text(datosRouter.bajada, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SeccionClientesLAN(datosRouter = datosRouter)

                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { abrirCreados = true }, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) {
                    Text("📋 TICKETS CREADOS ($creados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonPestana("🟢 ACTIVOS ($activos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos = true }
                    BotonPestana("🔴 VENCIDOS ($vencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos = true }
                }
            }
        }

        if (abrirConfig1) VentanaConfigRouter(1, "ROUTER #1") { abrirConfig1 = false }
        if (abrirConfig2) VentanaConfigRouter(2, "ROUTER #2") { abrirConfig2 = false }
        if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { Text("Creados", modifier = Modifier.padding(24.dp)) }
        if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { Text("Activos", modifier = Modifier.padding(24.dp)) }
        if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { Text("Vencidos", modifier = Modifier.padding(24.dp)) }
    }
}
