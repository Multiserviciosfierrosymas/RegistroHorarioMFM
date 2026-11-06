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
        estado: String = "ACTIVO" // Parámetro de estado por defecto
    ) {
        withContext(Dispatchers.IO) {
            try {
                val urlObj = URL("https://script.google.com/macros/s/AKfycbyQRYAFptY9mLS-xYJbO2qkg5i5ZtxmSsd9fuvyjEZ6qC-WRg-eFm-kGbZ6sYVYiU3j/exec")
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = StringBuilder().apply {
                    append(URLEncoder.encode("nombreCompleto", "UTF-8")).append("=").append(URLEncoder.encode(nombreCompleto, "UTF-8"))
                    append("&").append(URLEncoder.encode("fecha", "UTF-8")).append("=").append(URLEncoder.encode(fecha, "UTF-8"))
                    append("&").append(URLEncoder.encode("turno", "UTF-8")).append("=").append(URLEncoder.encode(turno, "UTF-8"))
                    append("&").append(URLEncoder.encode("horaEntrada", "UTF-8")).append("=").append(URLEncoder.encode(horaEntrada, "UTF-8"))
                    append("&").append(URLEncoder.encode("horaSalida", "UTF-8")).append("=").append(URLEncoder.encode(horaSalida, "UTF-8"))
                    append("&").append(URLEncoder.encode("idRegistro", "UTF-8")).append("=").append(URLEncoder.encode(idRegistro, "UTF-8"))
                    append("&").append(URLEncoder.encode("estado", "UTF-8")).append("=").append(URLEncoder.encode(estado, "UTF-8"))
                }.toString()

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }
                conn.responseCode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}