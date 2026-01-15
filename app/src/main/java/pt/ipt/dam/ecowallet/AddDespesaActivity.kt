package pt.ipt.dam.ecowallet

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class AddDespesaActivity : AppCompatActivity() {

    private lateinit var etTitulo: TextInputEditText
    private lateinit var etValor: TextInputEditText
    private lateinit var etCategoria: TextInputEditText
    private lateinit var ivPreview: ImageView
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Garante o topo verde e ícones brancos
        window.statusBarColor = Color.parseColor("#2E7D32")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_add_despesa)

        val mainContainer = findViewById<View>(R.id.mainContainer)
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val margem = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(margem, 0, margem, systemBars.bottom + margem) // Topo a 0
            insets
        }

        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)
        ivPreview = findViewById(R.id.ivPreview)

        findViewById<Button>(R.id.btnGuardar).setOnClickListener { guardarDespesa() }
    }

    private fun guardarDespesa() {
        val titulo = etTitulo.text.toString()
        val valorStr = etValor.text.toString()
        val categoria = etCategoria.text.toString().ifEmpty { "Geral" }

        if (titulo.isEmpty() || valorStr.isEmpty()) {
            Toast.makeText(this, "Preencha título e valor", Toast.LENGTH_SHORT).show()
            return
        }

        // Despesa deve ser negativa
        val valorDespesa = -abs(valorStr.toDoubleOrNull() ?: 0.0)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                val novoSaldo = user.saldo + valorDespesa
                val dataHoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                // Geramos um ID ÚNICO para o MongoDB
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
                    // 1. Atualizar Saldo na API
                    val saldoReq = SaldoRequest(saldo = novoSaldo)
                    RetrofitClient.instance.updateSaldo(user.id, saldoReq).execute()

                    // 2. Enviar Transação (Despesa) para a API
                    val response = RetrofitClient.instance.addDespesa(novaDespesa).execute()

                    if (response.isSuccessful) {
                        // 3. Atualizar localmente apenas se a API aceitou
                        db.utilizadorDao().updateSaldo(user.id, novoSaldo)
                        db.despesaDao().insert(novaDespesa.apply { isSynced = true })

                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Despesa registada!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Erro no servidor ao guardar despesa", Toast.LENGTH_SHORT).show()
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