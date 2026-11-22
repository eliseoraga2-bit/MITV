package com.tvbox.iptv

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object Ui {

    fun askPin(a: Activity, onOk: () -> Unit) {
        val pin = Prefs.pin(a)
        if (pin.isEmpty()) {
            onOk()
            return
        }
        val et = EditText(a)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.hint = "PIN"
        AlertDialog.Builder(a)
            .setTitle("Escribe el PIN")
            .setMessage(if (pin == "0000") "PIN inicial: 0000 (cámbialo en Configuración)" else null)
            .setView(et)
            .setPositiveButton("Entrar") { _, _ ->
                if (et.text.toString().trim() == pin) onOk()
                else Toast.makeText(a, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun openSource(a: Activity, s: Source) {
        if (s.isList) {
            if (!Prefs.listFile(a, s.id).exists()) {
                Toast.makeText(
                    a, "Esta lista no está descargada. Actualízala en Configuración.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            a.startActivity(
                Intent(a, TvActivity::class.java)
                    .putExtra("source_id", s.id)
                    .putExtra("group_first", "tv" !in s.cats)
            )
        } else {
            a.startActivity(Intent(a, BrowserActivity::class.java).putExtra("url", s.url))
        }
    }

    private val ytPackages = listOf(
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv.beta",
        "org.schabi.newpipe"
    )

    fun openYouTube(a: Activity) {
        val pm = a.packageManager
        for (p in ytPackages) {
            val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p)
            if (i != null) {
                a.startActivity(i)
                return
            }
        }
        AlertDialog.Builder(a)
            .setTitle("Falta la app de YouTube")
            .setMessage(
                "No encontré una app de YouTube sin anuncios en esta TV box.\n\n" +
                    "Instala SmartTube (github.com/yuliskov/SmartTube) o NewPipe (newpipe.net) " +
                    "desde su sitio oficial y vuelve a intentarlo."
            )
            .setPositiveButton("Entendido", null)
            .show()
    }
}
