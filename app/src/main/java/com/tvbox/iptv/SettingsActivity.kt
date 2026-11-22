package com.tvbox.iptv

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private lateinit var adapter: SourceAdapter
    private lateinit var emptyHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        emptyHint = findViewById(R.id.emptyHint)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        adapter = SourceAdapter { showActions(it) }
        list.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener { showSourceDialog(null) }
        findViewById<Button>(R.id.btnPin).setOnClickListener { changePin() }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        reload()
    }

    private fun reload() {
        val s = Prefs.sources(this)
        adapter.submit(s)
        emptyHint.visibility = if (s.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showActions(s: Source) {
        val opts = ArrayList<String>()
        opts += "Editar"
        if (s.isList) opts += "Actualizar lista"
        opts += "Borrar"
        AlertDialog.Builder(this)
            .setTitle(s.name)
            .setItems(opts.toTypedArray()) { _, i ->
                when (opts[i]) {
                    "Editar" -> showSourceDialog(s)
                    "Actualizar lista" -> download(s)
                    "Borrar" -> confirmDelete(s)
                }
            }
            .show()
    }

    private fun confirmDelete(s: Source) {
        AlertDialog.Builder(this)
            .setMessage("¿Borrar «${s.name}»?")
            .setPositiveButton("Borrar") { _, _ ->
                Prefs.saveSources(this, Prefs.sources(this).filter { it.id != s.id })
                Prefs.listFile(this, s.id).delete()
                reload()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun download(s: Source) {
        Toast.makeText(this, "Descargando «${s.name}»…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            Net.downloadList(this@SettingsActivity, s)
                .onSuccess { n ->
                    Toast.makeText(this@SettingsActivity, "Lista lista: $n canales", Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("No se pudo descargar «${s.name}»")
                        .setMessage(Net.describe(e))
                        .setPositiveButton("Entendido", null)
                        .show()
                }
        }
    }

    private fun showSourceDialog(existing: Source?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_source, null)
        val etName = v.findViewById<EditText>(R.id.etName)
        val etUrl = v.findViewById<EditText>(R.id.etUrl)
        val rbList = v.findViewById<RadioButton>(R.id.rbList)
        val rbWeb = v.findViewById<RadioButton>(R.id.rbWeb)
        val boxes = listOf(
            "tv" to v.findViewById<CheckBox>(R.id.cbTv),
            "peliculas" to v.findViewById<CheckBox>(R.id.cbPeliculas),
            "series" to v.findViewById<CheckBox>(R.id.cbSeries),
            "anime" to v.findViewById<CheckBox>(R.id.cbAnime),
            "infantil" to v.findViewById<CheckBox>(R.id.cbInfantil)
        )
        if (existing != null) {
            etName.setText(existing.name)
            etUrl.setText(existing.url)
            rbList.isChecked = existing.isList
            rbWeb.isChecked = !existing.isList
            boxes.forEach { (k, cb) -> cb.isChecked = k in existing.cats }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Agregar fuente" else "Editar fuente")
            .setView(v)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            var url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                etUrl.error = "Escribe la dirección"
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val cats = boxes.filter { it.second.isChecked }.map { it.first }
            if (cats.isEmpty()) {
                Toast.makeText(this, "Elige al menos un lugar donde mostrarla", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val isList = rbList.isChecked
            val name = etName.text.toString().trim().ifEmpty { Uri.parse(url).host ?: "Fuente" }
            val src = Source(
                existing?.id ?: UUID.randomUUID().toString().take(8),
                name, if (isList) Source.TYPE_LIST else Source.TYPE_WEB, url, cats
            )
            dialog.dismiss()

            val all = Prefs.sources(this).toMutableList()
            val idx = all.indexOfFirst { it.id == src.id }
            if (idx >= 0) all[idx] = src else all += src
            Prefs.saveSources(this, all)
            reload()

            val needsDownload = isList &&
                (existing == null || existing.url != url || !Prefs.listFile(this, src.id).exists())
            if (needsDownload) download(src)
        }
    }

    private fun changePin() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.hint = "Nuevo PIN (vacío = sin PIN)"
        AlertDialog.Builder(this)
            .setTitle("Cambiar PIN")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                Prefs.setPin(this, et.text.toString().trim())
                Toast.makeText(this, "PIN guardado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class SourceAdapter(private val onClick: (Source) -> Unit) :
        RecyclerView.Adapter<SourceAdapter.VH>() {

        private var items: List<Source> = emptyList()

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.srcTitle)
            val sub: TextView = v.findViewById(R.id.srcSub)
        }

        fun submit(l: List<Source>) {
            items = l
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_source, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val s = items[position]
            h.title.text = s.name
            h.sub.text = (if (s.isList) "Lista IPTV" else "Página web") + " · " +
                s.cats.joinToString(", ") { Cats.byKey(it).label }
            h.itemView.setOnClickListener { onClick(s) }
        }
    }
}
