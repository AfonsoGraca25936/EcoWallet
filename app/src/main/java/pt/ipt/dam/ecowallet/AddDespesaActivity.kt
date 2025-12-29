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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import java.util.UUID // <--- IMPORTANTE: Necessário para gerar o ID String

class AddDespesaActivity : AppCompatActivity() {

    private lateinit var etTitulo: TextInputEditText
    private lateinit var etValor: TextInputEditText
    private lateinit var etCategoria: TextInputEditText
    private lateinit var ivPreview: ImageView

    private var currentPhotoPath: String? = null
    private var photoUri: Uri? = null

    // Lógica da Câmara
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            ivPreview.setImageURI(photoUri)
        } else {
            Toast.makeText(this, "Foto cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_despesa)

        // Configuração de Margens (Edge-to-Edge)
        val mainContainer = findViewById<android.view.View>(R.id.mainContainer)
        if (mainContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val paddingBase = (24 * resources.displayMetrics.density).toInt()
                v.setPadding(paddingBase, systemBars.top + paddingBase, paddingBase, systemBars.bottom + paddingBase)
                insets
            }
        }

        // Ligar componentes
        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)
        ivPreview = findViewById(R.id.ivPreview)

        findViewById<Button>(R.id.btnFoto).setOnClickListener { tirarFoto() }
        findViewById<Button>(R.id.btnGuardar).setOnClickListener { guardarDespesa() }
    }

    private fun tirarFoto() {
        val photoFile = criarFicheiroImagem()
        if (photoFile != null) {
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, photoFile)
            photoUri = uri

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

        // 1. GERAR ID STRING (UUID)
        // Isto é fundamental agora que mudámos o ID de Int para String
        val novoId = UUID.randomUUID().toString()

        val novaDespesa = Despesa(
            id = novoId,
            titulo = titulo,
            valor = valor,
            categoria = categoria,
            data = dataHoje,
            fotoCaminho = currentPhotoPath,
            isSynced = false
        )

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            // 2. Guardar na Base de Dados Local (Room)
            db.despesaDao().insert(novaDespesa)

            // 3. Atualizar o Saldo do Utilizador
            val user = db.utilizadorDao().getUtilizador()
            if (user != null) {
                val novoSaldo = user.saldo - valor
                db.utilizadorDao().updateSaldo(user.id, novoSaldo)
            }

            // 4. Enviar para a API em background (Fire & Forget)
            enviarParaAPI(novaDespesa)

            // 5. Fechar a atividade imediatamente para dar sensação de rapidez
            Toast.makeText(applicationContext, "Despesa guardada!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun enviarParaAPI(despesa: Despesa) {
        RetrofitClient.instance.addDespesa(despesa).enqueue(object : Callback<Despesa> {
            override fun onResponse(call: Call<Despesa>, response: Response<Despesa>) {
                // Sucesso: O servidor recebeu.
                // Na próxima sincronização da MainActivity, os dados ficam todos alinhados.
            }
            override fun onFailure(call: Call<Despesa>, t: Throwable) {
                // Falha: Não faz mal, está guardado localmente com isSynced=false
            }
        })
    }
}