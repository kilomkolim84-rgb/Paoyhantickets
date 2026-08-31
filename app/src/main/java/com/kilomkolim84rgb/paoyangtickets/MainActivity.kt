package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import java.io.*
import java.net.Socket
import java.net.SocketTimeoutException // ✅ AGREGADO
import java.net.ConnectException
import javax.net.ssl.SSLContext

// ============= CONEXIÓN MIKROTIK — ROUTEROS 7.13 ✅ =============
object MikrotikApi {
    private suspend fun login(socket: Socket, usuario: String, clave: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val out = PrintWriter(socket.getOutputStream().writer(), true)
                val `in` = BufferedReader(socket.getInputStream().reader())

                // PASO 1: Pedir token — OBLIGATORIO en RouterOS 7
                out.println("/login")
                out.println("")
                out.flush()

                var linea: String?
                var token = ""
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re") && linea!!.contains("=ret=")) {
                        token = linea!!.split("=ret=")[1]
                    }
                }

                // PASO 2: Enviar credenciales
                out.println("/login")
                out.println("=name=$usuario")
                out.println("=password=$clave")
                if (token.isNotEmpty()) out.println("=token=$token")
                out.println("")
                out.flush()

                // PASO 3: Verificar
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") return@withContext true
                    if (linea.orEmpty().startsWith("!trap")) return@withContext false
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun testConexion(ip: String, usuario: String, clave: String): String =
        withContext(Dispatchers.IO) {
            if (ip.isBlank() || usuario.isBlank()) return@withContext "❌ Datos incompletos"
            try {
                val socket = Socket(ip, 8728)
                socket.soTimeout = 5000
                if (!login(socket, usuario, clave)) {
                    socket.close()
                    return@withContext "❌ Usuario o contraseña incorrectos"
                }
                socket.close()
                "✅ Conexión exitosa — RouterOS 7.13"
            } catch (e: SocketTimeoutException) {
                "❌ Sin respuesta — revisa IP o firewall"
            } catch (e: ConnectException) {
                "❌ No se conecta — revisa red o WiFi"
            } catch (e: Exception) {
                "❌ Error: ${e.message}"
            }
        }

    suspend fun leerEstado(ip: String, usuario: String, clave: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val datos = mutableMapOf(
                "subida" to "— Mbps", "bajada" to "— Mbps",
                "cpu" to "— %", "ram" to "— %", "temp" to "— °C"
            )
            try {
                val socket = Socket(ip, 8728)
                socket.soTimeout = 5000
                val out = PrintWriter(socket.getOutputStream().writer(), true)
                val `in` = BufferedReader(socket.getInputStream().reader())

                if (!login(socket, usuario, clave)) {
                    socket.close()
                    return@withContext datos
                }

                // CPU, RAM, Temp
                out.println("/system/resource/print")
                out.println("")
                out.flush()
                var linea: String?
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re")) {
                        val l = linea!!
                        if (l.contains("cpu-load")) {
                            val valor = l.split("cpu-load=")[1].split(" ")[0]
                            datos["cpu"] = "$valor %"
                        }
                        if (l.contains("total-memory") && l.contains("free-memory")) {
                            val totalMem = Regex("total-memory=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                            val freeMem = Regex("free-memory=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                            datos["ram"] = "${100 - (freeMem * 100 / totalMem)} %"
                        }
                        if (l.contains("cpu-temperature")) {
                            val valor = l.split("cpu-temperature=")[1].split(" ")[0]
                            datos["temp"] = "$valor °C"
                        }
                    }
                }

                // Tráfico
                out.println("/interface/print")
                out.println("=.proplist=name,rx-byte,tx-byte")
                out.println("")
                out.flush()
                var totalRx = 0L
                var totalTx = 0L
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re")) {
                        val rx = Regex("rx-byte=(\\d+)").find(linea!!)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                        val tx = Regex("tx-byte=(\\d+)").find(linea!!)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                        totalRx += rx
                        totalTx += tx
                    }
                }
                datos["bajada"] = "${(totalRx / 125000) / 1000} Mbps"
                datos["subida"] = "${(totalTx / 125000) / 1000} Mbps"
                socket.close()
            } catch (e: Exception) { }
            datos
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        setContent { PantallaPrincipal() }
    }
}

val db = FirebaseDatabase.getInstance().reference
lateinit var configMikrotik: MikrotikConfig

// ============= CONFIGURACIÓN =============
class MikrotikConfig(context: Context) {
    private val prefs = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)
    data class Config(val ip: String = "", val usuario: String = "admin", val clave: String = "", val dns: String = "")

    fun cargar(id: Int): Config = Config(
        ip = prefs.getString("r${id}_ip", "") ?: "",
        usuario = prefs.getString("r${id}_usuario", "admin") ?: "admin",
        clave = prefs.getString("r${id}_clave", "") ?: "",
        dns = prefs.getString("r${id}_dns", "") ?: ""
    )

    fun guardar(id: Int, cfg: Config) {
        prefs.edit()
            .putString("r${id}_ip", cfg.ip)
            .putString("r${id}_usuario", cfg.usuario)
            .putString("r${id}_clave", cfg.clave)
            .putString("r${id}_dns", cfg.dns)
            .apply()
    }
}

// ============= FIREBASE =============
fun escucharFirebase() {
    db.child("historial").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snap: DataSnapshot) {}
        override fun onCancelled(err: DatabaseError) {}
    })
}

// ============= VENTANA CONFIG — SIN PUERTO ✅ =============
@Composable
fun VentanaConfig(routerId: Int, nombre: String, onCerrar: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cfg = remember { configMikrotik.cargar(routerId) }
    var ip by remember { mutableStateOf(cfg.ip) }
    var user by remember { mutableStateOf(cfg.usuario) }
    var pass by remember { mutableStateOf(cfg.clave) }
    var dns by remember { mutableStateOf(cfg.dns) }
    var msg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onCerrar) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚙️ CONFIGURACIÓN — $nombre",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Mikrotik") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("172.16.1.1") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Contraseña") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(20.dp))

                msg?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            msg = "🔄 Conectando..."
                            CoroutineScope(Dispatchers.IO).launch {
                                val res = MikrotikApi.testConexion(ip, user, pass)
                                withContext(Dispatchers.Main) { msg = res }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🧪 PROBAR")
                    }

                    Button(
                        onClick = {
                            if (ip.isBlank()) {
                                msg = "❌ IP obligatoria"
                                return@Button
                            }
                            configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, user, pass, dns))
                            msg = "✅ Guardado"
                            Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))
                    ) {
                        Text("💾 GUARDAR")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCerrar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))
                ) {
                    Text("CERRAR")
                }
            }
        }
    }
}

// ============= TARJETA ROUTER =============
@Composable
fun TarjetaRouter(
    nombre: String,
    modelo: String,
    id: Int,
    sel: Boolean,
    onClick: () -> Unit,
    onCfg: () -> Unit
) {
    val cfg = remember { configMikrotik.cargar(id) }
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp).height(130.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(if (sel) Color(0xFFE3F2FD) else Color.White),
        border = if (sel) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Box {
            IconButton(
                onClick = onCfg,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF6366F1))
            }
            Column(
                modifier = Modifier.padding(top = 32.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(modelo, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Text("IP: ${cfg.ip.ifBlank { "Sin config" }}", fontSize = 11.sp)
            }
        }
    }
}

// ============= PANTALLA PRINCIPAL =============
@Composable
fun PantallaPrincipal() {
    var routerSel by remember { mutableStateOf(1) }
    var abrirCfg1 by remember { mutableStateOf(false) }
    var abrirCfg2 by remember { mutableStateOf(false) }
    var datos by remember {
        mutableStateOf(
            mapOf(
                "subida" to "— Mbps",
                "bajada" to "— Mbps",
                "cpu" to "— %",
                "ram" to "— %",
                "temp" to "— °C"
            )
        )
    }

    LaunchedEffect(Unit) { escucharFirebase() }
    LaunchedEffect(routerSel) {
        while (true) {
            val cfg = configMikrotik.cargar(routerSel)
            if (cfg.ip.isNotBlank()) {
                datos = MikrotikApi.leerEstado(cfg.ip, cfg.usuario, cfg.clave)
            }
            delay(3000)
        }
    }

    if (abrirCfg1) {
        VentanaConfig(1, "ROUTER #1") { abrirCfg1 = false }
    }
    if (abrirCfg2) {
        VentanaConfig(2, "ROUTER #2") { abrirCfg2 = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎟️ PAOYAN TICKETS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TarjetaRouter(
                nombre = "📡 Router #1",
                modelo = "RB750Gr3",
                id = 1,
                sel = routerSel == 1,
                onClick = { routerSel = 1 },
                onCfg = { abrirCfg1 = true }
            )
            TarjetaRouter(
                nombre = "📡 Router #2",
                modelo = "RB3011",
                id = 2,
                sel = routerSel == 2,
                onClick = { routerSel = 2 },
                onCfg = { abrirCfg2 = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        val cfg = configMikrotik.cargar(routerSel)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📊 ESTADO — Router #$routerSel",
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
                        Text(text = "IP", fontSize = 12.sp, color = Color.Gray)
                        Text(text = cfg.ip.ifBlank { "Sin config" }, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Usuario", fontSize = 12.sp, color = Color.Gray)
                        Text(text = cfg.usuario, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📤 Subida", fontSize = 12.sp, color = Color.Gray)
                        Text(text = datos["subida"]!!, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📥 Bajada", fontSize = 12.sp, color = Color.Gray)
                        Text(text = datos["bajada"]!!, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "💻 CPU", fontSize = 12.sp, color = Color.Gray)
                        Text(text = datos["cpu"]!!, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "💾 RAM", fontSize = 12.sp, color = Color.Gray)
                        Text(text = datos["ram"]!!, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🌡️ Temp", fontSize = 12.sp, color = Color.Gray)
                        Text(text = datos["temp"]!!, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
