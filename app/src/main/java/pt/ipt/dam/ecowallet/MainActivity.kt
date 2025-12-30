package pt.ipt.dam.ecowallet

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: DespesaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSaldo: TextView
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mainView = findViewById<View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        database = AppDatabase.getDatabase(this)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        tvSaldo = findViewById(R.id.tvSaldo)
        val btnEditSaldo = findViewById<View>(R.id.btnEditSaldo)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)

        btnEditSaldo.setOnClickListener { mostrarDialogoEditarSaldo() }

        adapter = DespesaAdapter(
            lista = emptyList(),
            onDespesaClick = { despesa ->
                Toast.makeText(this, despesa.titulo, Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { despesa ->
                mostrarDialogoApagar(despesa)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, AddDespesaActivity::class.java))
        }

        checkSessionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (currentUser != null) {
            loadDespesasLocais()
            syncDespesasAPI()
            // Atualizar o objeto currentUser da BD para ter o saldo mais recente
            lifecycleScope.launch {
                val user = database.utilizadorDao().getUtilizador()
                if (user != null) {
                    currentUser = user
                    updateSaldoUI(user.saldo)
                }
            }
        }
    }

    private fun checkSessionAndLoad() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            if (user == null) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
                currentUser = user
                updateSaldoUI(user.saldo)
                loadDespesasLocais()
                syncDespesasAPI()
            }
        }
    }

    private fun loadDespesasLocais() {
        lifecycleScope.launch {
            val lista = database.despesaDao().getAll()
            if (lista.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.updateList(lista)
            }
        }
    }

    private fun syncDespesasAPI() {
        val userId = currentUser?.id ?: return
        RetrofitClient.instance.getDespesas(userId).enqueue(object : Callback<List<Despesa>> {
            override fun onResponse(call: Call<List<Despesa>>, response: Response<List<Despesa>>) {
                if (response.isSuccessful) {
                    response.body()?.let { despesasRemotas ->
                        lifecycleScope.launch {
                            database.despesaDao().deleteAll()
                            despesasRemotas.forEach {
                                it.isSynced = true
                                database.despesaDao().insert(it)
                            }
                            loadDespesasLocais()
                        }
                    }
                }
            }
            override fun onFailure(call: Call<List<Despesa>>, t: Throwable) {}
        })
    }

    private fun mostrarDialogoApagar(despesa: Despesa) {
        AlertDialog.Builder(this)
            .setTitle("Apagar Despesa")
            .setMessage("Tem a certeza que deseja apagar '${despesa.titulo}'?")
            .setPositiveButton("Sim") { _, _ -> apagarDespesa(despesa) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun apagarDespesa(despesa: Despesa) {
        lifecycleScope.launch {
            // 1. Atualizar Saldo (Devolver valor)
            currentUser?.let { user ->
                val novoSaldo = user.saldo + despesa.valor
                database.utilizadorDao().updateSaldo(user.id, novoSaldo)
                currentUser = user.copy(saldo = novoSaldo)
                updateSaldoUI(novoSaldo)
                
                // Sincronizar saldo com API
                RetrofitClient.instance.updateSaldo(user.id, mapOf("saldo" to novoSaldo)).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {}
                    override fun onFailure(call: Call<Void>, t: Throwable) {}
                })
            }

            // 2. Apagar Localmente
            database.despesaDao().delete(despesa)
            loadDespesasLocais()

            // 3. Apagar no Servidor
            if (despesa.id.isNotEmpty()) {
                RetrofitClient.instance.deleteDespesa(despesa.id).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) {
                            Toast.makeText(applicationContext, "Apagado!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {}
                })
            }
        }
    }

    private fun updateSaldoUI(valor: Double) {
        tvSaldo.text = String.format("%.2f€", valor)
    }

    private fun mostrarDialogoEditarSaldo() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.leftMargin = 50; params.rightMargin = 50
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Definir Saldo")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val novoSaldo = input.text.toString().toDoubleOrNull()
                if (novoSaldo != null && currentUser != null) guardarNovoSaldo(novoSaldo)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarNovoSaldo(novoSaldo: Double) {
        lifecycleScope.launch {
            currentUser?.let { user ->
                // 1. Atualizar Localmente (para ser rápido)
                database.utilizadorDao().updateSaldo(user.id, novoSaldo)
                currentUser = user.copy(saldo = novoSaldo)
                updateSaldoUI(novoSaldo)

                // 2. Avisar o Servidor (O SEGREDO ESTÁ AQUI)
                val request = pt.ipt.dam.ecowallet.model.SaldoRequest(saldo = novoSaldo)

                RetrofitClient.instance.updateSaldo(user.id, request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) {
                            Toast.makeText(applicationContext, "Saldo guardado na nuvem!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(applicationContext, "Erro a guardar online", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Toast.makeText(applicationContext, "Sem net: Saldo guardado só no telemóvel", Toast.LENGTH_LONG).show()
                    }
                })
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                lifecycleScope.launch {
                    database.utilizadorDao().logout()
                    database.despesaDao().deleteAll()
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
                true
            }
            R.id.action_refresh -> {
                syncDespesasAPI()
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}