package pt.ipt.dam.ecowallet

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddDespesaActivity : AppCompatActivity() {

    private lateinit var etTitulo: TextInputEditText
    private lateinit var etValor: TextInputEditText
    private lateinit var etCategoria: TextInputEditText
    private lateinit var ivPreview: ImageView

    private var currentPhotoPath: String? = null
    private var photoUri: Uri? = null

    // Lançador da câmara
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ivPreview.setImageURI(photoUri)
        } else {
            Toast.makeText(this, "Foto cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_despesa)

        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)
        ivPreview = findViewById(R.id.ivPreview)

        findViewById<Button>(R.id.btnFoto).setOnClickListener {
            tirarFoto()
        }

        findViewById<Button>(R.id.btnGuardar).setOnClickListener {
            guardarDespesa()
        }
    }

    private fun tirarFoto() {
        val photoFile = criarFicheiroImagem()

        // Verificamos se o ficheiro foi criado
        if (photoFile != null) {
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, photoFile)

            // Guardamos na variável global
            photoUri = uri

            // CORREÇÃO: Só lançamos a câmara se o 'uri' NÃO for nulo
            if (uri != null) {
                takePictureLauncher.launch(uri)
            }
        }
    }

    private fun criarFicheiroImagem(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun guardarDespesa() {
        val titulo = etTitulo.text.toString()
        val valorStr = etValor.text.toString()
        val categoria = etCategoria.text.toString()

        if (titulo.isEmpty() || valorStr.isEmpty()) {
            Toast.makeText(this, "Preencha título e valor", Toast.LENGTH_SHORT).show()
            return
        }

        val valor = valorStr.toDoubleOrNull() ?: 0.0
        val dataHoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val novaDespesa = Despesa(
            titulo = titulo,
            valor = valor,
            categoria = categoria,
            data = dataHoje,
            fotoCaminho = currentPhotoPath,
            isSynced = false
        )

        // 1. Guardar localmente (Room)
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.despesaDao().insert(novaDespesa)

            // 2. Tentar enviar para a API (Bónus)
            enviarParaAPI(novaDespesa)

            Toast.makeText(applicationContext, "Despesa guardada!", Toast.LENGTH_SHORT).show()
            finish() // Volta para a MainActivity
        }
    }

    private fun enviarParaAPI(despesa: Despesa) {
        RetrofitClient.instance.addDespesa(despesa).enqueue(object : Callback<Despesa> {
            override fun onResponse(call: Call<Despesa>, response: Response<Despesa>) {
                // Se correr bem, ótimo. Se não, já está guardado localmente.
            }
            override fun onFailure(call: Call<Despesa>, t: Throwable) { }
        })
    }
}