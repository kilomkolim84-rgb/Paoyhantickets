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

        // === FILA DE ROUTERS LADO A LADO CON ENGRANAJE PEQUEÑO ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    .size(36.dp)  // ✅ ENGRANAJE PEQUEÑO
                    .background(Color(0xFFE0E0E0), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(20.dp)  // ✅ ÍCONO PEQUEÑO
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    .size(36.dp)  // ✅ ENGRANAJE PEQUEÑO
                    .background(Color(0xFFE0E0E0), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(20.dp)  // ✅ ÍCONO PEQUEÑO
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === TARJETA DE CONSUMO MÁS PEQUEÑA ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),  // ✅ MENOS ESPACIO → MÁS CHICA
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📊 Consumo de Internet — ${datosRouter["nombre"]}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️ UPLOAD", fontSize = 13.sp, color = Color.Gray)
                        Text("${datosRouter["upload"]}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇️ DOWNLOAD", fontSize = 13.sp, color = Color.Gray)
                        Text("${datosRouter["download"]}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🌡️ Temperatura: ${datosRouter["temp"]}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === BOTONES IGUALES A LA SEGUNDA IMAGEN ===
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { abrirActivos = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text("🟢 ACTIVOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { abrirPausados = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Text("🟡 PAUSADOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { abrirVencidos = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("🔴 VENCIDOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { abrirHistorial = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("📋 HISTORIAL", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            .height(120.dp),
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

        // === FILA DE ROUTERS LADO A LADO CON ENGRANAJE PEQUEÑO ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    .size(36.dp)  // ✅ ENGRANAJE PEQUEÑO
                    .background(Color(0xFFE0E0E0), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(20.dp)  // ✅ ÍCONO PEQUEÑO
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    .size(36.dp)  // ✅ ENGRANAJE PEQUEÑO
                    .background(Color(0xFFE0E0E0), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF37474F),
                    modifier = Modifier.size(20.dp)  // ✅ ÍCONO PEQUEÑO
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === TARJETA DE CONSUMO MÁS PEQUEÑA ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),  // ✅ MENOS ESPACIO → MÁS CHICA
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📊 Consumo de Internet — ${datosRouter["nombre"]}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️ UPLOAD", fontSize = 13.sp, color = Color.Gray)
                        Text("${datosRouter["upload"]}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇️ DOWNLOAD", fontSize = 13.sp, color = Color.Gray)
                        Text("${datosRouter["download"]}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🌡️ Temperatura: ${datosRouter["temp"]}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === BOTONES IGUALES A LA SEGUNDA IMAGEN ===
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { abrirActivos = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text("🟢 ACTIVOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { abrirPausados = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Text("🟡 PAUSADOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { abrirVencidos = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("🔴 VENCIDOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { abrirHistorial = true },
                modifier = Modifier.weight(1f).height(65.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("📋 HISTORIAL", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            .height(120.dp),
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
