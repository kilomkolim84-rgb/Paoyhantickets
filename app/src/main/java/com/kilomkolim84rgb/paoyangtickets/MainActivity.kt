package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        config = MikrotikConfig(this)
        setContent { PantallaPrincipal() }
    }
}

// ==============================================
// 🟢 FIREBASE — TAL CUAL FUNCIONABA ANTES
// ==============================================
val db = FirebaseDatabase.getInstance().reference
val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

data class Ticket(
    val codigo: String = "",
    val monto: Float = 0f,
    val minutos: Int = 0,
    val tiempoStr: String = "",
    val fecha: String = "",
    var estado: String = "CREADO",
    var tiempoRestanteSeg: Int = 0,
    var qrBitmap: Bitmap? = null
)

val listaTickets = mutableStateListOf<Ticket>()

// ✅ QR CON ZXING — IGUAL QUE ANTES
fun generarQRZxing(texto: String): Bitmap? {
    return try {
        val encoder = BarcodeEncoder()
        encoder.encodeBitmap(texto, com.google.zxing.BarcodeFormat.QR_CODE, 300, 300)
    } catch (e: Exception) { null }
}

// ✅ ESCUCHA FIREBASE — EXACTAMENTE COMO ANTES
fun escucharFirebase() {
    db.child("historial").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            listaTickets.clear()
            for (nodo in snapshot.children) {
                val codigo = nodo.child("codigo").getValue(String::class.java) ?: continue
                val montoD = nodo.child("monto").getValue(Double::class.java) ?: 0.0
                val tiempoMin = nodo.child("tiempo_minutos").getValue(Int::class.java) ?: 0
                val fecha = nodo.child("fecha").getValue(String::class.java) ?: ""
                val leido = nodo.child("leido_por_portal").getValue(Boolean::class.java) ?: false

                if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue

                val mins = if (tiempoMin > 0) tiempoMin else (montoD * 100).toInt()
                val h = mins / 60
                val m = mins % 60
                val tiempoStr = if (h > 0) "${h}h ${m}m" else "${mins}m"
                val contenidoQR = "COD:$codigo|MONTO:$montoD|MIN:$mins"
                val qr = generarQRZxing(contenidoQR)

                listaTickets.add(
                    Ticket(
                        codigo = codigo,
                        monto = montoD.toFloat(),
                        minutos = mins,
                        tiempoStr = tiempoStr,
                        fecha = fecha,
                        estado = if (leido) "ACTIVO" else "CREADO",
                        tiempoRestanteSeg = mins * 60,
                        qrBitmap = qr
                    )
                )
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    })
}

// ==============================================
// 🔵 MIKROTIK NUEVO — CONECTA EN VIVO
// ==============================================
data class MikrotikEstado(
    val conectado: Boolean = false,
    val cpu: String = "—",
    val ram: String = "—",
    val temp: String = "—",
    val subida: String = "— Mbps",
    val bajada: String = "— Mbps",
    val clientes: List<Cliente> = emptyList(),
    val mensaje: String = "Sin configurar"
)

data class Cliente(
    val ip: String,
    val mac: String,
    val nombre: String,
    val interfaz: String
)

val _estadoMikrotik = MutableStateFlow(MikrotikEstado())
val estadoMikrotik = _estadoMikrotik.asStateFlow()

class MikrotikConfig(context: Context) {
    private val prefs = context.getSharedPreferences("mikrotik_cfg", Context.MODE_PRIVATE)
    data class Config(
        val ip: String = "",
        val puerto: String = "8728",
        val usuario: String = "admin",
        val clave: String = ""
    )
    fun cargar(): Config = Config(
        prefs.getString("ip", "") ?: "",
        prefs.getString("puerto", "8728") ?: "8728",
        prefs.getString("usuario", "admin") ?: "admin",
        prefs.getString("clave", "") ?: ""
    )
    fun guardar(c: Config) = prefs.edit()
        .putString("ip", c.ip).putString("puerto", c.puerto)
        .putString("usuario", c.usuario).putString("clave", c.clave).apply()
}

lateinit var config: MikrotikConfig
var actualizando: Job? = null

fun conectarMikrotik(cfg: MikrotikConfig.Config) {
    actualizando?.cancel()
    if (cfg.ip.isBlank() || cfg.clave.isBlank()) {
        _estadoMikrotik.value = MikrotikEstado(mensaje = "⚠️ Falta IP o contraseña")
        return
    }
    _estadoMikrotik.value = MikrotikEstado(mensaje = "🔄 Conectando...")
    actualizando = scope.launch {
        while (isActive) {
            leerDatosMikrotik(cfg)
            delay(4000)
        }
    }
}

fun desconectarMikrotik() {
    actualizando?.cancel()
    _estadoMikrotik.value = MikrotikEstado(mensaje = "🔌 Desconectado")
}

fun leerDatosMikrotik(cfg: MikrotikConfig.Config) {
    try {
        val auth = "Basic " + Base64.getEncoder().encodeToString("${cfg.usuario}:${cfg.clave}".toByteArray())
        
        // CPU y RAM
        val urlSys = URL("http://${cfg.ip}:${cfg.puerto}/rest/system/resource")
        val connSys = urlSys.openConnection() as HttpURLConnection
        connSys.setRequestProperty("Authorization", auth)
        connSys.connectTimeout = 4000
        connSys.readTimeout = 4000
        var cpu = "—"
        var ram = "—"
        var temp = "—"
        if (connSys.responseCode == 200) {
            val resp = BufferedReader(InputStreamReader(connSys.inputStream)).readText()
            cpu = Regex(""""cpu-load":"?(\d+)""").find(resp)?.groupValues?.get(1) ?: "—"
            val totalMem = Regex(""""total-memory":"?(\d+)""").find(resp)?.groupValues?.get(1)?.toLongOrNull() ?: 1
            val freeMem = Regex(""""free-memory":"?(\d+)""").find(resp)?.groupValues?.get(1)?.toLongOrNull() ?: 0
            ram = if (totalMem > 0) "${((totalMem - freeMem) * 100 / totalMem).toInt()}%" else "—"
            temp = Regex(""""cpu-temperature":"?(\d+)""").find(resp)?.groupValues?.get(1)?.let { "$it°C" } ?: "—"
        }
        connSys.disconnect()

        // Velocidades de interfaces
        val urlIf = URL("http://${cfg.ip}:${cfg.puerto}/rest/interface/print")
        val connIf = urlIf.openConnection() as HttpURLConnection
        connIf.setRequestProperty("Authorization", auth)
        connIf.connectTimeout = 4000
        connIf.readTimeout = 4000
        var subida = "— Mbps"
        var bajada = "— Mbps"
        if (connIf.responseCode == 200) {
            val resp = BufferedReader(InputStreamReader(connIf.inputStream)).readText()
            val ether1Rx = Regex("ether1[^}]*\"rx-byte\":\"?(\\d+)").find(resp)?.groupValues?.get(1)?.toLongOrNull() ?: 0
            val ether4Tx = Regex("ether4[^}]*\"tx-byte\":\"?(\\d+)").find(resp)?.groupValues?.get(1)?.toLongOrNull() ?: 0
            bajada = "${ether1Rx / 125000} Mbps"
            subida = "${ether4Tx / 125000} Mbps"
        }
        connIf.disconnect()

        // Clientes ARP con nombre
        val urlArp = URL("http://${cfg.ip}:${cfg.puerto}/rest/ip/arp")
        val connArp = urlArp.openConnection() as HttpURLConnection
        connArp.setRequestProperty("Authorization", auth)
        connArp.connectTimeout = 4000
        connArp.readTimeout = 4000
        val clientes = mutableListOf<Cliente>()
        if (connArp.responseCode == 200) {
            val resp = BufferedReader(InputStreamReader(connArp.inputStream)).readText()
            val entradas = Regex("\\{[^}]+\\}").findAll(resp)
            entradas.forEach { match ->
                val txt = match.value
                val ip = Regex(""""address":"([^"]+)"""").find(txt)?.groupValues?.get(1) ?: ""
                val mac = Regex(""""mac-address":"([^"]+)"""").find(txt)?.groupValues?.get(1) ?: ""
                val nombre = Regex(""""comment":"([^"]+)"""").find(txt)?.groupValues?.get(1) ?: ""
                val interfaz = Regex(""""interface":"([^"]+)"""").find(txt)?.groupValues?.get(1) ?: ""
                if (ip.isNotBlank() && mac.isNotBlank()) {
                    clientes.add(Cliente(ip, mac, nombre, interfaz))
                }
            }
        }
        connArp.disconnect()

        _estadoMikrotik.value = MikrotikEstado(
            conectado = true, cpu = cpu, ram = ram, temp = temp,
            subida = subida, bajada = bajada, clientes = clientes,
            mensaje = "✅ Conectado"
        )
    } catch (e: Exception) {
        _estadoMikrotik.value = _estadoMikrotik.value.copy(mensaje = "❌ ${e.message}")
    }
}

// ==============================================
// 📱 PANTALLA PRINCIPAL — TODO JUNTO
// ==============================================
@Composable
fun PantallaPrincipal() {
    var abrirCfg by remember { mutableStateOf(false) }
    var verTicket by remember { mutableStateOf<Ticket?>(null) }
    val estadoMikroTik by estadoMikrotik.collectAsState()
    val cCreados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val cActivos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val cVencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    LaunchedEffect(Unit) {
        escucharFirebase()
        val cfg = config.cargar()
        if (cfg.ip.isNotBlank()) conectarMikrotik(cfg)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            listaTickets.forEachIndexed { i, t ->
                if (t.estado == "ACTIVO") {
                    listaTickets[i] = if (t.tiempoRestanteSeg - 1 <= 0) t.copy(estado = "VENCIDO", tiempoRestanteSeg = 0)
                    else t.copy(tiempoRestanteSeg = t.tiempoRestanteSeg - 1)
                }
            }
        }
    }

    // ✅ DIÁLOGO CONFIGURACIÓN — CORREGIDO LOS OUTLINEDTEXTFIELD
    if (abrirCfg) Dialog(onDismissRequest = { abrirCfg = false }) {
        var ip by remember { mutableStateOf(config.cargar().ip) }
        var puerto by remember { mutableStateOf(config.cargar().puerto) }
        var usuario by remember { mutableStateOf(config.cargar().usuario) }
        var clave by remember { mutableStateOf(config.cargar().clave) }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("⚙️ Configurar MikroTik", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                // ✅ TODOS CORREGIDOS: firma correcta (String, String)
                OutlinedTextField(
                    value = ip,
                    onValueChange = { nuevoValor -> ip = nuevoValor },
                    label = { Text("IP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = puerto,
                    onValueChange = { nuevoValor -> puerto = nuevoValor },
                    label = { Text("Puerto API") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = usuario,
                    onValueChange = { nuevoValor -> usuario = nuevoValor },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = clave,
                    onValueChange = { nuevoValor -> clave = nuevoValor },
                    label = { Text("Contraseña") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            config.guardar(MikrotikConfig.Config(ip, puerto, usuario, clave))
                            conectarMikrotik(MikrotikConfig.Config(ip, puerto, usuario, clave))
                            abrirCfg = false
                        },
                        Modifier.weight(1f)
                    ) { Text("💾 Guardar y Conectar") }
                    
                    Button(
                        onClick = { abrirCfg = false },
                        Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(Color(0xFF888888))
                    ) { Text("Cancelar") }
                }
            }
        }
    }

    // Dialog QR Ticket
    verTicket?.let { t ->
        Dialog(onDismissRequest = { verTicket = null }) {
            Card(Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎟️ TICKET — ${t.codigo}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("💰 S/ %.2f".format(t.monto), fontSize = 18.sp, color = Color(0xFF22C55E))
                    Text("⏱️ ${t.tiempoStr}", fontSize = 16.sp)
                    Text("📅 ${t.fecha}", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(20.dp))
                    t.qrBitmap?.let { Image(it.asImageBitmap(), null, Modifier.size(280.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { verTicket = null }, Modifier.fillMaxWidth()) { Text("CERRAR") }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎟️ PAOYHAN TICKETS", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical = 12.dp))

        // 📡 MIKROTIK EN VIVO
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("📡 RB750Gr3 — EN VIVO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(estadoMikroTik.mensaje, color = if (estadoMikroTik.conectado) Color(0xFF22C55E) else Color(0xFFF59E0B))
                }
                IconButton(onClick = { abrirCfg = true }) { Icon(Icons.Default.Settings, null, Modifier.size(28.dp), Color(0xFF6366F1)) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 📊 DATOS MIKROTIK EN TIEMPO REAL
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("📊 ESTADO EN TIEMPO REAL", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💻 CPU", fontSize = 13.sp); Text("${estadoMikroTik.cpu}%", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💾 RAM", fontSize = 13.sp); Text(estadoMikroTik.ram, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🌡️ Temp", fontSize = 13.sp); Text(estadoMikroTik.temp, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📤 Subida", fontSize = 13.sp); Text(estadoMikroTik.subida, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📥 Bajada", fontSize = 13.sp); Text(estadoMikroTik.bajada, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 👥 CLIENTES CONECTADOS
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("👥 CLIENTES CONECTADOS (${estadoMikroTik.clientes.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (estadoMikroTik.clientes.isEmpty()) Text("Configura el MikroTik para ver clientes", color = Color.Gray)
                else Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 160.dp)) {
                    estadoMikroTik.clientes.forEach { c ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                if (c.nombre.isNotBlank()) Text("👤 ${c.nombre}", fontWeight = FontWeight.Bold)
                                Text("🌐 ${c.ip} | 📶 ${c.mac}", fontSize = 13.sp)
                                Text("🔌 ${c.interfaz}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 📊 RESUMEN TICKETS FIREBASE
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🎟️ TICKETS — FIREBASE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋 CREADOS", color = Color.Gray); Text("$cCreados", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🟢 ACTIVOS", color = Color.Gray); Text("$cActivos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔴 VENCIDOS", color = Color.Gray); Text("$cVencidos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 📋 LISTA TICKETS CON QR
        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("📋 TICKETS RECIBIDOS (${listaTickets.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (listaTickets.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("📭 Esperando tickets desde Firebase...", color = Color.Gray)
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        listaTickets.forEach { t ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { verTicket = t },
                                colors = CardDefaults.cardColors(
                                    when (t.estado) {
                                        "CREADO" -> Color(0xFFE3F2FD)
                                        "ACTIVO" -> Color(0xFFE8F5E9)
                                        else -> Color(0xFFFFEBEE)
                                    }
                                )
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text("🆔 ${t.codigo}", fontWeight = FontWeight.Bold)
                                        Text("💰 S/ %.2f  ⏱️ ${t.tiempoStr}".format(t.monto), fontSize = 13.sp)
                                        Text(if (t.estado == "ACTIVO") "⏳ Restante: ${formatearTiempo(t.tiempoRestanteSeg)}" 
                                             else "📅 ${t.fecha}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text(when (t.estado) {
                                        "CREADO" -> "📋"
                                        "ACTIVO" -> "🟢"
                                        else -> "🔴"
                                    }, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatearTiempo(seg: Int) = "%02d:%02d:%02d".format(seg / 3600, seg % 3600 / 60, seg % 60)
