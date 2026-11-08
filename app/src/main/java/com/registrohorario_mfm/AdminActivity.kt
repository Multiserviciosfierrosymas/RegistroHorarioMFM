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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

// --- PALETA DE COLORES UNIFICADA ---
val FondoGrisClaro = Color(0xFFF5F5F5)
val AzulPrimario = Color(0xFF1976D2)
val AzulBotonFlotante = Color(0xFF42A5F5)
val AzulFondoSeleccion = Color(0xFFE3F2FD)
val TextoOscuro = Color(0xFF212121)
val TextoGris = Color(0xFF757575)

@Composable
fun miTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = AzulPrimario,
    unfocusedBorderColor = Color.LightGray,
    focusedLabelColor = AzulPrimario,
    unfocusedLabelColor = TextoGris
)

@Composable
fun CampoFechaAdmin(
    fecha: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = fecha,
            onValueChange = {},
            readOnly = true,
            enabled = true,
            colors = miTextFieldColors(),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Seleccionar fecha",
                    tint = AzulPrimario
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
        )
    }
}

@Keep
data class UsuarioItem(
    val idDoc: String = "",
    val codigoColaborador: String = "",
    val nombres: String = "",
    val apellidos: String = "",
    val dni: String = "",
    val cargo: String = "",
    val correo: String = "",
    val rol: String = "",
    val jornalDia: Double = 0.0,
    val jornalNoche: Double = 0.0,
    val horasExtras: Double = 0.0,
    val bcp: String = "",
    val interbank: String = "",
    val yapePlin: String = ""
) {
    val nombreCompleto: String
        get() {
            val n = nombres.trim()
            val a = apellidos.trim()
            return if (n.isNotEmpty() && a.isNotEmpty()) {
                "$n $a"
            } else {
                n.ifEmpty { a }
            }
        }
}

@Keep
data class Asistencia(
    val idDoc: String = "",
    val codigoColaborador: String = "",
    val fecha: String = "",
    val turno: String = "",
    val horaEntrada: String = "",
    val horaSalida: String = "",
    val estado: String = ""
)

@Keep
data class AsistenciaConDetalle(
    val asistencia: Asistencia,
    val nombreCompleto: String,
    val cargo: String,
    val dni: String
)

@Keep
data class GrupoAsistenciaColaborador(
    val nombreCompleto: String,
    val codigoColaborador: String,
    val cargo: String,
    val dni: String,
    val registros: List<Asistencia>
)

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                color = FondoGrisClaro
            ) {
                AdminScreen(
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this@AdminActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

fun generarSiguienteCodigo(lista: List<UsuarioItem>, prefijo: String): String {
    var maxNumero = 0
    for (usuario in lista) {
        val codigo = usuario.codigoColaborador
        if (codigo.startsWith(prefijo)) {
            val numeroStr = codigo.removePrefix(prefijo)
            val numero = numeroStr.toIntOrNull()
            if (numero != null && numero > maxNumero) {
                maxNumero = numero
            }
        }
    }
    return String.format("%s%03d", prefijo, maxNumero + 1)
}

fun calcularNumeroSemanaAdmin(fechaStr: String): Int {
    try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))
        val fechaDate = sdf.parse(fechaStr) ?: return 1

        val calBase = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = sdf.parse("04/01/2026") ?: Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calTarget = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = fechaDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = calTarget.timeInMillis - calBase.timeInMillis
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        if (diffDays < 0) return 1
        return (diffDays / 7) + 1
    } catch (e: Exception) {
        return 1
    }
}

fun convertirFechaAMillis(fecha: String): Long? {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(fecha)?.time
    } catch (e: Exception) {
        null
    }
}

fun convertirMillisAFecha(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("ADMIN", "COLABORADORES", "REGISTRO")

    var listaAdmins by remember { mutableStateOf<List<UsuarioItem>>(emptyList()) }
    var listaColaboradores by remember { mutableStateOf<List<UsuarioItem>>(emptyList()) }
    var listaTodosPersonal by remember { mutableStateOf<List<UsuarioItem>>(emptyList()) }

    var todosLosGruposAsistencia by remember { mutableStateOf<List<GrupoAsistenciaColaborador>>(emptyList()) }
    var semanasDisponibles by remember { mutableStateOf<List<Int>>(emptyList()) }
    var semanaSeleccionada by remember { mutableStateOf(44) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var usuarioParaEditar by remember { mutableStateOf<UsuarioItem?>(null) }
    var asistenciaParaEditar by remember { mutableStateOf<AsistenciaConDetalle?>(null) }
    var asistenciaParaEliminarAdmin by remember { mutableStateOf<AsistenciaConDetalle?>(null) }
    var mostrarModalNuevoRegistro by remember { mutableStateOf(false) }

    var mostrarModalNuevoAdmin by remember { mutableStateOf(false) }
    var mostrarModalNuevoColaborador by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val cargarDatos: () -> Unit = {
        scope.launch {
            try {
                isLoading = true
                val db = FirebaseFirestore.getInstance()

                val colabsSnapshot = db.collection("COLABORADORES").get().await()
                val mapaColaboradores = mutableMapOf<String, UsuarioItem>()

                for (doc in colabsSnapshot.documents) {
                    val codigo = doc.getString("codigoColaborador") ?: doc.getString("codColaborador") ?: ""
                    if (codigo.isNotEmpty()) {
                        val item = UsuarioItem(
                            idDoc = doc.id,
                            codigoColaborador = codigo,
                            nombres = doc.getString("nombres") ?: "",
                            apellidos = doc.getString("apellidos") ?: "",
                            dni = doc.getString("dni") ?: "",
                            cargo = doc.getString("cargo") ?: "No especificado",
                            correo = doc.getString("correo") ?: "",
                            rol = doc.getString("rol") ?: "COLABORADOR",
                            jornalDia = doc.getDouble("jornalDia") ?: 0.0,
                            jornalNoche = doc.getDouble("jornalNoche") ?: 0.0,
                            horasExtras = doc.getDouble("horasExtras") ?: 0.0,
                            bcp = doc.getString("bcp") ?: doc.getString("bancoCredito") ?: "",
                            interbank = doc.getString("interbank") ?: "",
                            yapePlin = doc.getString("yapePlin") ?: ""
                        )
                        mapaColaboradores[codigo] = item
                    }
                }

                val usuariosSnapshot = db.collection("USUARIOS").get().await()
                val admins = mutableListOf<UsuarioItem>()
                val colaboradoresList = mutableListOf<UsuarioItem>()
                val todos = mutableListOf<UsuarioItem>()

                for (doc in usuariosSnapshot.documents) {
                    val codigo = doc.getString("codigoColaborador") ?: doc.getString("codColaborador") ?: ""
                    val rol = doc.getString("rol") ?: "COLABORADOR"
                    val correoAuth = doc.getString("correo") ?: ""
                    val nombresAuth = doc.getString("nombre") ?: doc.getString("nombres") ?: ""

                    val detalleBase = mapaColaboradores[codigo] ?: UsuarioItem(
                        idDoc = doc.id,
                        codigoColaborador = codigo,
                        nombres = nombresAuth,
                        correo = correoAuth,
                        rol = rol
                    )

                    val usuarioFinal = detalleBase.copy(
                        idDoc = detalleBase.idDoc.ifEmpty { doc.id },
                        rol = rol,
                        correo = if (detalleBase.correo.isEmpty()) correoAuth else detalleBase.correo,
                        nombres = if (detalleBase.nombres.isEmpty()) nombresAuth else detalleBase.nombres
                    )

                    todos.add(usuarioFinal)
                    if (rol.uppercase() == "ADMIN") {
                        admins.add(usuarioFinal)
                    } else {
                        colaboradoresList.add(usuarioFinal)
                    }
                }

                listaAdmins = admins
                listaColaboradores = colaboradoresList
                listaTodosPersonal = todos

                val asistenciaSnapshot = db.collection("REGISTROS_ASISTENCIA").get().await()
                val asistenciasList = asistenciaSnapshot.documents.mapNotNull { doc ->
                    val asisten = doc.toObject(Asistencia::class.java)
                    asisten?.copy(idDoc = doc.id)
                }.filter { it.estado != "ANULADO" }

                val gruposMap = mutableMapOf<String, MutableList<Asistencia>>()
                val semanasSet = mutableSetOf<Int>()

                for (asistencia in asistenciasList) {
                    gruposMap.getOrPut(asistencia.codigoColaborador) { mutableListOf() }.add(asistencia)
                    semanasSet.add(calcularNumeroSemanaAdmin(asistencia.fecha))
                }

                val gruposList = mutableListOf<GrupoAsistenciaColaborador>()
                for ((codigo, regs) in gruposMap) {
                    val infoPersonal = mapaColaboradores[codigo] ?: todos.find { it.codigoColaborador == codigo }
                    val nombreCompleto = infoPersonal?.nombreCompleto ?: "Colaborador ($codigo)"
                    val cargo = infoPersonal?.cargo ?: "No especificado"
                    val dni = infoPersonal?.dni ?: "S/N"

                    gruposList.add(
                        GrupoAsistenciaColaborador(
                            nombreCompleto = nombreCompleto,
                            codigoColaborador = codigo,
                            cargo = cargo,
                            dni = dni,
                            registros = regs.sortedByDescending { it.fecha }
                        )
                    )
                }

                todosLosGruposAsistencia = gruposList
                semanasDisponibles = if (semanasSet.isNotEmpty()) semanasSet.sorted() else listOf(44)
                if (!semanasDisponibles.contains(semanaSeleccionada)) {
                    semanaSeleccionada = semanasDisponibles.last()
                }

            } catch (e: Exception) {
                errorMessage = "Error al cargar datos: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        cargarDatos()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = FondoGrisClaro,
            topBar = {
                TopAppBar(
                    title = { Text("Panel de Administrador", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = TextoOscuro
                    ),
                    actions = {
                        IconButton(onClick = { cargarDatos() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = AzulPrimario)
                        }
                        TextButton(onClick = onLogout) {
                            Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> mostrarModalNuevoAdmin = true
                            1 -> mostrarModalNuevoColaborador = true
                            2 -> mostrarModalNuevoRegistro = true
                        }
                    },
                    containerColor = AzulBotonFlotante,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar", modifier = Modifier.size(32.dp))
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = AzulPrimario
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) AzulPrimario else TextoGris
                                )
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AzulPrimario)
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        when (selectedTab) {
                            0 -> TabAdminView(listaAdmins) { admin -> usuarioParaEditar = admin }
                            1 -> TabColaboradoresView(listaColaboradores) { colab -> usuarioParaEditar = colab }
                            2 -> {
                                val gruposFiltrados = todosLosGruposAsistencia.mapNotNull { grupo ->
                                    val regsFiltrados = grupo.registros.filter { calcularNumeroSemanaAdmin(it.fecha) == semanaSeleccionada }
                                    if (regsFiltrados.isNotEmpty()) {
                                        grupo.copy(registros = regsFiltrados)
                                    } else {
                                        null
                                    }
                                }

                                TabRegistroAgrupadoConFiltroSemana(
                                    semanas = semanasDisponibles,
                                    semanaActual = semanaSeleccionada,
                                    onSemanaSelected = { semanaSeleccionada = it },
                                    grupos = gruposFiltrados,
                                    onRegistroClick = { grupo, reg ->
                                        asistenciaParaEditar = AsistenciaConDetalle(
                                            asistencia = reg,
                                            nombreCompleto = grupo.nombreCompleto,
                                            cargo = grupo.cargo,
                                            dni = grupo.dni
                                        )
                                    },
                                    onDeleteRegistro = { grupo, reg ->
                                        asistenciaParaEliminarAdmin = AsistenciaConDetalle(
                                            asistencia = reg,
                                            nombreCompleto = grupo.nombreCompleto,
                                            cargo = grupo.cargo,
                                            dni = grupo.dni
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mostrarModalNuevoRegistro) {
            DialogoNuevoRegistro(
                listaPersonal = listaTodosPersonal,
                onDismiss = { mostrarModalNuevoRegistro = false },
                onGuardadoExitoso = {
                    mostrarModalNuevoRegistro = false
                    cargarDatos()
                }
            )
        }

        if (mostrarModalNuevoAdmin) {
            val siguienteCodigoAdmin = generarSiguienteCodigo(listaAdmins, "ADMIN-")
            DialogoNuevoUsuarioRol(
                rolObjetivo = "ADMIN",
                codigoGenerado = siguienteCodigoAdmin,
                onDismiss = { mostrarModalNuevoAdmin = false },
                onGuardadoExitoso = {
                    mostrarModalNuevoAdmin = false
                    cargarDatos()
                }
            )
        }

        if (mostrarModalNuevoColaborador) {
            val siguienteCodigoColab = generarSiguienteCodigo(listaColaboradores, "COL-")
            DialogoNuevoUsuarioRol(
                rolObjetivo = "COLABORADOR",
                codigoGenerado = siguienteCodigoColab,
                onDismiss = { mostrarModalNuevoColaborador = false },
                onGuardadoExitoso = {
                    mostrarModalNuevoColaborador = false
                    cargarDatos()
                }
            )
        }

        if (usuarioParaEditar != null) {
            DialogoEditarUsuario(
                usuario = usuarioParaEditar!!,
                onDismiss = { usuarioParaEditar = null },
                onActualizadoExitoso = {
                    usuarioParaEditar = null
                    cargarDatos()
                }
            )
        }

        if (asistenciaParaEditar != null) {
            DialogoEditarAsistenciaAdmin(
                detalle = asistenciaParaEditar!!,
                onDismiss = { asistenciaParaEditar = null },
                onActualizadoExitoso = {
                    asistenciaParaEditar = null
                    cargarDatos()
                }
            )
        }

        if (asistenciaParaEliminarAdmin != null) {
            val detalle = asistenciaParaEliminarAdmin!!
            var isDeleting by remember { mutableStateOf(false) }

            AlertDialog(
                containerColor = Color.White,
                onDismissRequest = { if (!isDeleting) asistenciaParaEliminarAdmin = null },
                title = { Text("Confirmar Anulación", color = TextoOscuro) },
                text = { Text("¿Deseas anular el registro de ${detalle.nombreCompleto} para la fecha ${detalle.asistencia.fecha}?", color = TextoGris) },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeleting = true
                            scope.launch {
                                try {
                                    FirebaseFirestore.getInstance()
                                        .collection("REGISTROS_ASISTENCIA")
                                        .document(detalle.asistencia.idDoc)
                                        .update("estado", "ANULADO")
                                        .await()

                                    NetworkUtils.enviarRegistroASheetAndroid(
                                        nombreCompleto = detalle.nombreCompleto,
                                        fecha = detalle.asistencia.fecha,
                                        turno = detalle.asistencia.turno,
                                        horaEntrada = detalle.asistencia.horaEntrada,
                                        horaSalida = detalle.asistencia.horaSalida,
                                        idRegistro = detalle.asistencia.idDoc,
                                        estado = "ANULADO"
                                    )
                                    asistenciaParaEliminarAdmin = null
                                    cargarDatos()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al anular: ${e.message}", Toast.LENGTH_SHORT).show()
                                    isDeleting = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Anulando...", color = Color.White)
                        } else {
                            Text("Sí, Anular", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { asistenciaParaEliminarAdmin = null }, enabled = !isDeleting) {
                        Text("Cancelar", color = TextoGris)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoNuevoRegistro(
    listaPersonal: List<UsuarioItem>,
    onDismiss: () -> Unit,
    onGuardadoExitoso: () -> Unit
) {
    var colaboradorSeleccionado by remember { mutableStateOf<UsuarioItem?>(null) }
    var expandedPersonal by remember { mutableStateOf(false) }

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

    val datePickerEntrada = key(fechaEntrada) {
        rememberDatePickerState(
            initialSelectedDateMillis = convertirFechaAMillis(fechaEntrada)
        )
    }

    val datePickerSalida = key(fechaSalida) {
        rememberDatePickerState(
            initialSelectedDateMillis = convertirFechaAMillis(fechaSalida)
        )
    }

    var horaEntradaH by remember { mutableStateOf("08") }
    var horaEntradaM by remember { mutableStateOf("00") }
    var ampmEntrada by remember { mutableStateOf("AM") }
    var expandedAmPmEntrada by remember { mutableStateOf(false) }

    var horaSalidaH by remember { mutableStateOf("06") }
    var horaSalidaM by remember { mutableStateOf("00") }
    var ampmSalida by remember { mutableStateOf("PM") }
    var expandedAmPmSalida by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Nuevo Registro Manual (Admin)", color = TextoOscuro) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Seleccionar Colaborador:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                ExposedDropdownMenuBox(
                    expanded = expandedPersonal,
                    onExpandedChange = { if (!isSaving) expandedPersonal = !expandedPersonal }
                ) {
                    OutlinedTextField(
                        value = colaboradorSeleccionado?.let { "${it.nombreCompleto} (${it.codigoColaborador})" } ?: "Seleccione colaborador...",
                        onValueChange = {},
                        readOnly = true,
                        colors = miTextFieldColors(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPersonal) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedPersonal,
                        onDismissRequest = { expandedPersonal = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        listaPersonal.forEach { personal ->
                            DropdownMenuItem(
                                text = { Text("${personal.nombreCompleto} (${personal.codigoColaborador})") },
                                onClick = {
                                    colaboradorSeleccionado = personal
                                    expandedPersonal = false
                                }
                            )
                        }
                    }
                }

                Text("Turno:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
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
                            containerColor = if (turnoSeleccionado == "DIA") AzulFondoSeleccion else Color.White,
                            contentColor = if (turnoSeleccionado == "DIA") AzulPrimario else TextoGris
                        ),
                        border = BorderStroke(1.dp, if (turnoSeleccionado == "DIA") AzulPrimario else Color.LightGray)
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
                            containerColor = if (turnoSeleccionado == "NOCHE") AzulFondoSeleccion else Color.White,
                            contentColor = if (turnoSeleccionado == "NOCHE") AzulPrimario else TextoGris
                        ),
                        border = BorderStroke(1.dp, if (turnoSeleccionado == "NOCHE") AzulPrimario else Color.LightGray)
                    ) { Text("NOCHE / CRUZADO") }
                }

                Text("Fecha de Entrada:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                CampoFechaAdmin(
                    fecha = fechaEntrada,
                    enabled = !isSaving,
                    onClick = {
                        expandedFechaEntrada = true
                    }
                )

                Text("Hora de Entrada:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = horaEntradaH, onValueChange = { horaEntradaH = it }, label = { Text("HH") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Text(":", color = TextoOscuro)
                    OutlinedTextField(value = horaEntradaM, onValueChange = { horaEntradaM = it }, label = { Text("MM") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedAmPmEntrada = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) { Text(ampmEntrada, color = AzulPrimario) }
                        DropdownMenu(expanded = expandedAmPmEntrada, onDismissRequest = { expandedAmPmEntrada = false }, modifier = Modifier.background(Color.White)) {
                            DropdownMenuItem(text = { Text("AM") }, onClick = { ampmEntrada = "AM"; expandedAmPmEntrada = false })
                            DropdownMenuItem(text = { Text("PM") }, onClick = { ampmEntrada = "PM"; expandedAmPmEntrada = false })
                        }
                    }
                }

                if (turnoSeleccionado == "NOCHE") {
                    Text("Fecha de Salida:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                    CampoFechaAdmin(
                        fecha = fechaSalida,
                        enabled = !isSaving,
                        onClick = {
                            expandedFechaSalida = true
                        }
                    )
                }

                Text("Hora de Salida:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = horaSalidaH, onValueChange = { horaSalidaH = it }, label = { Text("HH") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Text(":", color = TextoOscuro)
                    OutlinedTextField(value = horaSalidaM, onValueChange = { horaSalidaM = it }, label = { Text("MM") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedAmPmSalida = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) { Text(ampmSalida, color = AzulPrimario) }
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
                    val colab = colaboradorSeleccionado

                    if (colab == null || colab.codigoColaborador.isBlank()) {
                        errorMessage = "Debe seleccionar un colaborador para registrar."
                        Toast.makeText(
                            context,
                            "Debe seleccionar un colaborador para registrar.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    if (fechaEntrada.isBlank()) {
                        errorMessage = "Seleccione la fecha de entrada."
                        return@Button
                    }

                    if (turnoSeleccionado == "NOCHE" && fechaSalida.isBlank()) {
                        errorMessage = "Seleccione la fecha de salida."
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()

                            // Lógica de validación para turnos largos/cruzados
                            val esTurnoDobleOCruzoLargo = (turnoSeleccionado == "NOCHE" && fechaEntrada != fechaSalida && (ampmSalida == "PM" || (ampmSalida == "AM" && horaSalidaH.toInt() >= 8)))

                            if (esTurnoDobleOCruzoLargo) {
                                // --- REGISTRO 1: TRAMO NOCTURNO (Hasta las 08:00 AM del día de salida) ---
                                val horaCorteNoche = "08:00 AM"
                                val textoSalidaNoche = "$horaCorteNoche (Salida el $fechaSalida)"

                                val regNoche = hashMapOf(
                                    "codigoColaborador" to colab.codigoColaborador,
                                    "fecha" to fechaEntrada,
                                    "turno" to "NOCHE",
                                    "horaEntrada" to "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                    "horaSalida" to textoSalidaNoche,
                                    "estado" to "COMPLETO"
                                )
                                val docRef1 = db.collection("REGISTROS_ASISTENCIA").add(regNoche).await()
                                NetworkUtils.enviarRegistroASheetAndroid(
                                    nombreCompleto = colab.nombreCompleto,
                                    fecha = fechaEntrada,
                                    turno = "NOCHE",
                                    horaEntrada = "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                    horaSalida = textoSalidaNoche,
                                    idRegistro = docRef1.id,
                                    estado = "ACTIVO"
                                )

                                // --- REGISTRO 2: TRAMO DIURNO (Desde las 08:00 AM hasta la hora final) ---
                                val horaInicioDia = "08:00 AM"
                                val textoSalidaFinal = "$horaSalidaH:$horaSalidaM $ampmSalida (Salida el $fechaSalida)"

                                val regDia = hashMapOf(
                                    "codigoColaborador" to colab.codigoColaborador,
                                    "fecha" to fechaSalida,
                                    "turno" to "DIA",
                                    "horaEntrada" to horaInicioDia,
                                    "horaSalida" to textoSalidaFinal,
                                    "estado" to "COMPLETO"
                                )
                                val docRef2 = db.collection("REGISTROS_ASISTENCIA").add(regDia).await()
                                NetworkUtils.enviarRegistroASheetAndroid(
                                    nombreCompleto = colab.nombreCompleto,
                                    fecha = fechaSalida,
                                    turno = "DIA",
                                    horaEntrada = horaInicioDia,
                                    horaSalida = textoSalidaFinal,
                                    idRegistro = docRef2.id,
                                    estado = "ACTIVO"
                                )

                            } else {
                                // --- REGISTRO NORMAL ---
                                val fechaRegistroFinal = fechaEntrada.trim()

                                val textoHoraSalidaCompleta = if (turnoSeleccionado == "DIA") {
                                    "$horaSalidaH:$horaSalidaM $ampmSalida"
                                } else {
                                    "$horaSalidaH:$horaSalidaM $ampmSalida (Salida el $fechaSalida)"
                                }

                                val nuevoRegistro = hashMapOf(
                                    "codigoColaborador" to colab.codigoColaborador,
                                    "fecha" to fechaRegistroFinal,
                                    "turno" to turnoSeleccionado,
                                    "horaEntrada" to "$horaEntradaH:$horaEntradaM $ampmEntrada",
                                    "horaSalida" to textoHoraSalidaCompleta,
                                    "estado" to "COMPLETO"
                                )
                                val docRef = db.collection("REGISTROS_ASISTENCIA").add(nuevoRegistro).await()

                                NetworkUtils.enviarRegistroASheetAndroid(
                                    nombreCompleto = colab.nombreCompleto,
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
                colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
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
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar", color = TextoGris) } }
    )

    if (expandedFechaEntrada) {
        DatePickerDialog(
            onDismissRequest = { expandedFechaEntrada = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerEntrada.selectedDateMillis?.let {
                        fechaEntrada = convertirMillisAFecha(it)
                    }
                    expandedFechaEntrada = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { expandedFechaEntrada = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerEntrada)
        }
    }

    if (expandedFechaSalida) {
        DatePickerDialog(
            onDismissRequest = { expandedFechaSalida = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerSalida.selectedDateMillis?.let {
                        fechaSalida = convertirMillisAFecha(it)
                    }
                    expandedFechaSalida = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { expandedFechaSalida = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerSalida)
        }
    }
}

@Composable
fun DialogoEditarUsuario(
    usuario: UsuarioItem,
    onDismiss: () -> Unit,
    onActualizadoExitoso: () -> Unit
) {
    var nombres by remember { mutableStateOf(usuario.nombres) }
    var apellidos by remember { mutableStateOf(usuario.apellidos) }
    var dni by remember { mutableStateOf(usuario.dni) }
    var cargo by remember { mutableStateOf(usuario.cargo) }
    var correo by remember { mutableStateOf(usuario.correo) }
    var jornalDia by remember { mutableStateOf(usuario.jornalDia.toString()) }
    var jornalNoche by remember { mutableStateOf(usuario.jornalNoche.toString()) }
    var horasExtras by remember { mutableStateOf(usuario.horasExtras.toString()) }
    var bancoCredito by remember { mutableStateOf(usuario.bcp) }
    var interbank by remember { mutableStateOf(usuario.interbank) }
    var yapePlin by remember { mutableStateOf(usuario.yapePlin) }

    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Editar Personal: ${usuario.nombreCompleto}", color = TextoOscuro) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = nombres, onValueChange = { nombres = it }, label = { Text("Nombres") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = apellidos, onValueChange = { apellidos = it }, label = { Text("Apellidos") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = dni, onValueChange = { dni = it }, label = { Text("DNI") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = cargo, onValueChange = { cargo = it }, label = { Text("Cargo") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = correo, onValueChange = { correo = it }, label = { Text("Correo electrónico") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = jornalDia, onValueChange = { jornalDia = it }, label = { Text("Jornal Día (S/)") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    OutlinedTextField(value = jornalNoche, onValueChange = { jornalNoche = it }, label = { Text("Jornal Noche (S/)") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                }

                OutlinedTextField(value = horasExtras, onValueChange = { horasExtras = it }, label = { Text("Horas Extras (S/)") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = bancoCredito, onValueChange = { bancoCredito = it }, label = { Text("Cuenta BCP / CCI") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = interbank, onValueChange = { interbank = it }, label = { Text("Cuenta Interbank") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = yapePlin, onValueChange = { yapePlin = it }, label = { Text("Número Yape/Plin") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            val datosActualizados = mapOf(
                                "nombres" to nombres.trim().uppercase(),
                                "apellidos" to apellidos.trim().uppercase(),
                                "dni" to dni.trim(),
                                "cargo" to cargo.trim().uppercase(),
                                "correo" to correo.trim(),
                                "jornalDia" to (jornalDia.toDoubleOrNull() ?: 0.0),
                                "jornalNoche" to (jornalNoche.toDoubleOrNull() ?: 0.0),
                                "horasExtras" to (horasExtras.toDoubleOrNull() ?: 0.0),
                                "bcp" to bancoCredito.trim(),
                                "interbank" to interbank.trim(),
                                "yapePlin" to yapePlin.trim()
                            )

                            if (usuario.idDoc.isNotEmpty()) {
                                db.collection("COLABORADORES").document(usuario.idDoc).update(datosActualizados).await()
                            }
                            onActualizadoExitoso()
                        } catch (e: Exception) {
                            errorMessage = "Error al actualizar: ${e.message}"
                            isSaving = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando...", color = Color.White)
                } else {
                    Text("Guardar Cambios", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar", color = TextoGris) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEditarAsistenciaAdmin(
    detalle: AsistenciaConDetalle,
    onDismiss: () -> Unit,
    onActualizadoExitoso: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))

    val calHoy = Calendar.getInstance()
    val calAyer = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val calMañana = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    val fechaHoy = dateFormat.format(calHoy.time)
    val fechaAyer = dateFormat.format(calAyer.time)
    val fechaMañana = dateFormat.format(calMañana.time)

    val registro = detalle.asistencia

    var turnoSeleccionado by remember { mutableStateOf(if (registro.turno.isNotBlank()) registro.turno else "DIA") }
    var fechaEntrada by remember { mutableStateOf(if (registro.fecha.isNotBlank()) registro.fecha else fechaHoy) }

    var fechaSalida by remember {
        val match = Regex("Salida el (\\d{2}/\\d{2}/\\d{4})").find(registro.horaSalida)
        mutableStateOf(match?.groupValues?.get(1) ?: registro.fecha.ifBlank { fechaHoy })
    }

    val partesEntrada = registro.horaEntrada.split(" ")
    val horaEntradaArr = partesEntrada.getOrNull(0)?.split(":") ?: listOf("08", "00")

    var horaEntradaH by remember {
        mutableStateOf(horaEntradaArr.getOrNull(0) ?: "08")
    }
    var horaEntradaM by remember {
        mutableStateOf(horaEntradaArr.getOrNull(1) ?: "00")
    }
    var ampmEntrada by remember {
        mutableStateOf(partesEntrada.getOrNull(1) ?: "AM")
    }
    var expandedAmPmEntrada by remember {
        mutableStateOf(false)
    }

    val partesSalidaClean = registro.horaSalida.split(" ")
    val horaSalidaArr = partesSalidaClean.getOrNull(0)?.split(":") ?: listOf("05", "00")

    var horaSalidaH by remember {
        mutableStateOf(horaSalidaArr.getOrNull(0) ?: "05")
    }
    var horaSalidaM by remember {
        mutableStateOf(horaSalidaArr.getOrNull(1) ?: "00")
    }
    var ampmSalida by remember {
        mutableStateOf(partesSalidaClean.getOrNull(1) ?: "PM")
    }
    var expandedAmPmSalida by remember { mutableStateOf(false) }

    var estadoRegistro by remember { mutableStateOf(if (registro.estado.isNotBlank()) registro.estado else "COMPLETO") }
    var expandedEstado by remember { mutableStateOf(false) }

    var expandedFechaEntrada by remember { mutableStateOf(false) }
    var expandedFechaSalida by remember { mutableStateOf(false) }

    val datePickerEntrada = key(fechaEntrada) {
        rememberDatePickerState(
            initialSelectedDateMillis = convertirFechaAMillis(fechaEntrada)
        )
    }

    val datePickerSalida = key(fechaSalida) {
        rememberDatePickerState(
            initialSelectedDateMillis = convertirFechaAMillis(fechaSalida)
        )
    }

    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Editar Registro - ${detalle.nombreCompleto}", color = TextoOscuro, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Turno:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
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
                            containerColor = if (turnoSeleccionado == "DIA") AzulFondoSeleccion else Color.White,
                            contentColor = if (turnoSeleccionado == "DIA") AzulPrimario else TextoGris
                        ),
                        border = BorderStroke(1.dp, if (turnoSeleccionado == "DIA") AzulPrimario else Color.LightGray)
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
                            containerColor = if (turnoSeleccionado == "NOCHE") AzulFondoSeleccion else Color.White,
                            contentColor = if (turnoSeleccionado == "NOCHE") AzulPrimario else TextoGris
                        ),
                        border = BorderStroke(1.dp, if (turnoSeleccionado == "NOCHE") AzulPrimario else Color.LightGray)
                    ) { Text("NOCHE / CRUZADO") }
                }

                Text("Fecha de Entrada:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                CampoFechaAdmin(
                    fecha = fechaEntrada,
                    enabled = !isSaving,
                    onClick = {
                        expandedFechaEntrada = true
                    }
                )

                Text("Hora de Entrada:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = horaEntradaH, onValueChange = { horaEntradaH = it }, label = { Text("HH") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Text(":", color = TextoOscuro)
                    OutlinedTextField(value = horaEntradaM, onValueChange = { horaEntradaM = it }, label = { Text("MM") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedAmPmEntrada = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) { Text(ampmEntrada, color = AzulPrimario) }
                        DropdownMenu(expanded = expandedAmPmEntrada, onDismissRequest = { expandedAmPmEntrada = false }, modifier = Modifier.background(Color.White)) {
                            DropdownMenuItem(text = { Text("AM") }, onClick = { ampmEntrada = "AM"; expandedAmPmEntrada = false })
                            DropdownMenuItem(text = { Text("PM") }, onClick = { ampmEntrada = "PM"; expandedAmPmEntrada = false })
                        }
                    }
                }

                Text("Fecha de Salida:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                CampoFechaAdmin(
                    fecha = fechaSalida,
                    enabled = !isSaving,
                    onClick = {
                        expandedFechaSalida = true
                    }
                )

                Text("Hora de Salida:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = horaSalidaH, onValueChange = { horaSalidaH = it }, label = { Text("HH") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Text(":", color = TextoOscuro)
                    OutlinedTextField(value = horaSalidaM, onValueChange = { horaSalidaM = it }, label = { Text("MM") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(onClick = { expandedAmPmSalida = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) { Text(ampmSalida, color = AzulPrimario) }
                        DropdownMenu(expanded = expandedAmPmSalida, onDismissRequest = { expandedAmPmSalida = false }, modifier = Modifier.background(Color.White)) {
                            DropdownMenuItem(text = { Text("AM") }, onClick = { ampmSalida = "AM"; expandedAmPmSalida = false })
                            DropdownMenuItem(text = { Text("PM") }, onClick = { ampmSalida = "PM"; expandedAmPmSalida = false })
                        }
                    }
                }

                Text("Estado:", style = MaterialTheme.typography.bodyMedium, color = TextoGris)
                ExposedDropdownMenuBox(
                    expanded = expandedEstado,
                    onExpandedChange = { if (!isSaving) expandedEstado = !expandedEstado }
                ) {
                    OutlinedTextField(
                        value = estadoRegistro,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSaving,
                        colors = miTextFieldColors(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEstado) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEstado,
                        onDismissRequest = { expandedEstado = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(text = { Text("COMPLETO") }, onClick = { estadoRegistro = "COMPLETO"; expandedEstado = false })
                        DropdownMenuItem(text = { Text("ACTIVO") }, onClick = { estadoRegistro = "ACTIVO"; expandedEstado = false })
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

                            // --- EDICIÓN DEL DOCUMENTO SELECCIONADO ---
                            val textoHoraSalidaCompleta =
                                "$horaSalidaH:$horaSalidaM $ampmSalida (Salida el $fechaSalida)"

                            val horaEntradaCompleta =
                                "$horaEntradaH:$horaEntradaM $ampmEntrada"

                            val cambios = mapOf(
                                "fecha" to fechaEntrada.trim(),
                                "turno" to turnoSeleccionado,
                                "horaEntrada" to horaEntradaCompleta,
                                "horaSalida" to textoHoraSalidaCompleta,
                                "estado" to estadoRegistro
                            )

// Actualizar únicamente el documento seleccionado en Firebase.
                            db.collection("REGISTROS_ASISTENCIA")
                                .document(registro.idDoc)
                                .update(cambios)
                                .await()

// Actualizar la fila correspondiente en Google Sheets usando el mismo ID.
                            NetworkUtils.enviarRegistroASheetAndroid(
                                nombreCompleto = detalle.nombreCompleto,
                                fecha = fechaEntrada.trim(),
                                turno = turnoSeleccionado,
                                horaEntrada = horaEntradaCompleta,
                                horaSalida = textoHoraSalidaCompleta,
                                idRegistro = registro.idDoc,
                                estado = estadoRegistro
                            )

                            onActualizadoExitoso()
                        } catch (e: Exception) {
                            errorMessage = "Error al actualizar: ${e.message}"
                            isSaving = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
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
                Text("Cancelar", color = TextoGris)
            }
        }
    )

    if (expandedFechaEntrada) {
        DatePickerDialog(
            onDismissRequest = { expandedFechaEntrada = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerEntrada.selectedDateMillis?.let {
                        fechaEntrada = convertirMillisAFecha(it)
                    }
                    expandedFechaEntrada = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { expandedFechaEntrada = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerEntrada)
        }
    }

    if (expandedFechaSalida) {
        DatePickerDialog(
            onDismissRequest = { expandedFechaSalida = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerSalida.selectedDateMillis?.let {
                        fechaSalida = convertirMillisAFecha(it)
                    }
                    expandedFechaSalida = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { expandedFechaSalida = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerSalida)
        }
    }
}

@Composable
fun TabAdminView(admins: List<UsuarioItem>, onItemClick: (UsuarioItem) -> Unit) {
    if (admins.isEmpty()) {
        Text("No hay administradores.", modifier = Modifier.padding(16.dp), color = TextoGris)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(admins) { admin ->
                ItemColaborador(
                    codigo = admin.codigoColaborador.ifEmpty { "ADMIN" },
                    cargo = admin.cargo,
                    nombreCompleto = admin.nombreCompleto,
                    dni = admin.dni.ifEmpty { "S/N" },
                    onEditClick = { onItemClick(admin) }
                )
            }
        }
    }
}

@Composable
fun TabColaboradoresView(colaboradores: List<UsuarioItem>, onItemClick: (UsuarioItem) -> Unit) {
    if (colaboradores.isEmpty()) {
        Text("No hay colaboradores.", modifier = Modifier.padding(16.dp), color = TextoGris)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colaboradores) { colab ->
                ItemColaborador(
                    codigo = colab.codigoColaborador,
                    cargo = colab.cargo,
                    nombreCompleto = colab.nombreCompleto,
                    dni = colab.dni.ifEmpty { "S/N" },
                    onEditClick = { onItemClick(colab) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabRegistroAgrupadoConFiltroSemana(
    semanas: List<Int>,
    semanaActual: Int,
    onSemanaSelected: (Int) -> Unit,
    grupos: List<GrupoAsistenciaColaborador>,
    onRegistroClick: (GrupoAsistenciaColaborador, Asistencia) -> Unit,
    onDeleteRegistro: (GrupoAsistenciaColaborador, Asistencia) -> Unit
) {
    var expandedSemana by remember { mutableStateOf(false) }
    var expandedStates by remember { mutableStateOf(mapOf<String, Boolean>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Filtrar por Semana:", style = MaterialTheme.typography.bodyMedium, color = TextoOscuro)
            ExposedDropdownMenuBox(
                expanded = expandedSemana,
                onExpandedChange = { expandedSemana = !expandedSemana },
                modifier = Modifier.width(160.dp)
            ) {
                OutlinedTextField(
                    value = "Semana $semanaActual",
                    onValueChange = {},
                    readOnly = true,
                    colors = miTextFieldColors(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSemana) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedSemana,
                    onDismissRequest = { expandedSemana = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    semanas.forEach { sem ->
                        DropdownMenuItem(
                            text = { Text("Semana $sem") },
                            onClick = { onSemanaSelected(sem); expandedSemana = false }
                        )
                    }
                }
            }
        }

        if (grupos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No hay registros para la Semana $semanaActual.", color = TextoGris)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(grupos) { grupo ->
                    val isExpanded = expandedStates[grupo.codigoColaborador] ?: false

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEEEEEE), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .clickable {
                                    expandedStates = expandedStates.toMutableMap().apply {
                                        this[grupo.codigoColaborador] = !isExpanded
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = grupo.nombreCompleto,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextoOscuro
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.Add,
                                contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                tint = TextoGris
                            )
                        }

                        if (isExpanded) {
                            grupo.registros.forEachIndexed { index, registro ->
                                ItemRegistro(
                                    fecha = registro.fecha,
                                    turno = registro.turno,
                                    horaEntrada = registro.horaEntrada,
                                    horaSalida = if (registro.horaSalida.isEmpty()) "En curso" else registro.horaSalida,
                                    onEditClick = { onRegistroClick(grupo, registro) },
                                    onDeleteClick = { onDeleteRegistro(grupo, registro) },
                                    esUltimoElemento = index == grupo.registros.size - 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemRegistro(
    fecha: String,
    turno: String,
    horaEntrada: String,
    horaSalida: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    esUltimoElemento: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // --- ENCABEZADO (Turno y Fecha) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = AzulFondoSeleccion,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = turno,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AzulPrimario
                        )
                    }
                    Text(
                        text = fecha,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextoOscuro
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // --- DETALLE DE HORAS (Entrada y Salida en columnas ordenadas) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "ENTRADA", fontSize = 10.sp, color = TextoGris, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = horaEntrada, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextoOscuro)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(text = "SALIDA", fontSize = 10.sp, color = TextoGris, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = horaSalida,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextoOscuro,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- BOTONES DE ACCIÓN (Editar y Eliminar) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = TextoGris,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EDITAR", fontSize = 12.sp, color = TextoGris, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onDeleteClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ANULAR", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!esUltimoElemento) {
            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
        }
    }
}

@Composable
fun ItemColaborador(
    codigo: String,
    cargo: String,
    nombreCompleto: String,
    dni: String,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = codigo,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextoOscuro
            )
            Text(
                text = cargo.uppercase(),
                fontSize = 12.sp,
                color = TextoGris
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = nombreCompleto.uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextoOscuro
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dni,
                fontSize = 14.sp,
                color = TextoGris
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEditClick) {
                    Text("EDITAR", color = AzulPrimario, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DialogoNuevoUsuarioRol(
    rolObjetivo: String,
    codigoGenerado: String,
    onDismiss: () -> Unit,
    onGuardadoExitoso: () -> Unit
) {
    var nombres by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var dni by remember { mutableStateOf("") }
    var cargo by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var jornalDia by remember { mutableStateOf("0.0") }
    var jornalNoche by remember { mutableStateOf("0.0") }

    var horasExtras by remember { mutableStateOf("0") }
    var bancoCredito by remember { mutableStateOf("") }
    var interbank by remember { mutableStateOf("") }
    var yapePlin by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (rolObjetivo == "ADMIN") "Agregar Nuevo Admin" else "Agregar Nuevo Colaborador", color = TextoOscuro) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Código Asignado: $codigoGenerado",
                    color = AzulPrimario,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(value = nombres, onValueChange = { nombres = it }, label = { Text("Nombres") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = apellidos, onValueChange = { apellidos = it }, label = { Text("Apellidos") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = dni, onValueChange = { dni = it }, label = { Text("DNI") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = cargo, onValueChange = { cargo = it }, label = { Text("Cargo") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = correo, onValueChange = { correo = it }, label = { Text("Correo electrónico") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = jornalDia, onValueChange = { jornalDia = it }, label = { Text("Jornal Día (S/)") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                    OutlinedTextField(value = jornalNoche, onValueChange = { jornalNoche = it }, label = { Text("Jornal Noche (S/)") }, colors = miTextFieldColors(), modifier = Modifier.weight(1f), enabled = !isSaving)
                }

                OutlinedTextField(value = horasExtras, onValueChange = { horasExtras = it }, label = { Text("Horas Extras (S/)") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = bancoCredito, onValueChange = { bancoCredito = it }, label = { Text("Cuenta BCP / CCI") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = interbank, onValueChange = { interbank = it }, label = { Text("Cuenta Interbank") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                OutlinedTextField(value = yapePlin, onValueChange = { yapePlin = it }, label = { Text("Número Yape/Plin") }, colors = miTextFieldColors(), modifier = Modifier.fillMaxWidth(), enabled = !isSaving)

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nombres.isBlank() || correo.isBlank()) {
                        errorMessage = "Complete los campos obligatorios."
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            val nuevoPersonal = hashMapOf(
                                "codigoColaborador" to codigoGenerado,
                                "nombres" to nombres.trim().uppercase(),
                                "apellidos" to apellidos.trim().uppercase(),
                                "dni" to dni.trim(),
                                "cargo" to cargo.trim().uppercase(),
                                "correo" to correo.trim(),
                                "rol" to rolObjetivo,
                                "estado" to true,
                                "jornalDia" to (jornalDia.toDoubleOrNull() ?: 0.0),
                                "jornalNoche" to (jornalNoche.toDoubleOrNull() ?: 0.0),
                                "horasExtras" to (horasExtras.toDoubleOrNull() ?: 0.0),
                                "bcp" to bancoCredito.trim(),
                                "interbank" to interbank.trim(),
                                "yapePlin" to yapePlin.trim()
                            )
                            db.collection("COLABORADORES").add(nuevoPersonal).await()
                            db.collection("USUARIOS").add(nuevoPersonal).await()

                            onGuardadoExitoso()
                        } catch (e: Exception) {
                            errorMessage = "Error al guardar: ${e.message}"
                            isSaving = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
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
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar", color = TextoGris) }
        }
    )
}