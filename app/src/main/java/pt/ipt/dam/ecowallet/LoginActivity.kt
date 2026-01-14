package pt.ipt.dam.ecowallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // --- CORREÇÃO DE MARGENS (Edge-to-Edge) ---
        val mainView = findViewById<android.view.View>(R.id.mainContainer)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val paddingBase = (24 * resources.displayMetrics.density).toInt()
                v.setPadding(paddingBase, systemBars.top + paddingBase, paddingBase, systemBars.bottom + paddingBase)
                insets
            }
        }

        database = AppDatabase.getDatabase(this)
        checkBiometricAvailability()

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnBiometric = findViewById<Button>(R.id.btnBiometric)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val etUser = findViewById<TextInputEditText>(R.id.etUsername)
        val etPass = findViewById<TextInputEditText>(R.id.etPassword)

        btnLogin.setOnClickListener {
            val user = etUser.text.toString()
            val pass = etPass.text.toString()
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                performLogin(user, pass)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        btnBiometric.setOnClickListener { showBiometricPrompt() }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun performLogin(user: String, pass: String) {
        val request = LoginRequest(user, pass)
        RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body()?.error == false) {
                    val loginResponse = response.body()!!
                    loginResponse.user?.let { u ->
                        // Corrigido: Usar Dispatchers.IO para operações de BD e try-catch para evitar crash
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                saveCredentialsEncrypted(user, pass)
                                database.utilizadorDao().insert(u)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "Login com sucesso!", Toast.LENGTH_SHORT).show()
                                    goToMain()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "Erro ao guardar dados: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } else {
                    val errorMsg = response.body()?.message ?: "Credenciais inválidas"
                    Toast.makeText(applicationContext, "Erro: $errorMsg", Toast.LENGTH_LONG).show()
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
        finish()
    }

    // --- BIOMETRIA ---
    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        this, "secure_prefs",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun saveCredentialsEncrypted(user: String, pass: String) {
        getEncryptedPrefs().edit().apply {
            putString("username", user)
            putString("password", pass)
            putBoolean("biometric_enabled", true)
            apply()
        }
    }

    private fun checkBiometricAvailability() {
        if (getEncryptedPrefs().getBoolean("biometric_enabled", false)) {
            findViewById<Button>(R.id.btnBiometric).visibility = Button.VISIBLE
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val prefs = getEncryptedPrefs()
                    val user = prefs.getString("username", "") ?: ""
                    val pass = prefs.getString("password", "") ?: ""
                    if (user.isNotEmpty() && pass.isNotEmpty()) performLogin(user, pass)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login Biométrico")
            .setSubtitle("Toque no sensor para entrar")
            .setNegativeButtonText("Cancelar")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}