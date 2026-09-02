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
import com.zxing.BarcodeFormat
import com.zxing.qrcode.QRCodeWriter
import io.github.dragneelfps.mikrotikapi.MikroTikApi
import io.github.dragneelfps.mikrotikapi.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.firebase.FirebaseApp.initializeApp(this)
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent { PantallaPrincipal() }
    }
}

val db = FirebaseDatabase.getInstance().reference
val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// ============= ESTADO CONEXIÓN MIKROTIK =============
data class MikrotikEstado(
    val conectado: Boolean = false,
    val cpu: String = "—",
    val ram: String = "—",
    val temp: String = "—",
    val subida: String = "— Mbps",
    val bajada: String = "— Mbps",
    val clientes: List<Cliente> = emptyList(),
    val mensaje: String = ""
)

data class Cliente(
    val ip: String,
    val mac: String,
    val nombre: String,
    val interfaz: String
)

val _estadoMikrotik = MutableStateFlow(MikrotikEstado())
val estadoMikrotik = _estadoMikrotik.asStateFlow()
var conexionMikrotik: MikroTikApi? = null
var trabajoActualizacion: Job? = null

// ============= GESTOR DE CONFIGURACIÓN =============
class MikrotikConfig(context: Context) {
    private val prefs = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)

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
            puerto = prefs.getString("r${id}_puerto", "8728") ?: "8728",
            usuario = prefs.getString("r${id}_usuario", "admin") ?: "admin",
            clave = prefs.getString("r${id}_clave", "") ?: "",
            dns = prefs.getString("r${id}_dns", "") ?: ""
        )
    }

    fun guardar(id: Int, config: Config) {
        prefs.edit()
            .putString("r${id}_ip", config.ip)
            .putString("r${id}_puerto", config.puerto)
            .putString("r${id}_usuario", config.usuario)
            .putString("r${id}_clave", config.clave)
            .putString("r${id}_dns", config.dns)
            .apply()
    }
}

lateinit var configMikrotik: MikrotikConfig

// ============= CONEXIÓN REAL AL MIKROTIK =============
fun conectarMikrotik(config: MikrotikConfig.Config) {
    desconectarMikrotik()
    
    if (config.ip.isBlank() || config.clave.isBlank()) {
        _estadoMikrotik.value = MikrotikEstado(mensaje = "⚠️ Falta IP o contraseña")
        return
    }

    scope.launch {
        try {
            _estadoMikrotik.value = MikrotikEstado(mensaje = "🔄 Conectando...")
            
            conexionMikrotik = MikroTikApi.connect(
                host = config.ip,
                port = config.puerto.toInt(),
                username = config.usuario,
                password = config.clave,
                enableSsl = config.puerto == "8729"
            )

            if (conexionMikrotik != null) {
                _estadoMikrotik.value = MikrotikEstado(conectado = true, mensaje = "✅ Conectado")
                iniciarActualizacionDatos()
            } else {
                _estadoMikrotik.value = MikrotikEstado(mensaje = "❌ No se pudo conectar")
            }
        } catch (e: Exception) {
            _estadoMikrotik.value = MikrotikEstado(mensaje = "❌ Error: ${e.message}")
        }
    }
}

fun desconectarMikrotik() {
    trabajoActualizacion?.cancel()
    scope.launch {
        try { conexionMikrotik?.close() } catch (_: Exception) {}
        conexionMikrotik = null
        _estadoMikrotik.value = MikrotikEstado(mensaje = "🔌 Desconectado")
    }
}

fun iniciarActualizacionDatos() {
    trabajoActualizacion?.cancel()
    trabajoActualizacion = scope.launch {
        while (isActive) {
            actualizarDatosMikrotik()
            delay(3000) // Actualiza cada 3 segundos
        }
    }
}

suspend fun actualizarDatosMikrotik() {
    val api = conexionMikrotik ?: return
    try {
        // CPU y RAM
        val recursos = api.query("/system/resource/print").execute()
        if (recursos is Result.Success && recursos.data.isNotEmpty()) {
            val r = recursos.data[0]
            val cpu = r["cpu-load"] ?: "—"
            val ramTotal = (r["total-memory"] ?: "0").toLongOrNull() ?: 1
            val ramUsada = (r["free-memory"] ?: "0").toLongOrNull() ?: 0
            val ramPorc = if (ramTotal > 0) ((ramTotal - ramUsada) * 100 / ramTotal).toInt() else 0
            val temp = r["cpu-temperature"] ?: "—"

            // Interfaces — velocidad
            val interfaces = api.query("/interface/print").execute()
            var subida = "— Mbps"
            var bajada = "— Mbps"
            if (interfaces is Result.Success) {
                val ether1 = interfaces.data.find { it["name"] == "ether1" }
                val ether4 = interfaces.data.find { it["name"] == "ether4" }
                if (ether1 != null) bajada = "${(ether1["rx-byte"] ?: "0").toLong() / 125000} Mbps"
                if (ether4 != null) subida = "${(ether4["tx-byte"] ?: "0").toLong() / 125000} Mbps"
            }

            // Clientes ARP + Simple Queue con nombres
            val arp = api.query("/ip/arp/print").execute()
            val cola = api.query("/queue/simple/print").execute()
            val listaClientes = mutableListOf<Cliente>()
            
            if (arp is Result.Success) {
                arp.data.forEach { a ->
                    val ip = a["address"] ?: ""
                    val mac = a["mac-address"] ?: ""
                    val nombre = a["comment"] ?: ""
                    val interfaz = a["interface"] ?: ""
                    if (ip.isNotBlank() && mac.isNotBlank()) {
                        listaClientes.add(Cliente(ip = ip, mac = mac, nombre = nombre, interfaz = interfaz))
                    }
                }
            }

            _estadoMikrotik.value = MikrotikEstado(
                conectado = true,
                cpu = cpu,
                ram = "$ramPorc%",
                temp = "$temp°C",
                subida = subida,
                bajada = bajada,
                clientes = listaClientes,
                mensaje = "✅ Actualizado"
            )
        }
    } catch (e: Exception) {
        _estadoMikrotik.value = _estadoMikrotik.value.copy(mensaje = "⚠️ Error: ${e.message}")
    }
}

// ============= GESTOR DE TICKETS =============
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
                if (datos.size >= 12) lista.add(Ticket(
                    codigo = datos[0], monto = datos[1].toFloatOrNull() ?: 0f,
                    minutos = datos[2].toIntOrNull() ?: 0, tiempoStr = datos[3],
                    fecha = datos[4], estado = datos[5], tiempoRestanteSeg = datos[6].toIntOrNull() ?: 0,
                    velocidadSubida = datos[7], velocidadBajada = datos[8],
                    ipUsuario = datos[9], macUsuario = datos[10], fotoBase64 = datos[11]
                ))
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

fun base64ABitmap(base64: String): Bitmap? = try {
    BitmapFactory.decodeByteArray(Base64.decode(base64, Base64.DEFAULT), 0, Base64.decode(base64, Base64.DEFAULT).size)
} catch (e: Exception) { null }

// ============= FIREBASE =============
fun escucharHistorialFirebase() {
    listaTickets.addAll(gestorTickets.cargar())
    println("✅ Firebase escuchando /historial — ${listaTickets.size} tickets cargados")
    db.child("historial").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            println("📡 Datos recibidos: ${snapshot.childrenCount}")
            for (nodo in snapshot.children) {
                val codigo = nodo.child("codigo").getValue(String::class.java) ?: ""
                val monto = nodo.child("monto").getValue(Double::class.java) ?: 0.0
                val tiempoMin = nodo.child("tiempo_minutos").getValue(Int::class.java) ?: 0
                val fecha = nodo.child("fecha").getValue(String::class.java) ?: ""
                val foto = nodo.child("fotoBase64").getValue(String::class.java) ?: ""
                val leidoPortal = nodo.child("leido_por_portal").getValue(Boolean::class.java) ?: false
                val leidoMonedero = nodo.child("leido_por_monedero").getValue(Boolean::class.java) ?: false
                val leidoTicket = nodo.child("leido_por_ticket").getValue(Boolean::class.java) ?: false

                if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                if (monto <= 0 && tiempoMin <= 0) continue

                if (leidoPortal) {
                    val idx = listaTickets.indexOfFirst { it.codigo == codigo }
                    if (idx >= 0 && listaTickets[idx].estado == "CREADO") {
                        listaTickets[idx] = listaTickets[idx].copy(estado = "ACTIVO")
                        gestorTickets.guardar(listaTickets)
                    }
                }

                if (!leidoTicket && leidoMonedero) nodo.ref.child("leido_por_ticket").setValue(true)
                if (leidoTicket && leidoMonedero && leidoPortal) { nodo.ref.removeValue(); continue }

                if (listaTickets.none { it.codigo == codigo }) {
                    val mins = if (tiempoMin > 0) tiempoMin else (monto * 100).toInt()
                    val h = mins / 60
                    val m = mins % 60
                    listaTickets.add(0, Ticket(
                        codigo = codigo, monto = monto.toFloat(), minutos = mins,
                        tiempoStr = if (h > 0) "${h}h ${m}m" else "${mins}m", fecha = fecha,
                        estado = "CREADO", tiempoRestanteSeg = mins * 60, fotoBase64 = foto
                    ))
                    gestorTickets.guardar(listaTickets)
                    println("✅ Ticket nuevo: $codigo")
                }
            }
        }
        override fun onCancelled(error: DatabaseError) = println("❌ Firebase: ${error.message}")
    })
}

fun formatearTiempo(seg: Int) = "%02d:%02d:%02d".format(seg/3600, seg%3600/60, seg%60)

// ============= QR =============
fun generarQR(texto: String, tam: Int = 300): Bitmap {
    val mat = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tam, tam)
    return Bitmap.createBitmap(tam, tam, Bitmap.Config.RGB_565).apply {
        for (x in 0 until tam) for (y in 0 until tam)
            setPixel(x, y, if (mat[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
}

data class Ticket(
    val codigo: String = "", val monto: Float = 0f, val minutos: Int = 0,
    val tiempoStr: String = "", val fecha: String = "", val estado: String = "CREADO",
    val tiempoRestanteSeg: Int = 0, val velocidadSubida: String = "— Mbps",
    val velocidadBajada: String = "— Mbps", val ipUsuario: String = "Sin asignar",
    val macUsuario: String = "Sin asignar", val fotoBase64: String = ""
)

// ============= VENTANA CONFIG =============
@Composable
fun VentanaConfig(routerId: Int, nombre: String, onCerrar: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cfg = remember { configMikrotik.cargar(routerId) }
    var ip by remember { mutableStateOf(cfg.ip) }
    var puerto by remember { mutableStateOf(cfg.puerto) }
    var usuario by remember { mutableStateOf(cfg.usuario) }
    var clave by remember { mutableStateOf(cfg.clave) }
    var dns by remember { mutableStateOf(cfg.dns) }
    var msg by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text("⚙️ $nombre", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(ip, { ip = it }, label = { Text("IP MikroTik") }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("172.16.1.1") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(puerto, { puerto = it }, label = { Text("Puerto API") }, Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña") }, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(dns, { dns = it }, label = { Text("DNS") }, Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(20.dp))
            msg?.let { Text(it, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (ip.isBlank()) { msg = "❌ IP obligatoria"; return@Button }
                    val testCfg = MikrotikConfig.Config(ip, puerto, usuario, clave, dns)
                    conectarMikrotik(testCfg)
                    msg = "🔄 Conectando..."
                }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🧪 PROBAR") }
                Button(onClick = {
                    if (ip.isBlank()) { msg = "❌ IP obligatoria"; return@Button }
                    configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, puerto, usuario, clave, dns))
                    conectarMikrotik(MikrotikConfig.Config(ip, puerto, usuario, clave, dns))
                    msg = "✅ Guardado y conectando..."
                    Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("💾 GUARDAR") }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCerrar, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("CERRAR") }
        }
    }
}

// ============= PANTALLA PRINCIPAL =============
@Composable
fun PantallaPrincipal() {
    var routerSel by remember { mutableStateOf(1) }
    var abrirCfg by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    val estado by estadoMikrotik.collectAsState()
    val cCreados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val cActivos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val cVencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    LaunchedEffect(Unit) {
        escucharHistorialFirebase()
        val cfg = configMikrotik.cargar(1)
        if (cfg.ip.isNotBlank()) conectarMikrotik(cfg)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            listaTickets.forEachIndexed { i, t ->
                if (t.estado == "ACTIVO") {
                    listaTickets[i] = if (t.tiempoRestanteSeg - 1 <= 0) t.copy(estado = "VENCIDO", tiempoRestanteSeg = 0)
                    else t.copy(tiempoRestanteSeg = t.tiempoRestanteSeg - 1)
                }
            }
        }
    }

    if (abrirCfg) Dialog(onDismissRequest = { abrirCfg = false }) { VentanaConfig(1, "RB750Gr3") { abrirCfg = false } }
    if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { TicketsCreados { abrirCreados = false } }
    if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { TicketsActivos(estado) { abrirActivos = false } }
    if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { TicketsVencidos { abrirVencidos = false } }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎟️ PAOYHAN TICKETS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical = 16.dp))

        // Tarjeta Router
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(if (estado.conectado) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("📡 RB750Gr3", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(estado.mensaje, color = if (estado.conectado) Color(0xFF22C55E) else Color(0xFFF59E0B))
                }
                IconButton(onClick = { abrirCfg = true }) { Icon(Icons.Default.Settings, null, Modifier.size(32.dp), Color(0xFF6366F1)) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Datos en tiempo real
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("📊 ESTADO EN TIEMPO REAL", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📤 Subida", color = Color.Gray); Text(estado.subida, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📥 Bajada", color = Color.Gray); Text(estado.bajada, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💻 CPU", color = Color.Gray); Text("${estado.cpu}%", fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💾 RAM", color = Color.Gray); Text(estado.ram, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🌡️ Temp", color = Color.Gray); Text(estado.temp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Clientes conectados
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("💻 CLIENTES CONECTADOS (${estado.clientes.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (estado.clientes.isEmpty()) Text("Conecta el router para ver clientes", color = Color.Gray)
                else Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 200.dp)) {
                    estado.clientes.forEach { c ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                if (c.nombre.isNotBlank()) Text("👤 ${c.nombre}", fontWeight = FontWeight.Bold)
                                Text("🌐 ${c.ip} | 📶 ${c.mac}")
                                Text("🔌 ${c.interfaz}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Botones Tickets
        Button(onClick = { abrirCreados = true }, Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) {
            Text("📋 TICKETS CREADOS ($cCreados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { abrirActivos = true }, Modifier.weight(1f).height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) {
                Text("🟢 ACTIVOS ($cActivos)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { abrirVencidos = true }, Modifier.weight(1f).height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))) {
                Text("🔴 VENCIDOS ($cVencidos)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============= VENTANAS DE TICKETS =============
@Composable
fun TicketsCreados(onCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtro by remember(buscar, listaTickets.size) {
        derivedStateOf { listaTickets.filter { it.estado == "CREADO" && (buscar.isBlank() || it.codigo.contains(buscar, true)) } }
    }
    Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp).height(550.dp)) {
            Text("📋 TICKETS CREADOS (${filtro.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(buscar, { buscar = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar código") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
                if (filtro.isEmpty()) Text("📭 Sin tickets", color = Color.Gray, Modifier.padding(16.dp))
                else filtro.forEach { t ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("🆔 ${t.codigo}", fontWeight = FontWeight.Bold)
                                Text("💰 S/ %.2f".format(t.monto), color = Color(0xFF22C55E))
                                Text("⏱️ ${t.tiempoStr}", color = Color.Gray)
                                Text("📅 ${t.fecha}", fontSize = 12.sp, color = Color.Gray)
                            }
                            var verQR by remember { mutableStateOf(false) }
                            Button(onClick = { verQR = true }, Modifier.height(36.dp)) { Text("QR", fontSize = 14.sp) }
                            if (verQR) Dialog(onDismissRequest = { verQR = false }) {
                                Card(Modifier.padding(20.dp), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CÓDIGO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(16.dp))
                                        Image(remember { generarQR("COD:${t.codigo}|MONTO:${t.monto}|MIN:${t.minutos}") }.asImageBitmap(), null, Modifier.size(250.dp))
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = { verQR = false }, Modifier.fillMaxWidth()) { Text("CERRAR") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCerrar, Modifier.fillMaxWidth()) { Text("CERRAR") }
        }
    }
}

@Composable
fun TicketsActivos(estado: MikrotikEstado, onCerrar: () -> Unit) {
    val activos by remember { derivedStateOf { listaTickets.filter { it.estado == "ACTIVO" } } }
    Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp).height(520.dp)) {
            Text("🟢 ACTIVOS (${activos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (activos.isEmpty()) Text("📭 Sin tickets activos", color = Color.Gray, Modifier.padding(16.dp))
                else activos.forEach { t ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                        Column(Modifier.padding(14.dp)) {
                            Text("🆔 ${t.codigo} | S/ %.2f".format(t.monto), fontWeight = FontWeight.Bold)
                            Text("⏱️ ${formatearTiempo(t.tiempoRestanteSeg)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (t.tiempoRestanteSeg < 300) Color.Red else Color(0xFF22C55E))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCerrar, Modifier.fillMaxWidth()) { Text("CERRAR") }
        }
    }
}

@Composable
fun TicketsVencidos(onCerrar: () -> Unit) {
    val vencidos by remember { derivedStateOf { listaTickets.filter { it.estado == "VENCIDO" } } }
    Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp).height(500.dp)) {
            Text("🔴 VENCIDOS (${vencidos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vencidos.isEmpty()) Text("📭 Sin vencidos", color = Color.Gray, Modifier.padding(16.dp))
                else vencidos.forEach { t ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("🆔 ${t.codigo}", fontWeight = FontWeight.Bold); Text("⏱️ ${t.tiempoStr}") }
                            Button(onClick = { listaTickets.removeAll { it.codigo == t.codigo }; gestorTickets.guardar(listaTickets) }, colors = ButtonDefaults.buttonColors(Color(0xFFEF4444)) ) { Text("BORRAR") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCerrar, Modifier.fillMaxWidth()) { Text("CERRAR") }
        }
    }
}
