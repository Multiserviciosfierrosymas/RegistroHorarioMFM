package com.registrohorario_mfm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun iniciarSesion(email: String, pass: String): Result<String> {
        return try {
            val authResult = FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("Usuario no encontrado")

            // Buscar el rol en Firestore
            val userDoc = FirebaseFirestore.getInstance().collection("USUARIOS").document(uid).get().await()
            val rol = userDoc.getString("rol") ?: "COLABORADOR"
            Result.success(rol)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("El correo electrónico no está registrado."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("La contraseña es incorrecta o el formato del correo es inválido."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Ocurrió un error al iniciar sesión."))
        }
    }
}