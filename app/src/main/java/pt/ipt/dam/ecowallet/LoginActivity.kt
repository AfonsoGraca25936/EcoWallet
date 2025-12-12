package pt.ipt.dam.ecowallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.LoginRequest
import pt.ipt.dam.ecowallet.model.LoginResponse
import pt.ipt.dam.ecowallet.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        database = AppDatabase.getDatabase(this)

        // Verificar se já existe login automático (Biometria configurada)
        checkBiometricAvailability()

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnBiometric = findViewById<Button>(R.id.btnBiometric)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val etUser = findViewById<TextInputEditText>(R.id.etUsername)
        val etPass = findViewById<TextInputEditText>(R.id.etPassword)

        // Botão Entrar (Login Normal)
        btnLogin.setOnClickListener {
            val user = etUser.text.toString()
            val pass = etPass.text.toString()

            if (user.isNotEmpty() && pass.isNotEmpty()) {
                performLogin(user, pass)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Botão Biometria
        btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }

        // Link para Registar
        tvRegister.setOnClickListener {
            // Ir para o ecrã de registo (vamos criar a seguir)
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin(user: String, pass: String) {
        val request = LoginRequest(user, pass)

        RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body()?.error == false) {
                    val loginResponse = response.body()!!

                    // Guardar na BD Local (Room)
                    lifecycleScope.launch {
                        loginResponse.user?.let { u ->
                            // IMPORTANTE: O user vem sem password da API,
                            // mas para a biometria funcionar precisamos de guardar as credenciais algures seguro
                            saveCredentialsEncrypted(user, pass)

                            // Guardar na BD
                            database.utilizadorDao().insert(u)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Login com sucesso!", Toast.LENGTH_SHORT).show()
                                goToMain()
                            }
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "Erro: ${response.body()?.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Falha na rede: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Fecha o login para não voltar atrás
    }

    // --- BIOMETRIA E SEGURANÇA ---

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        this,
        "secure_prefs",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun saveCredentialsEncrypted(user: String, pass: String) {
        val sharedPreferences = getEncryptedPrefs()
        sharedPreferences.edit().apply {
            putString("username", user)
            putString("password", pass)
            putBoolean("biometric_enabled", true)
            apply()
        }
    }

    private fun checkBiometricAvailability() {
        val sharedPreferences = getEncryptedPrefs()
        val isEnabled = sharedPreferences.getBoolean("biometric_enabled", false)

        if (isEnabled) {
            findViewById<Button>(R.id.btnBiometric).visibility = Button.VISIBLE
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    // Recuperar credenciais e fazer login automático
                    val prefs = getEncryptedPrefs()
                    val user = prefs.getString("username", "") ?: ""
                    val pass = prefs.getString("password", "") ?: ""

                    if (user.isNotEmpty() && pass.isNotEmpty()) {
                        performLogin(user, pass)
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login Biométrico")
            .setSubtitle("Use a sua impressão digital para entrar")
            .setNegativeButtonText("Cancelar")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}