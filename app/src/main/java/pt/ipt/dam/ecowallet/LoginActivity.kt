package pt.ipt.dam.ecowallet

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import java.io.File

class LoginActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        // FORÇAR MODO CLARO
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        super.onCreate(savedInstanceState)

        // Garante o topo verde também no Login
        window.statusBarColor = Color.parseColor("#2E7D32")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_login)

        val mainView = findViewById<android.view.View>(R.id.mainContainer)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
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
            if (user.isNotEmpty() && pass.isNotEmpty()) performLogin(user, pass)
            else Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
        }

        btnBiometric.setOnClickListener { showBiometricPrompt() }
        tvRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
    }

    private fun performLogin(user: String, pass: String) {
        val request = LoginRequest(user, pass)
        RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body()?.error == false) {
                    val loginResponse = response.body()!!
                    loginResponse.user?.let { u ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                // LIMPA DADOS DE CONTAS ANTERIORES
                                database.utilizadorDao().deleteAll()
                                database.despesaDao().deleteAll()

                                saveCredentialsEncrypted(user, pass)
                                database.utilizadorDao().insert(u)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "Sucesso!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "Erro BD: ${e.toString()}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "Login falhou", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Erro rede", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getEncryptedPrefs(): SharedPreferences {
        val name = "secure_prefs"
        return try {
            val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(this, name, key, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (e: Exception) {
            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
            val file = File(filesDir.parent, "shared_prefs/$name.xml")
            if (file.exists()) file.delete()
            val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(this, name, key, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        }
    }

    private fun saveCredentialsEncrypted(user: String, pass: String) {
        getEncryptedPrefs().edit().putString("username", user).putString("password", pass).putBoolean("biometric_enabled", true).apply()
    }

    private fun checkBiometricAvailability() {
        try {
            if (getEncryptedPrefs().getBoolean("biometric_enabled", false)) {
                findViewById<Button>(R.id.btnBiometric).visibility = android.view.View.VISIBLE
            }
        } catch (e: Exception) {}
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val prefs = getEncryptedPrefs()
                performLogin(prefs.getString("username", "") ?: "", prefs.getString("password", "") ?: "")
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Login").setSubtitle("Use a digital").setNegativeButtonText("Cancelar").build()
        biometricPrompt.authenticate(promptInfo)
    }
}
