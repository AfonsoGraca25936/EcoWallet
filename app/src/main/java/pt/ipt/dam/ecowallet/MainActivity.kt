package pt.ipt.dam.ecowallet

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
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

        // --- CORREÇÃO DE MARGENS ---
        val mainView = findViewById<android.view.View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Na main, aplicamos padding só nas barras, não nas laterais, para a lista ficar bonita
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        database = AppDatabase.getDatabase(this)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)

        adapter = DespesaAdapter(emptyList()) { despesa ->
            Toast.makeText(this, "Despesa: ${despesa.titulo}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, AddDespesaActivity::class.java))
        }

        checkSessionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        loadDespesasLocais()
    }

    private fun checkSessionAndLoad() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            if (user == null) {
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
        RetrofitClient.instance.getDespesas().enqueue(object : Callback<List<Despesa>> {
            override fun onResponse(call: Call<List<Despesa>>, response: Response<List<Despesa>>) {
                if (response.isSuccessful) {
                    response.body()?.let { despesasRemotas ->
                        lifecycleScope.launch {
                            database.despesaDao().deleteAll()
                            despesasRemotas.forEach { database.despesaDao().insert(it) }
                            loadDespesasLocais()
                        }
                    }
                }
            }
            override fun onFailure(call: Call<List<Despesa>>, t: Throwable) {
                // Modo Offline silencioso
            }
        })
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