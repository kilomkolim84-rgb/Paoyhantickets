package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
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
                if (datos.size >= 7) {
                    lista.add(
                        Ticket(
                            codigo = datos[0],
                            monto = datos[1].toFloatOrNull() ?: 0f,
                            minutos = datos[2].toIntOrNull() ?: 0,
                            tiempoStr = datos[3],
                            fecha = datos[4],
                            estado = datos[5],
                            tiempoRestanteSeg = datos[6].toIntOrNull() ?: 0
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
                escritor.write("${t.codigo}|${t.monto}|${t.minutos}|${t.tiempoStr}|${t.fecha}|${t.estado}|${t.tiempoRestanteSeg}")
                escritor.newLine()
            }
            escritor.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()

// ✅ ESCUCHA FIREBASE Y MARCA COMO LEÍDO
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
                            tiempoRestanteSeg = minutos * 60
                        )
                        listaTickets.add(0, nuevoTicket)
                        gestorTickets.guardar(listaTickets)
                        println("✅ Ticket leído y guardado: $codigo — S/ $monto")
                    }

                    if (leidoPorMonedero) {
                        ticketNodo.ref.removeValue()
                        println("🗑️ Borrado de Firebase: $codigo (leído por ambas apps)")
                    }
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            println("❌ Error Firebase: ${error.message}")
        }
    })
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
    var routerSeleccionado by remember { mutableStateOf(1) }
    var abrirTicketsCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirPausados by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var abrirHistorial by remember { mutableStateOf(false) }

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

    val datosRouter = remember(routerSeleccionado) {
        if (routerSeleccionado == 1) {
            mapOf(
                "nombre" to "📡 Router #1",
                "modelo" to "RB750Gr3",
                "ip" to "192.168.88.1",
                "puerto" to "Balanceador",
                "upload" to "--- MB/s",
                "download" to "--- MB/s",
                "temp" to "--- °C"
            )
        } else {
            mapOf(
                "nombre" to "📡 Router #2",
                "modelo" to "RB3011",
                "ip" to "192.168.88.1",
                "puerto" to "Administración",
                "upload" to "--- MB/s",
                "download" to "--- MB/s",
                "temp" to "--- °C"
            )
        }
    }

    if (abrirTicketsCreados) {
        Dialog(onDismissRequest = { abrirTicketsCreados = false }) {
            TicketsCreadosVentana(onCerrar = { abrirTicketsCreados = false })
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
    if (abrirHistorial) {
        Dialog(onDismissRequest = { abrirHistorial = false }) {
            HistorialVentana(onCerrar = { abrirHistorial = false })
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TarjetaRouter(
                nombre = "📡 Router #1",
                modelo = "RB750Gr3",
                ip = "192.168.88.1",
                puerto = "Balanceador",
                seleccionado = routerSeleccionado == 1,
                alTocar = { routerSeleccionado = 1 }
            )
            TarjetaRouter(
                nombre = "📡 Router #2",
                modelo = "RB3011",
                ip = "192.168.88.1",
                puerto = "Administración",
                seleccionado = routerSeleccionado == 2,
                alTocar = { routerSeleccionado = 2 }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📊 Consumo de Internet — ${datosRouter["nombre"]}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️ UPLOAD", fontSize = 14.sp, color = Color.Gray)
                        Text("${datosRouter["upload"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇️ DOWNLOAD", fontSize = 14.sp, color = Color.Gray)
                        Text("${datosRouter["download"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }
                Text(
                    text = "🌡️ Temperatura: ${datosRouter["temp"]}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { abrirTicketsCreados = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
        ) {
            Text(
                text = "📋 TICKETS CREADOS ($ticketsCreados)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana(
                texto = "🟢 ACTIVOS ($ticketsActivos)",
                color = Color(0xFF22C55E),
                modifier = Modifier.weight(1f),
                alTocar = { abrirActivos = true }
            )
            BotonPestana(
                texto = "🟡 PAUSADOS ($ticketsPausados)",
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                alTocar = { abrirPausados = true }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana(
                texto = "🔴 VENCIDOS ($ticketsVencidos)",
                color = Color(0xFFEF4444),
                modifier = Modifier.weight(1f),
                alTocar = { abrirVencidos = true }
            )
            BotonPestana(
                texto = "📋 HISTORIAL (${listaTickets.size})",
                color = Color(0xFF6366F1),
                modifier = Modifier.weight(1f),
                alTocar = { abrirHistorial = true }
            )
        }
    }
}

@Composable
fun TarjetaRouter(
    nombre: String,
    modelo: String,
    ip: String,
    puerto: String,
    seleccionado: Boolean,
    alTocar: () -> Unit
) {
    Card(
        onClick = alTocar,
        modifier = Modifier
            .width(160.dp)
            .height(130.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) Color(0xFFE3F2FD) else Color(0xFFFFFFFF)
        ),
        border = if (seleccionado) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
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
fun BotonPestana(
    texto: String,
    color: Color,
    modifier: Modifier = Modifier,
    alTocar: () -> Unit
) {
    Button(
        onClick = alTocar,
        modifier = modifier.height(55.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    val tiempoRestanteSeg: Int = 0
)

@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var textoBuscar by remember { mutableStateOf("") }

    val ticketsFiltrados = remember(textoBuscar, listaTickets.size) {
        listaTickets.filter { it.estado == "CREADO" }.let { lista ->
            if (textoBuscar.isBlank()) lista
            else lista.filter { it.codigo.contains(textoBuscar, ignoreCase = true) }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(550.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋 TICKETS CREADOS (${ticketsFiltrados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f)
            ) {
                if (ticketsFiltrados.isEmpty()) {
                    Text("📭 No hay tickets creados aún\nMete monedas en el cajero",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 15.sp)
                } else {
                    ticketsFiltrados.forEach { ticket ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp, color = Color(0xFF22C55E))
                                    Text("⏱️ ${ticket.tiempoStr}", fontSize = 13.sp, color = Color.Gray)
                                    Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                                    Text("⚪ CREADO — Pendiente de activar", fontSize = 12.sp, color = Color(0xFF6366F1))
                                }

                                var mostrarQR by remember { mutableStateOf(false) }

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
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("✅ ACTIVAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { mostrarQR = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("📱 QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            listaTickets.remove(ticket)
                                            gestorTickets.guardar(listaTickets)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("❌ BORRAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (mostrarQR) {
                                    Dialog(onDismissRequest = { mostrarQR = false }) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("📱 ESCANEAR PARA ACTIVAR", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(20.dp))

                                                val qrBitmap = remember(ticket.codigo) { generarCodigoQR("ID:${ticket.codigo}|S:${ticket.monto}|MIN:${ticket.minutos}") }
                                                Image(
                                                    bitmap = qrBitmap.asImageBitmap(),
                                                    contentDescription = "Código QR",
                                                    modifier = Modifier
                                                        .size(280.dp)
                                                        .border(BorderStroke(2.dp, Color(0xFFE0E0E0)), RoundedCornerShape(8.dp))
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                )

                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text("Código: ${ticket.codigo}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                Text("💰 S/ ${String.format("%.2f", ticket.monto)}  •  ⏱️ ${ticket.tiempoStr}", fontSize = 14.sp, color = Color.Gray)

                                                Spacer(modifier = Modifier.height(24.dp))
                                                Button(
                                                    onClick = { mostrarQR = false },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(50.dp),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            
            if (ticketsFiltrados.isNotEmpty()) {
                Button(
                    onClick = {
                        ticketsFiltrados.forEach { listaTickets.remove(it) }
                        gestorTickets.guardar(listaTickets)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("🗑️ BORRAR TODOS LOS CREADOS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                Text("CERRAR", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun TicketsActivosVentana(onCerrar: () -> Unit) {
    val ticketsActivos = remember(listaTickets.size) { listaTickets.filter { it.estado == "ACTIVO" } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(450.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🟢 TICKETS ACTIVOS (${ticketsActivos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (ticketsActivos.isEmpty()) {
                    Text("📭 No hay tickets activos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsActivos.forEach { ticket ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                                    Text("⏱️ ${formatearTiempo(ticket.tiempoRestanteSeg)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (ticket.tiempoRestanteSeg < 300) Color(0xFFEF4444) else Color(0xFF22C55E))
                                }
                                Button(
                                    onClick = {
                                        val index = listaTickets.indexOf(ticket)
                                        if (index >= 0) {
                                            listaTickets[index] = ticket.copy(estado = "PAUSADO")
                                            gestorTickets.guardar(listaTickets)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                                ) {
                                    Text("⏸️ PAUSAR", fontSize = 13.sp)
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

@Composable
fun TicketsPausadosVentana(onCerrar: () -> Unit) {
    val ticketsPausados = remember(listaTickets.size) { listaTickets.filter { it.estado == "PAUSADO" } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(450.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🟡 TICKETS PAUSADOS (${ticketsPausados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (ticketsPausados.isEmpty()) {
                    Text("📭 No hay tickets pausados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsPausados.forEach { ticket ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                                    Text("⏱️ ${formatearTiempo(ticket.tiempoRestanteSeg)}", fontSize = 13.sp)
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

@Composable
fun TicketsVencidosVentana(onCerrar: () -> Unit) {
    val ticketsVencidos = remember(listaTickets.size) { listaTickets.filter { it.estado == "VENCIDO" } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(450.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔴 TICKETS VENCIDOS (${ticketsVencidos.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (ticketsVencidos.isEmpty()) {
                    Text("📭 No hay tickets vencidos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsVencidos.forEach { ticket ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                                    Text("⏱️ ${formatearTiempo(ticket.tiempoRestanteSeg)}", fontSize = 13.sp)
                                    Text("🔴 VENCIDO", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
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

@Composable
fun HistorialVentana(onCerrar: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(500.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋 HISTORIAL COMPLETO (${listaTickets.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f)
            ) {
                if (listaTickets.isEmpty()) {
                    Text("📭 No hay registros aún", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    listaTickets.forEach { ticket ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 ${ticket.codigo}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}  •  ⏱️ ${ticket.tiempoStr}", fontSize = 12.sp, color = Color.Gray)
                                    Text("📅 ${ticket.fecha}", fontSize = 11.sp, color = Color.Gray)
                                }
                                val colorEstado = when (ticket.estado) {
                                    "CREADO" -> Color(0xFF6366F1)
                                    "ACTIVO" -> Color(0xFF22C55E)
                                    "PAUSADO" -> Color(0xFFF59E0B)
                                    "VENCIDO" -> Color(0xFFEF4444)
                                    else -> Color.Gray
                                }
                                Text(
                                    when (ticket.estado) {
                                        "CREADO" -> "⚪ CREADO"
                                        "ACTIVO" -> "🟢 ACTIVO"
                                        "PAUSADO" -> "🟡 PAUSADO"
                                        "VENCIDO" -> "🔴 VENCIDO"
                                        else -> ticket.estado
                                    },
                                    fontSize = 12.sp,
                                    color = colorEstado,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            if (listaTickets.isNotEmpty()) {
                Button(
                    onClick = {
                        listaTickets.clear()
                        gestorTickets.guardar(listaTickets)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("🗑️ BORRAR TODO EL HISTORIAL", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                Text("CERRAR", fontSize = 16.sp)
            }
        }
    }
}
