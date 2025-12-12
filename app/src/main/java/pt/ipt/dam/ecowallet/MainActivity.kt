package pt.ipt.dam.ecowallet

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: DespesaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurar Toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        database = AppDatabase.getDatabase(this)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)

        // Configurar Lista
        adapter = DespesaAdapter(emptyList()) { despesa ->
            // Ação ao clicar numa despesa (ex: abrir detalhes/editar)
            Toast.makeText(this, "Clicou em: ${despesa.titulo}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Botão Adicionar (Vai abrir a Câmara no próximo passo)
        fab.setOnClickListener {
            // AINDA VAMOS CRIAR ESTA ACTIVITY NO PRÓXIMO PASSO
            val intent = Intent(this, AddDespesaActivity::class.java)
            startActivity(intent)
        }

        // Verificar sessão e carregar dados
        checkSessionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        loadDespesasLocais() // Recarrega sempre que volta a esta janela
    }

    private fun checkSessionAndLoad() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            if (user == null) {
                // Se não houver user, vai para o Login
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
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
        // Vai à API buscar dados frescos
        RetrofitClient.instance.getDespesas().enqueue(object : Callback<List<Despesa>> {
            override fun onResponse(call: Call<List<Despesa>>, response: Response<List<Despesa>>) {
                if (response.isSuccessful) {
                    response.body()?.let { despesasRemotas ->
                        lifecycleScope.launch {
                            // Limpa o local e insere o que veio da API (Estratégia simples)
                            // Numa app real farias uma fusão mais inteligente
                            database.despesaDao().deleteAll()
                            despesasRemotas.forEach { database.despesaDao().insert(it) }
                            loadDespesasLocais() // Atualiza o ecrã
                        }
                    }
                }
            }
            override fun onFailure(call: Call<List<Despesa>>, t: Throwable) {
                // Se falhar a net, não faz mal, já mostrámos os dados locais
                Toast.makeText(applicationContext, "Modo Offline: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- MENU (Logout, Sobre) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                lifecycleScope.launch {
                    database.utilizadorDao().logout() // Apaga sessão
                    database.despesaDao().deleteAll() // Apaga dados privados
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
                // Toast.makeText(this, "EcoWallet v1.0\nAluno: Afonso Graça", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, AboutActivity::class.java)) // Cria isto depois
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}