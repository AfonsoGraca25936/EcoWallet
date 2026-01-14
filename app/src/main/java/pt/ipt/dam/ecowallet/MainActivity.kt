package pt.ipt.dam.ecowallet

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import pt.ipt.dam.ecowallet.api.RetrofitClient
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.SaldoRequest
import pt.ipt.dam.ecowallet.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: DespesaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSaldo: TextView
    private lateinit var pieChart: PieChart
    private var currentUser: User? = null
    private var listaCompleta: List<Despesa> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        tvSaldo = findViewById(R.id.tvSaldo)
        pieChart = findViewById(R.id.pieChart)

        // Configuração inicial do Gráfico para Percentagens
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setEntryLabelColor(Color.BLACK)

        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val btnAbout = findViewById<ImageView>(R.id.btnAbout)
        val btnShowDespesa = findViewById<Button>(R.id.btnShowDespesa)
        val btnShowReceita = findViewById<Button>(R.id.btnShowReceita)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabReceita = findViewById<FloatingActionButton>(R.id.fabReceita)

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        adapter = DespesaAdapter(
            lista = emptyList(),
            onDespesaClick = { despesa ->
                val intent = Intent(this, EditTransactionActivity::class.java).apply {
                    putExtra("ID", despesa.id)
                    putExtra("USER_ID", despesa.userId)
                    putExtra("TITULO", despesa.titulo)
                    putExtra("VALOR", despesa.valor)
                    putExtra("CATEGORIA", despesa.categoria)
                    putExtra("DATA", despesa.data)
                    putExtra("FOTO", despesa.fotoCaminho)
                }
                startActivity(intent)
            },
            onDeleteClick = { despesa -> mostrarDialogoApagar(despesa) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener { startActivity(Intent(this, AddDespesaActivity::class.java)) }
        fabReceita.setOnClickListener { startActivity(Intent(this, AddReceitaActivity::class.java)) }
        btnLogout.setOnClickListener { realizarLogout() }
        btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        btnShowDespesa.setOnClickListener {
            atualizarGrafico(listaCompleta.filter { it.valor < 0 }, "Gastos (%)")
        }
        btnShowReceita.setOnClickListener {
            atualizarGrafico(listaCompleta.filter { it.valor > 0 }, "Entradas (%)")
        }

        checkSessionAndLoad()
    }

    private fun atualizarGrafico(lista: List<Despesa>, titulo: String) {// Calcular o total da lista filtrada
        val totalSoma = lista.sumOf { abs(it.valor) }

        val categoriasMap = lista.groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { abs(it.valor) } }

        if (categoriasMap.isEmpty()) {
            pieChart.clear()
            pieChart.centerText = "Sem dados"
            return
        }

        val entries = categoriasMap.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.BLACK

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        pieChart.data = data

        // Configurar o texto central com o Título + Valor Total
        pieChart.centerText = "$titulo\n${String.format("%.2f€", totalSoma)}"
        pieChart.setCenterTextSize(16f) // Podes ajustar o tamanho se quiseres

        pieChart.animateY(800)
        pieChart.invalidate()
    }

    private fun loadDespesasLocais() {
        lifecycleScope.launch {
            listaCompleta = database.despesaDao().getAll()
            if (listaCompleta.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                pieChart.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                pieChart.visibility = View.VISIBLE
                adapter.updateList(listaCompleta)
                atualizarGrafico(listaCompleta.filter { it.valor < 0 }, "Gastos (%)")
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

    private fun realizarLogout() {
        lifecycleScope.launch {
            database.utilizadorDao().logout()
            database.despesaDao().deleteAll()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish()
        }
    }

    private fun mostrarDialogoApagar(despesa: Despesa) {
        AlertDialog.Builder(this)
            .setTitle("Apagar")
            .setMessage("Deseja apagar '${despesa.titulo}'?")
            .setPositiveButton("Sim") { _, _ -> apagarDespesa(despesa) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun apagarDespesa(despesa: Despesa) {
        lifecycleScope.launch {
            currentUser?.let { user ->
                val novoSaldo = user.saldo - despesa.valor
                database.utilizadorDao().updateSaldo(user.id, novoSaldo)
                currentUser = user.copy(saldo = novoSaldo)
                updateSaldoUI(novoSaldo)
                RetrofitClient.instance.updateSaldo(user.id, SaldoRequest(novoSaldo)).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {}
                    override fun onFailure(call: Call<Void>, t: Throwable) {}
                })
            }
            database.despesaDao().delete(despesa)
            loadDespesasLocais()
            if (despesa.id.isNotEmpty()) {
                RetrofitClient.instance.deleteDespesa(despesa.id).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {}
                    override fun onFailure(call: Call<Void>, t: Throwable) {}
                })
            }
        }
    }

    private fun updateSaldoUI(valor: Double) {
        tvSaldo.text = String.format("%.2f€", valor)
    }

    override fun onResume() {
        super.onResume()
        if (currentUser != null) {
            loadDespesasLocais()
            syncDespesasAPI()
            atualizarSaldoUser()
        }
    }

    private fun atualizarSaldoUser() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            user?.let {
                currentUser = it
                updateSaldoUI(it.saldo)
            }
        }
    }
}