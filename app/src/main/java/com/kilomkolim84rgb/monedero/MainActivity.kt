package com.kilomkolim84rgb.paoyangtickets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.text.toInt

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
    var abrirCrearTicket by remember { mutableStateOf(false) }

    if (abrirCrearTicket) {
        Dialog(onDismissRequest = { abrirCrearTicket = false }) {
            CrearTicketVentana(onCerrar = { abrirCrearTicket = false })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🏷️ TÍTULO
        Text(
            text = "🎟️ PAOYANG TICKETS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50),
            modifier = Modifier.padding(bottom = 20.dp, top = 16.dp)
        )

        // 📡 ROUTERS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TarjetaRouter(
                nombre = "📡 Router #1",
                modelo = "RB750Gr3",
                ip = "192.168.88.1",
                puerto = "Balanceador"
            )
            TarjetaRouter(
                nombre = "📡 Router #2",
                modelo = "RB3011",
                ip = "192.168.88.1",
                puerto = "Administración"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 📊 CONSUMO: UPLOAD + DOWNLOAD
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
                    text = "📊 Consumo de Internet",
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
                        Text("4.8 MB/s", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇️ DOWNLOAD", fontSize = 14.sp, color = Color.Gray)
                        Text("15.2 MB/s", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }
                Text(
                    text = "🌡️ Temperatura Router #2: 42.5 °C",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🎫 BOTÓN CREAR TICKET
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

        Spacer(modifier = Modifier.height(20.dp))

        // 4 BOTONES CON PUNTOS DE COLORES
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
fun TarjetaRouter(nombre: String, modelo: String, ip: String, puerto: String) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(130.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
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

// 🎫 VENTANA CREAR TICKET
@Composable
fun CrearTicketVentana(onCerrar: () -> Unit) {
    var tiempoSeleccion by remember { mutableStateOf("1 Hora") }
    var abrirQR by remember { mutableStateOf(false) }
    val codigoTicket = remember { "TK-${(1000..9999).random()}" }

    if (abrirQR) {
        Dialog(onDismissRequest = { abrirQR = false }) {
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
                    Text("Código: $codigoTicket", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀", fontSize = 12.sp)
                    Text("▀ █▀▀█ ▀ █ ▀▀ █ ", fontSize = 12.sp)
                    Text("▀ █──█ █ ▀ █ █▀▀ ", fontSize = 12.sp)
                    Text("▀ ▀▀▀▀ ─── ▀ ▀▀▀ ", fontSize = 12.sp)
                    Text("▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { abrirQR = false }) {
                        Text("CERRAR")
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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎫 CREAR TICKET", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))

            Text("Selecciona el tiempo:", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("30 min", "1 Hora", "2 Horas", "5 Horas").forEach { tiempo ->
                    Button(
                        onClick = { tiempoSeleccion = tiempo },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tiempoSeleccion == tiempo)
                                Color(0xFF2563EB) else Color(0xFFE0E0E0)
                        )
                    ) {
                        Text(tiempo, color = if (tiempoSeleccion == tiempo) Color.White else Color.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Código y QR al costado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Código:", fontSize = 14.sp, color = Color.Gray)
                    Text(codigoTicket, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
                Button(
                    onClick = { abrirQR = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("📷 QR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onCerrar,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("CANCELAR", fontSize = 16.sp)
                }
                Button(
                    onClick = { /* Guardar ticket en Firebase */ onCerrar() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Text("✅ GUARDAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
