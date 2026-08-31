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
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// ============= MIKROTIK API =============
object MikrotikApi {
    private suspend fun login(socket: Socket, usuario: String, clave: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val out = PrintWriter(socket.getOutputStream().writer(), true)
                val `in` = BufferedReader(socket.getInputStream().reader())
                out.println("/login"); out.println(""); out.flush()
                var linea: String?; var token = ""
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") break
                    if (linea.orEmpty().startsWith("!re") && linea!!.contains("=ret="))
                        token = linea!!.split("=ret=")[1]
                }
                out.println("/login"); out.println("=name=$usuario"); out.println("=password=$clave")
                if (token.isNotEmpty()) out.println("=token=$token")
                out.println(""); out.flush()
                while (`in`.readLine().also { linea = it } != null) {
                    if (linea == "!done") return@withContext true
                    if (linea.orEmpty().startsWith("!trap")) return@withContext false
                }
                true
            } catch (e: Exception) { false }
        }

    suspend fun testConexion(ip: String, usuario: String, clave: String): String =
        withContext(Dispatchers.IO) {
            if (ip.isBlank() || usuario.isBlank() || clave.isBlank())
                return@withContext "❌ Llena todos los campos"
            try {
                val socket = Socket(ip, 8728)
                socket.soTimeout = 5000
                if (!login(socket, usuario, clave)) {
                    socket.close()
                    return@withContext "❌ Usuario o contraseña incorrectos"
                }
                socket.close()
                "✅ Conectado — RouterOS 7"
            } catch (e: SocketTimeoutException) { "❌ Sin respuesta — revisa IP" }
            catch (e: ConnectException) { "❌ No conecta — revisa red" }
            catch (e: Exception) { "❌ Error: ${e.message}" }
        }
}

// ============= MAIN ACTIVITY =============
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        configMikrotik = MikrotikConfig(this)
        setContent { PantallaPrincipal() }
    }
}

val db = FirebaseDatabase.getInstance().reference
lateinit var appPrefs: SharedPreferences
lateinit var configMikrotik: MikrotikConfig

// ============= CONFIG MIKROTIK — IP / USUARIO / CONTRASEÑA / DNS =============
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

// ============= FIREBASE — TICKETS =============
fun generarTicket(monto: Double, horas: Int): String {
    val codigo = "TK-${Date().time / 1000}-${Random.nextInt(1000,9999)}"
    val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    val fecha = formatoFecha.format(Date())
    val datos = mapOf(
        "codigo" to codigo,
        "monto" to monto,
        "horas" to horas,
        "fecha" to fecha,
        "activo" to true
    )
    db.child("tickets").child(codigo).setValue(datos)
    return codigo
}

// ============= VENTANA CONFIG MIKROTIK =============
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
        Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(28.dp), Alignment.CenterHorizontally) {
                Text("⚙️ $nombre", 22.sp, FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(ip, { ip = it }, label = { Text("IP Mikrotik") }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("172.16.1.1") })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(dns, { dns = it }, label = { Text("DNS") }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("8.8.8.8") })
                Spacer(Modifier.height(20.dp))

                msg?.let { Text(it, 14.sp, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)) }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        msg = "🔄 Conectando..."
                        CoroutineScope(Dispatchers.IO).launch {
                            val res = MikrotikApi.testConexion(ip, user, pass)
                            withContext(Dispatchers.Main) { msg = res }
                        }
                    }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🧪 PROBAR") }

                    Button(onClick = {
                        if (ip.isBlank() || user.isBlank() || pass.isBlank()) {
                            msg = "❌ Llena todos los campos"
                            return@Button
                        }
                        configMikrotik.guardar(routerId, MikrotikConfig.Config(ip, user, pass, dns))
                        msg = "✅ Guardado"
                        Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("💾 GUARDAR") }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCerrar, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) { Text("CERRAR") }
            }
        }
    }
}

// ============= PANTALLA PRINCIPAL — TICKETS + MIKROTIK =============
@Composable
fun PantallaPrincipal() {
    var saldo by remember { mutableStateOf(appPrefs.getFloat("saldo", 0.0f).toDouble()) }
    var codigoTicket by remember { mutableStateOf("") }
    var mostrarConfig by remember { mutableStateOf(false) }
    var montoSeleccionado by remember { mutableStateOf(0.0) }
    var horasSeleccionadas by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        appPrefs.registerOnSharedPreferenceChangeListener { prefs, key ->
            if (key == "saldo") saldo = prefs.getFloat("saldo", 0.0f).toDouble()
        }
    }

    if (mostrarConfig) VentanaConfig(1, "CONFIGURACIÓN MIKROTIK") { mostrarConfig = false }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(20.dp),
        Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("🎟️ PAOYAN TICKETS", 32.sp, FontWeight.Bold, color = Color(0xFF2C3E50))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
            Column(Modifier.padding(24.dp), Alignment.CenterHorizontally) {
                Text("💰 SALDO ACTUAL", 16.sp, color = Color.Gray)
                Text("S/ ${String.format("%.2f", saldo)}", 36.sp, FontWeight.Bold, color = Color(0xFF2E7D32))
            }
        }

        Text("🎫 GENERAR TICKET", 20.sp, FontWeight.Bold)

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0.5 to 1, 1.0 to 2, 2.0 to 5, 5.0 to 12).forEach { (monto, horas) ->
                Button(
                    onClick = { montoSeleccionado = monto; horasSeleccionadas = horas },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        if (montoSeleccionado == monto) Color(0xFF22C55E) else Color(0xFF6366F1)
                    )
                ) {
                    Text("S/ $monto → $horas horas", 18.sp, FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = {
                if (montoSeleccionado == 0.0) { 
                    Toast.makeText(androidx.compose.ui.platform.LocalContext.current, "Selecciona un monto", Toast.LENGTH_SHORT).show()
                    return@Button 
                }
                if (saldo < montoSeleccionado) { 
                    Toast.makeText(androidx.compose.ui.platform.LocalContext.current, "Saldo insuficiente", Toast.LENGTH_SHORT).show()
                    return@Button 
                }
                saldo -= montoSeleccionado
                appPrefs.edit().putFloat("saldo", saldo.toFloat()).apply()
                codigoTicket = generarTicket(montoSeleccionado, horasSeleccionadas)
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFFFF6B00))
        ) {
            Text("✅ GENERAR TICKET", 20.sp, FontWeight.Bold)
        }

        if (codigoTicket.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color(0xFFE3F2FD))) {
                Column(Modifier.padding(24.dp), Alignment.CenterHorizontally) {
                    Text("✅ TICKET GENERADO", 18.sp, FontWeight.Bold, color = Color(0xFF1565C0))
                    Text(codigoTicket, 22.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("Valido por $horasSeleccionadas horas", 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Button(
            onClick = { mostrarConfig = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))
        ) {
            Icon(Icons.Default.Settings, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("⚙️ CONFIGURACIÓN MIKROTIK", 16.sp, FontWeight.Bold)
        }
    }
}
