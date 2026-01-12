package pt.ipt.dam.ecowallet

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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

        // Configuração Edge-to-Edge
        val mainView = findViewById<View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        // 1. Inicializar Base de Dados e Views
        database = AppDatabase.getDatabase(this)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        tvSaldo = findViewById(R.id.tvSaldo)

        // Botões Flutuantes (O Vermelho e o Verde)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabReceita = findViewById<FloatingActionButton>(R.id.fabReceita)

        // 2. Configurar a Lista (RecyclerView)
        adapter = DespesaAdapter(
            lista = emptyList(),
            onDespesaClick = { despesa ->
                // Aqui poderias abrir a edição, por agora mostra só o nome
                Toast.makeText(this, despesa.titulo, Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { despesa ->
                mostrarDialogoApagar(despesa)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 3. Ações dos Botões

        // Botão Vermelho -> Adicionar Despesa (Gastar)
        fabAdd.setOnClickListener {
            startActivity(Intent(this, AddDespesaActivity::class.java))
        }

        // Botão Verde -> Adicionar Receita (Carregar Saldo)
        fabReceita.setOnClickListener {
            startActivity(Intent(this, AddReceitaActivity::class.java))
        }

        // 4. Verificar Sessão
        checkSessionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // Sempre que voltamos a este ecrã, atualizamos tudo
        if (currentUser != null) {
            loadDespesasLocais()
            syncDespesasAPI()

            // Atualizar Saldo (Caso tenhamos vindo do ecrã de Receita ou Despesa)
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
                // Se não há user, vai para o Login
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
                // Se há user, carrega os dados
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

        // Pede ao servidor apenas as despesas deste utilizador
        RetrofitClient.instance.getDespesas(userId).enqueue(object : Callback<List<Despesa>> {
            override fun onResponse(call: Call<List<Despesa>>, response: Response<List<Despesa>>) {
                if (response.isSuccessful) {
                    response.body()?.let { despesasRemotas ->
                        lifecycleScope.launch {
                            // Atualiza a BD local com o que veio da nuvem
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
            .setMessage("Tem a certeza que deseja apagar '${despesa.titulo}'? O valor será devolvido ao saldo.")
            .setPositiveButton("Sim") { _, _ -> apagarDespesa(despesa) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun apagarDespesa(despesa: Despesa) {
        lifecycleScope.launch {
            // 1. ATUALIZAR SALDO (Devolver o dinheiro à carteira)
            currentUser?.let { user ->
                val novoSaldo = user.saldo + despesa.valor

                // A. Guardar no Telemóvel
                database.utilizadorDao().updateSaldo(user.id, novoSaldo)
                currentUser = user.copy(saldo = novoSaldo)
                updateSaldoUI(novoSaldo)

                // B. Guardar na Nuvem (USANDO O FORMATO CORRETO)
                val request = pt.ipt.dam.ecowallet.model.SaldoRequest(saldo = novoSaldo)

                RetrofitClient.instance.updateSaldo(user.id, request).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        // Sucesso silencioso
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        // Falha silenciosa
                    }
                })
            }

            // 2. Apagar a Despesa Localmente
            database.despesaDao().delete(despesa)
            loadDespesasLocais()

            // 3. Mandar Apagar a Despesa no Servidor
            if (despesa.id.isNotEmpty()) {
                RetrofitClient.instance.deleteDespesa(despesa.id).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) {
                            Toast.makeText(applicationContext, "Apagado e reembolsado!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Toast.makeText(applicationContext, "Sem net: Apagado só no telemóvel", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }

    private fun updateSaldoUI(valor: Double) {
        tvSaldo.text = String.format("%.2f€", valor)
    }

    // Menus (Logout, Refresh, About)
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
                Toast.makeText(this, "A atualizar...", Toast.LENGTH_SHORT).show()
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