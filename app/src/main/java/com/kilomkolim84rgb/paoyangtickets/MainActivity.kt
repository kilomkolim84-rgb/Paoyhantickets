package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import android.util.Base64
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
// ✅ ESTOS ERAN LOS IMPORTS QUE FALTABAN
import androidx.compose.foundation.pullRefresh
import androidx.compose.foundation.rememberPullRefreshState
import androidx.compose.material3.PullRefreshIndicator
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ==================== RESTO DEL CÓDIGO IGUAL ====================
// ... (todo lo anterior se mantiene igual) ...

// ============== PANTALLA PRINCIPAL — PULL-TO-REFRESH CORREGIDO ==============
@Composable
fun PantallaPrincipal() {
    var abrirConfig by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var datosRouter by remember { mutableStateOf(DatosRouter()) }
    var cargando by remember { mutableStateOf(false) }
    var refrescando by remember { mutableStateOf(false) }

    val config = remember { configMikrotik.cargar() }

    val cargarDatos = suspend {
        cargando = true
        refrescando = true
        datosRouter = MikrotikAPI.obtenerTodo(config.ip, 8080, config.usuario, config.clave)
        cargando = false
        refrescando = false
    }

    // ⚡ CARGA AUTOMÁTICA CADA 2 SEGUNDOS
    LaunchedEffect(config.ip) {
        if (config.ip.isBlank()) return@LaunchedEffect
        while (isActive) {
            cargarDatos()
            delay(2000)
        }
    }

    val creados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val activos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val vencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    MaterialTheme {
        // ✅ PULL-TO-REFRESH — AHORA SÍ RECONOCIDO
        val estadoDesplazamiento = rememberPullRefreshState(
            refreshing = refrescando,
            onRefresh = {
                CoroutineScope(Dispatchers.IO).launch {
                    cargarDatos()
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .pullRefresh(estadoDesplazamiento)  // ✅ Reconocido
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🎟️ PAOYHAN TICKETS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(Color(0xFFFFF3E0))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📡 RB750Gr3",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            IconButton(onClick = { abrirConfig = true }) {
                                Icon(Icons.Default.Settings, "Configurar", tint = Color(0xFF6366F1), modifier = Modifier.size(28.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (config.ip.isBlank()) {
                            Text("⚠️ Toca el ícono ⚙️ para configurar la IP", fontSize = 15.sp, color = Color.Gray)
                        } else if (!datosRouter.conectado) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔄 Conectando...", fontSize = 15.sp, color = Color(0xFFE65100))
                                if (cargando) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(start = 8.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else {
                            Text("🌐 IP: ${config.ip}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💻 CPU", fontSize = 13.sp, color = Color.Gray)
                                    Text("${datosRouter.cpu}%", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💾 RAM", fontSize = 13.sp, color = Color.Gray)
                                    Text("${datosRouter.ram}%", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("↓ BAJADA", fontSize = 13.sp, color = Color.Gray)
                                    Text(datosRouter.bajadaEth1, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF22C55E))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("↑ SUBIDA", fontSize = 13.sp, color = Color.Gray)
                                    Text(datosRouter.subidaEth1, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF6B00))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                SeccionClientesLAN(datosRouter = datosRouter)

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { abrirCreados = true },
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))
                ) {
                    Text("📋 TICKETS CREADOS ($creados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonPestana("🟢 ACTIVOS ($activos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos = true }
                    BotonPestana("🔴 VENCIDOS ($vencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos = true }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }

            // ✅ INDICADOR DE REFRESCO — ARRIBA DE TODO
            PullRefreshIndicator(
                refreshing = refrescando,
                state = estadoDesplazamiento,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (abrirConfig) VentanaConfig { abrirConfig = false }
        if (abrirCreados) Dialog(onDismissRequest = { abrirCreados = false }) { Text("Creados", modifier = Modifier.padding(24.dp)) }
        if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { Text("Activos", modifier = Modifier.padding(24.dp)) }
        if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { Text("Vencidos", modifier = Modifier.padding(24.dp)) }
    }
}
