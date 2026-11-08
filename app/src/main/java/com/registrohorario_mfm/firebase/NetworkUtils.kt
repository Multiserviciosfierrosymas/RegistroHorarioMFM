package com.registrohorario_mfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NetworkUtils {
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
                val urlObj = URL("https://script.google.com/macros/s/AKfycbyQRYAFptY9mLS-xYJbO2qkg5i5ZtxmSsd9fuvyjEZ6qC-WRg-eFm-kGbZ6sYVYiU3j/exec")

                conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
                )

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
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                        it.readText()
                    }
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: "Sin detalle de respuesta"
                }

                if (codigoRespuesta !in 200..299) {
                    throw Exception(
                        "Error HTTP $codigoRespuesta al sincronizar con Google Sheets: $respuesta"
                    )
                }

            } finally {
                conn?.disconnect()
            }
        }
    }
}