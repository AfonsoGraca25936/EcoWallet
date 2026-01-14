package pt.ipt.dam.ecowallet

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.SaldoRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddReceitaActivity : AppCompatActivity() {

    private lateinit var etValor: TextInputEditText
    private lateinit var etTitulo: TextInputEditText
    private lateinit var etCategoria: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_receita)

        etValor = findViewById(R.id.etValor)
        etTitulo = findViewById(R.id.etTitulo)
        etCategoria = findViewById(R.id.etCategoria)

        findViewById<Button>(R.id.btnGuardar).setOnClickListener { adicionarDinheiro() }
    }

    private fun adicionarDinheiro() {
        val valorStr = etValor.text.toString()
        if (valorStr.isEmpty()) return
        val valor = valorStr.toDoubleOrNull() ?: 0.0

        val titulo = etTitulo.text.toString().ifEmpty { "Depósito" }
        val categoria = etCategoria.text.toString().ifEmpty { "Receita" }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                val novoSaldo = user.saldo + valor
                val dataHoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                val novaReceita = Despesa(
                    id = UUID.randomUUID().toString(), // ID Único
                    titulo = titulo,
                    valor = valor,
                    categoria = categoria,
                    data = dataHoje,
                    fotoCaminho = null,
                    userId = user.id
                )

                try {
                    // Atualizar na API
                    RetrofitClient.instance.updateSaldo(user.id, SaldoRequest(novoSaldo)).execute()
                    val res = RetrofitClient.instance.addDespesa(novaReceita).execute()

                    if (res.isSuccessful) {
                        db.utilizadorDao().updateSaldo(user.id, novoSaldo)
                        db.despesaDao().insert(novaReceita.apply { isSynced = true })
                        withContext(Dispatchers.Main) { finish() }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}