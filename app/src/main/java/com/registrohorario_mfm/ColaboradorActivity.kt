package com.registrohorario_mfm

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
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

// --- SELECTOR HORA Y MINUTO (MINUTO A MINUTO) PARA COLABORADOR ---
@Composable
fun SelectorColabHoraMinuto(
    horaSeleccionada: String,
    minutoSeleccionado: String,
    ampmSeleccionado: String,
    enabled: Boolean = true,
    onHoraChange: (String) -> Unit,
    onMinutoChange: (String) -> Unit,
    onAmPmChange: (String) -> Unit
) {
    val horasList = remember { (1..12).map { String.format("%02d", it) } }
    val minutosList = remember { (0..59).map { String.format("%02d", it) } }
    val ampmList = listOf("AM", "PM")

    var expandedHora by remember { mutableStateOf(false) }
    var expandedMinuto by remember { mutableStateOf(false) }
    var expandedAmPm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selector de Hora
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { if (enabled) expandedHora = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray)
            ) {
                Text(horaSeleccionada, color = ColabTextoOscuro, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(
                expanded = expandedHora,
                onDismissRequest = { expandedHora = false },
                modifier = Modifier
                    .background(Color.White)
                    .heightIn(max = 200.dp)
            ) {
                horasList.forEach { h ->
                    DropdownMenuItem(
                        text = { Text(h) },
                        onClick = {
                            onHoraChange(h)
                            expandedHora = false
                        }
                    )
                }
            }
        }

        Text(":", color = ColabTextoOscuro, fontWeight = FontWeight.Bold, fontSize = 18.sp)

        // Selector de Minuto (Minuto a Minuto del 00 al 59)
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { if (enabled) expandedMinuto = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray)
            ) {
                Text(minutoSeleccionado, color = ColabTextoOscuro, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(
                expanded = expandedMinuto,
                onDismissRequest = { expandedMinuto = false },
                modifier = Modifier
                    .background(Color.White)
                    .heightIn(max = 200.dp)
            ) {
                minutosList.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = {
                            onMinutoChange(m)
                            expandedMinuto = false
                        }
                    )
                }
            }
        }

        // Selector AM / PM
        Box(modifier = Modifier.weight(1.2f)) {
            OutlinedButton(
                onClick = { if (enabled) expandedAmPm = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = ColabAzulFondoSeleccion),
                border = BorderStroke(1.dp, ColabAzulPrimario)
            ) {
                Text(ampmSeleccionado, color = ColabAzulPrimario, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = expandedAmPm,
                onDismissRequest = { expandedAmPm = false },
                modifier = Modifier.background(Color.White)
            ) {
                ampmList.forEach { ap ->
                    DropdownMenuItem(
                        text = { Text(ap, fontWeight = FontWeight.Bold, color = ColabAzulPrimario) },
                        onClick = {
                            onAmPmChange(ap)
                            expandedAmPm = false
                        }
                    )
                }
            }
        }
    }
}

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
    var mostrarModalCambiarPassword by remember { mutableStateOf(false) }
    var expandedSemana by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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
                        semanasDisponibles = if (semanasSet.isNotEmpty()) {
                            semanasSet.sortedDescending()
                        } else {
                            listOf(semanaActualGlobal)
                        }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(text = nombreColaborador, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColabTextoOscuro)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ColabAzulPrimario) },
                    label = { Text("Cambiar contraseña", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        mostrarModalCambiarPassword = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    label = { Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = ColabFondoGrisClaro,
                topBar = {
                    TopAppBar(
                        title = { Text("Panel de Colaborador", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Abrir Menú", tint = ColabTextoOscuro)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = ColabTextoOscuro
                        ),
                        actions = {
                            IconButton(onClick = { cargarDatosColaborador() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = ColabAzulPrimario)
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
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Surface(
                                                            color = ColabAzulFondoSeleccion,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = item.turno,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = ColabAzulPrimario
                                                            )
                                                        }
                                                        Text(
                                                            text = item.fecha,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = ColabTextoOscuro
                                                        )
                                                    }

                                                    Text(
                                                        text = item.estado,
                                                        color = ColabAzulPrimario,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 12.sp
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))
                                                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                                                Spacer(modifier = Modifier.height(10.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = "ENTRADA", fontSize = 10.sp, color = ColabTextoGris, fontWeight = FontWeight.Bold)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(text = item.horaEntrada, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ColabTextoOscuro)
                                                    }

                                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                        Text(text = "SALIDA", fontSize = 10.sp, color = ColabTextoGris, fontWeight = FontWeight.Bold)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        val horaSalidaTxt = if (item.horaSalida.isEmpty()) "En curso" else item.horaSalida
                                                        Text(
                                                            text = horaSalidaTxt,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = ColabTextoOscuro,
                                                            maxLines = 2
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

            if (mostrarModalCambiarPassword) {
                DialogoCambiarPassword(
                    onDismiss = { mostrarModalCambiarPassword = false }
                )
            }
        }
    }
}

@Composable
fun DialogoCambiarPassword(onDismiss: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    var passwordActual by remember { mutableStateOf("") }
    var passwordNueva by remember { mutableStateOf("") }
    var passwordNuevaConfirmar by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Cambiar Contraseña", color = ColabTextoOscuro) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = passwordActual,
                    onValueChange = { passwordActual = it },
                    label = { Text("Contraseña Actual") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = colabTfColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )

                OutlinedTextField(
                    value = passwordNueva,
                    onValueChange = { passwordNueva = it },
                    label = { Text("Nueva Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = colabTfColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )

                OutlinedTextField(
                    value = passwordNuevaConfirmar,
                    onValueChange = { passwordNuevaConfirmar = it },
                    label = { Text("Confirmar Nueva Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = colabTfColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (passwordActual.isBlank() || passwordNueva.isBlank() || passwordNuevaConfirmar.isBlank()) {
                        errorMessage = "Complete todos los campos."
                        return@Button
                    }
                    if (passwordNueva != passwordNuevaConfirmar) {
                        errorMessage = "Las nuevas contraseñas no coinciden."
                        return@Button
                    }
                    if (passwordNueva.length < 6) {
                        errorMessage = "La nueva contraseña debe tener al menos 6 caracteres."
                        return@Button
                    }

                    isSaving = true
                    scope.launch {
                        try {
                            val email = user?.email
                            if (email != null) {
                                val credential = EmailAuthProvider.getCredential(email, passwordActual)
                                user.reauthenticate(credential).await()
                                user.updatePassword(passwordNueva).await()

                                Toast.makeText(context, "Contraseña actualizada exitosamente", Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                errorMessage = "No se encontró sesión activa."
                                isSaving = false
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: Contraseña actual incorrecta o fallo al actualizar."
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
                    Text("Actualizando...", color = Color.White)
                } else {
                    Text("Actualizar", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar", color = ColabTextoGris)
            }
        }
    )
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

    var fechaEntrada by remember { mutableStateOf(fechaHoy) }
    var fechaSalida by remember { mutableStateOf(fechaHoy) }

    var turnoSeleccionado by remember { mutableStateOf("DIA") }
    var expandedFechaEntrada by remember { mutableStateOf(false) }
    var expandedFechaSalida by remember { mutableStateOf(false) }

    // Estados para hora de entrada mediante selectores minuto a minuto
    var horaEntradaH by remember { mutableStateOf("08") }
    var horaEntradaM by remember { mutableStateOf("00") }
    var ampmEntrada by remember { mutableStateOf("AM") }

    // Estados para hora de salida mediante selectores minuto a minuto
    var horaSalidaH by remember { mutableStateOf("06") }
    var horaSalidaM by remember { mutableStateOf("00") }
    var ampmSalida by remember { mutableStateOf("PM") }

    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                            fechaEntrada = fechaAyer
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
                        DropdownMenuItem(text = { Text("$fechaAyer (Ayer)") }, onClick = { fechaEntrada = fechaAyer; expandedFechaEntrada = false })
                        DropdownMenuItem(text = { Text("$fechaHoy (Hoy)") }, onClick = { fechaEntrada = fechaHoy; expandedFechaEntrada = false })
                    }
                }

                Text("Hora de Entrada:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                SelectorColabHoraMinuto(
                    horaSeleccionada = horaEntradaH,
                    minutoSeleccionado = horaEntradaM,
                    ampmSeleccionado = ampmEntrada,
                    enabled = !isSaving,
                    onHoraChange = { horaEntradaH = it },
                    onMinutoChange = { horaEntradaM = it },
                    onAmPmChange = { ampmEntrada = it }
                )

                if (turnoSeleccionado == "NOCHE") {
                    Text("Fecha de Salida:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)

                    ExposedDropdownMenuBox(
                        expanded = expandedFechaSalida,
                        onExpandedChange = {
                            if (!isSaving) expandedFechaSalida = !expandedFechaSalida
                        }
                    ) {
                        OutlinedTextField(
                            value = fechaSalida,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSaving,
                            colors = colabTfColors(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFechaSalida)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = expandedFechaSalida,
                            onDismissRequest = { expandedFechaSalida = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("$fechaAyer (Ayer)") },
                                onClick = {
                                    fechaSalida = fechaAyer
                                    expandedFechaSalida = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("$fechaHoy (Hoy)") },
                                onClick = {
                                    fechaSalida = fechaHoy
                                    expandedFechaSalida = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("$fechaMañana (Mañana)") },
                                onClick = {
                                    fechaSalida = fechaMañana
                                    expandedFechaSalida = false
                                }
                            )
                        }
                    }
                }

                Text("Hora de Salida:", style = MaterialTheme.typography.bodyMedium, color = ColabTextoGris)
                SelectorColabHoraMinuto(
                    horaSeleccionada = horaSalidaH,
                    minutoSeleccionado = horaSalidaM,
                    ampmSeleccionado = ampmSalida,
                    enabled = !isSaving,
                    onHoraChange = { horaSalidaH = it },
                    onMinutoChange = { horaSalidaM = it },
                    onAmPmChange = { ampmSalida = it }
                )

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (codigoColaborador.isBlank() || codigoColaborador == "SIN-CODIGO") {
                        errorMessage = "No se encontró un código de colaborador válido."

                        Toast.makeText(
                            context,
                            "No se encontró un código de colaborador válido.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@Button
                    }

                    if (fechaEntrada.isBlank() ||
                        (turnoSeleccionado == "NOCHE" && fechaSalida.isBlank())) {

                        errorMessage = "Seleccione las fechas correctamente."

                        Toast.makeText(
                            context,
                            "Seleccione las fechas correctamente.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()

                            val fechaSalidaFinal = if (turnoSeleccionado == "DIA") {
                                fechaEntrada
                            } else {
                                fechaSalida
                            }

                            val esTurnoDobleOCruzoLargo = (
                                    turnoSeleccionado == "NOCHE" &&
                                            fechaEntrada != fechaSalidaFinal &&
                                            (ampmSalida == "PM" || (ampmSalida == "AM" && horaSalidaH.toInt() >= 8))
                                    )

                            if (esTurnoDobleOCruzoLargo) {

                                val horaCorteNoche = "08:00 AM"
                                val textoSalidaNoche = "$horaCorteNoche (Salida el $fechaSalidaFinal)"

                                val regNoche = hashMapOf(
                                    "codigoColaborador" to codigoColaborador,
                                    "fecha" to fechaEntrada,
                                    "turno" to "NOCHE",
                                    "horaEntrada" to "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                    "horaSalida" to textoSalidaNoche,
                                    "estado" to "COMPLETO"
                                )

                                val docRef1 = db.collection("REGISTROS_ASISTENCIA").add(regNoche).await()

                                NetworkUtils.enviarRegistroASheetAndroid(
                                    nombreCompleto = nombreColaborador,
                                    fecha = fechaEntrada,
                                    turno = "NOCHE",
                                    horaEntrada = "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                    horaSalida = textoSalidaNoche,
                                    idRegistro = docRef1.id,
                                    estado = "ACTIVO"
                                )

                                val horaInicioDia = "08:00 AM"
                                val textoSalidaDia = "$horaSalidaH:$horaSalidaM $ampmSalida (Salida el $fechaSalidaFinal)"

                                val regDia = hashMapOf(
                                    "codigoColaborador" to codigoColaborador,
                                    "fecha" to fechaSalidaFinal,
                                    "turno" to "DIA",
                                    "horaEntrada" to horaInicioDia,
                                    "horaSalida" to textoSalidaDia,
                                    "estado" to "COMPLETO"
                                )

                                val docRef2 = db.collection("REGISTROS_ASISTENCIA").add(regDia).await()

                                NetworkUtils.enviarRegistroASheetAndroid(
                                    nombreCompleto = nombreColaborador,
                                    fecha = fechaSalidaFinal,
                                    turno = "DIA",
                                    horaEntrada = horaInicioDia,
                                    horaSalida = textoSalidaDia,
                                    idRegistro = docRef2.id,
                                    estado = "ACTIVO"
                                )

                            } else {

                                val fechaRegistroFinal = fechaEntrada.trim()

                                val textoHoraSalidaCompleta =
                                    "$horaSalidaH:$horaSalidaM $ampmSalida (Salida el $fechaSalidaFinal)"

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
                            }

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