package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.io.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PantallaPrincipal()
        }
    }
}

val db = FirebaseDatabase.getInstance().reference

// ✅ GESTOR DE TICKETS LOCALES
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
                if (datos.size >= 9) {
                    lista.add(
                        Ticket(
                            codigo = datos[0],
                            monto = datos[1].toFloatOrNull() ?: 0f,
                            minutos = datos[2].toIntOrNull() ?: 0,
                            tiempoStr = datos[3],
                            fecha = datos[4],
                            estado = datos[5],
                            alias = datos[6],
                            mac = datos[7],
                            ip = datos[8]
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
                escritor.write("${t.codigo}|${t.monto}|${t.minutos}|${t.tiempoStr}|${t.fecha}|${t.estado}|${t.alias}|${t.mac}|${t.ip}")
                escritor.newLine()
            }
            escritor.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()

// ✅ ESCUCHA FIREBASE
fun escucharHistorialFirebase(context: Context) {
    gestorTickets = TicketManager(context)
    listaTickets.addAll(gestorTickets.cargar())
    println("✅ Cargados ${listaTickets.size} tickets guardados")

    val ref = db.child("historial")
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (hijo in snapshot.children) {
                for (ticketNodo in hijo.children) {
                    val codigo = ticketNodo.child("codigo").getValue(String::class.java) ?: ""
                    val monto = ticketNodo.child("monto").getValue(Double::class.java) ?: 0.0
                    val fecha = ticketNodo.child("fecha").getValue(String::class.java) ?: ""
                    val leidoPorTicket = ticketNodo.child("leido_por_ticket").getValue(Boolean::class.java)
                    val leidoPorMonedero = ticketNodo.child("leido_por_monedero").getValue(Boolean::class.java) ?: false

                    if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                    if (monto <= 0.0) continue
                    if (leidoPorTicket == true) continue

                    ticketNodo.ref.child("leido_por_ticket").setValue(true)

                    if (listaTickets.none { it.codigo == codigo }) {
                        val minutos = (monto * 100).toInt()
                        val horas = minutos / 60
                        val mins = minutos % 60
                        val tiempoStr = if (horas > 0) "${horas}h ${mins}m" else "${mins}m"

                        val nuevoTicket = Ticket(
                            codigo = codigo,
                            monto = monto.toFloat(),
                            minutos = minutos,
                            tiempoStr = tiempoStr,
                            fecha = fecha,
                            estado = "CREADO",
                            alias = "",
                            mac = generarMACAleatorio(),
                            ip = generarIPAleatorio(),
                            tiempoRestanteSeg = minutos * 60
                        )
                        listaTickets.add(0, nuevoTicket)
                        gestorTickets.guardar(listaTickets)
                        println("✅ Ticket guardado: $codigo")
                    }

                    if (leidoPorMonedero) {
                        ticketNodo.ref.removeValue()
                    }
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {}
    })
}

// ✅ SIMULACIÓN DE DATOS DE RED
fun generarMACAleatorio(): String {
    val chars = "0123456789ABCDEF"
    return (1..5).joinToString(":") {
        "${chars.random()}${chars.random()}:${chars.random()}${chars.random()}"
    }
}

fun generarIPAleatorio(): String {
    return "192.168.${(1..250).random()}.${(2..250).random()}"
}

// ✅ FORMATEAR TIEMPO
fun formatearTiempo(segundos: Int): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

@Composable
fun PantallaPrincipal() {
    var abrirRouter1 by remember { mutableStateOf(false) }
    var abrirRouter2 by remember { mutableStateOf(false) }
    var abrirListaTickets by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirPausados by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }

    val contexto = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        escucharHistorialFirebase(contexto)
    }

    val ticketsCreados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val ticketsActivos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val ticketsPausados by remember { derivedStateOf { listaTickets.count { it.estado == "PAUSADO" } } }
    val ticketsVencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    // ⏱️ ACTUALIZAR TIEMPO REAL DE TICKETS ACTIVOS
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            var huboCambio = false
            listaTickets.forEachIndexed { index, ticket ->
                if (ticket.estado == "ACTIVO") {
                    val nuevoTiempo = ticket.tiempoRestanteSeg - 1
                    if (nuevoTiempo <= 0) {
                        listaTickets[index] = ticket.copy(
                            estado = "VENCIDO",
                            tiempoRestanteSeg = 0
                        )
                        huboCambio = true
                    } else {
                        listaTickets[index] = ticket.copy(tiempoRestanteSeg = nuevoTiempo)
                        huboCambio = true
                    }
                }
            }
            if (huboCambio) gestorTickets.guardar(listaTickets)
        }
    }

    // ============= VENTANAS DE ROUTERS =============
    if (abrirRouter1) {
        Dialog(onDismissRequest = { abrirRouter1 = false }) {
            VentanaRouter(
                titulo = "📡 ROUTER #1 — BALANCEO",
                ipPredeterminada = "192.168.88.1",
                puertoPredeterminado = "8728",
                usuarioPredeterminado = "admin",
                onCerrar = { abrirRouter1 = false }
            )
        }
    }
    if (abrirRouter2) {
        Dialog(onDismissRequest = { abrirRouter2 = false }) {
            VentanaRouter(
                titulo = "📡 ROUTER #2 — ADMINISTRACIÓN",
                ipPredeterminada = "192.168.88.1",
                puertoPredeterminado = "8728",
                usuarioPredeterminado = "admin",
                onCerrar = { abrirRouter2 = false }
            )
        }
    }

    // ============= VENTANAS DE TICKETS =============
    if (abrirListaTickets) {
        Dialog(onDismissRequest = { abrirListaTickets = false }) {
            ListaTicketsVentana(onCerrar = { abrirListaTickets = false })
        }
    }
    if (abrirActivos) {
        Dialog(onDismissRequest = { abrirActivos = false }) {
            TicketsActivosVentana(onCerrar = { abrirActivos = false })
        }
    }
    if (abrirPausados) {
        Dialog(onDismissRequest = { abrirPausados = false }) {
            TicketsPausadosVentana(onCerrar = { abrirPausados = false })
        }
    }
    if (abrirVencidos) {
        Dialog(onDismissRequest = { abrirVencidos = false }) {
            TicketsVencidosVentana(onCerrar = { abrirVencidos = false })
        }
    }

    // ============= PANTALLA PRINCIPAL =============
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎟️ PAOYANG TICKETS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50),
            modifier = Modifier.padding(bottom = 20.dp, top = 16.dp)
        )

        // ============= TARJETAS DE ROUTERS =============
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TarjetaRouter(
                nombre = "📡 Router #1",
                modelo = "RB750Gr3",
                ip = "192.168.88.1",
                puerto = "Balanceador",
                alTocar = { abrirRouter1 = true }
            )
            TarjetaRouter(
                nombre = "📡 Router #2",
                modelo = "RB3011",
                ip = "192.168.88.1",
                puerto = "Administración",
                alTocar = { abrirRouter2 = true }
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text("=== GESTIÓN DE TICKETS ===", fontSize = 16.sp, color = Color.Gray, fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.height(20.dp))

        // ============= 4 BOTONES CENTRADOS =============
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BotonPrincipal(
                texto = "📋 LISTA DE TICKETS ($ticketsCreados)",
                color = Color(0xFF6366F1),
                alTocar = { abrirListaTickets = true }
            )
            BotonPrincipal(
                texto = "🟢 ACTIVOS ($ticketsActivos)",
                color = Color(0xFF22C55E),
                alTocar = { abrirActivos = true }
            )
            BotonPrincipal(
                texto = "🟡 PAUSADOS ($ticketsPausados)",
                color = Color(0xFFF59E0B),
                alTocar = { abrirPausados = true }
            )
            BotonPrincipal(
                texto = "🔴 VENCIDOS ($ticketsVencidos)",
                color = Color(0xFFEF4444),
                alTocar = { abrirVencidos = true }
            )
        }
    }
}

// ============= VENTANA DE CONEXIÓN AL ROUTER =============
@Composable
fun VentanaRouter(
    titulo: String,
    ipPredeterminada: String,
    puertoPredeterminado: String,
    usuarioPredeterminado: String,
    onCerrar: () -> Unit
) {
    var ip by remember { mutableStateOf(ipPredeterminada) }
    var puerto by remember { mutableStateOf(puertoPredeterminado) }
    var usuario by remember { mutableStateOf(usuarioPredeterminado) }
    var clave by remember { mutableStateOf("") }
    var conectado by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP del Router") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = puerto,
                onValueChange = { puerto = it },
                label = { Text("Puerto API") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text("Usuario / DNI") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    cargando = true
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        delay(1500)
                        cargando = false
                        conectado = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = !cargando
            ) {
                if (cargando) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("🔌 CONECTAR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (conectado) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("✅ CONECTADO — Datos del Router", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.height(16.dp))

                        FilaDato("⬆️ Subida", "45.2 Mbps")
                        FilaDato("⬇️ Bajada", "92.7 Mbps")
                        FilaDato("🌡️ Temperatura", "42 °C")
                        FilaDato("💻 CPU", "28%")
                        FilaDato("🧠 RAM", "45%")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Button(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("CERRAR", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun FilaDato(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, fontSize = 14.sp, color = Color.Gray)
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TarjetaRouter(
    nombre: String,
    modelo: String,
    ip: String,
    puerto: String,
    alTocar: () -> Unit
) {
    Card(
        onClick = alTocar,
        modifier = Modifier.width(160.dp).height(130.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        border = BorderStroke(2.dp, Color(0xFF2563EB))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(modelo, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            Text("IP: $ip", fontSize = 12.sp)
            Text("Puerto: $puerto", fontSize = 12.sp)
        }
    }
}

@Composable
fun BotonPrincipal(texto: String, color: Color, alTocar: () -> Unit) {
    Button(
        onClick = alTocar,
        modifier = Modifier.fillMaxWidth(0.85f).height(65.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

fun generarCodigoQR(texto: String, tamano: Int = 300): Bitmap {
    val escritor = QRCodeWriter()
    val matrizBit = escritor.encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    val ancho = matrizBit.width
    val alto = matrizBit.height
    val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
    for (x in 0 until ancho) {
        for (y in 0 until alto) {
            bitmap.setPixel(x, y, if (matrizBit[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}

data class Ticket(
    val codigo: String = "",
    val monto: Float = 0f,
    val minutos: Int = 0,
    val tiempoStr: String = "",
    val fecha: String = "",
    val estado: String = "CREADO",
    val alias: String = "",
    val mac: String = "",
    val ip: String = "",
    val tiempoRestanteSeg: Int = 0
)

// ============= LISTA DE TICKETS (CREADOS) =============
@Composable
fun ListaTicketsVentana(onCerrar: () -> Unit) {
    var textoBuscar by remember { mutableStateOf("") }

    val ticketsFiltrados = remember(textoBuscar, listaTickets.size) {
        listaTickets.filter { it.estado == "CREADO" }.let { lista ->
            if (textoBuscar.isBlank()) lista
            else lista.filter { it.codigo.contains(textoBuscar, ignoreCase = true) }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(550.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📋 LISTA DE TICKETS (${ticketsFiltrados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = textoBuscar,
                onValueChange = { textoBuscar = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por código...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f)) {
                if (ticketsFiltrados.isEmpty()) {
                    Text("📭 No hay tickets creados aún", color = Color.Gray, modifier = Modifier.padding(16.dp), fontSize = 15.sp)
                } else {
                    ticketsFiltrados.forEach { ticket ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp, color = Color(0xFF22C55E))
                                    Text("⏱️ ${ticket.tiempoStr}", fontSize = 13.sp, color = Color.Gray)
                                    Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val index = listaTickets.indexOf(ticket)
                                            if (index >= 0) {
                                                listaTickets[index] = ticket.copy(
                                                    estado = "ACTIVO",
                                                    tiempoRestanteSeg = ticket.minutos * 60
                                                )
                                                gestorTickets.guardar(listaTickets)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("✅ ACTIVAR", fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                                    Button(
                                        onClick = {
                                            listaTickets.remove(ticket)
                                            gestorTickets.guardar(listaTickets)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("❌ BORRAR", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR", fontSize = 16.sp) }
        }
    }
}

// ============= TICKETS ACTIVOS — TIEMPO REAL CORRIENDO =============
@Composable
fun TicketsActivosVentana(onCerrar: () -> Unit) {
    val ticketsActivos = remember(listaTickets.size) {
        listaTickets.filter { it.estado == "ACTIVO" }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(550.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🟢 TICKETS ACTIVOS (${ticketsActivos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f)
            ) {
                if (ticketsActivos.isEmpty()) {
                    Text("📭 No hay tickets activos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsActivos.forEach { ticket ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 📷 FOTO SIMULADA
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(Color(0xFFDDDDDD), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📷", fontSize = 28.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("👤 ${if (ticket.alias.isNotEmpty()) ticket.alias else "Sin alias"}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("🆔 ${ticket.codigo}", fontSize = 13.sp)
                                        Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp, color = Color(0xFF22C55E))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(10.dp))

                                Text("📡 Datos de conexión", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("🔗 MAC: ${ticket.mac}", fontSize = 13.sp)
                                Text("🌐 IP: ${ticket.ip}", fontSize = 13.sp)

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(10.dp))

                                // ⏱️ TIEMPO CORRIENDO EN TIEMPO REAL
                                Text(
                                    text = "⏱️ TIEMPO RESTANTE: ${formatearTiempo(ticket.tiempoRestanteSeg)}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ticket.tiempoRestanteSeg < 300) Color(0xFFEF4444) else Color(0xFF22C55E)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        val index = listaTickets.indexOf(ticket)
                                        if (index >= 0) {
                                            listaTickets[index] = ticket.copy(estado = "PAUSADO")
                                            gestorTickets.guardar(listaTickets)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                    modifier = Modifier.fillMaxWidth().height(45.dp)
                                ) {
                                    Text("⏸️ PAUSAR TICKET", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                Text("CERRAR", fontSize = 16.sp)
            }
        }
    }
}

// ============= TICKETS PAUSADOS =============
@Composable
fun TicketsPausadosVentana(onCerrar: () -> Unit) {
    val ticketsPausados = remember(listaTickets.size) {
        listaTickets.filter { it.estado == "PAUSADO" }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(450.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🟡 TICKETS PAUSADOS (${ticketsPausados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (ticketsPausados.isEmpty()) {
                    Text("📭 No hay tickets pausados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsPausados.forEach { ticket ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                                    Text("⏱️ ${formatearTiempo(ticket.tiempoRestanteSeg)}", fontSize = 13.sp)
                                    Text("🔗 MAC: ${ticket.mac}", fontSize = 12.sp, color = Color.Gray)
                                    Text("🌐 IP: ${ticket.ip}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Button(
                                    onClick = {
                                        val index = listaTickets.indexOf(ticket)
                                        if (index >= 0) {
                                            listaTickets[index] = ticket.copy(estado = "ACTIVO")
                                            gestorTickets.guardar(listaTickets)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                                ) {
                                    Text("▶️ REANUDAR", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                Text("CERRAR", fontSize = 16.sp)
            }
        }
    }
}

// ============= TICKETS VENCIDOS =============
@Composable
fun TicketsVencidosVentana(onCerrar: () -> Unit) {
    val ticketsVencidos = remember(listaTickets.size) {
        listaTickets.filter { it.estado == "VENCIDO" }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(450.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔴 VENCIDOS (${ticketsVencidos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (ticketsVencidos.isEmpty()) {
                    Text("📭 No hay tickets vencidos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsVencidos.forEach { ticket ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                                    Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                                    Text("🔴 VENCIDO", fontSize = 13.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                Text("CERRAR", fontSize = 16.sp)
            }
        }
    }
}
