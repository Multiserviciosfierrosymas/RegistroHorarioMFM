package com.registrohorario_mfm

import androidx.compose.material.icons.filled.Refresh
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

private val ColabFondoGrisClaro = Color(0xFFF5F5F5)
private val ColabAzulPrimario = Color(0xFF1976D2)
private val ColabAzulBotonFlotante = Color(0xFF42A5F5)
private val ColabAzulFondoSeleccion = Color(0xFFE3F2FD)
private val ColabTextoOscuro = Color(0xFF212121)
private val ColabTextoGris = Color(0xFF757575)

@Composable
private fun colabTfColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = ColabAzulPrimario,
    unfocusedBorderColor = Color.LightGray,
    focusedLabelColor = ColabAzulPrimario,
    unfocusedLabelColor = ColabTextoGris
)

@Keep
data class AsistenciaColab(
    val idDoc: String = "",
    val codigoColaborador: String = "",
    val fecha: String = "",
    val turno: String = "",
    val horaEntrada: String = "",
    val horaSalida: String = "",
    val estado: String = ""
)

class ColaboradorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                color = ColabFondoGrisClaro
            ) {
                ColaboradorScreen(
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this@ColaboradorActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

fun calcularNumeroSemanaColab(fechaStr: String): Int {
    try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))
        val fechaDate = sdf.parse(fechaStr) ?: return 1

        val calBase = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = sdf.parse("04/01/2026") ?: Date()
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calTarget = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = fechaDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = calTarget.timeInMillis - calBase.timeInMillis
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        if (diffDays < 0) return 1
        return (diffDays / 7) + 1
    } catch (e: Exception) { return 1 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColaboradorScreen(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserUid = auth.currentUser?.uid

    var codigoColaborador by remember { mutableStateOf("Cargando...") }
    var nombreColaborador by remember { mutableStateOf("Colaborador") }
    var listaTodosMisRegistros by remember { mutableStateOf<List<AsistenciaColab>>(emptyList()) }

    val semanaActualGlobal = calcularNumeroSemanaColab(SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE")).format(Date()))
    var semanasDisponibles by remember { mutableStateOf<List<Int>>(emptyList()) }
    var semanaSeleccionada by remember { mutableStateOf(semanaActualGlobal) }

    var isLoading by remember { mutableStateOf(true) }
    var mostrarModalNuevoRegistro by remember { mutableStateOf(false) }
    var expandedSemana by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val cargarDatosColaborador: () -> Unit = {
        if (currentUserUid != null) {
            scope.launch {
                try {
                    isLoading = true
                    val userDoc = db.collection("USUARIOS").document(currentUserUid).get().await()
                    if (userDoc.exists()) {
                        val cod = userDoc.getString("codigoColaborador") ?: userDoc.getString("codColaborador") ?: "SIN-CODIGO"
                        val nom = userDoc.getString("nombre") ?: userDoc.getString("nombres") ?: "Colaborador"
                        val ape = userDoc.getString("apellidos") ?: ""
                        codigoColaborador = cod
                        nombreColaborador = "$nom $ape".trim()

                        val asistenciasSnapshot = db.collection("REGISTROS_ASISTENCIA")
                            .whereEqualTo("codigoColaborador", cod)
                            .get()
                            .await()

                        val registrosList = asistenciasSnapshot.documents.mapNotNull { doc ->
                            doc.toObject(AsistenciaColab::class.java)?.copy(idDoc = doc.id)
                        }.filter { it.estado != "ANULADO" }.sortedByDescending { it.fecha }

                        listaTodosMisRegistros = registrosList
                        val semanasSet = registrosList.map { calcularNumeroSemanaColab(it.fecha) }.toSet()
                        semanasDisponibles = if (semanasSet.isNotEmpty()) semanasSet.sorted() else listOf(semanaActualGlobal)
                        if (!semanasDisponibles.contains(semanaSeleccionada)) {
                            semanaSeleccionada = semanasDisponibles.last()
                        }
                    }
                } catch (e: Exception) {
                    codigoColaborador = "Error al cargar"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(currentUserUid) {
        cargarDatosColaborador()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = ColabFondoGrisClaro,
            topBar = {
                TopAppBar(
                    title = { Text("Panel de Colaborador", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = ColabTextoOscuro
                    ),
                    actions = {
                        IconButton(onClick = { cargarDatosColaborador() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = ColabAzulPrimario)
                        }
                        TextButton(onClick = onLogout) {
                            Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            },
            floatingActionButton = {
                if (codigoColaborador != "Cargando..." && codigoColaborador != "Error al cargar") {
                    FloatingActionButton(
                        onClick = { mostrarModalNuevoRegistro = true },
                        containerColor = ColabAzulBotonFlotante,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar Registro", modifier = Modifier.size(32.dp))
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Colaborador", fontSize = 12.sp, color = ColabTextoGris)
                        Text(text = nombreColaborador, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColabTextoOscuro)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Mis Registros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColabTextoOscuro)

                    ExposedDropdownMenuBox(
                        expanded = expandedSemana,
                        onExpandedChange = { expandedSemana = !expandedSemana },
                        modifier = Modifier.width(150.dp)
                    ) {
                        OutlinedTextField(
                            value = "Semana $semanaSeleccionada",
                            onValueChange = {},
                            readOnly = true,
                            colors = colabTfColors(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSemana) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSemana,
                            onDismissRequest = { expandedSemana = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            semanasDisponibles.forEach { sem ->
                                DropdownMenuItem(
                                    text = { Text("Semana $sem") },
                                    onClick = { semanaSeleccionada = sem; expandedSemana = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ColabAzulPrimario)
                    } else {
                        val registrosFiltrados = listaTodosMisRegistros.filter { calcularNumeroSemanaColab(it.fecha) == semanaSeleccionada }

                        if (registrosFiltrados.isEmpty()) {
                            Text(text = "No hay registros para la Semana $semanaSeleccionada.", modifier = Modifier.align(Alignment.Center), color = ColabTextoGris)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(registrosFiltrados) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column {
                                                    Text(text = item.fecha, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColabTextoOscuro)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(text = item.turno, fontSize = 14.sp, color = ColabTextoGris)
                                                }

                                                val horaSalidaTxt = if (item.horaSalida.isEmpty()) "En curso" else item.horaSalida
                                                Text(text = "${item.horaEntrada} - $horaSalidaTxt", fontSize = 14.sp, color = ColabTextoGris)
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.estado,
                                                    color = ColabAzulPrimario,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mostrarModalNuevoRegistro) {
            DialogoNuevoRegistroColaborador(
                codigoColaborador = codigoColaborador,
                nombreColaborador = nombreColaborador,
                onDismiss = { mostrarModalNuevoRegistro = false },
                onGuardadoExitoso = {
                    mostrarModalNuevoRegistro = false
                    cargarDatosColaborador()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoNuevoRegistroColaborador(
    codigoColaborador: String,
    nombreColaborador: String,
    onDismiss: () -> Unit,
    onGuardadoExitoso: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))

    val calHoy = Calendar.getInstance()
    val calAyer = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val calMañana = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    val fechaHoy = dateFormat.format(calHoy.time)
    val fechaAyer = dateFormat.format(calAyer.time)
    val fechaMañana = dateFormat.format(calMañana.time)

    // Fechas de Entrada y Salida separadas para permitir turnos cruzados (Ej: de noche a tarde del día siguiente)
    var fechaEntrada by remember { mutableStateOf(fechaHoy) }
    var fechaSalida by remember { mutableStateOf(fechaHoy) }

    var turnoSeleccionado by remember { mutableStateOf("DIA") }
    var expandedFechaEntrada by remember { mutableStateOf(false) }
    var expandedFechaSalida by remember { mutableStateOf(false) }

    var horaEntradaH by remember { mutableStateOf("08") }
    var horaEntradaM by remember { mutableStateOf("00") }
    var ampmEntrada by remember { mutableStateOf("AM") }
    var expandedAmPmEntrada by remember { mutableStateOf(false) }

    var horaSalidaH by remember { mutableStateOf("05") }
    var horaSalidaM by remember { mutableStateOf("00") }
    var ampmSalida by remember { mutableStateOf("PM") }
    var expandedAmPmSalida by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Nuevo Registro de Horario", color = ColabTextoOscuro) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nombreColaborador,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Colaborador") },
                    colors = colabTfColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Turno:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            turnoSeleccionado = "DIA"
                            fechaEntrada = fechaHoy
                            fechaSalida = fechaHoy
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (turnoSeleccionado == "DIA") ColabAzulFondoSeleccion else Color.White,
                            contentColor = if (turnoSeleccionado == "DIA") ColabAzulPrimario else ColabTextoGris
                        ),
                        border = BorderStroke(1.dp, if (turnoSeleccionado == "DIA") ColabAzulPrimario else Color.LightGray)
                    ) { Text("DÍA") }

                    OutlinedButton(
                        onClick = {
                            turnoSeleccionado = "NOCHE"
                            fechaEntrada = fechaAyer // Por defecto sugerimos ayer para turnos nocturnos largos
                            fechaSalida = fechaHoy
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (turnoSeleccionado == "NOCHE") ColabAzulFondoSeleccion else Color.White,
                            contentColor = if (turnoSeleccionado == "NOCHE") ColabAzulPrimario else ColabTextoGris
                        ),
                        border = BorderStroke(1.dp, if (turnoSeleccionado == "NOCHE") ColabAzulPrimario else Color.LightGray)
                    ) { Text("NOCHE / CRUZADO") }
                }

                // --- FECHA DE ENTRADA ---
                Text("Fecha de Entrada:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                ExposedDropdownMenuBox(
                    expanded = expandedFechaEntrada,
                    onExpandedChange = { if (!isSaving) expandedFechaEntrada = !expandedFechaEntrada }
                ) {
                    OutlinedTextField(
                        value = fechaEntrada,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSaving,
                        colors = colabTfColors(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFechaEntrada) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFechaEntrada,
                        onDismissRequest = { expandedFechaEntrada = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(text = { Text(fechaHoy) }, onClick = { fechaEntrada = fechaHoy; expandedFechaEntrada = false })
                        DropdownMenuItem(text = { Text(fechaAyer) }, onClick = { fechaEntrada = fechaAyer; expandedFechaEntrada = false })
                    }
                }

                // --- HORA DE ENTRADA ---
                Text("Hora de Entrada:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = horaEntradaH, onValueChange = { horaEntradaH = it }, label = { Text("HH") }, colors = colabTfColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Text(":", color = ColabTextoOscuro)
                    OutlinedTextField(value = horaEntradaM, onValueChange = { horaEntradaM = it }, label = { Text("MM") }, colors = colabTfColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedAmPmEntrada = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) { Text(ampmEntrada, color = ColabAzulPrimario) }
                        DropdownMenu(expanded = expandedAmPmEntrada, onDismissRequest = { expandedAmPmEntrada = false }, modifier = Modifier.background(Color.White)) {
                            DropdownMenuItem(text = { Text("AM") }, onClick = { ampmEntrada = "AM"; expandedAmPmEntrada = false })
                            DropdownMenuItem(text = { Text("PM") }, onClick = { ampmEntrada = "PM"; expandedAmPmEntrada = false })
                        }
                    }
                }

                // --- FECHA DE SALIDA (Permite cruzar al día siguiente) ---
                Text("Fecha de Salida:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                ExposedDropdownMenuBox(
                    expanded = expandedFechaSalida,
                    onExpandedChange = { if (!isSaving) expandedFechaSalida = !expandedFechaSalida }
                ) {
                    OutlinedTextField(
                        value = fechaSalida,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSaving,
                        colors = colabTfColors(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFechaSalida) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFechaSalida,
                        onDismissRequest = { expandedFechaSalida = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(text = { Text(fechaAyer) }, onClick = { fechaSalida = fechaAyer; expandedFechaSalida = false })
                        DropdownMenuItem(text = { Text(fechaHoy) }, onClick = { fechaSalida = fechaHoy; expandedFechaSalida = false })
                        DropdownMenuItem(text = { Text(fechaMañana) }, onClick = { fechaSalida = fechaMañana; expandedFechaSalida = false })
                    }
                }

                // --- HORA DE SALIDA ---
                Text("Hora de Salida:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = horaSalidaH, onValueChange = { horaSalidaH = it }, label = { Text("HH") }, colors = colabTfColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Text(":", color = ColabTextoOscuro)
                    OutlinedTextField(value = horaSalidaM, onValueChange = { horaSalidaM = it }, label = { Text("MM") }, colors = colabTfColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedAmPmSalida = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) { Text(ampmSalida, color = ColabAzulPrimario) }
                        DropdownMenu(expanded = expandedAmPmSalida, onDismissRequest = { expandedAmPmSalida = false }, modifier = Modifier.background(Color.White)) {
                            DropdownMenuItem(text = { Text("AM") }, onClick = { ampmSalida = "AM"; expandedAmPmSalida = false })
                            DropdownMenuItem(text = { Text("PM") }, onClick = { ampmSalida = "PM"; expandedAmPmSalida = false })
                        }
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fechaEntrada.isBlank() || fechaSalida.isBlank()) {
                        errorMessage = "Seleccione las fechas correctamente."
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()

                            // Guardamos la fecha principal como la fecha de entrada (o puedes unirlas si lo requiere tu Google Sheet)
                            val fechaRegistroFinal = fechaEntrada.trim()
                            val textoHoraSalidaCompleta = "$horaSalidaH:$horaSalidaM $ampmSalida (Salida el $fechaSalida)"

                            val nuevoRegistro = hashMapOf(
                                "codigoColaborador" to codigoColaborador,
                                "fecha" to fechaRegistroFinal,
                                "turno" to turnoSeleccionado,
                                "horaEntrada" to "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                "horaSalida" to textoHoraSalidaCompleta,
                                "estado" to "COMPLETO"
                            )
                            val docRef = db.collection("REGISTROS_ASISTENCIA").add(nuevoRegistro).await()

                            NetworkUtils.enviarRegistroASheetAndroid(
                                nombreCompleto = nombreColaborador,
                                fecha = fechaRegistroFinal,
                                turno = turnoSeleccionado,
                                horaEntrada = "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                horaSalida = textoHoraSalidaCompleta,
                                idRegistro = docRef.id,
                                estado = "ACTIVO"
                            )
                            onGuardadoExitoso()
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                            isSaving = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ColabAzulPrimario),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando...", color = Color.White)
                } else {
                    Text("Guardar", color = Color.White)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar", color = ColabTextoGris) } }
    )
}