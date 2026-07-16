package com.kilomkolim84rgb.paoyangtickets

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
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

    val datosRouter = remember(routerSeleccionado) {
        if (routerSeleccionado == 1) {
            mapOf(
                "nombre" to "📡 Router #1",
                "modelo" to "RB750Gr3",
                "ip" to "192.168.88.1",
                "puerto" to "Balanceador",
                "upload" to "2.4 MB/s",
                "download" to "8.6 MB/s",
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
                "temp" to "42.5 °C"
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana(texto = "🟢 Activos", color = Color(0xFF22C55E), modifier = Modifier.weight(1f))
            BotonPestana(texto = "🟡 Pausados", color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana(texto = "🔴 Vencidos", color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
            BotonPestana(texto = "📋 Historial", color = Color(0xFF6366F1), modifier = Modifier.weight(1f))
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
fun BotonPestana(texto: String, color: Color, modifier: Modifier = Modifier) {
    Button(
        onClick = { },
        modifier = modifier.height(55.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

fun generarQR(texto: String, tamano: Int = 300): Bitmap {
    val escritor = QRCodeWriter()
    val matriz = escritor.encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    val ancho = matriz.width
    val alto = matriz.height
    val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
    for (x in 0 until ancho) {
        for (y in 0 until alto) {
            bitmap.setPixel(x, y, if (matriz[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
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

    if (ticketConQR != null) {
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

                    val qrBitmap = remember(ticketConQR!!.codigo) { generarQR(ticketConQR!!.codigo, 300) }
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(300.dp)
                            .border(BorderStroke(2.dp, Color.LightGray), RoundedCornerShape(8.dp))
                            .background(Color.White, RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Código: ${ticketConQR!!.codigo}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${ticketConQR!!.valor} • ${ticketConQR!!.tiempo}", fontSize = 15.sp, color = Color.Gray)
                    Text("Creado: ${ticketConQR!!.fechaHora}", fontSize = 13.sp, color = Color.Gray)

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
                    Text("📭 No hay tickets creados aún", color = Color.Gray, modifier = Modifier.padding(16.dp))
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
                                    IconButton(
                                        onClick = { ticketConQR = ticket },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.QrCode, contentDescription = "Ver QR", tint = Color(0xFF2563EB))
                                    }
                                    IconButton(
                                        onClick = { listaTickets.remove(ticket) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color(0xFFEF4444))
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
        "S/ 60.00", "S/ 70.00", "S/ 80.00", "S/ 90.00", "S/ 100.00"
    )

    val listaTiempos = listOf(
        "30 Minutos", "1 Hora", "2 Horas", "3 Horas", "4 Horas", "5 Horas",
        "8 Horas", "10 Horas", "12 Horas", "1 Día", "7 Días", "15 Días", "30 Días"
    )

    val listaTipoTiempo = listOf("⏱️ Tiempo Corrido", "⏸️ Tiempo Pausado")
    val listaCantidades = listOf("1", "2", "3", "5", "10", "20", "50", "100")
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

    var estadoCreacion by remember { mutableStateOf<EstadoCreacion>(EstadoCreacion.Idle) }

    fun generarCodigo(): String {
        val cantDigitos = if (digitosSeleccion == "5 Dígitos") 5 else 6
        return if (tipoCodigoSeleccion == "🔢 Solo Números") {
            (1..cantDigitos).joinToString("") { Random.nextInt(0, 10).toString() }
        } else {
            val letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            (1..cantDigitos).joinToString("") { letras.random().toString() }
        }
    }

    fun obtenerFechaHora(): String {
        val ahora = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        return ahora.format(java.util.Date())
    }

    LaunchedEffect(estadoCreacion) {
        if (estadoCreacion is EstadoCreacion.Creando) {
            val total = cantidadSeleccion.toInt()
            for (i in 1..total) {
                (estadoCreacion as? EstadoCreacion.Creando)?.progreso = i.toFloat() / total.toFloat()
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
                    listaTiempos.forEach { tiempo ->
                        DropdownMenuItem(
                            text = { Text(tiempo) },
                            onClick = {
                                tiempoSeleccion = tiempo
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
                    listaValores.forEach { valor ->
                        DropdownMenuItem(
                            text = { Text(valor) },
                            onClick = {
                                valorSeleccion = valor
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
                    listaCantidades.forEach { cant ->
                        DropdownMenuItem(
                            text = { Text(cant) },
                            onClick = {
                                cantidadSeleccion = cant
                                cantidadExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (estadoCreacion) {
                is EstadoCreacion.Idle -> { }
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
                if (estadoCreacion is EstadoCreacion.Idle) {
                    Button(
                        onClick = onCerrar,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("❌ CANCELAR", fontSize = 13.sp)
                    }
                    Button(
                        onClick = { estadoCreacion = EstadoCreacion.Creando(0f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✅ CREAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            estadoCreacion = EstadoCreacion.Idle
                            onCerrar()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✅ ACEPTAR", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

sealed class EstadoCreacion {
    object Idle : EstadoCreacion()
    data class Creando(var progreso: Float) : EstadoCreacion()
    object Terminado : EstadoCreacion()
}
