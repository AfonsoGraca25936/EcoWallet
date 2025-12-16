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

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // --- CORREÇÃO DE MARGENS ---
        // Nota: Adicione android:id="@+id/mainContainer" no XML do registo se ainda não tiver
        val mainView = findViewById<android.view.View>(R.id.mainContainer)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val paddingBase = (24 * resources.displayMetrics.density).toInt()
                v.setPadding(paddingBase, systemBars.top + paddingBase, paddingBase, systemBars.bottom + paddingBase)
                insets
            }
        }

        val etUser = findViewById<TextInputEditText>(R.id.etRegUser)
        val etEmail = findViewById<TextInputEditText>(R.id.etRegEmail)
        val etPass = findViewById<TextInputEditText>(R.id.etRegPass)
        val btnRegister = findViewById<Button>(R.id.btnRegisterAction)
        val btnBack = findViewById<Button>(R.id.btnBackToLogin)

        btnRegister.setOnClickListener {
            val user = etUser.text.toString()
            val email = etEmail.text.toString()
            val pass = etPass.text.toString()

            if (user.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                performRegister(user, email, pass)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun performRegister(user: String, email: String, pass: String) {
        val request = RegisterRequest(user, email, pass)
        RetrofitClient.instance.register(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body()?.error == false) {
                    Toast.makeText(applicationContext, "Registo efetuado! Faça login.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(applicationContext, "Erro: ${response.body()?.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Erro de rede: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}