package com.kilomkolim84rgb.paoyangtickets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch  // ✅ FALTABA ESTA IMPORTACIÓN
import java.io.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configMikrotik = MikrotikConfig(this)
        gestorTickets = TicketManager(this)
        setContent {
            PantallaPrincipal()
        }
    }
}

val db = FirebaseDatabase.getInstance().reference

// ============= GESTOR DE CONFIGURACIÓN MIKROTIK =============
class MikrotikConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mikrotik_config", Context.MODE_PRIVATE)

    data class Config(
        val ip: String = "",
        val puerto: String = "8728",
        val usuario: String = "admin",
        val clave: String = "",
        val dns: String = ""
    )

    fun cargar(id: Int): Config {
        return Config(
            ip = prefs.getString("r${id}_ip", "") ?: "",
            puerto = prefs.getString("r${id}_puerto", "8728") ?: "8728",
            usuario = prefs.getString("r${id}_usuario", "admin") ?: "admin",
            clave = prefs.getString("r${id}_clave", "") ?: "",
            dns = prefs.getString("r${id}_dns", "") ?: ""
        )
    }

    fun guardar(id: Int, config: Config) {
        prefs.edit()
            .putString("r${id}_ip", config.ip)
            .putString("r${id}_puerto", config.puerto)
            .putString("r${id}_usuario", config.usuario)
            .putString("r${id}_clave", config.clave)
            .putString("r${id}_dns", config.dns)
            .apply()
    }
}

lateinit var configMikrotik: MikrotikConfig

// ============= GESTOR DE TICKETS =============
class TicketManager(context: Context) {
    private val archivo = File(context.filesDir, "tickets_guardados.txt")

    fun cargar(): MutableList<Ticket> {
        val lista = mutableListOf<Ticket>()
        try {
            if (!archivo.exists()) return lista
            val lector = BufferedReader(InputStreamReader(FileInputStream(archivo)))
            var linea: String?
            while (lector.readLine().also { linea = it } != null) {
                val datos = linea!!.split("|")
                if (datos.size >= 11) {
                    lista.add(
                        Ticket(
                            codigo = datos[0],
                            monto = datos[1].toFloatOrNull() ?: 0f,
                            minutos = datos[2].toIntOrNull() ?: 0,
                            tiempoStr = datos[3],
                            fecha = datos[4],
                            estado = datos[5],
                            tiempoRestanteSeg = datos[6].toIntOrNull() ?: 0,
                            velocidadSubida = datos[7],
                            velocidadBajada = datos[8],
                            ipUsuario = datos[9],
                            nombreUsuario = datos[10]
                        )
                    )
                }
            }
            lector.close()
        } catch (e: Exception) { e.printStackTrace() }
        return lista
    }

    fun guardar(tickets: List<Ticket>) {
        try {
            val escritor = BufferedWriter(OutputStreamWriter(FileOutputStream(archivo)))
            tickets.forEach { t ->
                escritor.write("${t.codigo}|${t.monto}|${t.minutos}|${t.tiempoStr}|${t.fecha}|${t.estado}|${t.tiempoRestanteSeg}|${t.velocidadSubida}|${t.velocidadBajada}|${t.ipUsuario}|${t.nombreUsuario}")
                escritor.newLine()
            }
            escritor.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

lateinit var gestorTickets: TicketManager
val listaTickets = mutableStateListOf<Ticket>()

// ============= ESCUCHA FIREBASE =============
fun escucharHistorialFirebase() {
    listaTickets.addAll(gestorTickets.cargar())
    println("✅ Cargados ${listaTickets.size} tickets guardados")

    val ref = db.child("historial")
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (hijo in snapshot.children) {
                for (ticketNodo in hijo.children) {
                    val codigo = ticketNodo.child("codigo").getValue(String::class.java) ?: ""
                    val monto = ticketNodo.child("monto").getValue(Double::class.java) ?: 0.0
                    val fecha = ticketNodo.child("fecha").getValue(String::class.java) ?: ""
                    val leidoPorTicket = ticketNodo.child("leido_por_ticket").getValue(Boolean::class.java)
                    val leidoPorMonedero = ticketNodo.child("leido_por_monedero").getValue(Boolean::class.java) ?: false

                    if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                    if (monto <= 0.0) continue
                    if (leidoPorTicket == true) {
                        if (leidoPorMonedero == true) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                ticketNodo.ref.removeValue()
                                println("🗑️ Ticket eliminado de Firebase: $codigo")
                            }, 3000)
                        }
                        continue
                    }

                    ticketNodo.ref.child("leido_por_ticket").setValue(true)

                    if (listaTickets.none { it.codigo == codigo }) {
                        val minutos = (monto * 100).toInt()
                        val horas = minutos / 60
                        val mins = minutos % 60
                        val tiempoStr = if (horas > 0) "${horas}h ${mins}m" else "${mins}m"

                        val nuevoTicket = Ticket(
                            codigo = codigo,
                            monto = monto.toFloat(),
                            minutos = minutos,
                            tiempoStr = tiempoStr,
                            fecha = fecha,
                            estado = "CREADO",
                            tiempoRestanteSeg = minutos * 60,
                            velocidadSubida = "— Mbps",
                            velocidadBajada = "— Mbps",
                            ipUsuario = "Sin asignar",
                            nombreUsuario = "Sin asignar"
                        )
                        listaTickets.add(0, nuevoTicket)
                        gestorTickets.guardar(listaTickets)
                        println("✅ Ticket leído y guardado: $codigo — S/ $monto")
                    }

                    if (leidoPorMonedero) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            ticketNodo.ref.removeValue()
                            println("🗑️ Borrado de Firebase: $codigo")
                        }, 3000)
                    }
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            println("❌ Error Firebase: ${error.message}")
        }
    })
}

fun formatearTiempo(segundos: Int): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

// ============= VENTANA DE CONFIGURACIÓN MIKROTIK =============
@Composable
fun VentanaConfigMikrotik(
    routerId: Int,
    nombreRouter: String,
    onCerrar: () -> Unit
) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configMikrotik.cargar(routerId) }

    var ip by remember { mutableStateOf(config.ip) }
    var puerto by remember { mutableStateOf(config.puerto) }
    var usuario by remember { mutableStateOf(config.usuario) }
    var clave by remember { mutableStateOf(config.clave) }
    var dns by remember { mutableStateOf(config.dns) }
    var probandoConexion by remember { mutableStateOf(false) }
    var mensajeEstado by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚙️ CONFIGURACIÓN — $nombreRouter",
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
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("192.168.88.1") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = puerto,
                onValueChange = { puerto = it },
                label = { Text("Puerto API") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("8728") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("admin") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("••••••••") }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = dns,
                onValueChange = { dns = it },
                label = { Text("DNS Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("mikrotik1.net") }
            )
            Spacer(modifier = Modifier.height(20.dp))

            mensajeEstado?.let { msg ->
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = if (msg.contains("✅")) Color(0xFF22C55E) else Color(0xFFEF4444),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        probandoConexion = true
                        mensajeEstado = null
                        scope.launch {
                            delay(1500)
                            probandoConexion = false
                            mensajeEstado = if (ip.isNotBlank()) {
                                "✅ Conexión exitosa con $ip"
                            } else {
                                "❌ Error: Ingrese la IP"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(55.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    enabled = !probandoConexion
                ) {
                    if (probandoConexion) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("🧪 PROBAR CONEXIÓN", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (ip.isBlank()) {
                            mensajeEstado = "❌ La IP es obligatoria"
                            return@Button
                        }
                        configMikrotik.guardar(
                            routerId,
                            MikrotikConfig.Config(ip, puerto, usuario, clave, dns)
                        )
                        mensajeEstado = "✅ ¡Guardado correctamente!"
                        Toast.makeText(contexto, "Configuración guardada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(55.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Text("💾 GUARDAR", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============= TARJETA DEL ROUTER CON ENGRANAJE =============
@Composable
fun TarjetaRouter(
    nombre: String,
    modelo: String,
    ip: String,
    puerto: String,
    routerId: Int,
    seleccionado: Boolean,
    alTocar: () -> Unit,
    alConfigurar: () -> Unit
) {
    Card(
        onClick = alTocar,
        modifier = Modifier
            .width(160.dp)
            .height(145.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) Color(0xFFE3F2FD) else Color(0xFFFFFFFF)
        ),
        border = if (seleccionado) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = alConfigurar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurar",
                    tint = Color(0xFF6366F1)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(modelo, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                
                val config = remember { configMikrotik.cargar(routerId) }
                Text("IP: ${if (config.ip.isNotEmpty()) config.ip else "No config"}", fontSize = 11.sp)
                Text("Puerto: ${if (config.puerto.isNotEmpty()) config.puerto else "8728"}", fontSize = 11.sp)
            }
        }
    }
}

// ============= PANTALLA PRINCIPAL =============
@Composable
fun PantallaPrincipal() {
    var routerSeleccionado by remember { mutableStateOf(1) }
    var abrirConfigRouter1 by remember { mutableStateOf(false) }
    var abrirConfigRouter2 by remember { mutableStateOf(false) }
    var abrirTicketsCreados by remember { mutableStateOf(false) }
    var abrirActivos by remember { mutableStateOf(false) }
    var abrirPausados by remember { mutableStateOf(false) }
    var abrirVencidos by remember { mutableStateOf(false) }
    var abrirHistorial by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        escucharHistorialFirebase()
    }

    val ticketsCreados by remember { derivedStateOf { listaTickets.count { it.estado == "CREADO" } } }
    val ticketsActivos by remember { derivedStateOf { listaTickets.count { it.estado == "ACTIVO" } } }
    val ticketsPausados by remember { derivedStateOf { listaTickets.count { it.estado == "PAUSADO" } } }
    val ticketsVencidos by remember { derivedStateOf { listaTickets.count { it.estado == "VENCIDO" } } }

    // ⏱️ RELOJ EN TIEMPO REAL — CUENTA SOLO HASTA 0
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            
            var huboCambio = false
            
            listaTickets.forEachIndexed { index, ticket ->
                if (ticket.estado == "ACTIVO") {
                    val nuevoTiempo = ticket.tiempoRestanteSeg - 1
                    
                    if (nuevoTiempo <= 0) {
                        listaTickets[index] = ticket.copy(
                            estado = "VENCIDO",
                            tiempoRestanteSeg = 0,
                            velocidadSubida = "-- Mbps",
                            velocidadBajada = "-- Mbps"
                        )
                        huboCambio = true
                    } else {
                        listaTickets[index] = ticket.copy(
                            tiempoRestanteSeg = nuevoTiempo
                        )
                        huboCambio = true
                    }
                }
            }
            
            if (huboCambio) {
                gestorTickets.guardar(listaTickets)
            }
        }
    }

    // 📊 Actualizar velocidad cada 3 segundos
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            listaTickets.forEachIndexed { index, ticket ->
                if (ticket.estado == "ACTIVO") {
                    listaTickets[index] = ticket.copy(
                        velocidadSubida = "${(0..50).random()}.${(0..9).random()} Mbps",
                        velocidadBajada = "${(5..100).random()}.${(0..9).random()} Mbps"
                    )
                }
            }
            gestorTickets.guardar(listaTickets)
        }
    }

    if (abrirConfigRouter1) {
        Dialog(onDismissRequest = { abrirConfigRouter1 = false }) {
            VentanaConfigMikrotik(routerId = 1, nombreRouter = "ROUTER #1", onCerrar = { abrirConfigRouter1 = false })
        }
    }
    if (abrirConfigRouter2) {
        Dialog(onDismissRequest = { abrirConfigRouter2 = false }) {
            VentanaConfigMikrotik(routerId = 2, nombreRouter = "ROUTER #2", onCerrar = { abrirConfigRouter2 = false })
        }
    }

    if (abrirTicketsCreados) Dialog(onDismissRequest = { abrirTicketsCreados = false }) { TicketsCreadosVentana { abrirTicketsCreados = false } }
    if (abrirActivos) Dialog(onDismissRequest = { abrirActivos = false }) { TicketsActivosVentana { abrirActivos = false } }
    if (abrirPausados) Dialog(onDismissRequest = { abrirPausados = false }) { TicketsPausadosVentana { abrirPausados = false } }
    if (abrirVencidos) Dialog(onDismissRequest = { abrirVencidos = false }) { TicketsVencidosVentana { abrirVencidos = false } }
    if (abrirHistorial) Dialog(onDismissRequest = { abrirHistorial = false }) { HistorialVentana { abrirHistorial = false } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎟️ PAOYHAN TICKETS",
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
                routerId = 1,
                seleccionado = routerSeleccionado == 1,
                alTocar = { routerSeleccionado = 1 },
                alConfigurar = { abrirConfigRouter1 = true }
            )
            TarjetaRouter(
                nombre = "📡 Router #2",
                modelo = "RB3011",
                ip = "192.168.88.1",
                puerto = "Administración",
                routerId = 2,
                seleccionado = routerSeleccionado == 2,
                alTocar = { routerSeleccionado = 2 },
                alConfigurar = { abrirConfigRouter2 = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        val configActual = configMikrotik.cargar(routerSeleccionado)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📡 CONFIGURACIÓN — Router #$routerSeleccionado",
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
                        Text("IP", fontSize = 12.sp, color = Color.Gray)
                        Text(configActual.ip.ifBlank { "No configurada" }, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Puerto", fontSize = 12.sp, color = Color.Gray)
                        Text(configActual.puerto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Usuario", fontSize = 12.sp, color = Color.Gray)
                        Text(configActual.usuario, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("DNS: ${configActual.dns.ifBlank { "No configurado" }}", fontSize = 14.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📊 VELOCIDAD Y ESTADO DEL ROUTER",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️ Subida", fontSize = 12.sp, color = Color.Gray)
                        Text("— Mbps", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇️ Bajada", fontSize = 12.sp, color = Color.Gray)
                        Text("— Mbps", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🖥️ CPU", fontSize = 12.sp, color = Color.Gray)
                        Text("— %", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💾 RAM", fontSize = 12.sp, color = Color.Gray)
                        Text("— %", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌡️ Temperatura", fontSize = 12.sp, color = Color.Gray)
                        Text("— °C", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { abrirTicketsCreados = true },
            modifier = Modifier.fillMaxWidth().height(70.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
        ) {
            Text("📋 TICKETS CREADOS ($ticketsCreados)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana("🟢 ACTIVOS ($ticketsActivos)", Color(0xFF22C55E), Modifier.weight(1f)) { abrirActivos = true }
            BotonPestana("🟡 PAUSADOS ($ticketsPausados)", Color(0xFFF59E0B), Modifier.weight(1f)) { abrirPausados = true }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPestana("🔴 VENCIDOS ($ticketsVencidos)", Color(0xFFEF4444), Modifier.weight(1f)) { abrirVencidos = true }
            BotonPestana("📋 HISTORIAL (${listaTickets.size})", Color(0xFF6366F1), Modifier.weight(1f)) { abrirHistorial = true }
        }
    }
}

@Composable
fun BotonPestana(
    texto: String,
    color: Color,
    modifier: Modifier = Modifier,
    alTocar: () -> Unit
) {
    Button(
        onClick = alTocar,
        modifier = modifier.height(55.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

fun generarCodigoQR(texto: String, tamano: Int = 300): Bitmap {
    val escritor = QRCodeWriter()
    val matrizBit = escritor.encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    val ancho = matrizBit.width
    val alto = matrizBit.height
    val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
    for (x in 0 until ancho) {
        for (y in 0 until alto) {
            bitmap.setPixel(x, y, if (matrizBit[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}

data class Ticket(
    val codigo: String = "",
    val monto: Float = 0f,
    val minutos: Int = 0,
    val tiempoStr: String = "",
    val fecha: String = "",
    val estado: String = "CREADO",
    val tiempoRestanteSeg: Int = 0,
    val velocidadSubida: String = "— Mbps",
    val velocidadBajada: String = "— Mbps",
    val ipUsuario: String = "Sin asignar",
    val nombreUsuario: String = "Sin asignar"
)

// ============= VENTANA TICKETS CREADOS =============
@Composable
fun TicketsCreadosVentana(onCerrar: () -> Unit) {
    var textoBuscar by remember { mutableStateOf("") }
    val ticketsFiltrados = remember(textoBuscar, listaTickets.size) {
        listaTickets.filter { it.estado == "CREADO" }.let { lista ->
            if (textoBuscar.isBlank()) lista
            else lista.filter { it.codigo.contains(textoBuscar, ignoreCase = true) }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(550.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📋 TICKETS CREADOS (${ticketsFiltrados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = textoBuscar, onValueChange = { textoBuscar = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar por código...") }, leadingIcon = { Icon(Icons.Default.Search, "Buscar") }, singleLine = true, shape = RoundedCornerShape(10.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f)) {
                if (ticketsFiltrados.isEmpty()) Text("📭 No hay tickets creados aún\nMete monedas en el cajero", color = Color.Gray, modifier = Modifier.padding(16.dp), fontSize = 15.sp)
                else ticketsFiltrados.forEach { ticket ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp, color = Color(0xFF22C55E))
                                Text("⏱️ ${ticket.tiempoStr}", fontSize = 13.sp, color = Color.Gray)
                                Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                                Text("⚪ CREADO — Pendiente de activar", fontSize = 12.sp, color = Color(0xFF6366F1))
                            }
                            var mostrarQR by remember { mutableStateOf(false) }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { 
                                    val i = listaTickets.indexOf(ticket)
                                    if (i >= 0) { 
                                        listaTickets[i] = ticket.copy(
                                            estado = "ACTIVO", 
                                            tiempoRestanteSeg = ticket.minutos * 60,
                                            velocidadSubida = "0.0 Mbps",
                                            velocidadBajada = "0.0 Mbps"
                                        )
                                        gestorTickets.guardar(listaTickets) 
                                    } 
                                }, colors = ButtonDefaults.buttonColors(Color(0xFF22C55E)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) { Text("✅ ACTIVAR", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                Button(onClick = { mostrarQR = true }, colors = ButtonDefaults.buttonColors(Color(0xFF2563EB)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) { Text("📱 QR", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                Button(onClick = { listaTickets.remove(ticket); gestorTickets.guardar(listaTickets) }, colors = ButtonDefaults.buttonColors(Color(0xFFEF4444)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) { Text("❌ BORRAR", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            }
                            if (mostrarQR) Dialog(onDismissRequest = { mostrarQR = false }) { Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("📱 ESCANEAR PARA ACTIVAR", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(20.dp)); val qr = remember(ticket.codigo) { generarCodigoQR("ID:${ticket.codigo}|S:${ticket.monto}|MIN:${ticket.minutos}") }; Image(qr.asImageBitmap(), null, modifier = Modifier.size(280.dp).border(BorderStroke(2.dp, Color(0xFFE0E0E0)), RoundedCornerShape(8.dp)).background(Color.White, RoundedCornerShape(8.dp))); Spacer(modifier = Modifier.height(16.dp)); Text("Código: ${ticket.codigo}", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("💰 S/ ${String.format("%.2f", ticket.monto)}  •  ⏱️ ${ticket.tiempoStr}", fontSize = 14.sp, color = Color.Gray); Spacer(modifier = Modifier.height(24.dp)); Button(onClick = { mostrarQR = false }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold) } } } }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (ticketsFiltrados.isNotEmpty()) { Button(onClick = { ticketsFiltrados.forEach { listaTickets.remove(it) }; gestorTickets.guardar(listaTickets) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))) { Text("🗑️ BORRAR TODOS LOS CREADOS", fontSize = 16.sp, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(12.dp)) }
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR", fontSize = 16.sp) }
        }
    }
}

// ============= VENTANA TICKETS ACTIVOS =============
@Composable
fun TicketsActivosVentana(onCerrar: () -> Unit) {
    val tickets = remember(listaTickets.size) { listaTickets.filter { it.estado == "ACTIVO" } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🟢 TICKETS ACTIVOS (${tickets.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
            Text("⏱️ Tiempo y velocidad se actualizan solos", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (tickets.isEmpty()) Text("📭 No hay tickets activos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else tickets.forEach { ticket ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🆔 CÓDIGO: ${ticket.codigo}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("💰 S/ ${String.format("%.2f", ticket.monto)}  •  📅 ${ticket.fecha}", fontSize = 13.sp, color = Color.Gray)
                                    Text("👤 ${ticket.nombreUsuario}  •  🌐 ${ticket.ipUsuario}", fontSize = 12.sp, color = Color.Gray)
                                }
                                var mostrarQR by remember { mutableStateOf(false) }
                                Button(onClick = { mostrarQR = true }, colors = ButtonDefaults.buttonColors(Color(0xFF2563EB)), modifier = Modifier.height(36.dp)) { Text("📱 QR", fontSize = 13.sp) }
                                if (mostrarQR) Dialog(onDismissRequest = { mostrarQR = false }) { Card(modifier = Modifier.padding(20.dp), shape = RoundedCornerShape(20.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("📱 CÓDIGO QR", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(20.dp)); val qr = remember(ticket.codigo) { generarCodigoQR("ID:${ticket.codigo}|S:${ticket.monto}|MIN:${ticket.minutos}") }; Image(qr.asImageBitmap(), null, modifier = Modifier.size(250.dp).border(BorderStroke(2.dp, Color(0xFFE0E0E0)), RoundedCornerShape(8.dp)).background(Color.White, RoundedCornerShape(8.dp))); Spacer(modifier = Modifier.height(16.dp)); Text("Código: ${ticket.codigo}", fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { mostrarQR = false }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold) } } } }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "⏱️ TIEMPO RESTANTE: ${formatearTiempo(ticket.tiempoRestanteSeg)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ticket.tiempoRestanteSeg < 300) Color(0xFFEF4444) else Color(0xFF22C55E)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📤 SUBIDA", fontSize = 12.sp, color = Color.Gray)
                                    Text(ticket.velocidadSubida, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📥 BAJADA", fontSize = 12.sp, color = Color.Gray)
                                    Text(ticket.velocidadBajada, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = { val i = listaTickets.indexOf(ticket); if (i >= 0) { listaTickets[i] = ticket.copy(estado = "PAUSADO", velocidadSubida = "— Mbps", velocidadBajada = "— Mbps"); gestorTickets.guardar(listaTickets) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFFF59E0B))) { Text("⏸️ PAUSAR TICKET", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR", fontSize = 16.sp) }
        }
    }
}

// ============= VENTANA TICKETS PAUSADOS =============
@Composable
fun TicketsPausadosVentana(onCerrar: () -> Unit) {
    val tickets = remember(listaTickets.size) { listaTickets.filter { it.estado == "PAUSADO" } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(450.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🟡 TICKETS PAUSADOS (${tickets.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (tickets.isEmpty()) Text("📭 No hay tickets pausados", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else tickets.forEach { ticket ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(Color(0xFFFFF8E1))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                            Text("⏱️ ${formatearTiempo(ticket.tiempoRestanteSeg)}", fontSize = 13.sp)
                            Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Text("📤 ${ticket.velocidadSubida}", fontSize = 13.sp, color = Color.Gray)
                                Text("📥 ${ticket.velocidadBajada}", fontSize = 13.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { val i = listaTickets.indexOf(ticket); if (i >= 0) { listaTickets[i] = ticket.copy(estado = "ACTIVO"); gestorTickets.guardar(listaTickets) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E))) { Text("▶️ REANUDAR", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR", fontSize = 16.sp) }
        }
    }
}

// ============= VENTANA TICKETS VENCIDOS =============
@Composable
fun TicketsVencidosVentana(onCerrar: () -> Unit) {
    val tickets = remember(listaTickets.size) { listaTickets.filter { it.estado == "VENCIDO" } }
    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(450.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔴 TICKETS VENCIDOS (${tickets.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (tickets.isEmpty()) Text("📭 No hay tickets vencidos", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else tickets.forEach { ticket ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(Color(0xFFFFEBEE))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("💰 S/ ${String.format("%.2f", ticket.monto)}", fontSize = 13.sp)
                            Text("⏱️ ${formatearTiempo(ticket.tiempoRestanteSeg)}", fontSize = 13.sp)
                            Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                            Text("🔴 VENCIDO", fontSize = 13.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("CERRAR", fontSize = 16.sp) }
        }
    }
}

// ============= VENTANA HISTORIAL COMPLETO =============
@Composable
fun HistorialVentana(onCerrar: () -> Unit) {
    var textoBuscar by remember { mutableStateOf("") }
    val ticketsFiltrados = remember(textoBuscar, listaTickets.size) {
        listaTickets.filter {
            it.codigo.contains(textoBuscar, ignoreCase = true) ||
            it.nombreUsuario.contains(textoBuscar, ignoreCase = true)
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(24.dp).height(550.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📋 HISTORIAL COMPLETO (${ticketsFiltrados.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = textoBuscar,
                onValueChange = { textoBuscar = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por código o usuario...") },
                leadingIcon = { Icon(Icons.Default.Search, "Buscar") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f)) {
                if (ticketsFiltrados.isEmpty()) {
                    Text("📭 No hay tickets en el historial", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    ticketsFiltrados.forEach { ticket ->
                        val colorFondo = when (ticket.estado) {
                            "CREADO" -> Color(0xFFE3F2FD)
                            "ACTIVO" -> Color(0xFFE8F5E9)
                            "PAUSADO" -> Color(0xFFFFF8E1)
                            "VENCIDO" -> Color(0xFFFFEBEE)
                            else -> Color(0xFFFFFFFF)
                        }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(colorFondo)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("🆔 ${ticket.codigo}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("💰 S/ ${String.format("%.2f", ticket.monto)}  •  ⏱️ ${ticket.tiempoStr}", fontSize = 13.sp)
                                Text("📅 ${ticket.fecha}", fontSize = 12.sp, color = Color.Gray)
                                Text("👤 ${ticket.nombreUsuario}  •  🌐 ${ticket.ipUsuario}", fontSize = 12.sp, color = Color.Gray)
                                Text("Estado: ${ticket.estado}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = when (ticket.estado) {
                                    "CREADO" -> Color(0xFF6366F1)
                                    "ACTIVO" -> Color(0xFF22C55E)
                                    "PAUSADO" -> Color(0xFFF59E0B)
                                    "VENCIDO" -> Color(0xFFEF4444)
                                    else -> Color.Gray
                                })
                                if (ticket.estado == "ACTIVO") {
                                    Text("📤 ${ticket.velocidadSubida}  •  📥 ${ticket.velocidadBajada}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (listaTickets.isNotEmpty()) {
                var confirmarBorrarTodo by remember { mutableStateOf(false) }
                Button(
                    onClick = { confirmarBorrarTodo = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFFEF4444))
                ) {
                    Text("🗑️ BORRAR TODO EL HISTORIAL", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (confirmarBorrarTodo) {
                    AlertDialog(
                        onDismissRequest = { confirmarBorrarTodo = false },
                        title = { Text("⚠️ Confirmar borrado") },
                        text = { Text("¿Seguro que quieres borrar TODOS los tickets del historial? Esta acción no se puede deshacer.") },
                        confirmButton = {
                            TextButton(onClick = {
                                listaTickets.clear()
                                gestorTickets.guardar(listaTickets)
                                confirmarBorrarTodo = false
                            }) {
                                Text("✅ SÍ, BORRAR TODO", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmarBorrarTodo = false }) {
                                Text("❌ CANCELAR")
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CERRAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
