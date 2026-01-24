package pt.ipt.dam.ecowallet

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.model.LoginResponse
import pt.ipt.dam.ecowallet.model.RegisterRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Atividade responsável pelo registo de novos utilizadores na aplicação.
 */
class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // --- AJUSTE DINÂMICO DE MARGENS (Edge-to-Edge) ---
        // Garante que o conteúdo respeita as barras de sistema (estado e navegação)
        val mainView = findViewById<android.view.View>(R.id.mainContainer)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val paddingBase = (24 * resources.displayMetrics.density).toInt()
                // Aplica o preenchimento necessário para não sobrepor a barra de estado
                v.setPadding(paddingBase, systemBars.top + paddingBase, paddingBase, systemBars.bottom + paddingBase)
                insets
            }
        }

        // Inicialização dos componentes do layout
        val etUser = findViewById<TextInputEditText>(R.id.etRegUser)
        val etEmail = findViewById<TextInputEditText>(R.id.etRegEmail)
        val etPass = findViewById<TextInputEditText>(R.id.etRegPass)
        val btnRegister = findViewById<Button>(R.id.btnRegisterAction)
        val btnBack = findViewById<Button>(R.id.btnBackToLogin)

        // Configuração do clique no botão de registo
        btnRegister.setOnClickListener {
            val user = etUser.text.toString()
            val email = etEmail.text.toString()
            val pass = etPass.text.toString()

            // Validação simples: verifica se todos os campos foram preenchidos
            if (user.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                performRegister(user, email, pass)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Botão para voltar ao ecrã de Login
        btnBack.setOnClickListener { finish() }
    }

    /**
     * Faz a chamada à API para criar uma nova conta de utilizador.
     */
    private fun performRegister(user: String, email: String, pass: String) {
        // Cria o objeto de pedido com os dados do formulário
        val request = RegisterRequest(user, email, pass)

        // Envia o pedido de registo através do Retrofit
        RetrofitClient.instance.register(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                // Verifica se a resposta foi positiva e se não houve erro no servidor
                if (response.isSuccessful && response.body()?.error == false) {
                    Toast.makeText(applicationContext, "Registo efetuado! Faça login.", Toast.LENGTH_LONG).show()
                    // Fecha o ecrã de registo após o sucesso
                    finish()
                } else {
                    // Mostra a mensagem de erro vinda da API (ex: "utilizador já existe")
                    val errorMsg = response.body()?.message ?: "Erro desconhecido"
                    Toast.makeText(applicationContext, "Erro: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // Caso ocorra uma falha de rede ou o servidor esteja desligado
                Toast.makeText(applicationContext, "Erro de rede: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}