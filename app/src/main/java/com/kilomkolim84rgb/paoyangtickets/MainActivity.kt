package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import com.google.firebase.database.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent { PantallaPrincipal() }
    }
}

val db = FirebaseDatabase.getInstance().reference

data class DatosRouter(
    val conectado: Boolean = false,
    val cpu: Int = 0,
    val ram: Int = 0,
    val bajadaEth1: String = "— Kbps",
    val subidaEth1: String = "— Kbps",
    val clientes: List<ClienteLAN> = emptyList(),
    val error: String = ""
)

data class ClienteLAN(
    val ip: String,
    val mac: String,
    val nombre: String = "",
    val velocidadBajada: String = "0 bps",
    val velocidadSubida: String = "0 bps"
)

object MikrotikAPI {
    var ultimoError = ""
    private var ultimaRxEth1: Long = 0
    private var ultimaTxEth1: Long = 0
    private var ultimaMedicionEth1: Long = 0

    private suspend fun hacerPeticion(
        ip: String, puerto: Int, usuario: String, clave: String, recurso: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:$puerto/rest$recurso"
            val conexion = URL(url).openConnection() as HttpURLConnection
            conexion.apply {
                requestMethod = "GET"
                connectTimeout = 4000; readTimeout = 4000
                setRequestProperty("Authorization", "Basic " +
                    Base64.encodeToString("$usuario:$clave".toByteArray(), Base64.NO_WRAP))
            }
            if (conexion.responseCode == 401) {
                ultimoError = "❌ Usuario o contraseña incorrectos"
                return@withContext null
            }
            if (conexion.responseCode != 200) {
                ultimoError = "❌ Error HTTP ${conexion.responseCode}"
                return@withContext null
            }
            conexion.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ultimoError = "❌ ${e.message ?: "Sin conexión"}"
            null
        }
    }

    suspend fun probarConexion(ip: String, puerto: Int, usuario: String, clave: String): Boolean {
        ultimoError = ""
        listOf(puerto, 8080, 80).forEach { p ->
            if (hacerPeticion(ip, p, usuario, clave, "/system/resource") != null) return true
        }
        return false
    }

    private fun calcularVelocidad(actual: Long, anterior: Long, tiempoMs: Long): String {
        if (tiempoMs <= 0 || anterior == 0L || actual < anterior) return "— Kbps"
        val bits = (actual - anterior) * 8 * 1000 / tiempoMs
        return when {
            bits >= 1_000_000 -> "%.1f Mbps".format(bits / 1_000_000.0)
            bits >= 1_000 -> "%.0f Kbps".format(bits / 1_000.0)
            else -> "$bits bps"
        }
    }

    suspend fun obtenerTodo(ip: String, puerto: Int, usuario: String, clave: String): DatosRouter {
        ultimoError = ""
        return withContext(Dispatchers.IO) {
            var puertoUsado = 8080
            var respuesta: String? = null
            listOf(puerto, 8080, 80).forEach { p ->
                respuesta = hacerPeticion(ip, p, usuario, clave, "/system/resource")
                if (respuesta != null) { puertoUsado = p; return@forEach }
            }
            if (respuesta == null) return@withContext DatosRouter(conectado = false, error = ultimoError)

            var cpu = 0; var ram = 0
            try {
                val map = parsearJsonSimple(respuesta!!.trim().removeSurrounding("[", "]"))
                map["cpu-load"]?.toIntOrNull()?.let { cpu = it }
                map["free-memory"]?.toLongOrNull()?.let { libre ->
                    val total = map["total-memory"]?.toLongOrNull() ?: 1
                    ram = ((total - libre) * 100 / total).toInt()
                }
            } catch (e: Exception) {}

            var bajadaEth1 = "— Kbps"
            var subidaEth1 = "— Kbps"
            hacerPeticion(ip, puertoUsado, usuario, clave, "/interface")?.let { respIf ->
                val eth1 = parsearListaJson(respIf).find { it["name"] == "WAN1" }
                if (eth1 != null) {
                    val rxBytes = eth1["rx-byte"]?.toLongOrNull() ?: 0L
                    val txBytes = eth1["tx-byte"]?.toLongOrNull() ?: 0L
                    val ahora = System.currentTimeMillis()
                    val tiempo = ahora - ultimaMedicionEth1

                    if (ultimaMedicionEth1 > 0L && tiempo > 0L) {
                        // ✅ CORREGIDO: RX = RECIBIR = BAJADA | TX = ENVIAR = SUBIDA
                        bajadaEth1 = calcularVelocidad(rxBytes, ultimaRxEth1, tiempo)
                        subidaEth1 = calcularVelocidad(txBytes, ultimaTxEth1, tiempo)
                    }
                    ultimaRxEth1 = rxBytes
                    ultimaTxEth1 = txBytes
                    ultimaMedicionEth1 = ahora
                }
            }

            val simpleQueue = mutableListOf<ClienteLAN>()
            hacerPeticion(ip, puertoUsado, usuario, clave, "/queue/simple")?.let { respQ ->
                parsearListaJson(respQ).forEach { q ->
                    val nombre = q["name"] ?: ""
                    val target = q["target"] ?: ""
                    val partes = (q["rate"] ?: "").trim().split("/")
                    // ✅ CORREGIDO: [0] = BAJADA | [1] = SUBIDA
                    val bajada = if (partes.size >= 1 && partes[0] != "0") formatearTasa(partes[0].toLongOrNull() ?: 0L) else "0 bps"
                    val subida = if (partes.size >= 2 && partes[1] != "0") formatearTasa(partes[1].toLongOrNull() ?: 0L) else "0 bps"
                    val ip = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(target)?.groupValues?.get(1)
                    if (ip != null && nombre.isNotEmpty()) {
                        simpleQueue.add(ClienteLAN(ip, "", nombre, bajada, subida))
                    }
                }
            }

            DatosRouter(true, cpu, ram, bajadaEth1, subidaEth1, simpleQueue.distinctBy { it.ip })
        }
    }

    private fun formatearTasa(bps: Long): String = when {
        bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.1f Kbps".format(bps / 1_000.0)
        bps > 0 -> "$bps bps"
        else -> "0 bps"
    }

    private fun parsearJsonSimple(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        json.trim().removeSurrounding("{", "}").split(",").forEach { par ->
            val p = par.split(":", limit = 2)
            if (p.size == 2) map[p[0].trim().removeSurrounding("\"")] = p[1].trim().removeSurrounding("\"")
        }
        return map
    }

    private fun parsearListaJson(json: String): List<Map<String, String>> {
        val lista = mutableListOf<Map<String, String>>()
        val c = json.trim().removeSurrounding("[", "]")
        if (c.isBlank()) return lista
        var i = 0
        while (i < c.length) {
            val ini = c.indexOf("{", i).takeIf { it >= 0 } ?: break
            val fin = c.indexOf("}", ini).takeIf { it >= 0 } ?: c.length
            lista.add(parsearJsonSimple(c.substring(ini, fin + 1)))
            i = fin + 1
        }
        return lista
    }
}

class MikrotikConfig(c: Context) {
    private val p = c.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)
    data class Config(val ip: String = "", val usuario: String = "admin", val clave: String = "", val dns: String = "")
    fun cargar() = Config(p.getString("ip", "")!!, p.getString("usuario", "admin")!!, p.getString("clave", "")!!, p.getString("dns", "")!!)
    fun guardar(c: Config) = p.edit().putString("ip", c.ip).putString("usuario", c.usuario).putString("clave", c.clave).putString("dns", c.dns).apply()
}
lateinit var configMikrotik: MikrotikConfig

data class Ticket(
    val id: String = "", val codigo: String = "", val minutos: Int = 0, val fechaCreacion: String = "",
    val estado: String = "CREADO", val tiempoRestante: Int = 0,
    val velocidadBajada: String = "—", val velocidadSubida: String = "—",
    val ipUsuario: String = "", val macUsuario: String = "", val fotoBase64: String = ""
)

class TicketManager(c: Context) {
    private val f = c.filesDir.resolve("tickets_guardados.txt")
    fun cargar() = mutableListOf<Ticket>().apply {
        runCatching {
            if (!f.exists()) return@runCatching
            f.bufferedReader().use { it.lineSequence().forEach { l ->
                val d = l.split("|")
                if (d.size >= 10) add(Ticket(d[0],d[1],d[2].toIntOrNull()?:0,d[3],d[4],d[5].toIntOrNull()?:0,d[6],d[7],d[8],d[9],d.getOrNull(10)?: ""))
            }}
        }
    }
    fun guardar(t: List<Ticket>) {
        runCatching { f.bufferedWriter().use { t.forEach { l -> it.append("${l.id}|${l.codigo}|${l.minutos}|${l.fechaCreacion}|${l.estado}|${l.tiempoRestante}|${l.velocidadBajada}|${l.velocidadSubida}|${l.ipUsuario}|${l.macUsuario}|${l.fotoBase64}\n") } } }
    }
}
lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()

fun generarCodigoQR(texto: String, tam: Int = 300): android.graphics.Bitmap {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tam, tam)
    return android.graphics.Bitmap.createBitmap(tam, tam, android.graphics.Bitmap.Config.RGB_565).apply {
        for (x in 0 until tam) for (y in 0 until tam) setPixel(x,y,if(m[x,y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
}

fun escucharTicketsFirebase() {
    db.child("historial").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            listaTickets.clear()
            s.children.forEach { n ->
                val cod = n.child("codigo").getValue(String::class.java) ?: return@forEach
                if (cod.length != 6 || !cod.all { it.isDigit() }) return@forEach
                val mins = n.child("tiempo_minutos").getValue(Int::class.java) ?: ((n.child("monto").getValue(Double::class.java) ?: 0.0) * 100).toInt()
                listaTickets.add(Ticket(n.key!!, cod, mins, n.child("fechaCreacion").getValue(String::class.java) ?: "",
                    if (n.child("leido_por_portal").getValue(Boolean::class.java) == true) "ACTIVO" else "CREADO", mins*60))
            }
            gestorTickets.guardar(listaTickets)
        }
        override fun onCancelled(e: DatabaseError) {}
    })
}

@Composable fun VentanaConfig(onCerrar: () -> Unit, alGuardar: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = remember { configMikrotik.cargar() }
    var ip by remember { mutableStateOf(c.ip) }
    var usr by remember { mutableStateOf(c.usuario) }
    var pwd by remember { mutableStateOf(c.clave) }
    var dns by remember { mutableStateOf(c.dns) }
    var msg by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCerrar) {
        Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(28.dp)) {
                Text("⚙️ CONFIGURACIÓN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(ip, {ip=it}, label={Text("IP")}, Modifier.fillMaxWidth(), singleLine=true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(usr, {usr=it}, label={Text("Usuario")}, Modifier.fillMaxWidth(), singleLine=true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pwd, {pwd=it}, label={Text("Contraseña")}, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine=true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(dns, {dns=it}, label={Text("DNS (opcional)")}, Modifier.fillMaxWidth(), singleLine=true)
                Spacer(Modifier.height(20.dp))
                msg?.let { Text(it, color = if (it.startsWith("✅")) Color(0xFF22C55E) else Color(0xFFEF4444)) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({probando=true; msg="🔄 Conectando..."; CoroutineScope(Dispatchers.IO).launch{
                        val ok = MikrotikAPI.probarConexion(ip,8080,usr,pwd)
                        withContext(Dispatchers.Main){ msg=if(ok)"✅ CONECTADO" else MikrotikAPI.ultimoError; probando=false }
                    }}, Modifier.weight(1f), enabled=!probando) { Text(if(probando)"⏳" else "PROBAR") }
                    Button({if(ip.isBlank()){msg="❌ IP obligatoria";return@Button}; configMikrotik.guardar(MikrotikConfig.Config(ip,usr,pwd,dns)); alGuardar(); msg="✅ Guardado"; Toast.makeText(ctx,"Guardado",Toast.LENGTH_SHORT).show()}, Modifier.weight(1f), colors=ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("GUARDAR") }
                }
                Spacer(Modifier.height(16.dp))
                Button(onCerrar, Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(Color(0xFF818CF8))) { Text("CERRAR") }
            }
        }
    }
}

@Composable fun SeccionClientesLAN(d: DatosRouter) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFF3E5F5))) {
        Column(Modifier.padding(16.dp)) {
            Text("💻 CLIENTES CONECTADOS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
            Spacer(Modifier.height(12.dp))
            if (!d.conectado) Text("⚠️ Conecta al router", color = Color.Gray)
            else if (d.clientes.isEmpty()) Text("📭 Sin clientes", color = Color.Gray)
            else {
                Row(Modifier.fillMaxWidth()) {
                    Text("IP", Modifier.weight(0.28f), fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    Text("NOMBRE", Modifier.weight(0.28f), fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    Text("↓ BAJADA / ↑ SUBIDA", Modifier.weight(0.44f), fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                }
                Spacer(Modifier.height(8.dp))
                d.clientes.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                        Text(c.ip, Modifier.weight(0.28f), fontSize = 12.sp)
                        Text(c.nombre.ifBlank{"—"}, Modifier.weight(0.28f), fontSize = 12.sp)
                        Row(Modifier.weight(0.44f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text("${c.velocidadBajada} ↓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E)) // 🟢 VERDE = BAJADA
                            Text(" / ", fontSize = 12.sp, color = Color.Gray)
                            Text("${c.velocidadSubida} ↑", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) // 🔴 ROJO = SUBIDA
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical=2.dp), color = Color(0xFFE0E0E0))
                }
            }
        }
    }
}

@Composable fun TarjetaTicket(t: Ticket) {
    val qr = remember(t.codigo) { if(t.codigo.isNotEmpty()) generarCodigoQR(t.codigo) else null }
    Card(Modifier.fillMaxWidth().padding(vertical=8.dp), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(when(t.estado){"CREADO"->Color(0xFFE3F2FD)"ACTIVO"->Color(0xFFE8F5E9)"VENCIDO"->Color(0xFFFFEBEE) else->Color(0xFFF5F5F5)})) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            qr?.let { Image(it.asImageBitmap(), null, Modifier.size(100.dp).padding(end=16.dp)) }
                ?: Box(Modifier.size(100.dp), Alignment.Center) { Text("—", fontSize = 24.sp, color = Color.LightGray) }
            Column(Modifier.weight(1f)) {
                Text("CÓDIGO: ${t.codigo}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("⏱️ ${t.minutos} min", fontSize = 14.sp)
                Text("📅 ${t.fechaCreacion}", fontSize = 13.sp, color = Color.Gray)
                Text(when(t.estado){"CREADO"->"🟡 CREADO""ACTIVO"->"🟢 ACTIVO — ${t.tiempoRestante} min""VENCIDO"->"🔴 VENCIDO" else->t.estado},
                    color = when(t.estado){"CREADO"->Color(0xFFF57C00)"ACTIVO"->Color(0xFF22C55E)"VENCIDO"->Color(0xFFEF4444) else->Color.Gray},
                    fontWeight = FontWeight.Bold)
                if(t.ipUsuario.isNotEmpty()) Text("📱 IP: ${t.ipUsuario}", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable fun VentanaTickets(tit: String, filtro: String?, cerrar: ()->Unit) {
    val l = remember(filtro) { if(filtro==null) listaTickets else listaTickets.filter{it.estado==filtro} }
    Dialog(onDismissRequest = cerrar) {
        Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(tit, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom=16.dp))
                if(l.isEmpty()) Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { Text("📭 Vacío", fontSize = 16.sp, color = Color.Gray) }
                else Column(Modifier.verticalScroll(rememberScrollState())) { l.forEach { TarjetaTicket(it) } }
                Spacer(Modifier.height(16.dp))
                Button(cerrar, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) { Text("CERRAR", fontSize = 16.sp) }
            }
        }
    }
}

@Composable fun Boton(texto: String, c: Color, mod: Modifier, onClick: ()->Unit) {
    Button(onClick, mod.height(55.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(c)) { Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable fun PantallaPrincipal() {
    var abrirConfig by remember { mutableStateOf(false) }
    var abrirCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var datos by remember { mutableStateOf(DatosRouter()) }
    var cargando by remember { mutableStateOf(false) }
    var recargar by remember { mutableStateOf(false) }
    val cfg = remember(recargar) { configMikrotik.cargar() }
    LaunchedEffect(Unit) { escucharTicketsFirebase() }
    LaunchedEffect(cfg.ip, recargar) {
        if(cfg.ip.isBlank()) return@LaunchedEffect
        while(isActive) {
            cargando = true
            datos = MikrotikAPI.obtenerTodo(cfg.ip, 8080, cfg.usuario, cfg.clave)
            cargando = false
            delay(2000)
        }
    }
    val creados by remember { derivedStateOf { listaTickets.count{it.estado=="CREADO"} } }
    val activos by remember { derivedStateOf { listaTickets.count{it.estado=="ACTIVO"} } }
    val vencidos by remember { derivedStateOf { listaTickets.count{it.estado=="VENCIDO"} } }

    MaterialTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Color(0xFFF5F5F5)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎟️ PAOYHAN TICKETS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.padding(vertical=16.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color(0xFFFFF3E0))) {
                Column(Modifier.padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("📡 RB750Gr3", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        IconButton({abrirConfig=true}) { Icon(Icons.Default.Settings, null, tint = Color(0xFF6366F1), modifier = Modifier.size(28.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    if(cfg.ip.isBlank()) Text("⚠️ Toca ⚙️ para configurar", color = Color.Gray)
                    else if(!datos.conectado) Row(Alignment.CenterVertically) {
                        Text("🔄 Conectando a ${cfg.ip}...", color = Color(0xFFE65100))
                        if(cargando) CircularProgressIndicator(Modifier.size(18.dp).padding(start=8.dp), strokeWidth = 2.dp)
                    }
                    else {
                        Text("🌐 IP: ${cfg.ip}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💻 CPU", color = Color.Gray); Text("${datos.cpu}%", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💾 RAM", color = Color.Gray); Text("${datos.ram}%", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↓ BAJADA", color = Color.Gray)
                                Text(datos.bajadaEth1, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF22C55E)) // 🟢 VERDE
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↑ SUBIDA", color = Color.Gray)
                                Text(datos.subidaEth1, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFEF4444)) // 🔴 ROJO
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SeccionClientesLAN(datos)
            Spacer(Modifier.height(24.dp))
            Button({abrirCreados=true}, Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) {
                Text("📋 TICKETS CREADOS ($creados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                Boton("🟢 ACTIVOS ($activos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos=true }
                Boton("🔴 VENCIDOS ($vencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos=true }
            }
            Spacer(Modifier.height(40.dp))
        }
        if(abrirConfig) VentanaConfig({abrirConfig=false}) { recargar = !recargar }
        if(abrirCreados) VentanaTickets("📋 CREADOS", "CREADO") { abrirCreados=false }
        if(abrirActivos) VentanaTickets("🟢 ACTIVOS", "ACTIVO") { abrirActivos=false }
        if(abrirVencidos) VentanaTickets("🔴 VENCIDOS", "VENCIDO") { abrirVencidos=false }
    }
}
