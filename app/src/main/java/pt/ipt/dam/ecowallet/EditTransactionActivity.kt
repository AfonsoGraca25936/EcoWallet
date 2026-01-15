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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs

class EditTransactionActivity : AppCompatActivity() {

    private lateinit var etTitulo: TextInputEditText
    private lateinit var etValor: TextInputEditText
    private lateinit var etCategoria: TextInputEditText

    private var despesaId: String = ""
    private var userId: String = ""
    private var valorAntigo: Double = 0.0
    private var dataOriginal: String = ""
    private var fotoCaminhoOriginal: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_transaction)

        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)

        despesaId = intent.getStringExtra("ID") ?: ""
        userId = intent.getStringExtra("USER_ID") ?: ""
        val titulo = intent.getStringExtra("TITULO") ?: ""
        valorAntigo = intent.getDoubleExtra("VALOR", 0.0)
        val categoria = intent.getStringExtra("CATEGORIA") ?: ""
        dataOriginal = intent.getStringExtra("DATA") ?: ""
        fotoCaminhoOriginal = intent.getStringExtra("FOTO")

        etTitulo.setText(titulo)
        etValor.setText(abs(valorAntigo).toString())
        etCategoria.setText(categoria)

        findViewById<Button>(R.id.btnUpdate).setOnClickListener { atualizarTransacao() }
    }

    private fun atualizarTransacao() {
        val novoTitulo = etTitulo.text.toString()
        val novoValorStr = etValor.text.toString()
        val novaCategoria = etCategoria.text.toString()

        if (novoTitulo.isEmpty() || novoValorStr.isEmpty()) return

        val valorAbsoluto = novoValorStr.toDoubleOrNull() ?: 0.0
        val novoValorFinal = if (valorAntigo < 0) -abs(valorAbsoluto) else abs(valorAbsoluto)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                val saldoCorrigido = user.saldo - valorAntigo + novoValorFinal
                val despesaEditada = Despesa(despesaId, user.id, novoTitulo, novoValorFinal, novaCategoria, dataOriginal, fotoCaminhoOriginal)

                try {
                    RetrofitClient.instance.updateSaldo(user.id, SaldoRequest(saldoCorrigido)).execute()
                    val response = RetrofitClient.instance.updateDespesa(despesaId, despesaEditada).execute()

                    if (response.isSuccessful) {
                        db.utilizadorDao().updateSaldo(user.id, saldoCorrigido)
                        db.despesaDao().insert(despesaEditada)
                        withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Atualizado!", Toast.LENGTH_SHORT).show(); finish() }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Erro rede", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}