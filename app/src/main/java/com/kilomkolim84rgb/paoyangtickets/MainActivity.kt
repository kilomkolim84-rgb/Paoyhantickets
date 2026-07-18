package com.kilomkolim84rgb.paoyangtickets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Datos vacíos por defecto — se llenan al conectar MikroTik
data class RouterEstado(
    val ip: String = "---",
    val cpu: String = "--",
    val ram: String = "--",
    val temperatura: String = "--",
    val interfaces: List<InterfaceDatos> = emptyList(),
    val conectado: Boolean = false
)

data class InterfaceDatos(
    val nombre: String = "",
    val download: String = "0",
    val upload: String = "0"
)

@Composable
fun PantallaMonitoreoMikrotik() {
    // Estado — VACÍO hasta que se conecte el MikroTik
    val router1 = remember { mutableStateOf(RouterEstado()) }
    val router2 = remember { mutableStateOf(RouterEstado()) }
    val routerActivo = remember { mutableStateOf(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PaoyangTickets",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ===== ROUTER #1 — RB750Gr3 (Balanceador) =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(if (routerActivo.value == 1) Color(0xFFE3F2FD) else Color.White),
            border = if (routerActivo.value == 1) BorderStroke(3.dp, Color(0xFF2196F3)) else null,
            onClick = { routerActivo.value = 1 }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📡 Router #1", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("RB750Gr3 (Balanceador)", fontSize = 16.sp, color = Color.DarkGray)
                    Text("IP: ${router1.value.ip}", fontSize = 15.sp, color = Color.Gray)
                }
                Button(
                    onClick = { /* Configurar IP, Puerto, Contraseña */ },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(Color(0xFFE0E0E0))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurar", tint = Color.Black)
                }
            }
        }

        // ===== ROUTER #2 — RB3011 (Administración) =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(if (routerActivo.value == 2) Color(0xFFE3F2FD) else Color.White),
            border = if (routerActivo.value == 2) BorderStroke(3.dp, Color(0xFF2196F3)) else null,
            onClick = { routerActivo.value = 2 }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📡 Router #2", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("RB3011 (Administración)", fontSize = 16.sp, color = Color.DarkGray)
                    Text("IP: ${router2.value.ip}", fontSize = 15.sp, color = Color.Gray)
                }
                Button(
                    onClick = { /* Configurar IP, Puerto, Contraseña */ },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(Color(0xFFE0E0E0))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurar", tint = Color.Black)
                }
            }
        }

        // ===== CONSUMO DE INTERNET — UN SOLO RECUADRO VERDE =====
        val datos = if (routerActivo.value == 1) router1.value else router2.value

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "📊 Consumo de Internet — 📡 Router #${routerActivo.value}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // CPU, RAM, TEMPERATURA — VACÍO SI NO HAY CONEXIÓN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💻 CPU", fontSize = 16.sp, color = Color.Gray)
                        Text("${datos.cpu}%", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = if (datos.cpu == "--") Color.Gray else Color(0xFFE65100))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧠 MEMORIA RAM", fontSize = 16.sp, color = Color.Gray)
                        Text("${datos.ram}%", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = if (datos.ram == "--") Color.Gray else Color(0xFF303F9F))
                    }
                }

                Text(
                    text = "🌡️ Temperatura: ${datos.temperatura} °C",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Divider(color = Color(0xFF2196F3), thickness = 2.dp, modifier = Modifier.padding(bottom = 16.dp))

                // ===== SECCIÓN INTERNET =====
                Text(
                    text = "Internet",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                if (!datos.conectado) {
                    Text(
                        text = "No disponible",
                        fontSize = 18.sp,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    Text(
                        text = "Sin puertos conectados",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        text = "Disponible en: ${datos.interfaces.joinToString { it.nombre }}",
                        fontSize = 18.sp,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    Text(
                        text = "IP: ${datos.ip}",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // ===== TABLA DE INTERFACES =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFC8E6C9), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(text = "Interface", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(text = "⬇ Download", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(text = "⬆ Upload", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                }

                if (datos.interfaces.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(text = "--", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        Text(text = "-- Kbps", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        Text(text = "-- Kbps", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                    }
                } else {
                    datos.interfaces.forEach { interfaz ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(text = interfaz.nombre, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(text = "${interfaz.download} Kbps", fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(text = "${interfaz.upload} Kbps", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // ===== GRÁFICO DE TRÁFICO =====
                Text(
                    text = "ether1  Download: -- kbps  Upload: -- kbps",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // GRÁFICO VACÍO — LÍNEAS EN EL SUELO (0)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Línea AZUL (Tx) en el suelo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFF2196F3))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Línea ROJA (Rx) en el suelo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFFF44336))
                        )
                    }
                    Text(
                        text = "0 bps",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 4.dp)
                    )
                }

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text("🔵 Tx", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("🔴 Rx", fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== BOTONES =====
        Button(
            onClick = { /* Crear Ticket */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF2962FF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("🎟️ CREAR NUEVO TICKET", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { /* Tickets Creados */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF5C6BC0)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("📋 TICKETS CREADOS (0)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { /* Activos */ },
                modifier = Modifier.weight(1f).height(65.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("● ACTIVOS (2)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { /* Pausados */ },
                modifier = Modifier.weight(1f).height(65.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFFFF9800)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("● PAUSADOS (1)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { /* Vencidos */ },
                modifier = Modifier.weight(1f).height(65.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFFE53935)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("● VENCIDOS (1)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { /* Historial */ },
                modifier = Modifier.weight(1f).height(65.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF5C6BC0)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📋 HISTORIAL", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
