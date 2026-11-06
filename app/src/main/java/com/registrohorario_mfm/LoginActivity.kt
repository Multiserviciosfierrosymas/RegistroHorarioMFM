package com.registrohorario_mfm

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginFondoGrisClaro)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- LOGO CENTRADO ARRIBA ---
        // Asegúrate de tener tu imagen en res/drawable con el nombre "logo_mfm"
        // Si tu archivo se llama diferente, cambia R.drawable.logo_mfm por el correcto.
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
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            // NUEVO: Optimiza el teclado para correos electrónicos
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next // Cambia la tecla a "Siguiente" para saltar a la contraseña
            ),
            colors = loginTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            // NUEVO: Esto le dice al teclado que es un campo de contraseña
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done // Cambia la tecla del teclado a "Hecho" o "Check"
            ),
            colors = loginTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Casilla de verificación para Recordar Credenciales
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

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true

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
                            onLoginError("Error: ${error.message}")
                        }
                    }
                } else {
                    onLoginError("Por favor completa todos los campos")
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