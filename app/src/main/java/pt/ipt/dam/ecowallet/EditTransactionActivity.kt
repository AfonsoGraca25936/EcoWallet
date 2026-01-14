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
import kotlinx.coroutines.launch
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

        val mainContainer = findViewById<View>(R.id.mainContainer)
        if (mainContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val margem = (16 * resources.displayMetrics.density).toInt()
                v.setPadding(margem, systemBars.top + margem, margem, systemBars.bottom + margem)
                insets
            }
        }

        etTitulo = findViewById(R.id.etTitulo)
        etValor = findViewById(R.id.etValor)
        etCategoria = findViewById(R.id.etCategoria)

        // Recuperar Dados
        val intent = intent
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

        val valorAbsoluto = novoValorStr.toDouble()
        val novoValorFinal = if (valorAntigo < 0) -abs(valorAbsoluto) else abs(valorAbsoluto)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            // A. Corrigir Saldo
            val user = db.utilizadorDao().getUtilizador()
            if (user != null) {
                val saldoCorrigido = user.saldo - valorAntigo + novoValorFinal
                db.utilizadorDao().updateSaldo(user.id, saldoCorrigido)
                atualizarCloud(user.id, saldoCorrigido)
            }

            // B. Atualizar na BD
            val despesaAtualizada = Despesa(
                id = despesaId,
                titulo = novoTitulo,
                valor = novoValorFinal,
                categoria = novaCategoria,
                data = dataOriginal,
                fotoCaminho = fotoCaminhoOriginal, // <--- ADICIONADO AQUI
                userId = userId
            )
            db.despesaDao().update(despesaAtualizada)

            Toast.makeText(applicationContext, "Atualizado!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun atualizarCloud(userId: String, novoSaldo: Double) {
        val request = SaldoRequest(saldo = novoSaldo)
        RetrofitClient.instance.updateSaldo(userId, request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {}
            override fun onFailure(call: Call<Void>, t: Throwable) {}
        })
    }
}