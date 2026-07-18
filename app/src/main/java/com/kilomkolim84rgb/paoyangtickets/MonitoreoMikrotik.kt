import io.github.mmilet.mikrotik.MikrotikClient
import io.github.mmilet.mikrotik.api.response.Result
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MonitoreoMikrotik {

    private var cliente: MikrotikClient? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var conectado = false
        private set

    // DATOS QUE SE VAN A MOSTRAR EN LA PANTALLA
    var ipRouter = "---"
    var cpu = "--"
    var ram = "--"
    var temperatura = "--"
    var interfaces = mutableListOf<Map<String, String>>()
    var onDatosCambiados: (() -> Unit)? = null

    // ⚙️ CONECTAR AL MIKROTIK
    fun conectar(ip: String, puerto: Int, usuario: String, clave: String): Boolean {
        return try {
            cliente = MikrotikClient(ip, puerto, usuario, clave)
            cliente?.connect()
            conectado = true
            ipRouter = ip
            iniciarLecturaAutomatica()
            true
        } catch (e: Exception) {
            conectado = false
            false
        }
    }

    // 🔄 LECTURA CADA 2 SEGUNDOS EN TIEMPO REAL
    private fun iniciarLecturaAutomatica() {
        scope.launch {
            while (conectado) {
                leerDatosSistema()
                leerInterfaces()
                delay(2000) // Se actualiza cada 2 segundos
            }
        }
    }

    // 📊 LEER CPU, RAM, TEMPERATURA
    private suspend fun leerDatosSistema() {
        return suspendCancellableCoroutine { cont ->
            cliente?.execute("/system/resource/print") { resultado ->
                if (resultado is Result.Success && resultado.data.isNotEmpty()) {
                    val datos = resultado.data[0]
                    
                    cpu = datos["cpu-load"] ?: "--"
                    val memoriaTotal = datos["total-memory"]?.toLongOrNull() ?: 1L
                    val memoriaUsada = datos["free-memory"]?.toLongOrNull() ?: 0L
                    val porcentajeRam = 100 - ((memoriaUsada * 100) / memoriaTotal)
                    ram = if (porcentajeRam > 0) "$porcentajeRam" else "--"
                    temperatura = datos["cpu-temperature"] ?: "--"
                    
                    onDatosCambiados?.invoke()
                }
                cont.resume(Unit) {}
            }
        }
    }

    // 🌐 LEER INTERFACES Y VELOCIDADES
    private suspend fun leerInterfaces() {
        return suspendCancellableCoroutine { cont ->
            cliente?.execute("/interface/print", listOf("stats")) { resultado ->
                if (resultado is Result.Success) {
                    interfaces.clear()
                    resultado.data.forEach { iface ->
                        val nombre = iface["name"] ?: ""
                        val rxBytes = iface["rx-byte"]?.toLongOrNull() ?: 0L
                        val txBytes = iface["tx-byte"]?.toLongOrNull() ?: 0L
                        
                        interfaces.add(mapOf(
                            "nombre" to nombre,
                            "download" to calcularVelocidad(rxBytes),
                            "upload" to calcularVelocidad(txBytes)
                        ))
                    }
                    onDatosCambiados?.invoke()
                }
                cont.resume(Unit) {}
            }
        }
    }

    private fun calcularVelocidad(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes bps"
            bytes < 1024 * 1024 -> "${bytes / 1024} Kbps"
            else -> "${bytes / (1024 * 1024)} Mbps"
        }
    }

    // ❌ DESCONECTAR
    fun desconectar() {
        scope.cancel()
        cliente?.disconnect()
        cliente = null
        conectado = false
        ipRouter = "---"
        cpu = "--"
        ram = "--"
        temperatura = "--"
        interfaces.clear()
        onDatosCambiados?.invoke()
    }
}
