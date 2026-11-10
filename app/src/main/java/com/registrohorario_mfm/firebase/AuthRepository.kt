package com.registrohorario_mfm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun iniciarSesion(email: String, pass: String): Result<String> {
        return try {
            // 1. Iniciar sesión en Firebase Auth
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw Exception("Usuario no encontrado")
            val userEmail = user.email ?: email

            // 2. Buscar el rol en Firestore consultando por el correo electrónico
            // (Soporta tanto el campo 'correo' como 'email')
            var usuariosQuery = db.collection("USUARIOS")
                .whereEqualTo("correo", userEmail)
                .get()
                .await()

            var userDoc = if (!usuariosQuery.isEmpty) {
                usuariosQuery.documents[0]
            } else {
                db.collection("USUARIOS")
                    .whereEqualTo("email", userEmail)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
            }

            val rol = userDoc?.getString("rol")?.uppercase() ?: "COLABORADOR"
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