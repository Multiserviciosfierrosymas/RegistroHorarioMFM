package com.registrohorario_mfm

import android.app.AlertDialog // AlertDialog clásico de Android (no Compose)
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

// --- IMPORTACIONES CLAVE DE FIREBASE QUE FALTABAN ---
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot

// Paleta unificada coherente con Admin y Colaborador
private val LoginFondoGrisClaro = Color(0xFFF5F5F5)
private val LoginAzulPrimario = Color(0xFF1976D2)
private val LoginTextoOscuro = Color(0xFF212121)
private val LoginTextoGris = Color(0xFF757575)

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = LoginAzulPrimario,
    unfocusedBorderColor = Color.LightGray,
    focusedLabelColor = LoginAzulPrimario,
    unfocusedLabelColor = LoginTextoGris
)

class LoginActivity : ComponentActivity() {
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = LoginFondoGrisClaro
            ) {
                LoginScreen(
                    context = this,
                    onLoginSuccess = { rol ->
                        val intent = if (rol == "ADMIN") {
                            Intent(this@LoginActivity, AdminActivity::class.java)
                        } else {
                            Intent(this@LoginActivity, ColaboradorActivity::class.java)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onLoginError = { errorMsg ->
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                    },
                    authRepository = authRepository
                )
            }
        }

        verificarActualizacionFirebase(this)
    }
}

@Composable
fun LoginScreen(
    context: Context,
    onLoginSuccess: (String) -> Unit,
    onLoginError: (String) -> Unit,
    authRepository: AuthRepository
) {
    val sharedPreferences = context.getSharedPreferences("PreferenciasLoginMFM", Context.MODE_PRIVATE)

    var email by remember { mutableStateOf(sharedPreferences.getString("KEY_EMAIL", "") ?: "") }
    var password by remember { mutableStateOf(sharedPreferences.getString("KEY_PASSWORD", "") ?: "") }
    var recordarCredenciales by remember { mutableStateOf(sharedPreferences.getBoolean("KEY_RECORDAR", false)) }

    var isLoading by remember { mutableStateOf(false) }
    var loginErrorMsg by remember { mutableStateOf<String?>(null) } // <-- NUEVO: Estado para el mensaje de error visual
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginFondoGrisClaro)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_mfm),
            contentDescription = "Logo MFM",
            modifier = Modifier
                .size(90.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "Registro Horario MFM",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = LoginTextoOscuro,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; loginErrorMsg = null }, // Limpia el error al escribir
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = loginTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; loginErrorMsg = null }, // Limpia el error al escribir
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            colors = loginTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = recordarCredenciales,
                onCheckedChange = { recordarCredenciales = it },
                colors = CheckboxDefaults.colors(checkedColor = LoginAzulPrimario)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Recordar mis datos", fontSize = 14.sp, color = LoginTextoGris)
        }

        // --- NUEVO: Texto de error en pantalla si las credenciales fallan ---
        if (!loginErrorMsg.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = loginErrorMsg ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    loginErrorMsg = null

                    val editor = sharedPreferences.edit()
                    if (recordarCredenciales) {
                        editor.putString("KEY_EMAIL", email.trim())
                        editor.putString("KEY_PASSWORD", password.trim())
                        editor.putBoolean("KEY_RECORDAR", true)
                    } else {
                        editor.clear()
                    }
                    editor.apply()

                    scope.launch {
                        val resultado = authRepository.iniciarSesion(email.trim(), password.trim())
                        isLoading = false
                        resultado.onSuccess { rol ->
                            onLoginSuccess(rol)
                        }.onFailure { error ->
                            val mensajeAmigable = error.message ?: "Ocurrió un error inesperado"
                            loginErrorMsg = mensajeAmigable // Muestra el error en la interfaz
                            onLoginError(mensajeAmigable)  // Opcional: Mantiene el Toast si lo deseas
                        }
                    }
                } else {
                    loginErrorMsg = "Por favor completa todos los campos"
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoginAzulPrimario),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

fun verificarActualizacionFirebase(context: Context) {
    val db = FirebaseFirestore.getInstance()

    db.collection("CONFIG_APP").document("actualizacion").get()
        .addOnSuccessListener { document: DocumentSnapshot ->
            if (document.exists()) {
                val versionCodeRemoto = document.getLong("versionCode")?.toInt() ?: 1
                val urlApk = document.getString("urlApk") ?: ""
                val mensaje = document.getString("mensaje") ?: "Actualización disponible"

                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionCodeActual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    pInfo.versionCode
                }

                if (versionCodeRemoto > versionCodeActual && urlApk.isNotEmpty()) {
                    mostrarDialogoActualizacion(context, mensaje, urlApk)
                }
            }
        }
        .addOnFailureListener { e: Exception ->
            Toast.makeText(context, "Error Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}

private fun mostrarDialogoActualizacion(context: Context, mensaje: String, urlApk: String) {
    AlertDialog.Builder(context)
        .setTitle("¡Actualización disponible!")
        .setMessage(mensaje)
        .setCancelable(false)
        .setPositiveButton("Actualizar") { _, _ ->
            descargarEInstalarApk(context, urlApk)
        }
        .show()
}

private fun descargarEInstalarApk(context: Context, urlApk: String) {
    Toast.makeText(context, "Iniciando descarga...", Toast.LENGTH_SHORT).show()

    try {
        val request = DownloadManager.Request(Uri.parse(urlApk)).apply {
            setTitle("Actualización MFM")
            setDescription("Descargando nueva versión...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            // Usar la ruta pública de descargas con un nombre limpio
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "registro_horario_update.apk")

            // Permitir descarga en cualquier red (datos o Wi-Fi)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)

            // Permitir que aparezca en las descargas del sistema
            allowScanningByMediaScanner()
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)

        Toast.makeText(context, "La descarga continúa en la barra de notificaciones", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al iniciar descarga: ${e.message}", Toast.LENGTH_LONG).show()
    }
}