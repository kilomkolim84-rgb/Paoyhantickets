package com.kilomkolim84rgb.paoyangtickets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

// 📋 VENTANA TICKETS CREADOS CON BUSCAR
@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var textoBuscar by remember { mutableStateOf("") }

    val todosTickets = remember {
        listOf(
            "TK-6050 • 1 Hora • S/1.00 • ✅ Activo",
            "TK-6051 • 30 min • S/0.50 • ⏳ Pendiente",
            "TK-6052 • 2 Horas • S/2.00 • ✅ Activo",
            "TK-6053 • 1 Hora • S/1.00 • 🔴 Vencido",
            "TK-6054 • 4 Horas • S/5.00 • ✅ Activo",
            "TK-1801 • 1 Hora • S/1.00 • ✅ Activo",
            "TK-1818 • 2 Horas • S/2.00 • 🔴 Vencido",
            "TK-0018 • 30 min • S/0.50 • ✅ Activo"
        )
    }

    val ticketsFiltrados = remember(textoBuscar) {
        if (textoBuscar.isBlank()) todosTickets
        else todosTickets.filter { it.contains(textoBuscar, ignoreCase = true) }
    }

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
            Text("📋 TICKETS CREADOS", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            // 🔍 BARRA DE BÚSQUEDA
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
                    Text("❌ No se encontraron tickets", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsFiltrados.forEach { ticket ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = ticket,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
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

// 🎫 VENTANA CREAR TICKET ACTUALIZADA
@Composable
fun CrearTicketVentana(onCerrar: () -> Unit) {
    val listaTiempos = listOf("30 min", "1 Hora", "2 Horas", "4 Horas", "8 Horas")
    val listaValores = listOf("S/ 1.00", "S/ 2.00", "S/ 3.00", "S/ 5.00", "S/ 10.00", "S/ 20.00", "S/ 50.00")
    val listaCantidades = listOf("1", "5", "10", "50", "100", "500", "1000")
    val listaTipoCodigo = listOf("Solo Números", "Letras + Números")
    val listaDigitos = listOf("5 Dígitos", "6 Dígitos")

    var tiempoExpandido by remember { mutableStateOf(false) }
    var valorExpandido by remember { mutableStateOf(false) }
    var cantidadExpandido by remember { mutableStateOf(false) }
    var tipoCodigoExpandido by remember { mutableStateOf(false) }
    var digitosExpandido by remember { mutableStateOf(false) }

    var tiempoSeleccion by remember { mutableStateOf("1 Hora") }
    var valorSeleccion by remember { mutableStateOf("S/ 1.00") }
    var cantidadSeleccion by remember { mutableStateOf("1") }
    var tipoCodigoSeleccion by remember { mutableStateOf("Solo Números") }
    var digitosSeleccion by remember { mutableStateOf("6 Dígitos") }

    var estadoCreacion by remember { mutableStateOf<EstadoCreacion>(EstadoCreacion.Idle) }

    fun generarCodigo(): String {
        val cantDigitos = if (digitosSeleccion == "5 Dígitos") 5 else 6
        return if (tipoCodigoSeleccion == "Solo Números") {
            (1..cantDigitos).joinToString("") { Random.nextInt(0, 10).toString() }
        } else {
            val letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            (1..cantDigitos).joinToString("") { letras.random().toString() }
        }
    }

    LaunchedEffect(estadoCreacion) {
        if (estadoCreacion is EstadoCreacion.Creando) {
            val total = cantidadSeleccion.toInt()
            for (i in 1..total) {
                (estadoCreacion as? EstadoCreacion.Creando)?.progreso = i.toFloat() / total.toFloat()
                kotlinx.coroutines.delay(30)
            }
            estadoCreacion = EstadoCreacion.Terminado(generarCodigo())
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
            Spacer(modifier = Modifier.height(16.dp))

            // 🔤 TIPO DE CÓDIGO
            Text("🔤 Tipo de código:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

            Spacer(modifier = Modifier.height(12.dp))

            // 🔢 CANTIDAD DE DÍGITOS
            Text("🔢 Dígitos del código:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

            Spacer(modifier = Modifier.height(12.dp))

            // ⏱️ TIEMPO
            Text("⏱️ Tiempo:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

            Spacer(modifier = Modifier.height(12.dp))

            // 💰 VALOR
            Text("💰 Valor:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

            Spacer(modifier = Modifier.height(12.dp))

            // 🔢 CANTIDAD
            Text("🔢 Cantidad de tickets:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

            // 📊 BARRA DE PROGRESO O CÓDIGO GENERADO
            when (estadoCreacion) {
                is EstadoCreacion.Idle -> { /* Sin mostrar nada */ }
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
                    val codigo = (estadoCreacion as EstadoCreacion.Terminado).codigo
                    Text("✅ ¡Tickets creados!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                    Text("Código inicial: $codigo", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // BOTONES
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

// ESTADOS DE CREACIÓN
sealed class EstadoCreacion {
    object Idle : EstadoCreacion()
    data class Creando(var progreso: Float) : EstadoCreacion()
    data class Terminado(val codigo: String) : EstadoCreacion()
}
