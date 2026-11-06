package com.registrohorario_mfm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// 1. LLAMA A ESTA FUNCIÓN DENTRO DE TU onCreate()
fun verificarActualizacionFirebase(context: Context) {
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    db.collection("CONFIG_APP").document("actualizacion").get()
        .addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val versionCodeRemoto = document.getLong("versionCode")?.toInt() ?: 1
                val urlApk = document.getString("urlApk") ?: ""
                val mensaje = document.getString("mensaje") ?: "Actualización disponible"

                // Obtener versión actual instalada en el teléfono
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionCodeActual = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    pInfo.versionCode
                }

                // Si la versión en Firestore es mayor, lanzamos la alerta
                if (versionCodeRemoto > versionCodeActual && urlApk.isNotEmpty()) {
                    mostrarDialogoActualizacion(context, mensaje, urlApk)
                }
            }
        }
        .addOnFailureListener {
            // Error de red o lectura, la app sigue funcionando normal
        }
}

// 2. MUESTRA EL DIÁLOGO DE ALERTA
private fun mostrarDialogoActualizacion(context: Context, mensaje: String, urlApk: String) {
    AlertDialog.Builder(context)
        .setTitle("¡Actualización disponible!")
        .setMessage(mensaje)
        .setCancelable(false)
        .setPositiveButton("Actualizar") { _, _ ->
            descargarEInstalarApk(context, urlApk)
        }
        .setNegativeButton("Más tarde", null)
        .show()
}

// 3. DESCARGA EL APK USANDO EL GESTOR NATIVO DE ANDROID
private fun descargarEInstalarApk(context: Context, urlApk: String) {
    Toast.makeText(context, "Descargando actualización...", Toast.LENGTH_SHORT).show()

    val request = DownloadManager.Request(Uri.parse(urlApk)).apply {
        setTitle("Actualización MFM")
        setDescription("Descargando nueva versión...")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "actualizacion-mfm.apk")
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
    }

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}