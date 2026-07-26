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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent {
            PantallaPrincipal()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trabajoReloj?.cancel()
    }

    companion object {
        lateinit var configMikrotik: MikrotikConfig
        lateinit var gestorTickets: TicketManager
        var trabajoReloj: Job? = null
        val db = FirebaseDatabase.getInstance().reference
        val listaTickets = mutableStateListOf<Ticket>()
    }
}

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
                            nombreUsuario = datos[10]
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
                escritor.write("${t.codigo}|${t.monto}|${t.minutos}|${t.tiempoStr}|${t.fecha}|${t.estado}|${t.tiempoRestanteSeg}|${t.velocidadSubida}|${t.velocidadBajada}|${t.ipUsuario}|${t.nombreUsuario}")
                escritor.newLine()
            }
            escritor.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ============= ESCUCHA FIREBASE =============
fun escucharHistorialFirebase() {
    MainActivity.listaTickets.addAll(MainActivity.gestorTickets.cargar())
    println("✅ Cargados ${MainActivity.listaTickets.size} tickets guardados")

    val ref = MainActivity.db.child("historial")
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (hijo in snapshot.children) {
                for (ticketNodo in hijo.children) {
                    val codigo = ticketNodo.child("codigo").getValue(String::class.java) ?: ""
                    val monto = ticketNodo.child("monto").getValue(Double::class.java) ?: 0.0
                    val fecha = ticketNodo.child("fecha").getValue(String::class.java) ?: ""
                    val leidoPorTicket = ticketNodo.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                    val leidoPorMonedero = ticketNodo.child("leido_por_monedero").getValue(Boolean::class.java) ?: false
                    val leidoPorPortal = ticketNodo.child("leido_por_portal").getValue(Boolean::class.java) ?: false

                    if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                    if (monto <= 0.0) continue

                    if (!leidoPorTicket) {
                        ticketNodo.ref.child("leido_por_ticket").setValue(true)
                    }

                    if (MainActivity.listaTickets.none { it.codigo == codigo }) {
                        val minutos = (monto * 100).toInt()
                        val horas = minutos / 60
                        val mins = minutos % 60
                        val tiempoStr = if (horas > 0) "${horas}h ${mins}m" else "${mins}m"

                        MainActivity.listaTickets.add(0, Ticket(
                            codigo = codigo,
                            monto = monto.toFloat(),
                            minutos = minutos,
                            tiempoStr = tiempoStr,
                            fecha = fecha,
                            estado = "CREADO",
                            tiempoRestanteSeg = minutos * 60
                        ))
                        MainActivity.gestorTickets.guardar(MainActivity.listaTickets)
                    }

                    if (leidoPorTicket && leidoPorMonedero && leidoPorPortal) {
                        ticketNodo.ref.removeValue()
                    }
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
    val config = remember { MainActivity.configMikrotik.cargar(routerId) }

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
                    MainActivity.configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, puerto, usuario, clave, dns))
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
    val config = remember { MainActivity.configMikrotik.cargar(routerId) }
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

// ============= PANTALLA PRINCIPAL =============
@Composable
fun PantallaPrincipal() {
    var routerSeleccionado by remember { mutableStateOf(1) }
    var abrirConfig1 by remember { mutableStateOf(false) }
    var abrirConfig2 by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirPausados by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var abrirHistorial by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { escucharHistorialFirebase() }
    val cCreados by remember { derivedStateOf { MainActivity.listaTickets.count { it.estado == "CREADO" } } }
    val cActivos by remember { derivedStateOf { MainActivity.listaTickets.count { it.estado == "ACTIVO" } } }
    val cPausados by remember { derivedStateOf { MainActivity.listaTickets.count { it.estado == "PAUSADO" } } }
    val cVencidos by remember { derivedStateOf { MainActivity.listaTickets.count { it.estado == "VENCIDO" } } }

    // CONTADOR DE TIEMPO OPTIMIZADO
    LaunchedEffect(Unit) {
        MainActivity.trabajoReloj = launch {
            while (isActive) {
                delay(1000)
                var huboCambios = false

                MainActivity.listaTickets.forEachIndexed { indice, ticket ->
                    if (ticket.estado == "ACTIVO") {
                        val nuevoTiempo = ticket.tiempoRestanteSeg - 1
                        MainActivity.listaTickets[indice] = if (nuevoTiempo <= 0) {
                            ticket.copy(estado = "VENCIDO", tiempoRestanteSeg = 0)
                        } else {
                            ticket.copy(tiempoRestanteSeg = nuevoTiempo)
                        }
                        huboCambios = true
                    }
                }

                if (huboCambios) {
                    MainActivity.gestorTickets.guardar(MainActivity.listaTickets)
                }
            }
        }
    }

    if (abrirConfig1) Dialog(onDismissRequest = { abrirConfig1 = false }) { VentanaConfigMikrotik(1, "ROUTER #1") { abrirConfig1 = false } }
    if (abrirConfig2) Dialog(onDismissRequest = { abrirConfig2 = false }) { VentanaConfigMikrotik(2, "ROUTER #2") { abrirConfig2 = false } }
    if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { TicketsCreadosVentana { abrirCreados = false } }
    if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { TicketsActivosVentana { abrirActivos = false } }
    if (abrirPausados) Dialog(onDismissRequest = { abrirPausados = false }) { TicketsPausadosVentana { abrirPausados = false } }
    if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { TicketsVencidosVentana { abrirVencidos = false } }
    if (abrirHistorial) Dialog(onDismissRequest = { abrirHistorial = false }) { HistorialVentana { abrirHistorial = false } }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎟️ PAOYHAN TICKETS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TarjetaRouter("📡 Router #1", "RB750Gr3", 1, routerSeleccionado == 1, { routerSeleccionado = 1 }, { abrirConfig1 = true })
            TarjetaRouter("📡 Router #2", "RB3011", 2, routerSeleccionado == 2, { routerSeleccionado = 2 }, { abrirConfig2 = true })
        }

        Spacer(modifier = Modifier.height(20.dp))
        val configActual = remember(routerSeleccionado) { MainActivity.configMikrotik.cargar(routerSeleccionado) }
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
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { abrirCreados = true }, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp)) {
            Text("📋 TICKETS CREADOS ($cCreados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana("🟢 ACTIVOS ($cActivos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos = true }
            BotonPestana("🟡 PAUSADOS ($cPausados)", Color(0xFFF59E0B), Modifier.weight(1f)) { abrirPausados = true }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana("🔴 VENCIDOS ($cVencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos = true }
            BotonPestana("📋 HISTORIAL (${MainActivity.listaTickets.size})", Color(0xFF6366F1), Modifier.weight(1f)) { abrirHistorial = true }
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
    val nombreUsuario: String = "Sin asignar"
)

// ============= VENTANAS DE TICKETS =============
@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtro = remember(buscar, MainActivity.listaTickets.size) {
        MainActivity.listaTickets.filter { it.estado == "CREADO" && (buscar.isBlank() || it.codigo.contains(buscar, true)) }
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
                            }
                            var verQR by remember { mutableStateOf(false) }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = {
                                    val idx = MainActivity.listaTickets.indexOfFirst { it.codigo == t.codigo }
                                    if (idx >= 0) {
                                        MainActivity.listaTickets[idx] = t.copy(estado = "ACTIVO", tiempoRestanteSeg = t.minutos * 60)
                                        MainActivity.gestorTickets.guardar(MainActivity.listaTickets)
                                    }
                                }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("ACTIVAR", fontSize = 12.sp) }
                                Button(onClick = { verQR = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("QR", fontSize = 12.sp) }
                                Button(onClick = {
                                    MainActivity.listaTickets.removeAll { it.codigo == t.codigo }
                                    MainActivity.gestorTickets.guardar(MainActivity.listaTickets)
                                }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))) { Text("BORRAR", fontSize = 12.sp) }
                            }
                            if (verQR) Dialog(onDismissRequest = { verQR = false }) {
                                Card(modifier = Modifier.padding(20.dp), shape = RoundedCornerShape(16.dp)) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CÓDIGO DE ACTIVACIÓN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Image(remember { generarCodigoQR("COD:${t.codigo}|MONTO:${t.monto}|MIN:${t.minutos}") }.asImageBitmap(), null, modifier = Modifier.size(250.dp).border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp)))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Código: ${t.codigo}\nS/ %.2f - ${t.tiempoStr}".format(t.monto), fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { verQR = false }, modifier = Modifier.fillMaxWidth()) { Text("CERRAR") }
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
    val activos = remember(MainActivity.listaTickets.size) { MainActivity.listaTickets.filter { it.estado == "ACTIVO" } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(520.dp)) {
            Text("🟢 TICKETS ACTIVOS (${activos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (activos.isEmpty()) Text("📭 Sin tickets activos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else activos.forEach { t ->
                    val actual = MainActivity.listaTickets.find { it.codigo == t.codigo } ?: t
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("🆔 ${actual.codigo} | S/ %.2f".format(actual.monto), fontWeight = FontWeight.Bold)
                            Text("⏱️ Tiempo restante: ${formatearTiempo(actual.tiempoRestanteSeg)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (actual.tiempoRestanteSeg < 300) Color.Red else Color(0xFF22C55E))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Text("📤 ${actual.velocidadSubida}")
                                Text("📥 ${actual.velocidadBajada}")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val idx = MainActivity.listaTickets.indexOfFirst { it.codigo == actual.codigo }
                                if (idx >= 0) {
                                    MainActivity.listaTickets[idx] = actual.copy(estado = "PAUSADO")
                                    MainActivity.gestorTickets.guardar(MainActivity.listaTickets)
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFFF59E0B))) { Text("⏸️ PAUSAR") }
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
fun TicketsPausadosVentana(onCerrar: () -> Unit) {
    val pausados = remember(MainActivity.listaTickets.size) { MainActivity.listaTickets.filter { it.estado == "PAUSADO" } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(450.dp)) {
            Text("🟡 TICKETS PAUSADOS (${pausados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (pausados.isEmpty()) Text("📭 Sin tickets pausados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else pausados.forEach { t ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFFFFF8E1))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🆔 ${t.codigo} | S/ %.2f".format(t.monto), fontWeight = FontWeight.Bold)
                            Text("⏱️ ${formatearTiempo(t.tiempoRestanteSeg)}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val idx = MainActivity.listaTickets.indexOfFirst { it.codigo == t.codigo }
                                if (idx >= 0) {
                                    MainActivity.listaTickets[idx] = t.copy(estado = "ACTIVO")
                                    MainActivity.gestorTickets.guardar(MainActivity.listaTickets)
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("▶️ REANUDAR") }
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
    val vencidos = remember(MainActivity.listaTickets.size) { MainActivity.listaTickets.filter { it.estado == "VENCIDO" } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(450.dp)) {
            Text("🔴 TICKETS VENCIDOS (${vencidos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (vencidos.isEmpty()) Text("📭 Sin tickets vencidos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else vencidos.forEach { t ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFFFFEBEE))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🆔 ${t.codigo} | S/ %.2f".format(t.monto), fontWeight = FontWeight.Bold)
                            Text("⏱️ ${formatearTiempo(t.tiempoRestanteSeg)}")
                            Text("🔴 Vencido", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
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
fun HistorialVentana(onCerrar: () -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtro = remember(buscar, MainActivity.listaTickets.size) {
        MainActivity.listaTickets.filter { buscar.isBlank() || it.codigo.contains(buscar, true) || it.nombreUsuario.contains(buscar, true) }
    }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(550.dp)) {
            Text("📋 HISTORIAL COMPLETO (${filtro.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(buscar, { buscar = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar código o usuario") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
                if (filtro.isEmpty()) Text("📭 Sin registros", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else filtro.forEach { t ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("🆔 ${t.codigo}", fontWeight = FontWeight.Bold)
                                Text("💰 S/ %.2f | ⏱️ ${t.tiempoStr}".format(t.monto))
                                Text("📅 ${t.fecha} | Estado: ${t.estado}", fontSize = 12.sp, color = Color.Gray)
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
