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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext

// ============= CONEXIÓN MIKROTIK — ROUTEROS 7.13 ✅ =============
object MikrotikApi {
    private suspend fun login(socket: Socket, usuario: String, clave: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val out = PrintWriter(socket.getOutputStream().writer(), true)
                val `in` = BufferedReader(socket.getInputStream().reader())

                // PASO 1: Pedir token — OBLIGATORIO en RouterOS 7
                out.println("/login")
                out.println("")
                out.flush()

                var linea: String?
                var token = ""
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re") && linea!!.contains("=ret=")) {
                        token = linea!!.split("=ret=")[1]
                    }
                }

                // PASO 2: Enviar credenciales
                out.println("/login")
                out.println("=name=$usuario")
                out.println("=password=$clave")
                if (token.isNotEmpty()) out.println("=token=$token")
                out.println("")
                out.flush()

                // PASO 3: Verificar
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") return@withContext true
                    if (linea.orEmpty().startsWith("!trap")) return@withContext false
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun testConexion(ip: String, usuario: String, clave: String): String =
        withContext(Dispatchers.IO) {
            if (ip.isBlank() || usuario.isBlank()) return@withContext "❌ Datos incompletos"
            try {
                val socket = Socket(ip, 8728)
                socket.soTimeout = 5000
                if (!login(socket, usuario, clave)) {
                    socket.close()
                    return@withContext "❌ Usuario o contraseña incorrectos"
                }
                socket.close()
                "✅ Conexión exitosa — RouterOS 7.13"
            } catch (e: Exception) {
                when (e) {
                    is SocketTimeoutException -> "❌ Sin respuesta — revisa IP o firewall"
                    else -> "❌ Error: ${e.message}"
                }
            }
        }

    suspend fun leerEstado(ip: String, usuario: String, clave: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val datos = mutableMapOf(
                "subida" to "— Mbps", "bajada" to "— Mbps",
                "cpu" to "— %", "ram" to "— %", "temp" to "— °C"
            )
            try {
                val socket = Socket(ip, 8728)
                socket.soTimeout = 5000
                val out = PrintWriter(socket.getOutputStream().writer(), true)
                val `in` = BufferedReader(socket.getInputStream().reader())

                if (!login(socket, usuario, clave)) {
                    socket.close()
                    return@withContext datos
                }

                // CPU, RAM, Temp
                out.println("/system/resource/print")
                out.println("")
                out.flush()
                var linea: String?
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re")) {
                        val l = linea!!
                        if (l.contains("cpu-load")) datos["cpu"] = l.split("cpu-load=")[1].split(" ")[0] + " %"
                        if (l.contains("free-memory")) {
                            val totalMem = l.split("total-memory=")[1].split(" ")[0].toLongOrNull() ?: 1
                            val freeMem = l.split("free-memory=")[1].split(" ")[0].toLongOrNull() ?: 0
                            datos["ram"] = "${100 - (freeMem * 100 / totalMem)} %"
                        }
                        if (l.contains("cpu-temperature")) datos["temp"] = l.split("cpu-temperature=")[1].split(" ")[0] + " °C"
                    }
                }

                // Tráfico
                out.println("/interface/print")
                out.println("=.proplist=name,rx-byte,tx-byte")
                out.println("")
                out.flush()
                var totalRx = 0L
                var totalTx = 0L
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re")) {
                        val l = linea!!
                        if (l.contains("rx-byte=")) {
                            val rx = Regex("rx-byte=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                            val tx = Regex("tx-byte=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                            totalRx += rx
                            totalTx += tx
                        }
                    }
                }
                datos["bajada"] = "${(totalRx / 125000) / 1000} Mbps"
                datos["subida"] = "${(totalTx / 125000) / 1000} Mbps"
                socket.close()
            } catch (e: Exception) { }
            datos
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent { PantallaPrincipal() }
    }
}

val db = FirebaseDatabase.getInstance().reference
lateinit var configMikrotik: MikrotikConfig
lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()

// ============= CONFIGURACIÓN =============
class MikrotikConfig(context: Context) {
    private val prefs = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)
    data class Config(val ip: String = "", val usuario: String = "admin", val clave: String = "", val dns: String = "")

    fun cargar(id: Int): Config = Config(
        ip = prefs.getString("r${id}_ip", "") ?: "",
        usuario = prefs.getString("r${id}_usuario", "admin") ?: "admin",
        clave = prefs.getString("r${id}_clave", "") ?: "",
        dns = prefs.getString("r${id}_dns", "") ?: ""
    )

    fun guardar(id: Int, cfg: Config) {
        prefs.edit()
            .putString("r${id}_ip", cfg.ip)
            .putString("r${id}_usuario", cfg.usuario)
            .putString("r${id}_clave", cfg.clave)
            .putString("r${id}_dns", cfg.dns)
            .apply()
    }
}

// ============= TICKETS =============
class TicketManager(private val archivo: File) {
    constructor(ctx: Context) : this(File(ctx.filesDir, "tickets.txt"))
    fun cargar(): MutableList<Ticket> = mutableListOf<Ticket>().apply {
        if (!archivo.exists()) return@apply
        archivo.readLines().forEach { line ->
            val d = line.split("|")
            if (d.size >= 6) add(Ticket(d[0], d[1].toFloatOrNull() ?: 0f, d[2].toIntOrNull() ?: 0, d[3], d[4], d[5]))
        }
    }
    fun guardar(lista: List<Ticket>) = archivo.writeText(lista.joinToString("\n") { "${it.codigo}|${it.monto}|${it.minutos}|${it.tiempoStr}|${it.fecha}|${it.estado}" })
}

data class Ticket(
    val codigo: String = "", val monto: Float = 0f, val minutos: Int = 0,
    val tiempoStr: String = "", val fecha: String = "", val estado: String = "CREADO",
    val tiempoRestanteSeg: Int = 0, val fotoBase64: String = ""
)

// ============= FIREBASE =============
fun escucharFirebase() {
    listaTickets.clear()
    db.child("historial").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snap: DataSnapshot) {
            snap.children.forEach { n ->
                val cod = n.child("codigo").getValue(String::class.java) ?: return@forEach
                if (cod.length != 6 || !cod.all { it.isDigit() }) return@forEach
                listaTickets.add(0, Ticket(cod, fecha = n.child("fecha").getValue(String::class.java) ?: ""))
            }
        }
        override fun onCancelled(err: DatabaseError) {}
    })
}

// ============= VENTANA CONFIG — SIN PUERTO ✅ =============
@Composable
fun VentanaConfig(routerId: Int, nombre: String, onCerrar: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cfg = remember { configMikrotik.cargar(routerId) }
    var ip by remember { mutableStateOf(cfg.ip) }
    var user by remember { mutableStateOf(cfg.usuario) }
    var pass by remember { mutableStateOf(cfg.clave) }
    var dns by remember { mutableStateOf(cfg.dns) }
    var msg by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚙️ CONFIGURACIÓN — $nombre", 22.sp, FontWeight.Bold, color = Color(0xFF1565C0))
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(ip, { ip = it }, label = { Text("IP Mikrotik") }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("172.16.1.1") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(dns, { dns = it }, label = { Text("DNS") }, Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(20.dp))

            msg?.let { Text(it, 14.sp, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)) }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    msg = "🔄 Conectando..."
                    kotlinx.coroutines.GlobalScope.launch {
                        val res = MikrotikApi.testConexion(ip, user, pass)
                        withContext(Dispatchers.Main) { msg = res }
                    }
                }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🧪 PROBAR") }

                Button(onClick = {
                    if (ip.isBlank()) { msg = "❌ IP obligatoria"; return@Button }
                    configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, user, pass, dns))
                    msg = "✅ Guardado"
                    Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("💾 GUARDAR") }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCerrar, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) { Text("CERRAR") }
        }
    }
}

// ============= TARJETA ROUTER =============
@Composable
fun TarjetaRouter(nombre: String, modelo: String, id: Int, sel: Boolean, onClick: () -> Unit, onCfg: () -> Unit) {
    val cfg = remember { configMikrotik.cargar(id) }
    Card(onClick = onClick, Modifier.width(160.dp).height(130.dp), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(if (sel) Color(0xFFE3F2FD) else Color.White),
        border = if (sel) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Box {
            IconButton(onClick = onCfg, Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(Icons.Default.Settings, null, tint = Color(0xFF6366F1))
            }
            Column(Modifier.padding(top = 32.dp, start = 12.dp, end = 12.dp), Alignment.CenterHorizontally) {
                Text(nombre, 15.sp, FontWeight.Bold)
                Text(modelo, 12.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Text("IP: ${cfg.ip.ifBlank { "Sin config" }}", 11.sp)
            }
        }
    }
}

// ============= PANTALLA PRINCIPAL =============
@Composable
fun PantallaPrincipal() {
    var routerSel by remember { mutableStateOf(1) }
    var abrirCfg1 by remember { mutableStateOf(false) }
    var abrirCfg2 by remember { mutableStateOf(false) }
    var datos by remember { mutableStateOf(mapOf("subida" to "— Mbps", "bajada" to "— Mbps", "cpu" to "— %", "ram" to "— %", "temp" to "— °C")) }

    LaunchedEffect(Unit) { escucharFirebase() }
    LaunchedEffect(routerSel) {
        while (true) {
            val cfg = configMikrotik.cargar(routerSel)
            if (cfg.ip.isNotBlank()) datos = MikrotikApi.leerEstado(cfg.ip, cfg.usuario, cfg.clave)
            delay(3000)
        }
    }

    if (abrirCfg1) Dialog({ abrirCfg1 = false }) { VentanaConfig(1, "ROUTER #1") { abrirCfg1 = false } }
    if (abrirCfg2) Dialog({ abrirCfg2 = false }) { VentanaConfig(2, "ROUTER #2") { abrirCfg2 = false } }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp), Alignment.CenterHorizontally) {
        Text("🎟️ PAOYAN TICKETS", 28.sp, FontWeight.Bold, color = Color(0xFF2C3E50), Modifier.padding(vertical = 16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TarjetaRouter("📡 Router #1", "RB750Gr3", 1, routerSel == 1, { routerSel = 1 }, { abrirCfg1 = true })
            TarjetaRouter("📡 Router #2", "RB3011", 2, routerSel == 2, { routerSel = 2 }, { abrirCfg2 = true })
        }

        Spacer(Modifier.height(20.dp))
        val cfg = configMikrotik.cargar(routerSel)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
            Column(Modifier.padding(16.dp), Alignment.CenterHorizontally) {
                Text("📊 ESTADO — Router #$routerSel", 16.sp, FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("IP", 12.sp, color = Color.Gray); Text(cfg.ip.ifBlank { "Sin config" }, FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Usuario", 12.sp, color = Color.Gray); Text(cfg.usuario, FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📤 Subida", 12.sp, color = Color.Gray); Text(datos["subida"]!!, FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📥 Bajada", 12.sp, color = Color.Gray); Text(datos["bajada"]!!, FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💻 CPU", 12.sp, color = Color.Gray); Text(datos["cpu"]!!, FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💾 RAM", 12.sp, color = Color.Gray); Text(datos["ram"]!!, FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🌡️ Temp", 12.sp, color = Color.Gray); Text(datos["temp"]!!, FontWeight.Bold) }
                }
            }
        }
    }
}
