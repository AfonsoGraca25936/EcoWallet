package pt.ipt.dam.ecowallet

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.SaldoRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AddReceitaActivity : AppCompatActivity() {

    private lateinit var etValor: TextInputEditText
    private lateinit var etTitulo: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MUDANÇA 1: Usar o novo layout
        setContentView(R.layout.activity_add_receita)

        // Configuração das margens (Edge-to-edge)
        val mainContainer = findViewById<View>(R.id.mainContainer)
        if (mainContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // MUDANÇA 2: Ligar os IDs novos
        etValor = findViewById(R.id.etValor)
        etTitulo = findViewById(R.id.etTitulo)

        // Botão Guardar
        val btnGuardar = findViewById<Button>(R.id.btnGuardar)
        btnGuardar.setOnClickListener { adicionarDinheiro() }
    }

    private fun adicionarDinheiro() {
        val valorStr = etValor.text.toString()
        if (valorStr.isEmpty()) {
            Toast.makeText(this, "Insira um valor", Toast.LENGTH_SHORT).show()
            return
        }
        val valor = valorStr.toDoubleOrNull() ?: 0.0

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.utilizadorDao().getUtilizador()

            if (user != null) {
                // SOMA ao saldo
                val novoSaldo = user.saldo + valor

                // 1. Atualizar Local
                db.utilizadorDao().updateSaldo(user.id, novoSaldo)

                // 2. Atualizar na Cloud
                val request = SaldoRequest(saldo = novoSaldo)
                RetrofitClient.instance.updateSaldo(user.id, request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {}
                    override fun onFailure(call: Call<Void>, t: Throwable) {}
                })

                Toast.makeText(applicationContext, "Saldo adicionado: +$valor€", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}