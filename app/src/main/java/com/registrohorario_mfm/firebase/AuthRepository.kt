package com.registrohorario_mfm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun iniciarSesion(email: String, pass: String): Result<String> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("No se pudo obtener el UID")

            val userDoc = db.collection("USUARIOS").document(uid).get().await()
            if (userDoc.exists() && userDoc.getBoolean("estado") == true) {
                val rol = userDoc.getString("rol") ?: "COLABORADOR"
                Result.success(rol)
            } else {
                auth.signOut()
                Result.failure(Exception("Usuario inactivo o no registrado en el sistema."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}