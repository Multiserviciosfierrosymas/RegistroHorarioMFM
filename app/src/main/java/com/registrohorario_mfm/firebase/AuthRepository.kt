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

            // 2. Buscar el documento en la colección USUARIOS por correo o email
            val usuariosQuery = db.collection("USUARIOS")
                .whereEqualTo("correo", userEmail)
                .get()
                .await()

            val userDoc = if (!usuariosQuery.isEmpty) {
                usuariosQuery.documents[0]
            } else {
                db.collection("USUARIOS")
                    .whereEqualTo("email", userEmail)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
            }

            if (userDoc != null && userDoc.exists()) {
                // Obtener el rol y asegurar que esté en mayúsculas (ej: "ADMIN" o "COLABORADOR")
                val rol = userDoc.getString("rol")?.trim()?.uppercase() ?: "COLABORADOR"
                Result.success(rol)
            } else {
                Result.failure(Exception("El usuario no está registrado en la base de datos de usuarios."))
            }

        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("El correo electrónico no está registrado."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("La contraseña es incorrecta o el formato del correo es inválido."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Ocurrió un error al iniciar sesión."))
        }
    }
}