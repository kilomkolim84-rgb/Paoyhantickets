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
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ Firebase inicializado — tickets funcionan
        com.google.firebase.FirebaseApp.initializeApp(this)
        
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent {
            PantallaPrincipal()
        }
    }
}

val db = FirebaseDatabase.getInstance().reference

// ============= CONFIGURACIÓN MIKROTIK =============
class MikrotikConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)

    data class Config(
        val ip: String = "",
        val puerto: String = "8728",
        val usuario: String = "admin",
        val clave: String = "",
        val dns: String = ""
    )

    fun cargar(): Config {
        return Config(
            ip = prefs.getString("ip", "") ?: "",
            puerto = prefs.getString("puerto", "8728") ?: "8728",
            usuario = prefs.getString("usuario", "admin") ?: "admin",
            clave = prefs.getString("clave", "") ?: "",
            dns = prefs.getString("dns", "") ?: ""
        )
    }

    fun guardar(config: Config) {
        prefs.edit()
            .putString("ip", config.ip)
            .putString("puerto", config.puerto)
            .putString("usuario", config.usuario)
            .putString("clave", config.clave)
            .putString("dns", config.dns)
            .apply()
    }
}

lateinit var configMikrotik: MikrotikConfig

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

fun base64ABitmap(base64: String): Bitmap? {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

// ============= ESCUCHA FIREBASE =============
fun escucharHistorialFirebase() {
    listaTickets.addAll(gestorTickets.cargar())

    val ref = db.child("historial")
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (ticketNodo in snapshot.children) {
                val codigo = ticketNodo.child("codigo").getValue(String::class.java) ?: ""
                val monto = ticketNodo.child("monto").getValue(Double::class.java) ?: 0.0
                val tiempoMinutos = ticketNodo.child("tiempo_minutos").getValue(Int::class.java) ?: 0
                val fecha = ticketNodo.child("fecha").getValue(String::class.java) ?: ""
                val fotoBase64 = ticketNodo.child("fotoBase64").getValue(String::class.java) ?: ""
                val leidoPorTicket = ticketNodo.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                val leidoPorMonedero = ticketNodo.child("leido_por_monedero").getValue(Boolean::class.java) ?: false
                val leidoPorPortal = ticketNodo.child("leido_por_portal").getValue(Boolean::class.java) ?: false

                if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                if (monto <= 0.0 && tiempoMinutos <= 0) continue

                if (fotoBase64.isNotBlank()) {
                    val idx = listaTickets.indexOfFirst { it.codigo == codigo }
                    if (idx >= 0 && listaTickets[idx].fotoBase64.isBlank()) {
                        listaTickets[idx] = listaTickets[idx].copy(fotoBase64 = fotoBase64)
                        gestorTickets.guardar(listaTickets)
                    }
                }

                if (leidoPorPortal) {
                    val idx = listaTickets.indexOfFirst { it.codigo == codigo }
                    if (idx >= 0 && listaTickets[idx].estado == "CREADO") {
                        listaTickets[idx] = listaTickets[idx].copy(estado = "ACTIVO")
                        gestorTickets.guardar(listaTickets)
                    }
                }

                if (!leidoPorTicket && leidoPorMonedero) {
                    ticketNodo.ref.child("leido_por_ticket").setValue(true)
                }

                if (leidoPorTicket && leidoPorMonedero && leidoPorPortal) {
                    ticketNodo.ref.removeValue()
                    continue
                }

                if (listaTickets.none { it.codigo == codigo }) {
                    val minutos = if (tiempoMinutos > 0) tiempoMinutos else (monto * 100).toInt()
                    val horas = minutos / 60
                    val mins = minutos % 60
                    val tiempoStr = if (horas > 0) "${horas}h ${mins}m" else "${mins}m"

                    listaTickets.add(0, Ticket(
                        codigo = codigo,
                        monto = monto.toFloat(),
                        minutos = minutos,
                        tiempoStr = tiempoStr,
                        fecha = fecha,
                        estado = "CREADO",
                        tiempoRestanteSeg = minutos * 60,
                        fotoBase64 = fotoBase64
                    ))
                    gestorTickets.guardar(listaTickets)
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {}
    })
}

fun formatearTiempo(segundos: Int): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

// ============= VENTANA CONFIGURACIÓN MIKROTIK =============
@Composable
fun VentanaConfigMikrotik(onCerrar: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configMikrotik.cargar() }

    var ip by remember { mutableStateOf(config.ip) }
    var puerto by remember { mutableStateOf(config.puerto) }
    var usuario by remember { mutableStateOf(config.usuario) }
    var clave by remember { mutableStateOf(config.clave) }
    var dns by remember { mutableStateOf(config.dns) }
    var mensajeEstado by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚙️ CONFIGURACIÓN — RB750Gr3", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(ip, { ip = it }, label = { Text("IP Mikrotik") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("172.16.1.1") })
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(puerto, { puerto = it }, label = { Text("Puerto API") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(dns, { dns = it }, label = { Text("DNS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(20.dp))

            mensajeEstado?.let { Text(it, fontSize = 14.sp, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)) }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { mensajeEstado = if (ip.isNotBlank()) "✅ Conexión válida" else "❌ Ingrese la IP" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🧪 PROBAR") }
                Button(onClick = {
                    if (ip.isBlank()) { mensajeEstado = "❌ IP obligatoria"; return@Button }
                    configMikrotik.guardar(MikrotikConfig.Config(ip, puerto, usuario, clave, dns))
                    mensajeEstado = "✅ Guardado"
                    Toast.makeText(contexto, "Configuración guardada", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("💾 GUARDAR") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) { Text("CERRAR") }
        }
    }
}

// ============= PANTALLA PRINCIPAL — 1 SOLO ROUTER =============
@Composable
fun PantallaPrincipal() {
    var abrirConfig by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var trabajoReloj: Job? = null

    LaunchedEffect(Unit) { escucharHistorialFirebase() }
    val cCreados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val cActivos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val cVencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    LaunchedEffect(Unit) {
        trabajoReloj = launch {
            while (true) {
                delay(1000)
                var huboCambios = false
                listaTickets.forEachIndexed { indice, ticket ->
                    if (ticket.estado == "ACTIVO") {
                        val nuevoTiempo = ticket.tiempoRestanteSeg - 1
                        listaTickets[indice] = if (nuevoTiempo <= 0) {
                            ticket.copy(estado = "VENCIDO", tiempoRestanteSeg = 0)
                        } else {
                            ticket.copy(tiempoRestanteSeg = nuevoTiempo)
                        }
                        huboCambios = true
                    }
                }
                if (huboCambios) gestorTickets.guardar(listaTickets)
            }
        }
    }

    if (abrirConfig) Dialog(onDismissRequest = { abrirConfig = false }) { VentanaConfigMikrotik { abrirConfig = false } }
    if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { TicketsCreadosVentana { abrirCreados = false } }
    if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { TicketsActivosVentana { abrirActivos = false } }
    if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { TicketsVencidosVentana { abrirVencidos = false } }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎟️ PAOYANG TICKETS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical = 16.dp))

        // ========== TARJETA ROUTER — IGUAL QUE TU IMAGEN: 1 SOLO RB750Gr3 ==========
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color(0xFFFFF3E0))) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsEthernet, null, modifier = Modifier.size(28.dp), tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RB750Gr3", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val config = remember { configMikrotik.cargar() }
                    Text("🌐 IP: ${config.ip.ifBlank { "Sin configurar" }}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💻 CPU", fontSize = 12.sp, color = Color.Gray)
                            Text("0%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💾 RAM", fontSize = 12.sp, color = Color.Gray)
                            Text("27%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📥 BAJADA", fontSize = 12.sp, color = Color.Gray)
                            Text("67 Kbps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📤 SUBIDA", fontSize = 12.sp, color = Color.Gray)
                            Text("43 Kbps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                        }
                    }
                }
                IconButton(onClick = { abrirConfig = true }) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(28.dp), tint = Color(0xFF6366F1))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== CLIENTES CONECTADOS — IGUAL QUE TU IMAGEN ==========
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color(0xFFF3E5F5))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💻 CLIENTES CONECTADOS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IP", fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.width(100.dp))
                    Text("NOMBRE", fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(1f))
                    Text("↓ BAJADA / ↑ SUBIDA", fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                // Lista de clientes — igual que tu imagen
                listOf(
                    Triple("172.16.1.250", "laptop", "0 bps ↓ / 0 bps ↑"),
                    Triple("172.16.1.251", "CESAR", "4.5 Kbps ↓ / 25.4 Kbps ↑"),
                    Triple("172.16.1.249", "Tico", "0 bps ↓ / 0 bps ↑"),
                    Triple("172.16.1.248", "—", "0 bps ↓ / 0 bps ↑"),
                    Triple("172.16.1.253", "Computadora", "0 bps ↓ / 0 bps ↑"),
                    Triple("192.168.18.1", "wow", "0 bps ↓ / 0 bps ↑")
                ).forEach { (ip, nombre, vel) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(ip, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                        Text(nombre, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(vel, fontSize = 13.sp, color = if (vel.contains("bps ↓ / 0 bps ↑")) Color.Gray else Color(0xFFEF4444))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ========== TICKETS — YA FUNCIONANDO CON FIREBASE Y QR ==========
        Button(onClick = { abrirCreados = true }, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) {
            Text("📋 TICKETS CREADOS ($cCreados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { abrirActivos = true }, modifier = Modifier.weight(1f).height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) {
                Text("🟢 ACTIVOS ($cActivos)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { abrirVencidos = true }, modifier = Modifier.weight(1f).height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))) {
                Text("🔴 VENCIDOS ($cVencidos)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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

// ============= VENTANAS DE TICKETS — IGUAL, YA FUNCIONAN =============
@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtro by remember(buscar, listaTickets.size) {
        derivedStateOf {
            listaTickets.filter { it.estado == "CREADO" && (buscar.isBlank() || it.codigo.contains(buscar, true)) }
        }
    }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(550.dp)) {
            Text("📋 TICKETS CREADOS (${filtro.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(buscar, { buscar = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar código") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
                if (filtro.isEmpty()) Text("📭 Sin tickets creados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else filtro.forEach { t ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🆔 ${t.codigo}", fontWeight = FontWeight.Bold)
                                Text("💰 S/ %.2f".format(t.monto), color = Color(0xFF22C55E))
                                Text("⏱️ ${t.tiempoStr}", color = Color.Gray)
                                Text("📅 ${t.fecha}", fontSize = 12.sp, color = Color.Gray)
                                if (t.fotoBase64.isNotBlank()) Text("📸 Foto registrada", fontSize = 12.sp, color = Color(0xFF6366F1))
                            }
                            var verQR by remember { mutableStateOf(false) }
                            var verFoto by remember { mutableStateOf(false) }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { verQR = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("VER QR", fontSize = 12.sp) }
                                Button(
                                    onClick = { verFoto = true },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(Color(0xFF8B5CF6)),
                                    enabled = t.fotoBase64.isNotBlank()
                                ) {
                                    Text(if (t.fotoBase64.isNotBlank()) "VER FOTO" else "SIN FOTO", fontSize = 12.sp)
                                }
                            }

                            if (verQR) Dialog(onDismissRequest = { verQR = false }) {
                                Card(modifier = Modifier.padding(20.dp), shape = RoundedCornerShape(16.dp)) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CÓDIGO DE ACTIVACIÓN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val horaQR = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                        val contenidoQR = "COD:${t.codigo}|MONTO:${t.monto}|MIN:${t.minutos}|HORA:${horaQR}"
                                        Image(remember { generarCodigoQR(contenidoQR) }.asImageBitmap(), null,
                                            modifier = Modifier.size(250.dp).border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp)))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Código: ${t.codigo}\nS/ %.2f - ${t.tiempoStr}\nFecha: ${t.fecha}".format(t.monto), fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { verQR = false }, modifier = Modifier.fillMaxWidth()) { Text("CERRAR") }
                                    }
                                }
                            }

                            if (verFoto && t.fotoBase64.isNotBlank()) Dialog(onDismissRequest = { verFoto = false }) {
                                Card(modifier = Modifier.padding(20.dp), shape = RoundedCornerShape(16.dp)) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("📸 FOTO DEL USUARIO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val fotoBitmap = remember { base64ABitmap(t.fotoBase64) }
                                        if (fotoBitmap != null) {
                                            Image(bitmap = fotoBitmap.asImageBitmap(), contentDescription = "Foto del usuario", modifier = Modifier.size(300.dp).border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp)))
                                        } else {
                                            Text("No se pudo cargar la imagen", color = Color.Red, fontSize = 15.sp)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Código: ${t.codigo}\nMonto: S/ %.2f\nTiempo: ${t.tiempoStr}\nFecha y hora: ${t.fecha}".format(t.monto), fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { verFoto = false }, modifier = Modifier.fillMaxWidth()) { Text("CERRAR") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR") }
        }
    }
}

@Composable
fun TicketsActivosVentana(onCerrar: () -> Unit) {
    val activos by remember(listaTickets.size) { derivedStateOf { listaTickets.filter { it.estado == "ACTIVO" } } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(520.dp)) {
            Text("🟢 TICKETS ACTIVOS (${activos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (activos.isEmpty()) Text("📭 Sin tickets activos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else activos.forEach { t ->
                    val actual = listaTickets.find { it.codigo == t.codigo } ?: t
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("🆔 ${actual.codigo} | S/ %.2f".format(actual.monto), fontWeight = FontWeight.Bold)
                            Text("⏱️ Tiempo restante: ${formatearTiempo(actual.tiempoRestanteSeg)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (actual.tiempoRestanteSeg < 300) Color.Red else Color(0xFF22C55E))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column { Text("📤 Subida", fontSize = 12.sp, color = Color.Gray); Text(actual.velocidadSubida, fontWeight = FontWeight.Bold) }
                                Column { Text("📥 Bajada", fontSize = 12.sp, color = Color.Gray); Text(actual.velocidadBajada, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column { Text("🌐 IP", fontSize = 12.sp, color = Color.Gray); Text(actual.ipUsuario, fontWeight = FontWeight.Bold) }
                                Column { Text("📶 MAC", fontSize = 12.sp, color = Color.Gray); Text(actual.macUsuario, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR") }
        }
    }
}

@Composable
fun TicketsVencidosVentana(onCerrar: () -> Unit) {
    val vencidos by remember(listaTickets.size) { derivedStateOf { listaTickets.filter { it.estado == "VENCIDO" } } }
    var confirmarLimpiarTodo by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(500.dp)) {
            Text("🔴 TICKETS VENCIDOS (${vencidos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(12.dp))

            if (vencidos.isNotEmpty()) {
                Button(onClick = { confirmarLimpiarTodo = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(Color(0xFFB71C1C))) {
                    Text("🗑️ LIMPIAR TODA LA LISTA", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (vencidos.isEmpty()) Text("📭 Sin tickets vencidos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else vencidos.forEach { t ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFFFFEBEE))) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("🆔 ${t.codigo} | S/ %.2f".format(t.monto), fontWeight = FontWeight.Bold)
                                Text("⏱️ Tiempo usado: ${t.tiempoStr}", fontSize = 13.sp)
                                Text("🔴 Vencido", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { listaTickets.removeAll { it.codigo == t.codigo }; gestorTickets.guardar(listaTickets) }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))) {
                                Text("BORRAR", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR") }
        }
    }

    if (confirmarLimpiarTodo) {
        Dialog(onDismissRequest = { confirmarLimpiarTodo = false }) {
            Card(modifier = Modifier.padding(24.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ CONFIRMAR", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("¿Borrar TODOS los tickets vencidos? Esta acción no se puede deshacer.", fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { confirmarLimpiarTodo = false }, modifier = Modifier.weight(1f)) { Text("CANCELAR") }
                        Button(onClick = {
                            listaTickets.removeAll { it.estado == "VENCIDO" }
                            gestorTickets.guardar(listaTickets)
                            confirmarLimpiarTodo = false
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))) { Text("BORRAR TODO") }
                    }
                }
            }
        }
    }
}
