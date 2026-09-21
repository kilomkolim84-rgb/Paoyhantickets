package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import com.google.firebase.database.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent {
            PantallaPrincipal()
        }
    }
}

val db = FirebaseDatabase.getInstance().reference

data class DatosRouter(
    val conectado: Boolean = false,
    val cpu: Int = 0,
    val ram: Int = 0,
    val bajadaEth1: String = "— Kbps",
    val subidaEth1: String = "— Kbps",
    val clientes: List<ClienteLAN> = emptyList(),
    val error: String = ""
)

data class ClienteLAN(
    val ip: String,
    val mac: String,
    val nombre: String = "",
    val velocidadBajada: String = "0 bps",
    val velocidadSubida: String = "0 bps"
)

object MikrotikAPI {
    var ultimoError = ""
    private var ultimaRxEth1: Long = 0
    private var ultimaTxEth1: Long = 0
    private var ultimaMedicionEth1: Long = 0

    private suspend fun hacerPeticion(
        ip: String,
        usuario: String,
        clave: String,
        recurso: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cred = Base64.encodeToString("$usuario:$clave".toByteArray(), Base64.NO_WRAP)
            val url = URL("http://$ip/$recurso")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Basic $cred")
            }
            when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                401 -> { ultimoError = "❌ Usuario o contraseña incorrectos"; null }
                else -> { ultimoError = "❌ Error ${conn.responseCode}"; null }
            }
        } catch (e: Exception) {
            ultimoError = "❌ Sin conexión — revisa IP/WiFi"
            null
        }
    }

    suspend fun probarConexion(ip: String, usuario: String, clave: String): Boolean {
        ultimoError = ""
        val resp = hacerPeticion(ip, usuario, clave, "system/resource")
        return resp != null
    }

    private fun calcularVelocidad(actual: Long, anterior: Long, ms: Long): String {
        if (ms <= 0 || anterior == 0L || actual < anterior) return "— Kbps"
        val bps = (actual - anterior) * 8 * 1000 / ms
        return when {
            bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
            bps >= 1_000 -> "%.0f Kbps".format(bps / 1_000.0)
            else -> "$bps bps"
        }
    }

    suspend fun obtenerTodo(ip: String, usuario: String, clave: String): DatosRouter {
        ultimoError = ""
        return withContext(Dispatchers.IO) {
            val respSys = hacerPeticion(ip, usuario, clave, "system/resource")
            if (respSys == null) return@withContext DatosRouter(conectado = false, error = ultimoError)

            var cpu = 0; var ram = 0
            try {
                val m = parsearWebFig(respSys)
                m["cpu-load"]?.toIntOrNull()?.let { cpu = it }
                val libre = m["free-memory"]?.toLongOrNull() ?: 1
                val total = m["total-memory"]?.toLongOrNull() ?: 1
                ram = ((total - libre) * 100 / total).toInt()
            } catch (e: Exception) {}

            var bajadaEth1 = "— Kbps"
            var subidaEth1 = "— Kbps"
            val respIf = hacerPeticion(ip, usuario, clave, "interface")
            if (respIf != null) {
                val lista = parsearListaWebFig(respIf)
                val eth1 = lista.find { it["name"] == "ether1" }
                if (eth1 != null) {
                    val rx = eth1["rx-byte"]?.toLongOrNull() ?: 0L
                    val tx = eth1["tx-byte"]?.toLongOrNull() ?: 0L
                    val ahora = System.currentTimeMillis()
                    val tiempo = ahora - ultimaMedicionEth1
                    if (ultimaMedicionEth1 > 0 && tiempo > 0) {
                        bajadaEth1 = calcularVelocidad(rx, ultimaRxEth1, tiempo)
                        subidaEth1 = calcularVelocidad(tx, ultimaTxEth1, tiempo)
                    }
                    ultimaRxEth1 = rx
                    ultimaTxEth1 = tx
                    ultimaMedicionEth1 = ahora
                }
            }

            val clientes = mutableListOf<ClienteLAN>()
            val respArp = hacerPeticion(ip, usuario, clave, "ip/arp")
            if (respArp != null) {
                parsearListaWebFig(respArp).forEach { a ->
                    val ipCli = a["address"] ?: return@forEach
                    val mac = a["mac-address"] ?: return@forEach
                    if (ipCli.isNotEmpty() && mac.isNotEmpty()) {
                        clientes.add(ClienteLAN(ipCli, mac, "", "0 bps", "0 bps"))
                    }
                }
            }

            DatosRouter(
                conectado = true,
                cpu = cpu,
                ram = ram,
                bajadaEth1 = bajadaEth1,
                subidaEth1 = subidaEth1,
                clientes = clientes.distinctBy { it.ip }
            )
        }
    }

    private fun parsearWebFig(html: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        Regex("""name="([^"]+)"\s+value="([^"]+)"""").findAll(html).forEach {
            map[it.groupValues[1]] = it.groupValues[2]
        }
        return map
    }

    private fun parsearListaWebFig(html: String): List<Map<String, String>> {
        val lista = mutableListOf<Map<String, String>>()
        val filas = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL).findAll(html).drop(1)
        filas.forEach { fila ->
            val map = mutableMapOf<String, String>()
            val celdas = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL).findAll(fila.value)
            celdas.forEach { celda ->
                val texto = celda.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                val partes = texto.split("=", limit = 2)
                if (partes.size == 2) map[partes[0].trim()] = partes[1].trim()
            }
            if (map.isNotEmpty()) lista.add(map)
        }
        return lista
    }
}

class MikrotikConfig(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("mikrotik_cfg", Context.MODE_PRIVATE)
    data class Config(
        val ip: String = "",
        val usuario: String = "admin",
        val clave: String = "",
        val dns: String = ""
    )
    fun cargar() = Config(
        ip = prefs.getString("ip", "") ?: "",
        usuario = prefs.getString("usuario", "admin") ?: "admin",
        clave = prefs.getString("clave", "") ?: "",
        dns = prefs.getString("dns", "") ?: ""
    )
    fun guardar(cfg: Config) = prefs.edit().apply {
        putString("ip", cfg.ip)
        putString("usuario", cfg.usuario)
        putString("clave", cfg.clave)
        putString("dns", cfg.dns)
    }.apply()
}
lateinit var configMikrotik: MikrotikConfig

data class Ticket(
    val id: String = "",
    val codigo: String = "",
    val minutos: Int = 0,
    val fechaCreacion: String = "",
    val estado: String = "CREADO",
    val tiempoRestante: Int = 0,
    val velocidadBajada: String = "—",
    val velocidadSubida: String = "—",
    val ipUsuario: String = "",
    val macUsuario: String = "",
    val fotoBase64: String = ""
)

class TicketManager(ctx: Context) {
    private val archivo = ctx.filesDir.resolve("tickets_guardados.txt")
    fun cargar(): MutableList<Ticket> = mutableListOf<Ticket>().apply {
        if (!archivo.exists()) return@apply
        archivo.bufferedReader().use { reader ->
            reader.lineSequence().forEach { linea ->
                val datos = linea.split("|")
                if (datos.size >= 10) add(Ticket(
                    id = datos[0], codigo = datos[1], minutos = datos[2].toIntOrNull() ?: 0,
                    fechaCreacion = datos[3], estado = datos[4], tiempoRestante = datos[5].toIntOrNull() ?: 0,
                    velocidadBajada = datos[6], velocidadSubida = datos[7],
                    ipUsuario = datos[8], macUsuario = datos[9],
                    fotoBase64 = datos.getOrNull(10) ?: ""
                ))
            }
        }
    }
    fun guardar(tickets: List<Ticket>) {
        archivo.bufferedWriter().use { w ->
            tickets.forEach { t ->
                w.append("${t.id}|${t.codigo}|${t.minutos}|${t.fechaCreacion}|${t.estado}|${t.tiempoRestante}|${t.velocidadBajada}|${t.velocidadSubida}|${t.ipUsuario}|${t.macUsuario}|${t.fotoBase64}")
                w.newLine()
            }
        }
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

fun escucharTicketsFirebase() {
    db.child("historial").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            listaTickets.clear()
            snapshot.children.forEach nodoBucle@{ nodo ->
                val codigo = nodo.child("codigo").getValue(String::class.java) 
                    ?: return@nodoBucle
                if (codigo.length != 6 || !codigo.all { it.isDigit() }) {
                    return@nodoBucle
                }
                val monto = nodo.child("monto").getValue(Double::class.java) ?: 0.0
                val tiempoMin = nodo.child("tiempo_minutos").getValue(Int::class.java) ?: 0
                val mins = if (tiempoMin > 0) tiempoMin else (monto * 100).toInt()
                listaTickets.add(Ticket(
                    id = nodo.key ?: "",
                    codigo = codigo,
                    minutos = mins,
                    fechaCreacion = "",
                    estado = "CREADO",
                    tiempoRestante = mins * 60
                ))
            }
            gestorTickets.guardar(listaTickets)
        }
        override fun onCancelled(error: DatabaseError) {}
    })
}

@Composable
fun VentanaConfig(onCerrar: () -> Unit, alGuardar: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cfg = remember { configMikrotik.cargar() }
    var ip by remember { mutableStateOf(cfg.ip) }
    var usuario by remember { mutableStateOf(cfg.usuario) }
    var clave by remember { mutableStateOf(cfg.clave) }
    var dns by remember { mutableStateOf(cfg.dns) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCerrar) {
        Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text("⚙️ CONFIGURACIÓN — RB750Gr3", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP del Router") },
                    placeholder = { Text("172.16.201.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text("Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))

                mensaje?.let {
                    Text(it, fontSize = 14.sp, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444))
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (ip.isBlank()) {
                                mensaje = "❌ Escribe la IP"
                                return@Button
                            }
                            probando = true
                            mensaje = "🔄 Conectando..."
                            CoroutineScope(Dispatchers.IO).launch {
                                val ok = MikrotikAPI.probarConexion(ip, usuario, clave)
                                withContext(Dispatchers.Main) {
                                    mensaje = if (ok) "✅ CONECTADO" else MikrotikAPI.ultimoError
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
                                mensaje = "❌ Escribe la IP"
                                return@Button
                            }
                            configMikrotik.guardar(MikrotikConfig.Config(ip, usuario, clave, dns))
                            alGuardar()
                            Toast.makeText(ctx, "✅ Guardado", Toast.LENGTH_SHORT).show()
                            onCerrar()
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color(0xFF818CF8))
                ) {
                    Text("CERRAR")
                }
            }
        }
    }
}

@Composable
fun SeccionClientes(datos: DatosRouter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Color(0xFFF3E5F5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("💻 CLIENTES CONECTADOS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
            Spacer(Modifier.height(12.dp))
            when {
                !datos.conectado -> Text(
                    "⚠️ Configura la IP primero",
                    color = androidx.compose.ui.graphics.Color.Gray,
                    fontSize = 14.sp
                )
                datos.clientes.isEmpty() -> Text(
                    "📭 Sin clientes",
                    color = androidx.compose.ui.graphics.Color.Gray,
                    fontSize = 14.sp
                )
                else -> datos.clientes.forEach { cliente ->
                    Text("• ${cliente.ip} — ${cliente.mac}", fontSize = 13.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun TarjetaTicket(ticket: Ticket) {
    val qr = remember(ticket.codigo) { generarCodigoQR(ticket.codigo) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            when (ticket.estado) {
                "CREADO" -> Color(0xFFE3F2FD)
                "ACTIVO" -> Color(0xFFE8F5E9)
                "VENCIDO" -> Color(0xFFFFEBEE)
                else -> Color(0xFFF5F5F5)
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = "QR",
                modifier = Modifier.size(100.dp).padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("CÓDIGO: ${ticket.codigo}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("⏱️ Tiempo: ${ticket.minutos} min", fontSize = 14.sp)
                Text(
                    when (ticket.estado) {
                        "CREADO" -> "🟡 CREADO"
                        "ACTIVO" -> "🟢 ACTIVO — ${ticket.tiempoRestante/60} min restantes"
                        "VENCIDO" -> "🔴 VENCIDO"
                        else -> ticket.estado
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = when (ticket.estado) {
                        "CREADO" -> Color(0xFFF57C00)
                        "ACTIVO" -> Color(0xFF22C55E)
                        "VENCIDO" -> Color(0xFFEF4444)
                        else -> androidx.compose.ui.graphics.Color.Gray
                    }
                )
            }
        }
    }
}

@Composable
fun VentanaTickets(titulo: String, filtro: String?, onCerrar: () -> Unit) {
    val lista = remember { listaTickets.filter { filtro == null || it.estado == filtro } }
    Dialog(onDismissRequest = onCerrar) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                when {
                    lista.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
                        Text("📭 No hay tickets", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.Gray)
                    }
                    else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        lista.forEach { TarjetaTicket(it) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onCerrar,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))
                ) {
                    Text("CERRAR", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun BotonPestana(texto: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(55.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(color)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PantallaPrincipal() {
    var abrirConfig by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var datosRouter by remember { mutableStateOf(DatosRouter()) }
    var cargando by remember { mutableStateOf(false) }
    var reiniciar by remember { mutableStateOf(false) }

    val cfg = remember(reiniciar) { configMikrotik.cargar() }

    LaunchedEffect(Unit) { escucharTicketsFirebase() }

    val refrescar = suspend {
        if (cfg.ip.isBlank()) return@suspend
        cargando = true
        datosRouter = MikrotikAPI.obtenerTodo(cfg.ip, cfg.usuario, cfg.clave)
        cargando = false
    }

    LaunchedEffect(cfg.ip, reiniciar) {
        while (isActive && cfg.ip.isNotBlank()) {
            refrescar()
            delay(3000)
        }
    }

    val creados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val activos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val vencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🎟️ PAOYHAN TICKETS",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                modifier = Modifier.padding(vertical = 16.dp)
            )

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
                        Text("📡 RB750Gr3", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        IconButton(onClick = { abrirConfig = true }) {
                            Icon(
                                Icons.Default.Settings,
                                "Config",
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (cfg.ip.isBlank()) {
                        Text(
                            "⚠️ Toca el ⚙️ para poner tu IP: 172.16.201.1",
                            fontSize = 15.sp,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    } else if (!datosRouter.conectado) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄 Conectando a ${cfg.ip}...", fontSize = 15.sp, color = Color(0xFFE65100))
                            if (cargando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).padding(start = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    } else {
                        Text("🌐 IP: ${cfg.ip}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💻 CPU", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                                Text("${datosRouter.cpu}%", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💾 RAM", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                                Text("${datosRouter.ram}%", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↓ BAJADA", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                                Text(
                                    datosRouter.bajadaEth1,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF22C55E)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↑ SUBIDA", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                                Text(
                                    datosRouter.subidaEth1,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFFFF6B00)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SeccionClientes(datos = datosRouter)

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { abrirCreados = true },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))
            ) {
                Text("📋 TICKETS CREADOS ($creados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonPestana("🟢 ACTIVOS ($activos)", Color(0xFF22C55E)) { abrirActivos = true }
                BotonPestana("🔴 VENCIDOS ($vencidos)", Color(0xFFEF4444)) { abrirVencidos = true }
            }
            Spacer(Modifier.height(40.dp))
        }

        if (abrirConfig) VentanaConfig(onCerrar = { abrirConfig = false }) { reiniciar = !reiniciar }
        if (abrirCreados) VentanaTickets("📋 TICKETS CREADOS", "CREADO") { abrirCreados = false }
        if (abrirActivos) VentanaTickets("🟢 TICKETS ACTIVOS", "ACTIVO") { abrirActivos = false }
        if (abrirVencidos) VentanaTickets("🔴 TICKETS VENCIDOS", "VENCIDO") { abrirVencidos = false }
    }
}
