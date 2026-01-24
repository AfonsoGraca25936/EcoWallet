package pt.ipt.dam.ecowallet

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
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
import kotlin.math.abs

/**
 * Atividade responsável por editar uma transação existente.
 * Permite alterar título, valor, categoria e a foto da fatura associada.
 */
class EditTransactionActivity : AppCompatActivity() {

    // Componentes da Interface de Utilizador (UI)
    private lateinit var etTitulo: TextInputEditText
    private lateinit var etValor: TextInputEditText
    private lateinit var etCategoria: TextInputEditText
    private lateinit var ivEditPreview: ImageView
    private lateinit var btnRemoveFoto: ImageButton
    private lateinit var btnEditFoto: Button

    // Variáveis de estado para controlo da transação e imagem
    private var despesaId: String = ""
    private var userId: String = ""
    private var valorAntigo: Double = 0.0
    private var dataOriginal: String = ""
    private var currentPhotoPath: String? = null
    private var photoUri: Uri? = null

    // Launcher para gerir o pedido de permissão da câmara em tempo de execução
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) iniciarCamara() else Toast.makeText(this, "Permissão necessária", Toast.LENGTH_SHORT).show()
    }

    // Launcher para capturar a fotografia e exibir o resultado
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            exibirFoto(currentPhotoPath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_transaction)

        // Inicialização dos campos do formulário
        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)
        ivEditPreview = findViewById(R.id.ivEditPreview)
        btnRemoveFoto = findViewById(R.id.btnRemoveFoto)
        btnEditFoto = findViewById(R.id.btnEditFoto)

        // Recuperar os dados enviados pela MainActivity através do Intent
        despesaId = intent.getStringExtra("ID") ?: ""
        userId = intent.getStringExtra("USER_ID") ?: ""
        val titulo = intent.getStringExtra("TITULO") ?: ""
        valorAntigo = intent.getDoubleExtra("VALOR", 0.0)
        val categoria = intent.getStringExtra("CATEGORIA") ?: ""
        dataOriginal = intent.getStringExtra("DATA") ?: ""
        currentPhotoPath = intent.getStringExtra("FOTO")

        // Preencher o formulário com os valores atuais da transação
        etTitulo.setText(titulo)
        etValor.setText(abs(valorAntigo).toString()) // Mostra o valor absoluto (positivo) para edição
        etCategoria.setText(categoria)

        // Lógica inicial de exibição da foto (se existir)
        if (!currentPhotoPath.isNullOrEmpty()) {
            exibirFoto(currentPhotoPath)
        } else {
            ocultarFoto()
        }

        // Definição dos listeners de clique
        btnRemoveFoto.setOnClickListener { ocultarFoto() }
        btnEditFoto.setOnClickListener { verificarPermissao() }
        findViewById<Button>(R.id.btnUpdate).setOnClickListener { atualizarTransacao() }
    }

    /**
     * Mostra a miniatura da fatura e ajusta a visibilidade dos botões.
     */
    private fun exibirFoto(caminho: String?) {
        if (!caminho.isNullOrEmpty()) {
            val file = File(caminho)
            if (file.exists()) {
                ivEditPreview.visibility = View.VISIBLE
                btnRemoveFoto.visibility = View.VISIBLE
                btnEditFoto.visibility = View.GONE
                ivEditPreview.load(file) // Carrega a imagem usando a biblioteca Coil
                currentPhotoPath = caminho
            }
        }
    }

    /**
     * Remove a referência da foto e permite anexar uma nova.
     */
    private fun ocultarFoto() {
        ivEditPreview.visibility = View.GONE
        btnRemoveFoto.visibility = View.GONE
        btnEditFoto.visibility = View.VISIBLE
        currentPhotoPath = null
    }

    /**
     * Verifica se a app tem permissão para usar a câmara antes de a abrir.
     */
    private fun verificarPermissao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Configura o ficheiro temporário e lança a aplicação de câmara do sistema.
     */
    private fun iniciarCamara() {
        val photoFile = try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            // Cria um ficheiro .jpg único na pasta de imagens da app
            File.createTempFile("EDIT_FATURA_${timeStamp}_", ".jpg", storageDir).apply {
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) { null }

        if (photoFile != null) {
            val authority = "${packageName}.fileprovider"
            // Gera um URI seguro para a câmara poder escrever o ficheiro
            val uri = FileProvider.getUriForFile(this, authority, photoFile)
            photoUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    /**
     * Lógica principal de atualização: calcula o novo saldo, atualiza o servidor (API)
     * e a base de dados local (Room).
     */
    private fun atualizarTransacao() {
        val novoTitulo = etTitulo.text.toString()
        val novoValorStr = etValor.text.toString()
        val novaCategoria = etCategoria.text.toString()

        if (novoTitulo.isEmpty() || novoValorStr.isEmpty()) return

        val valorAbsoluto = novoValorStr.toDoubleOrNull() ?: 0.0
        // Mantém o sinal original (se era despesa continua negativa, se era receita continua positiva)
        val novoValorFinal = if (valorAntigo < 0) -abs(valorAbsoluto) else abs(valorAbsoluto)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                // FÓRMULA DE RECALCULO: Saldo = Saldo Atual - Valor Antigo + Novo Valor
                val saldoCorrigido = user.saldo - valorAntigo + novoValorFinal
                val despesaEditada = Despesa(despesaId, user.id, novoTitulo, novoValorFinal, novaCategoria, dataOriginal, currentPhotoPath)

                try {
                    // 1. Sincroniza o novo saldo com a API
                    RetrofitClient.instance.updateSaldo(user.id, SaldoRequest(saldoCorrigido)).execute()
                    // 2. Envia a transação editada para a API
                    val response = RetrofitClient.instance.updateDespesa(despesaId, despesaEditada).execute()

                    if (response.isSuccessful) {
                        // 3. Se o servidor aceitou, atualiza a Base de Dados local
                        db.utilizadorDao().updateSaldo(user.id, saldoCorrigido)
                        db.despesaDao().insert(despesaEditada) // O 'insert' com OnConflictStrategy.REPLACE faz o update

                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Alterações guardadas!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Erro na ligação ao servidor", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}