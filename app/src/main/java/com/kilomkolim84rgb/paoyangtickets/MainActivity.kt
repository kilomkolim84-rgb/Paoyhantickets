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
// 🔧 CONEXIÓN MIKROTIK — LOGIN COMPATIBLE V6 Y V7
// ==============================================
object MikrotikAPI {
    var ultimoError = ""

    suspend fun probarConexion(ip: String, puerto: Int, usuario: String, clave: String): Boolean {
        ultimoError = ""
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ip, puerto)
                socket.soTimeout = 5000
                val entrada = socket.getInputStream()
                val salida = socket.getOutputStream()

                // ✅ LOGIN DIRECTO — FUNCIONA EN MIKROTIK 7.x (NO USA RETO)
                enviarComando(salida, "/login", "=name=$usuario", "=password=$clave")
                val respuesta = leerRespuesta(entrada)

                socket.close()

                when {
                    respuesta.any { it == "!done" } -> true
                    respuesta.any { it.startsWith("!trap") } -> {
                        ultimoError = respuesta.first { it.startsWith("!trap") }
                        false
                    }
                    else -> {
                        ultimoError = "Respuesta inesperada del router"
                        false
                    }
                }
            } catch (e: Exception) {
                ultimoError = e.message ?: "No se pudo conectar al router"
                false
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

// ============== DATOS ==============
data class ClienteLAN(
    val ip: String,
    val mac: String,
    val velocidadSubida: String,
    val velocidadBajada: String
)

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

// ============== ESCUCHA FIREBASE ==============
fun escucharHistorialFirebase() {
    listaTickets.addAll(gestorTickets.cargar())
    println("✅ Cargados ${listaTickets.size} tickets guardados")

    val ref = db.child("historial")
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (ticketNodo in snapshot.children) {
                val codigo = ticketNodo.child("codigo").getValue(String::class.java) ?: ""
                val monto = ticketNodo.child("monto").getValue(Double::class.java) ?: 0.0
                val tiempoMin = ticketNodo.child("tiempo_minutos").getValue(Int::class.java) ?: 0
                val fecha = ticketNodo.child("fecha").getValue(String::class.java) ?: ""
                val foto = ticketNodo.child("foto_base64").getValue(String::class.java) ?: ""
                val leidoTicket = ticketNodo.child("leido_ticket").getValue(Boolean::class.java) ?: false
                val leidoMonedero = ticketNodo.child("leido_monedero").getValue(Boolean::class.java) ?: false
                val leidoPortal = ticketNodo.child("leido_portal").getValue(Boolean::class.java) ?: false

                if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                if (monto <= 0.0 && tiempoMin <= 0) continue

                if (foto.isNotBlank()) {
                    val idx = listaTickets.indexOfFirst { it.codigo == codigo }
                    if (idx >= 0 && listaTickets[idx].fotoBase64.isBlank()) {
                        listaTickets[idx] = listaTickets[idx].copy(fotoBase64 = foto)
                        gestorTickets.guardar(listaTickets)
                    }
                }

                if (leidoPortal) {
                    val idx = listaTickets.indexOfFirst { it.codigo == codigo }
                    if (idx >= 0 && listaTickets[idx].estado == "CREADO") {
                        listaTickets[idx] = listaTickets[idx].copy(estado = "ACTIVO")
                        gestorTickets.guardar(listaTickets)
                    }
                }

                if (!leidoTicket && leidoMonedero) {
                    ticketNodo.ref.child("leido_ticket").setValue(true)
                }

                if (leidoTicket && leidoMonedero && leidoPortal) {
                    ticketNodo.ref.removeValue()
                    continue
                }

                if (listaTickets.none { it.codigo == codigo }) {
                    val minutos = if (tiempoMin > 0) tiempoMin else (monto * 100).toInt()
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
                        fotoBase64 = foto
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

// ============== VENTANA CONFIGURACIÓN MIKROTIK ==============
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "⚙️ CONFIGURACIÓN\n$nombreRouter",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Mikrotik") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("192.168.88.1") }
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(20.dp))

                mensajeEstado?.let {
                    Text(
                        it,
                        fontSize = 14.sp,
                        color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (ip.isBlank()) {
                                mensajeEstado = "❌ Ingrese la IP"
                                return@Button
                            }
                            probando = true
                            mensajeEstado = "🔄 Conectando..."
                            CoroutineScope(Dispatchers.IO).launch {
                                val ok = MikrotikAPI.probarConexion(ip, 8728, usuario, clave)
                                withContext(Dispatchers.Main) {
                                    mensajeEstado = if (ok) {
                                        "✅ CONECTADO — MikroTik respondió correctamente"
                                    } else {
                                        "❌ ERROR: ${MikrotikAPI.ultimoError}"
                                    }
                                    probando = false
                                }
                            }
                        },
                        enabled = !probando,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text(if (probando) "⏳..." else "🧪 PROBAR", fontSize = 15.sp)
                    }

                    Button(
                        onClick = {
                            if (ip.isBlank()) {
                                mensajeEstado = "❌ IP obligatoria"
                                return@Button
                            }
                            configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, "8728", usuario, clave, dns))
                            mensajeEstado = "✅ Guardado"
                            Toast.makeText(contexto, "Configuración guardada", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E)
                        )
                    ) {
                        Text("💾 GUARDAR", fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCerrar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF818CF8)
                    )
                ) {
                    Text("CERRAR", fontSize = 15.sp)
                }
            }
        }
    }
}

// ============== TARJETA ROUTER ==============
@Composable
fun TarjetaRouter(nombre: String, modelo: String, routerId: Int, seleccionado: Boolean, alSeleccionar: () -> Unit, alConfigurar: () -> Unit) {
    val config = remember { configMikrotik.cargar(routerId) }
    Card(
        onClick = alSeleccionar,
        modifier = Modifier
            .width(160.dp)
            .height(145.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) Color(0xFFE3F2FD) else Color.White
        ),
        border = if (seleccionado) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Box {
            IconButton(
                onClick = alConfigurar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Configurar", tint = Color(0xFF6366F1))
            }
            Column(
                modifier = Modifier
                    .padding(top = 32.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(modelo, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Text("IP: ${config.ip.ifBlank { "Sin config" }}", fontSize = 11.sp)
                Text("Puerto: 8728", fontSize = 11.sp)
            }
        }
    }
}

// ============== LISTA CLIENTES LAN ==============
@Composable
fun SeccionClientesLAN() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3E5F5)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "💻 CLIENTES LAN — PUERTO 4",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7B1FA2)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (listaClientesLAN.isEmpty()) {
                Text(
                    "📭 Sin clientes conectados",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("IP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.25f))
                    Text("MAC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.35f))
                    Text("Subida", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.2f))
                    Text("Bajada", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.weight(0.2f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                listaClientesLAN.forEach { cliente ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cliente.ip, fontSize = 12.sp, modifier = Modifier.weight(0.25f))
                        Text(cliente.mac, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
                        Text(cliente.velocidadSubida, fontSize = 12.sp, modifier = Modifier.weight(0.2f))
                        Text(cliente.velocidadBajada, fontSize = 12.sp, modifier = Modifier.weight(0.2f))
                    }
                }
            }
        }
    }
}

// ============== BOTÓN PESTAÑA ==============
@Composable
fun BotonPestana(texto: String, colorFondo: Color, modifier: Modifier = Modifier, alPresionar: () -> Unit) {
    Button(
        onClick = alPresionar,
        modifier = modifier.height(55.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colorFondo)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ============== GENERAR QR ==============
fun generarCodigoQR(texto: String, tamano: Int = 300): Bitmap {
    val escritor = QRCodeWriter()
    val matriz = escritor.encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    val bitmap = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565)
    for (x in 0 until tamano) {
        for (y in 0 until tamano) {
            bitmap.setPixel(x, y, if (matriz[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}

// ============== VENTANA TICKETS CREADOS ==============
@Composable
fun VentanaTicketsCreados(onCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtro by remember(buscar, listaTickets.size) {
        derivedStateOf {
            listaTickets.filter { it.estado == "CREADO" && (buscar.isBlank() || it.codigo.contains(buscar, ignoreCase = true)) }
        }
    }

    Dialog(onDismissRequest = onCerrar) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(550.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("📋 TICKETS CREADOS (${filtro.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = buscar,
                    onValueChange = { buscar = it },
                    placeholder = { Text("Buscar código") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (filtro.isEmpty()) {
                        Text("📭 Sin tickets creados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    } else {
                        filtro.forEach { ticket ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("🆔 ${ticket.codigo}", fontWeight = FontWeight.Bold)
                                        Text("💰 S/ ${String.format("%.2f", ticket.monto)}", color = Color(0xFF22C55E))
                                        Text("⏱️ ${ticket.tiempoStr}", color = Color.Gray)
                                        Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                                        if (ticket.fotoBase64.isNotBlank()) {
                                            Text("📸 Foto registrada", fontSize = 12.sp, color = Color(0xFF6366F1))
                                        }
                                    }
                                    var verQR by remember { mutableStateOf(false) }
                                    var verFoto by remember { mutableStateOf(false) }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { verQR = true },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text("VER QR", fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { verFoto = true },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            enabled = ticket.fotoBase64.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                                        ) {
                                            Text(if (ticket.fotoBase64.isNotBlank()) "VER FOTO" else "SIN FOTO", fontSize = 12.sp)
                                        }
                                    }

                                    if (verQR) {
                                        Dialog(onDismissRequest = { verQR = false }) {
                                            Card(
                                                modifier = Modifier.padding(20.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(24.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("CÓDIGO DE ACTIVACIÓN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    val qr = remember(ticket.codigo) {
                                                        generarCodigoQR("COD:${ticket.codigo}|MONTO:${ticket.monto}|TIEMPO:${ticket.tiempoStr}")
                                                    }
                                                    Image(
                                                        bitmap = qr.asImageBitmap(),
                                                        contentDescription = "QR",
                                                        modifier = Modifier.size(250.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text("Código: ${ticket.codigo}\nS/ ${String.format("%.2f", ticket.monto)} — ${ticket.tiempoStr}\nFecha: ${ticket.fecha}")
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Button(
                                                        onClick = { verQR = false },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("CERRAR")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (verFoto && ticket.fotoBase64.isNotBlank()) {
                                        Dialog(onDismissRequest = { verFoto = false }) {
                                            Card(
                                                modifier = Modifier.padding(20.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(24.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("📸 FOTO DEL USUARIO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    val foto = remember(ticket.fotoBase64) { base64ABitmap(ticket.fotoBase64) }
                                                    if (foto != null) {
                                                        Image(
                                                            bitmap = foto.asImageBitmap(),
                                                            contentDescription = "Foto",
                                                            modifier = Modifier.size(300.dp)
                                                        )
                                                    } else {
                                                        Text("No se pudo cargar la imagen", color = Color.Red, fontSize = 15.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Button(
                                                        onClick = { verFoto = false },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("CERRAR")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                    Text("CERRAR")
                }
            }
        }
    }
}

// ============== VENTANA TICKETS ACTIVOS ==============
@Composable
fun VentanaTicketsActivos(onCerrar: () -> Unit) {
    val activos by remember(listaTickets.size) {
        derivedStateOf { listaTickets.filter { it.estado == "ACTIVO" } }
    }

    Dialog(onDismissRequest = onCerrar) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("🟢 TICKETS ACTIVOS (${activos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (activos.isEmpty()) {
                        Text("📭 Sin tickets activos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    } else {
                        activos.forEach { ticket ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("🆔 ${ticket.codigo} | S/ ${String.format("%.2f", ticket.monto)}", fontWeight = FontWeight.Bold)
                                    Text(
                                        "⏱️ Tiempo restante: ${formatearTiempo(ticket.tiempoRestanteSeg)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ticket.tiempoRestanteSeg < 300) Color.Red else Color(0xFF22C55E)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column { Text("📤 Subida", fontSize = 12.sp, color = Color.Gray); Text(ticket.velocidadSubida, fontWeight = FontWeight.Bold) }
                                        Column { Text("📥 Bajada", fontSize = 12.sp, color = Color.Gray); Text(ticket.velocidadBajada, fontWeight = FontWeight.Bold) }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column { Text("🌐 IP", fontSize = 12.sp, color = Color.Gray); Text(ticket.ipUsuario, fontWeight = FontWeight.Bold) }
                                        Column { Text("📶 MAC", fontSize = 12.sp, color = Color.Gray); Text(ticket.macUsuario, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                    Text("CERRAR")
                }
            }
        }
    }
}

// ============== VENTANA TICKETS VENCIDOS ==============
@Composable
fun VentanaTicketsVencidos(onCerrar: () -> Unit) {
    val vencidos by remember(listaTickets.size) {
        derivedStateOf { listaTickets.filter { it.estado == "VENCIDO" } }
    }
    var confirmarLimpiarTodo by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCerrar) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("🔴 TICKETS VENCIDOS (${vencidos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                Spacer(modifier = Modifier.height(12.dp))

                if (vencidos.isNotEmpty()) {
                    Button(
                        onClick = { confirmarLimpiarTodo = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Text("🗑️ LIMPIAR TODA LA LISTA", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (vencidos.isEmpty()) {
                        Text("📭 Sin tickets vencidos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    } else {
                        vencidos.forEach { ticket ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("🆔 ${ticket.codigo} | S/ ${String.format("%.2f", ticket.monto)}", fontWeight = FontWeight.Bold)
                                        Text("⏱️ Tiempo usado: ${ticket.tiempoStr}", fontSize = 13.sp)
                                        Text("🔴 Vencido", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            listaTickets.removeAll { it.codigo == ticket.codigo }
                                            gestorTickets.guardar(listaTickets)
                                        },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                    ) {
                                        Text("BORRAR", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                    Text("CERRAR")
                }
            }
        }
    }

    if (confirmarLimpiarTodo) {
        Dialog(onDismissRequest = { confirmarLimpiarTodo = false }) {
            Card(
                modifier = Modifier.padding(24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠️ CONFIRMAR", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("¿Borrar TODOS los tickets vencidos? Esta acción no se puede deshacer.", fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { confirmarLimpiarTodo = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCELAR")
                        }
                        Button(
                            onClick = {
                                listaTickets.removeAll { it.estado == "VENCIDO" }
                                gestorTickets.guardar(listaTickets)
                                confirmarLimpiarTodo = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("BORRAR TODO")
                        }
                    }
                }
            }
        }
    }
}

// ============== PANTALLA PRINCIPAL ==============
@Composable
fun PantallaPrincipal() {
    var routerSeleccionado by remember { mutableStateOf(2) }
    var abrirConfig1 by remember { mutableStateOf(false) }
    var abrirConfig2 by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var trabajoReloj: Job? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        escucharHistorialFirebase()
        trabajoReloj = launch {
            while (true) {
                delay(1000)
                var cambio = false
                listaTickets.forEachIndexed { i, t ->
                    if (t.estado == "ACTIVO") {
                        val nuevo = t.tiempoRestanteSeg - 1
                        listaTickets[i] = if (nuevo <= 0) {
                            t.copy(estado = "VENCIDO", tiempoRestanteSeg = 0)
                        } else {
                            t.copy(tiempoRestanteSeg = nuevo)
                        }
                        cambio = true
                    }
                }
                if (cambio) gestorTickets.guardar(listaTickets)
            }
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
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🎟️ PAOYHAN TICKETS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TarjetaRouter(
                        "📡 Router #1", "RB750Gr3", 1,
                        routerSeleccionado == 1,
                        alSeleccionar = { routerSeleccionado = 1 },
                        alConfigurar = { abrirConfig1 = true }
                    )
                    TarjetaRouter(
                        "📡 Router #2", "RB3011", 2,
                        routerSeleccionado == 2,
                        alSeleccionar = { routerSeleccionado = 2 },
                        alConfigurar = { abrirConfig2 = true }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                SeccionClientesLAN()
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { abrirCreados = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("📋 TICKETS CREADOS ($creados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BotonPestana("🟢 ACTIVOS ($activos)", Color(0xFF22C55E), Modifier.weight(1f)) {
                        abrirActivos = true
                    }
                    BotonPestana("🔴 VENCIDOS ($vencidos)", Color(0xFFEF4444), Modifier.weight(1f)) {
                        abrirVencidos = true
                    }
                }
            }
        }

        if (abrirConfig1) VentanaConfigRouter(1, "ROUTER #1") { abrirConfig1 = false }
        if (abrirConfig2) VentanaConfigRouter(2, "ROUTER #2") { abrirConfig2 = false }
        if (abrirCreados) VentanaTicketsCreados { abrirCreados = false }
        if (abrirActivos) VentanaTicketsActivos { abrirActivos = false }
        if (abrirVencidos) VentanaTicketsVencidos { abrirVencidos = false }
    }
}
