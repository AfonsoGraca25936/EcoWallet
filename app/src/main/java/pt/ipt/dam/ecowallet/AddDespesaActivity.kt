package pt.ipt.dam.ecowallet

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.SaldoRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Atividade para adicionar uma nova despesa.
 * Inclui suporte para captura de fotografia da fatura e sincronização com o servidor.
 */
class AddDespesaActivity : AppCompatActivity() {

    // Componentes da interface
    private lateinit var etTitulo: TextInputEditText
    private lateinit var etValor: TextInputEditText
    private lateinit var etCategoria: TextInputEditText
    private lateinit var ivPreview: ImageView

    // Controlo da fotografia
    private var currentPhotoPath: String? = null
    private var photoUri: Uri? = null

    /**
     * Gere o pedido de permissão da câmara em tempo de execução.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permissão concedida pelo utilizador, procede para a câmara
            iniciarCamara()
        } else {
            // Permissão negada, informa o utilizador da necessidade da mesma
            Toast.makeText(this, "A permissão da câmara é necessária para tirar a foto da fatura.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Gere o resultado da aplicação de câmara.
     * Se a foto foi tirada com sucesso, exibe a miniatura no ecrã.
     */
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            ivPreview.visibility = View.VISIBLE
            ivPreview.setImageURI(photoUri)
        } else {
            Toast.makeText(this, "Foto cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configura visualmente a barra de estado (topo verde)
        window.statusBarColor = Color.parseColor("#2E7D32")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_add_despesa)

        // Ajuste de margens para ecrãs responsivos e áreas seguras (Edge-to-Edge)
        val mainContainer = findViewById<View>(R.id.mainContainer)
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val margem = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(margem, 0, margem, systemBars.bottom + margem)
            insets
        }

        // Inicialização dos campos do formulário
        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)
        ivPreview = findViewById(R.id.ivPreview)

        // Listeners de clique para os botões principais
        findViewById<Button>(R.id.btnFoto).setOnClickListener { verificarPermissaoETirarFoto() }
        findViewById<Button>(R.id.btnGuardar).setOnClickListener { guardarDespesa() }
    }

    /**
     * Verifica se a app já tem autorização para aceder à câmara.
     */
    private fun verificarPermissaoETirarFoto() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                // Já temos permissão, inicia o processo de captura
                iniciarCamara()
            }
            else -> {
                // Solicita a permissão ao utilizador através do sistema Android
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Cria o ficheiro onde a foto será guardada e lança a câmara.
     */
    private fun iniciarCamara() {
        val photoFile = criarFicheiroImagem()
        if (photoFile != null) {
            val authority = "${applicationContext.packageName}.fileprovider"
            // Gera um URI seguro para partilhar com a app de câmara
            val uri = FileProvider.getUriForFile(this, authority, photoFile)
            photoUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    /**
     * Cria um ficheiro temporário .jpg com um nome baseado na data/hora atual.
     */
    private fun criarFicheiroImagem(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (storageDir?.exists() == false) storageDir.mkdirs()
            File.createTempFile("FATURA_${timeStamp}_", ".jpg", storageDir).apply {
                // Guarda o caminho absoluto para uso futuro (gravação na BD)
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Valida os dados, calcula o novo saldo e grava a despesa na BD local e na API.
     */
    private fun guardarDespesa() {
        val titulo = etTitulo.text.toString()
        val valorStr = etValor.text.toString()
        val categoria = etCategoria.text.toString().ifEmpty { "Geral" }

        // Validação de campos obrigatórios
        if (titulo.isEmpty() || valorStr.isEmpty()) {
            Toast.makeText(this, "Preencha título e valor", Toast.LENGTH_SHORT).show()
            return
        }

        // Converte o valor para negativo (uma despesa subtrai ao saldo)
        val valorDespesa = -abs(valorStr.toDoubleOrNull() ?: 0.0)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                val novoSaldo = user.saldo + valorDespesa
                val dataHoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                // Criação do objeto Despesa com ID único (UUID)
                val novaDespesa = Despesa(
                    id = UUID.randomUUID().toString(),
                    titulo = titulo,
                    valor = valorDespesa,
                    categoria = categoria,
                    data = dataHoje,
                    fotoCaminho = currentPhotoPath,
                    userId = user.id
                )

                try {
                    // 1. Atualiza o novo saldo na API remota
                    val saldoReq = SaldoRequest(saldo = novoSaldo)
                    RetrofitClient.instance.updateSaldo(user.id, saldoReq).execute()

                    // 2. Tenta enviar a despesa para o servidor
                    val response = RetrofitClient.instance.addDespesa(novaDespesa).execute()

                    if (response.isSuccessful) {
                        // 3. Se a API aceitou, atualiza os dados locais (Room)
                        db.utilizadorDao().updateSaldo(user.id, novoSaldo)
                        db.despesaDao().insert(novaDespesa.apply { isSynced = true })

                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Despesa registada com sucesso!", Toast.LENGTH_SHORT).show()
                            finish() // Volta para o ecrã anterior
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Erro no servidor ao guardar", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Falha de rede: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}