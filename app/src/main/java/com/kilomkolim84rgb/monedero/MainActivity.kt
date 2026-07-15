package com.kilomkolim84rgb.paoyang

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.room.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

// ====================== ESTRUCTURA DE TICKETS ======================
@Entity
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val montoSoles: Int,
    val estado: String = "ACTIVO", // ACTIVO / USADO / VENCIDO
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActivacion: Long = 0,
    val ip: String = "--",
    val mac: String = "--",
    val fotoUrl: String = ""
)

@Dao
interface TicketDao {
    @Query("SELECT * FROM Ticket ORDER BY fechaCreacion DESC")
    suspend fun getAll(): List<Ticket>
    @Insert
    suspend fun insert(ticket: Ticket)
    @Query("DELETE FROM Ticket")
    suspend fun deleteAll()
}

@Database(entities = [Ticket::class], version = 1, exportSchema = false)
abstract class PaoyangDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao
    companion object {
        @Volatile private var INSTANCE: PaoyangDatabase? = null
        fun getDatabase(context: Context): PaoyangDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, PaoyangDatabase::class.java, "paoyang_db")
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

class PaoyangViewModel : ViewModel() {
    private val _tickets = MutableStateFlow<List<Ticket>>(emptyList())
    val tickets: StateFlow<List<Ticket>> = _tickets
    private var db: PaoyangDatabase? = null
    
    fun initDatabase(context: Context) {
        db = PaoyangDatabase.getDatabase(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { cargarTickets() }
    }

    fun crearTicket(montoSoles: Int) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            db?.ticketDao()?.insert(Ticket(montoSoles = montoSoles))
            cargarTickets()
        }
    }

    fun borrarTodos() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            db?.ticketDao()?.deleteAll()
            cargarTickets()
        }
    }
    
    private suspend fun cargarTickets() {
        _tickets.value = db?.ticketDao()?.getAll() ?: emptyList()
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var tts: TextToSpeech
    private val CHANNEL_ID = "paoyang_channel"
    private lateinit var viewModel: PaoyangViewModel
    
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        askNotificationPermission()
        
        viewModel = PaoyangViewModel()
        viewModel.initDatabase(this)
        
        setContent {
            MaterialTheme {
                PaoyangScreen(viewModel) { monto ->
                    viewModel.crearTicket(monto)
                    hablarTicket(monto)
                    mostrarNotificacion(monto)
                }
            }
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("es", "PE")
    }
    
    private fun hablarTicket(monto: Int) {
        val texto = "Se creó ticket de $monto soles, tiempo guardado"
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Paoyang Tickets", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    private fun mostrarNotificacion(monto: Int) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ticket creado")
            .setContentText("Ticket de $monto Soles generado correctamente")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
    
    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun PaoyangScreen(viewModel: PaoyangViewModel, onCrearTicket: (Int) -> Unit) {
    val tickets by viewModel.tickets.collectAsState()
    var montoSeleccionado by remember { mutableStateOf(1) }
    var showDialog by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("¿Borrar todos los tickets?") },
            text = { Text("Se eliminarán todos los registros. No se puede deshacer.") },
            confirmButton = {
                Button(onClick = { viewModel.borrarTodos(); showDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Borrar todo") }
            },
            dismissButton = { Button(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PAOYANG TICKETS", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        // CREAR TICKET
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Monto del Ticket:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if(montoSeleccionado > 1) montoSeleccionado-- }) { Text("-") }
                    Text("$montoSeleccionado SOLES", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { if(montoSeleccionado < 30) montoSeleccionado++ }) { Text("+") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onCrearTicket(montoSeleccionado) }, modifier = Modifier.fillMaxWidth()) { Text("GENERAR TICKET", fontSize = 16.sp) }
                Text("✅ Tiempo guardado: si no se usa no se pierde", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("BORRAR TODOS LOS TICKETS") }
        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Text("Historial - ${tickets.size} tickets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tickets) { ticket ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ticket.montoSoles} SOLES", fontWeight = FontWeight.Bold)
                            Text(ticket.estado, fontWeight = FontWeight.Medium,
                                color = if(ticket.estado == "ACTIVO") Color(0xFF2E7D32) else Color(0xFFC62828))
                        }
                        Text("Creado: ${dateFormat.format(Date(ticket.fechaCreacion))}", fontSize = 12.sp)
                        if(ticket.estado == "USADO") {
                            Text("Activado: ${dateFormat.format(Date(ticket.fechaActivacion))}", fontSize = 12.sp)
                            Text("IP: ${ticket.ip} | MAC: ${ticket.mac}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
