package com.tvbox.iptv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Net {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun fetch(c: OkHttpClient, url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
        return c.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body!!.string()
        }
    }

    // Solo se usa para descargar listas (datos publicos), nunca para el video.
    private fun insecureClient(): OkHttpClient {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
        return client.newBuilder()
            .sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    suspend fun downloadList(ctx: Context, src: Source): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = try {
                fetch(client, src.url)
            } catch (e: SSLException) {
                fetch(insecureClient(), src.url)
            }
            val n = M3uParser.parse(text).size
            if (n == 0) error("sin canales")
            Prefs.listFile(ctx, src.id).writeText(text)
            n
        }
    }

    fun describe(e: Throwable): String {
        val base = when {
            e is UnknownHostException -> "No hay internet o no se encontró el servidor."
            e is SocketTimeoutException -> "La conexión tardó demasiado."
            e is SSLException -> "Problema de seguridad de la conexión."
            e.message == "sin canales" -> "La lista se descargó pero no tiene canales."
            e.message?.startsWith("HTTP") == true -> "El servidor respondió con error."
            e is IllegalArgumentException -> "La dirección está mal escrita."
            else -> "No se pudo cargar la lista."
        }
        return base + "\n(" + e.javaClass.simpleName + ": " + e.message + ")"
    }
}
