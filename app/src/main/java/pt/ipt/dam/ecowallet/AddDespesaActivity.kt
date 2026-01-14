package pt.ipt.dam.ecowallet

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
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
        setContentView(R.layout.activity_add_despesa)

        val mainContainer = findViewById<View>(R.id.mainContainer)
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val margem = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(margem, systemBars.top + margem, margem, systemBars.bottom + margem)
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

        val valor = -abs(valorStr.toDoubleOrNull() ?: 0.0)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                // 1. Atualizar Saldo Local e Remoto
                val novoSaldo = user.saldo + valor
                db.utilizadorDao().updateSaldo(user.id, novoSaldo)

                val request = SaldoRequest(saldo = novoSaldo)
                RetrofitClient.instance.updateSaldo(user.id, request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {}
                    override fun onFailure(call: Call<Void>, t: Throwable) {}
                })

                // 2. Criar Despesa
                val dataHoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                // Geramos um ID temporário (String) para a despesa local
                val tempId = UUID.randomUUID().toString()

                val novaDespesa = Despesa(
                    id = tempId,
                    titulo = titulo,
                    valor = valor,
                    categoria = categoria,
                    data = dataHoje,
                    fotoCaminho = currentPhotoPath,
                    userId = user.id // Isto é String agora, e o Modelo aceita String
                )

                db.despesaDao().insert(novaDespesa)
                Toast.makeText(applicationContext, "Despesa registada!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(applicationContext, "Erro: Utilizador não encontrado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}