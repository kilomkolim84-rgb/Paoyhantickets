package com.kilomkolim84rgb.paoyangtickets

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PantallaPrincipal()
        }
    }
}

@Composable
fun PantallaPrincipal() {
    var routerSeleccionado by remember { mutableStateOf(1) }
    var abrirCrearTicket by remember { mutableStateOf(false) }
    var abrirTicketsCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirPausados by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var abrirHistorial by remember { mutableStateOf(false) }
    var abrirConfigRouter1 by remember { mutableStateOf(false) }
    var abrirConfigRouter2 by remember { mutableStateOf(false) }

    val datosRouter = remember(routerSeleccionado) {
        if (routerSeleccionado == 1) {
            mapOf(
                "nombre" to "📡 Router #1",
                "modelo" to "RB750Gr3",
                "ip" to "192.168.88.1",
                "puerto" to "Balanceador",
                "upload" to "2.4 MB/s",
                "download" to "8.6 MB/s",
                "cpu" to "4%",
                "memoria" to "32%",
                "temp" to "38.2 °C"
            )
        } else {
            mapOf(
                "nombre" to "📡 Router #2",
                "modelo" to "RB3011",
                "ip" to "192.168.88.1",
                "puerto" to "Administración",
                "upload" to "4.8 MB/s",
                "download" to "15.2 MB/s",
                "cpu" to "8%",
                "memoria" to "45%",
                "temp" to "42.5 °C"
            )
        }
    }

    if (abrirConfigRouter1) {
        Dialog(onDismissRequest = { abrirConfigRouter1 = false }) {
            VentanaConfiguracionRouter(
                titulo = "⚙️ Router #1 — RB750Gr3",
                onCerrar = { abrirConfigRouter1 = false }
            )
        }
    }

    if (abrirConfigRouter2) {
        Dialog(onDismissRequest = { abrirConfigRouter2 = false }) {
            VentanaConfiguracionRouter(
                titulo = "⚙️ Router #2 — RB3011",
                onCerrar = { abrirConfigRouter2 = false }
            )
        }
    }

    if (abrirCrearTicket) {
        Dialog(onDismissRequest = { abrirCrearTicket = false }) {
            CrearTicketVentana(onCerrar = { abrirCrearTicket = false })
        }
    }

    if (abrirTicketsCreados) {
        Dialog(onDismissRequest = { abrirTicketsCreados = false }) {
            TicketsCreadosVentana(onCerrar = { abrirTicketsCreados = false })
        }
    }

    if (abrirActivos) {
        Dialog(onDismissRequest = { abrirActivos = false }) {
            ListaTicketsVentana(
                titulo = "🟢 TICKETS ACTIVOS",
                puntoColor = Color(0xFF22C55E),
                tickets = listaActivos,
                onCerrar = { abrirActivos = false }
            )
        }
    }

    if (abrirPausados) {
        Dialog(onDismissRequest = { abrirPausados = false }) {
            ListaTicketsVentana(
                titulo = "🟡 TICKETS PAUSADOS",
                puntoColor = Color(0xFFF59E0B),
                tickets = listaPausados,
                onCerrar = { abrirPausados = false }
            )
        }
    }

    if (abrirVencidos) {
        Dialog(onDismissRequest = { abrirVencidos = false }) {
            ListaTicketsVentana(
                titulo = "🔴 TICKETS VENCIDOS",
                puntoColor = Color(0xFFEF4444),
                tickets = listaVencidos,
                onCerrar = { abrirVencidos = false }
            )
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TarjetaRouter(
                    nombre = "📡 Router #1",
                    modelo = "RB750Gr3",
                    ip = "192.168.88.1",
                    puerto = "Balanceador",
                    seleccionado = routerSeleccionado == 1,
                    alTocar = { routerSeleccionado = 1 }
                )
            }
            IconButton(
                onClick = { abrirConfigRouter1 = true },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFE0E0E0), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TarjetaRouter(
                    nombre = "📡 Router #2",
                    modelo = "RB3011",
                    ip = "192.168.88.1",
                    puerto = "Administración",
                    seleccionado = routerSeleccionado == 2,
                    alTocar = { routerSeleccionado = 2 }
                )
            }
            IconButton(
                onClick = { abrirConfigRouter2 = true },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFE0E0E0), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
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
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💻 CPU", fontSize = 14.sp, color = Color.Gray)
                        Text("${datosRouter["cpu"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧠 MEMORIA", fontSize = 14.sp, color = Color.Gray)
                        Text("${datosRouter["memoria"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF283593))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌡️ TEMP", fontSize = 14.sp, color = Color.Gray)
                        Text("${datosRouter["temp"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { abrirCrearTicket = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text(
                text = "🎫 CREAR NUEVO TICKET",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { abrirTicketsCreados = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
        ) {
            Text(
                text = "📋 TICKETS CREADOS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPestanaVentana(
                    texto = "🟢 ACTIVOS",
                    color = Color(0xFF22C55E),
                    modifier = Modifier.weight(1f)
                ) { abrirActivos = true }
                BotonPestanaVentana(
                    texto = "🟡 PAUSADOS",
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                ) { abrirPausados = true }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPestanaVentana(
                    texto = "🔴 VENCIDOS",
                    color = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f)
                ) { abrirVencidos = true }
                BotonPestanaVentana(
                    texto = "📋 HISTORIAL",
                    color = Color(0xFF6366F1),
                    modifier = Modifier.weight(1f)
                ) { abrirHistorial = true }
            }
        }
    }
}

@Composable
fun VentanaConfiguracionRouter(titulo: String, onCerrar: () -> Unit) {
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var puerto by remember { mutableStateOf("8728") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = onCerrar,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEFEFEF), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF444444))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text("👤 Usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("🔒 Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = puerto,
                onValueChange = { puerto = it },
                label = { Text("🔌 Puerto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { /* Conectar a MikroTik */ },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text("🔗 CONECTAR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SALIR", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BotonPestanaVentana(
    texto: String,
    color: Color,
    modifier: Modifier = Modifier,
    alTocar: () -> Unit
) {
    Button(
        onClick = alTocar,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White)
    }
}

val listaActivos = listOf(
    TicketEstado(
        codigo = "0MXLC6",
        subida = "2.1 MB/s",
        bajada = "7.8 MB/s",
        mac = "AA:BB:CC:DD:EE:01",
        fecha = "16/07/2026",
        hora = "18:50:00",
        foto = true
    ),
    TicketEstado(
        codigo = "LSJBHM",
        subida = "1.5 MB/s",
        bajada = "5.2 MB/s",
        mac = "AA:BB:CC:DD:EE:02",
        fecha = "16/07/2026",
        hora = "18:45:00",
        foto = true
    ),
    TicketEstado(
        codigo = "XYZ789",
        subida = "3.2 MB/s",
        bajada = "9.5 MB/s",
        mac = "AA:BB:CC:DD:EE:07",
        fecha = "16/07/2026",
        hora = "18:30:00",
        foto = true
    )
)

val listaPausados = listOf(
    TicketEstado(
        codigo = "0DUUHT",
        subida = "—",
        bajada = "—",
        mac = "AA:BB:CC:DD:EE:03",
        fecha = "16/07/2026",
        hora = "17:30:00",
        foto = true
    ),
    TicketEstado(
        codigo = "ABC123",
        subida = "—",
        bajada = "—",
        mac = "AA:BB:CC:DD:EE:04",
        fecha = "16/07/2026",
        hora = "16:20:00",
        foto = true
    )
)

val listaVencidos = listOf(
    TicketEstado(
        codigo = "DEF456",
        subida = "—",
        bajada = "—",
        mac = "AA:BB:CC:DD:EE:05",
        fecha = "16/07/2026",
        hora = "15:10:00",
        foto = true
    ),
    TicketEstado(
        codigo = "GHI789",
        subida = "—",
        bajada = "—",
        mac = "AA:BB:CC:DD:EE:06",
        fecha = "16/07/2026",
        hora = "14:05:00",
        foto = true
    )
)

val listaHistorial = listOf(
    HistorialItem("0MXLC6", "16/07/2026", "18:50:00", "AA:BB:CC:DD:EE:01", Color(0xFF22C55E)),
    HistorialItem("LSJBHM", "16/07/2026", "18:45:00", "AA:BB:CC:DD:EE:02", Color(0xFF22C55E)),
    HistorialItem("0DUUHT", "16/07/2026", "17:30:00", "AA:BB:CC:DD:EE:03", Color(0xFFF59E0B)),
    HistorialItem("ABC123", "16/07/2026", "16:20:00", "AA:BB:CC:DD:EE:04", Color(0xFFF59E0B)),
    HistorialItem("DEF456", "16/07/2026", "15:10:00", "AA:BB:CC:DD:EE:05", Color(0xFFEF4444)),
    HistorialItem("GHI789", "16/07/2026", "14:05:00", "AA:BB:CC:DD:EE:06", Color(0xFFEF4444))
)

data class TicketEstado(
    val codigo: String,
    val subida: String,
    val bajada: String,
    val mac: String,
    val fecha: String,
    val hora: String,
    val foto: Boolean
)

data class HistorialItem(
    val codigo: String,
    val fecha: String,
    val hora: String,
    val mac: String,
    val colorPunto: Color
)

@Composable
fun ListaTicketsVentana(
    titulo: String,
    puntoColor: Color,
    tickets: List<TicketEstado>,
    onCerrar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                tickets.forEach { ticket ->
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(puntoColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0E0E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Foto",
                                    modifier = Modifier.size(30.dp),
                                    tint = Color(0xFF757575)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Código: ${ticket.codigo}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "⬆️ Subida: ${ticket.subida}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    "⬇️ Bajada: ${ticket.bajada}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0)
                                )
                                Text(
                                    "📶 MAC: ${ticket.mac}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    "📅 ${ticket.fecha}  ${ticket.hora}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋 HISTORIAL DE TICKETS", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                listaHistorial.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(item.colorPunto)
                            )
                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Código: ${item.codigo}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "📅 ${item.fecha}  ${item.hora}",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    "📶 MAC: ${item.mac}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
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
            .fillMaxWidth()
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
val listaTickets = mutableStateListOf<Ticket>()

data class Ticket(
    val codigo: String,
    val tiempo: String,
    val valor: String,
    val tipoTiempo: String,
    val fechaHora: String,
    val estado: String = "✅ Activo"
)

sealed class EstadoCreacion {
    object Inactivo : EstadoCreacion()
    data class Creando(val progreso: Float = 0f) : EstadoCreacion()
    object Terminado : EstadoCreacion()
}

fun generarCodigoQR(texto: String, tamano: Int = 300): android.graphics.Bitmap {
    val escritor = QRCodeWriter()
    val matrizBit = escritor.encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    val ancho = matrizBit.width
    val alto = matrizBit.height
    val bitmap = android.graphics.Bitmap.createBitmap(ancho, alto, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until ancho) {
        for (y in 0 until alto) {
            bitmap.setPixel(x, y, if (matrizBit[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}

@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var textoBuscar by remember { mutableStateOf("") }
    var ticketConQR by remember { mutableStateOf<Ticket?>(null) }

    val ticketsFiltrados = remember(textoBuscar, listaTickets.size) {
        if (textoBuscar.isBlank()) listaTickets
        else listaTickets.filter { it.codigo.contains(textoBuscar, ignoreCase = true) }
    }

    ticketConQR?.let { ticket ->
        Dialog(onDismissRequest = { ticketConQR = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📱 CÓDIGO QR", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))

                    val qrBitmap = remember(ticket.codigo) { generarCodigoQR(ticket.codigo, 300) }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Código QR",
                        modifier = Modifier
                            .size(300.dp)
                            .border(BorderStroke(2.dp, Color.LightGray), RoundedCornerShape(8.dp))
                            .background(Color.White, RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Código: ${ticket.codigo}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${ticket.valor} • ${ticket.tiempo}", fontSize = 15.sp, color = Color.Gray)
                    Text("Creado: ${ticket.fechaHora}", fontSize = 13.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { ticketConQR = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CERRAR", fontSize = 16.sp)
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).height(480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋 TICKETS CREADOS", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
            ) {
                if (ticketsFiltrados.isEmpty()) {
                    Text("📭 No hay tickets creados", color = Color.Gray, modifier = Modifier.padding(16.dp))
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
                                    Text("Código: ${ticket.codigo}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("${ticket.tiempo} • ${ticket.valor} • ${ticket.tipoTiempo}", fontSize = 12.sp, color = Color.Gray)
                                    Text("📅 ${ticket.fechaHora}", fontSize = 11.sp, color = Color.Gray)
                                    Text(ticket.estado, fontSize = 12.sp)
                                }
                                Row {
                                    Button(
                                        onClick = { ticketConQR = ticket },
                                        modifier = Modifier.wrapContentWidth(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { listaTickets.remove(ticket) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFEF4444))
                                    }
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
fun CrearTicketVentana(onCerrar: () -> Unit) {
    val listaValores = listOf(
        "S/ 0.50", "S/ 1.00", "S/ 2.00", "S/ 3.00", "S/ 4.00", "S/ 5.00",
        "S/ 8.00", "S/ 10.00", "S/ 20.00", "S/ 30.00", "S/ 40.00", "S/ 50.00",
        "S/ 60.00", "S/ 70.00", "S/ 80.00", "S/ 90.00", "S/ 100.00",
        "S/ 200.00", "S/ 500.00", "S/ 1000.00"
    )

    val listaTiempos = listOf(
        "30 Minutos", "1 Hora", "2 Horas", "3 Horas", "4 Horas", "5 Horas",
        "8 Horas", "10 Horas", "12 Horas", "1 Día", "7 Días", "15 Días", "30 Días"
    )

    val listaTipoTiempo = listOf("⏱️ Tiempo Corrido", "⏸️ Tiempo Pausado")
    val listaCantidades = listOf("1", "2", "3", "5", "10", "20", "50", "100", "200", "500", "1000")
    val listaTipoCodigo = listOf("🔢 Solo Números", "🔤 Letras + Números")
    val listaDigitos = listOf("5 Dígitos", "6 Dígitos")

    var tiempoExpandido by remember { mutableStateOf(false) }
    var valorExpandido by remember { mutableStateOf(false) }
    var cantidadExpandido by remember { mutableStateOf(false) }
    var tipoCodigoExpandido by remember { mutableStateOf(false) }
    var digitosExpandido by remember { mutableStateOf(false) }
    var tipoTiempoExpandido by remember { mutableStateOf(false) }

    var tiempoSeleccion by remember { mutableStateOf("1 Hora") }
    var valorSeleccion by remember { mutableStateOf("S/ 1.00") }
    var cantidadSeleccion by remember { mutableStateOf("1") }
    var tipoCodigoSeleccion by remember { mutableStateOf("🔢 Solo Números") }
    var digitosSeleccion by remember { mutableStateOf("6 Dígitos") }
    var tipoTiempoSeleccion by remember { mutableStateOf("⏱️ Tiempo Corrido") }

    var estadoCreacion by remember { mutableStateOf<EstadoCreacion>(EstadoCreacion.Inactivo) }

    fun generarCodigo(): String {
        val cantDig = if (digitosSeleccion == "5 Dígitos") 5 else 6
        return if (tipoCodigoSeleccion == "🔢 Solo Números") {
            (1..cantDig).joinToString("") { Random.nextInt(0, 10).toString() }
        } else {
            val caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            (1..cantDig).joinToString("") { caracteres.random().toString() }
        }
    }

    fun obtenerFechaHora(): String {
        val formato = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        return formato.format(java.util.Date())
    }

    LaunchedEffect(estadoCreacion) {
        if (estadoCreacion is EstadoCreacion.Creando) {
            val total = cantidadSeleccion.toInt()
            for (i in 1..total) {
                estadoCreacion = EstadoCreacion.Creando(i.toFloat() / total.toFloat())
                kotlinx.coroutines.delay(30)
            }
            val fechaHora = obtenerFechaHora()
            repeat(cantidadSeleccion.toInt()) {
                listaTickets.add(
                    Ticket(
                        codigo = generarCodigo(),
                        tiempo = tiempoSeleccion,
                        valor = valorSeleccion,
                        tipoTiempo = tipoTiempoSeleccion,
                        fechaHora = fechaHora
                    )
                )
            }
            estadoCreacion = EstadoCreacion.Terminado
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎫 CREAR TICKET", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(14.dp))

            Text("Tipo de código:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box {
                Button(
                    onClick = { tipoCodigoExpandido = !tipoCodigoExpandido },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3E5F5))
                ) {
                    Text(tipoCodigoSeleccion, color = Color.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = tipoCodigoExpandido,
                    onDismissRequest = { tipoCodigoExpandido = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    listaTipoCodigo.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                tipoCodigoSeleccion = opcion
                                tipoCodigoExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Dígitos del código:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box {
                Button(
                    onClick = { digitosExpandido = !digitosExpandido },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECEFF1))
                ) {
                    Text(digitosSeleccion, color = Color.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = digitosExpandido,
                    onDismissRequest = { digitosExpandido = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    listaDigitos.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                digitosSeleccion = opcion
                                digitosExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Tiempo:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box {
                Button(
                    onClick = { tiempoExpandido = !tiempoExpandido },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Text(tiempoSeleccion, color = Color.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = tiempoExpandido,
                    onDismissRequest = { tiempoExpandido = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    listaTiempos.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                tiempoSeleccion = opcion
                                tiempoExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Tipo de tiempo:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box {
                Button(
                    onClick = { tipoTiempoExpandido = !tipoTiempoExpandido },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE0B2))
                ) {
                    Text(tipoTiempoSeleccion, color = Color.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = tipoTiempoExpandido,
                    onDismissRequest = { tipoTiempoExpandido = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    listaTipoTiempo.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                tipoTiempoSeleccion = opcion
                                tipoTiempoExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Valor:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box {
                Button(
                    onClick = { valorExpandido = !valorExpandido },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Text(valorSeleccion, color = Color.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = valorExpandido,
                    onDismissRequest = { valorExpandido = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    listaValores.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                valorSeleccion = opcion
                                valorExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Cantidad de tickets:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box {
                Button(
                    onClick = { cantidadExpandido = !cantidadExpandido },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Text(cantidadSeleccion, color = Color.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = cantidadExpandido,
                    onDismissRequest = { cantidadExpandido = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    listaCantidades.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                cantidadSeleccion = opcion
                                cantidadExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (estadoCreacion) {
                is EstadoCreacion.Inactivo -> {}
                is EstadoCreacion.Creando -> {
                    val progreso = (estadoCreacion as EstadoCreacion.Creando).progreso
                    Text("🔄 Creando $cantidadSeleccion tickets...", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progreso },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = Color(0xFF22C55E)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                is EstadoCreacion.Terminado -> {
                    Text("✅ ¡$cantidadSeleccion tickets creados!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (estadoCreacion is EstadoCreacion.Inactivo || estadoCreacion is EstadoCreacion.Terminado) {
                    Button(
                        onClick = onCerrar,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text("CANCELAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = { estadoCreacion = EstadoCreacion.Creando() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text("CREAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
