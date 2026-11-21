package com.registrohorario_mfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NetworkUtils {
    private const val URL_APPS_SCRIPT =
        "https://script.google.com/macros/s/AKfycbyQRYAFptY9mLS-xYJbO2qkg5i5ZtxmSsd9fuvyjEZ6qC-WRg-eFm-kGbZ6sYVYiU3j/exec"

    // Se conserva el envío de asistencias a REGISTRO FORMULARIO.
    suspend fun enviarRegistroASheetAndroid(
        nombreCompleto: String,
        fecha: String,
        turno: String,
        horaEntrada: String,
        horaSalida: String,
        idRegistro: String,
        estado: String = "ACTIVO"
    ) {
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null

            try {
                val urlObj = URL(URL_APPS_SCRIPT)
                conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

                val postData = StringBuilder().apply {
                    append("nombreCompleto=").append(URLEncoder.encode(nombreCompleto, "UTF-8"))
                    append("&fecha=").append(URLEncoder.encode(fecha, "UTF-8"))
                    append("&turno=").append(URLEncoder.encode(turno, "UTF-8"))
                    append("&horaEntrada=").append(URLEncoder.encode(horaEntrada, "UTF-8"))
                    append("&horaSalida=").append(URLEncoder.encode(horaSalida, "UTF-8"))
                    append("&idRegistro=").append(URLEncoder.encode(idRegistro, "UTF-8"))
                    append("&estado=").append(URLEncoder.encode(estado, "UTF-8"))
                }.toString()

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val codigoRespuesta = conn.responseCode
                val respuesta = if (codigoRespuesta in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: "Sin detalle de respuesta"
                }

                if (codigoRespuesta !in 200..299) {
                    throw Exception("Error HTTP $codigoRespuesta al sincronizar con Google Sheets: $respuesta")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // NUEVO: registro de ADMIN/COLABORADOR en la hoja COLABORADORES.
    suspend fun enviarColaboradorASheetAndroid(
        uid: String,
        codigoColaborador: String,
        correo: String,
        nombres: String,
        apellidos: String,
        dni: String,
        cargo: String,
        jornalDia: Double,
        jornalNoche: Double,
        horasExtras: Double,
        bcp: String,
        interbank: String,
        yapePlin: String
    ) {
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null

            try {
                conn = URL(URL_APPS_SCRIPT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

                val parametros = linkedMapOf(
                    "accion" to "REGISTRAR_COLABORADOR",
                    "uid" to uid,
                    "codigoColaborador" to codigoColaborador,
                    "correo" to correo,
                    "nombres" to nombres,
                    "apellidos" to apellidos,
                    "dni" to dni,
                    "cargo" to cargo,
                    "jornalDia" to jornalDia.toString(),
                    "jornalNoche" to jornalNoche.toString(),
                    "horasExtras" to horasExtras.toString(),
                    "bcp" to bcp,
                    "scotiabank" to "",
                    "interbank" to interbank,
                    "yapePlin" to yapePlin
                )

                val postData = parametros.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" +
                            URLEncoder.encode(it.value, "UTF-8")
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val codigoRespuesta = conn.responseCode
                val respuesta = if (codigoRespuesta in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: "Sin detalle de respuesta"
                }

                if (codigoRespuesta !in 200..299) {
                    throw Exception("Error HTTP $codigoRespuesta: $respuesta")
                }

                val json = try {
                    JSONObject(respuesta)
                } catch (e: Exception) {
                    throw Exception("Google Apps Script no devolvió JSON válido: $respuesta")
                }

                if (json.optString("status") != "success") {
                    throw Exception(json.optString("message", "No se pudo registrar el colaborador en Google Sheets."))
                }
            } finally {
                conn?.disconnect()
            }
        }
    }
}
