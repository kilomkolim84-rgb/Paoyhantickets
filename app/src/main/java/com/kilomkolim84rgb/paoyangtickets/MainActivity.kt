package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
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
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent {
            PantallaPrincipal()
        }
    }
}

val db = FirebaseDatabase.getInstance().reference

// ============= GESTOR DE CONFIGURACIÓN MIKROTIK =============
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

// ============= ESCUCHA FIREBASE INTACTA ✅ =============
fun escucharHistorialFirebase() {
    listaTickets.addAll(gestorTickets.cargar())
    println("✅ Cargados ${listaTickets.size} tickets guardados")

    val ref = db.child("historial")
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (ticketNodo in snapshot.children) {
                val codigo = ticketNodo.child("codigo").getValue(String::class.java) ?: ""
                val monto = ticketNodo.child("monto").getValue(Double::class.java) ?: 0.0
                val fecha = ticketNodo.child("fecha").getValue(String::class.java) ?: ""
                val leidoPorTicket = ticketNodo.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                val leidoPorMonedero = ticketNodo.child("leido_por_monedero").getValue(Boolean::class.java) ?: false
                val leidoPorPortal = ticketNodo.child("leido_por_portal").getValue(Boolean::class.java) ?: false

                if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                if (monto <= 0.0) continue

                // ✅ DETECTA SI EL PORTAL LO USÓ → PASA A ACTIVO
                if (leidoPorPortal) {
                    val idx = listaTickets.indexOfFirst { it.codigo == codigo }
                    if (idx >= 0 && listaTickets[idx].estado == "CREADO") {
                        listaTickets[idx] = listaTickets[idx].copy(estado = "ACTIVO")
                        gestorTickets.guardar(listaTickets)
                    }
                }

                // ✅ MARCA LEÍDO SOLO SI YA LO HIZO EL MONEDERO
                if (!leidoPorTicket && leidoPorMonedero) {
                    ticketNodo.ref.child("leido_por_ticket").setValue(true)
                }

                // ✅ BORRA AUTOMÁTICO SOLO CUANDO LOS 3 SON TRUE
                if (leidoPorTicket && leidoPorMonedero && leidoPorPortal) {
                    ticketNodo.ref.removeValue()
                    continue
                }

                // ✅ AGREGA NUEVO TICKET SI NO EXISTE
                if (listaTickets.none { it.codigo == codigo }) {
                    val minutos = (monto * 100).toInt()
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
                        tiempoRestanteSeg = minutos * 60
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

// ============= VENTANA CONFIGURACIÓN =============
@Composable
fun VentanaConfigMikrotik(routerId: Int, nombreRouter: String, onCerrar: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configMikrotik.cargar(routerId) }

    var ip by remember { mutableStateOf(config.ip) }
    var puerto by remember { mutableStateOf(config.puerto) }
    var usuario by remember { mutableStateOf(config.usuario) }
    var clave by remember { mutableStateOf(config.clave) }
    var dns by remember { mutableStateOf(config.dns) }
    var mensajeEstado by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚙️ CONFIGURACIÓN — $nombreRouter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(ip, { ip = it }, label = { Text("IP Mikrotik") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("192.168.88.1") })
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
                    configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, puerto, usuario, clave, dns))
                    mensajeEstado = "✅ Guardado"
                    Toast.makeText(contexto, "Configuración guardada", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("💾 GUARDAR") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) { Text("CERRAR") }
        }
    }
}

// ============= TARJETA ROUTER =============
@Composable
fun TarjetaRouter(nombre: String, modelo: String, routerId: Int, seleccionado: Boolean, alTocar: () -> Unit, alConfigurar: () -> Unit) {
    val config = remember { configMikrotik.cargar(routerId) }
    Card(onClick = alTocar, modifier = Modifier.width(160.dp).height(145.dp), shape = RoundedCornerShape(12.dp),
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
                Text("Puerto: ${config.puerto}", fontSize = 11.sp)
            }
        }
    }
}

// ============= PANTALLA PRINCIPAL REESTRUCTURADA =============
@Composable
fun PantallaPrincipal() {
    var routerSeleccionado by remember { mutableStateOf(2) } // Por defecto RB3011 que gestiona tickets
    var abrirConfig1 by remember { mutableStateOf(false) }
    var abrirConfig2 by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var trabajoReloj: Job? = null

    LaunchedEffect(Unit) { escucharHistorialFirebase() }
    val cCreados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val cActivos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val cVencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    // ⏱️ RELOJ CONTINUO, NO SE DETIENE
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

                if (huboCambios) {
                    gestorTickets.guardar(listaTickets)
                }
            }
        }
    }

    // DIÁLOGOS
    if (abrirConfig1) Dialog(onDismissRequest = { abrirConfig1 = false }) { VentanaConfigMikrotik(1, "ROUTER #1") { abrirConfig1 = false } }
    if (abrirConfig2) Dialog(onDismissRequest = { abrirConfig2 = false }) { VentanaConfigMikrotik(2, "ROUTER #2") { abrirConfig2 = false } }
    if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { TicketsCreadosVentana { abrirCreados = false } }
    if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { TicketsActivosVentana { abrirActivos = false } }
    if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { TicketsVencidosVentana { abrirVencidos = false } }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎟️ PAOYANG TICKETS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TarjetaRouter("📡 Router #1", "RB750Gr3", 1, routerSeleccionado == 1, { routerSeleccionado = 1 }, { abrirConfig1 = true })
            TarjetaRouter("📡 Router #2", "RB3011", 2, routerSeleccionado == 2, { routerSeleccionado = 2 }, { abrirConfig2 = true })
        }

        Spacer(modifier = Modifier.height(20.dp))
        val configActual = remember(routerSeleccionado) { configMikrotik.cargar(routerSeleccionado) }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📡 CONFIGURACIÓN — Router #$routerSeleccionado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column { Text("IP", fontSize = 12.sp, color = Color.Gray); Text(configActual.ip.ifBlank { "Sin configurar" }, fontWeight = FontWeight.Bold) }
                    Column { Text("Puerto", fontSize = 12.sp, color = Color.Gray); Text(configActual.puerto, fontWeight = FontWeight.Bold) }
                    Column { Text("Usuario", fontSize = 12.sp, color = Color.Gray); Text(configActual.usuario, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("DNS: ${configActual.dns.ifBlank { "Sin configurar" }}", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text("📊 VELOCIDAD Y ESTADO", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column { Text("📤 Subida", fontSize = 12.sp, color = Color.Gray); Text("— Mbps", fontWeight = FontWeight.Bold) }
                    Column { Text("📥 Bajada", fontSize = 12.sp, color = Color.Gray); Text("— Mbps", fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column { Text("💻 CPU", fontSize = 12.sp, color = Color.Gray); Text("— %", fontWeight = FontWeight.Bold) }
                    Column { Text("💾 RAM", fontSize = 12.sp, color = Color.Gray); Text("— %", fontWeight = FontWeight.Bold) }
                    Column { Text("🌡️ Temp", fontSize = 12.sp, color = Color.Gray); Text("— °C", fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { abrirCreados = true }, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) {
            Text("📋 TICKETS CREADOS ($cCreados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana("🟢 ACTIVOS ($cActivos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos = true }
            BotonPestana("🔴 VENCIDOS ($cVencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos = true }
        }
    }
}

@Composable
fun BotonPestana(texto: String, color: Color, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Button(onClick = alTocar, modifier = modifier.height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(color)) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

// ============= VENTANAS AJUSTADAS =============
@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtro = remember(buscar, listaTickets.size) {
        listaTickets.filter { it.estado == "CREADO" && (buscar.isBlank() || it.codigo.contains(buscar, true)) }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(550.dp)) {
            Text("📋 TICKETS CREADOS (${filtro.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = buscar,
                onValueChange = { buscar = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar código") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
                if (filtro.isEmpty()) {
                    Text("📭 Sin tickets creados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    filtro.forEach { t ->
                        var verQR by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 🖼️ FOTO AL COSTADO IZQUIERDO - TAMAÑO GENEROSO
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (t.fotoBase64.isNotBlank()) {
                                        // Cuando el ESP32 envíe la foto en Base64, aquí se agrega la carga real
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Foto usuario",
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.Gray
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.PhotoCamera,
                                            contentDescription = "Sin foto",
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                // 📋 DATOS DEL TICKET - TAMAÑO NORMAL
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "🆔 ${t.codigo}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "💰 S/ %.2f".format(t.monto),
                                        fontSize = 15.sp,
                                        color = Color(0xFF22C55E)
                                    )
                                    Text(
                                        text = "⏱️ ${t.tiempoStr}",
                                        fontSize = 15.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "📅 ${t.fecha}",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }

                                // 🟣 BOTÓN VER QR
                                Button(
                                    onClick = { verQR = true },
                                    modifier = Modifier.height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(Color(0xFF7E57C2))
                                ) {
                                    Text("VER QR", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 📱 VENTANA DEL CÓDIGO QR
                        if (verQR) {
                            Dialog(onDismissRequest = { verQR = false }) {
                                Card(modifier = Modifier.padding(24.dp), shape = RoundedCornerShape(16.dp)) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("CÓDIGO DE ACTIVACIÓN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val horaQR = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                        val contenidoQR = "COD:${t.codigo}|MONTO:${t.monto}|MIN:${t.minutos}|HORA:${horaQR}"

                                        Image(
                                            bitmap = remember { generarCodigoQR(contenidoQR) }.asImageBitmap(),
                                            contentDescription = "Código QR",
                                            modifier = Modifier
                                                .size(260.dp)
                                                .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Código: ${t.codigo}\nS/ %.2f - ${t.tiempoStr}".format(t.monto),
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { verQR = false },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { Text("CERRAR") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("CERRAR", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun TicketsActivosVentana(onCerrar: () -> Unit) {
    val activos = remember(listaTickets.size) { listaTickets.filter { it.estado == "ACTIVO" } }
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
    val vencidos = remember(listaTickets.size) { listaTickets.filter { it.estado == "VENCIDO" } }
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
