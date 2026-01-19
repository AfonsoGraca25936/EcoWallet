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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#2E7D32")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        tvSaldo = findViewById(R.id.tvSaldo)
        pieChart = findViewById(R.id.pieChart)

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
        val btnShowResumo = findViewById<Button>(R.id.btnShowResumo)
        btnShowResumo.setOnClickListener { mostrarGraficoResumo() }

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

        btnShowDespesa.setOnClickListener { atualizarGrafico(listaCompleta.filter { it.valor < 0 }, "Gastos ", true) }
        btnShowReceita.setOnClickListener { atualizarGrafico(listaCompleta.filter { it.valor > 0 }, "Entradas ", false) }

        checkSessionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        recuperarDadosUtilizador()
    }

    private fun recuperarDadosUtilizador() {
        lifecycleScope.launch {
            val user = database.utilizadorDao().getUtilizador()
            user?.let {
                currentUser = it
                updateSaldoUI(it.saldo)
            }
            loadDespesasLocais()
            syncDespesasAPI()
        }
    }

    private fun atualizarGrafico(lista: List<Despesa>, titulo: String, isDespesa: Boolean) {
        val totalSoma = lista.sumOf { abs(it.valor) }
        val categoriasMap = lista.groupBy { it.categoria }.mapValues { it.value.sumOf { abs(it.valor) } }

        if (categoriasMap.isEmpty()) {
            pieChart.clear(); pieChart.centerText = "Sem dados"; return
        }

        val entries = categoriasMap.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "").apply {
            if (isDespesa) {
                colors = listOf(Color.parseColor("#FF5252"), Color.parseColor("#D32F2F"), Color.parseColor("#B71C1C"), Color.parseColor("#8B0000"))
            } else {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
            }
            valueTextSize = 12f; valueTextColor = Color.BLACK
        }

        pieChart.data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(pieChart)) }
        pieChart.centerText = "$titulo\n${String.format("%.2f€", totalSoma)}"
        pieChart.animateY(800); pieChart.invalidate()
    }

    private fun mostrarGraficoResumo() {
        val totalReceitas = listaCompleta.filter { it.valor > 0 }.sumOf { it.valor }
        val totalDespesas = listaCompleta.filter { it.valor < 0 }.sumOf { abs(it.valor) }

        if (totalReceitas == 0.0 && totalDespesas == 0.0) {
            pieChart.clear(); pieChart.centerText = "Sem dados"; return
        }

        val entries = mutableListOf<PieEntry>()
        if (totalReceitas > 0) entries.add(PieEntry(totalReceitas.toFloat(), "Receitas"))
        if (totalDespesas > 0) entries.add(PieEntry(totalDespesas.toFloat(), "Despesas"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336")) // VERDE e VERMELHO
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        pieChart.data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(pieChart)) }
        pieChart.centerText = "Balanço Geral\n${String.format("%.2f€", totalReceitas - totalDespesas)}"
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    private fun loadDespesasLocais() {
        lifecycleScope.launch {
            listaCompleta = database.despesaDao().getAll()
            if (listaCompleta.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE; recyclerView.visibility = View.GONE; pieChart.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE; recyclerView.visibility = View.VISIBLE; pieChart.visibility = View.VISIBLE
                adapter.updateList(listaCompleta)
                atualizarGrafico(listaCompleta.filter { it.valor < 0 }, "Gastos (%)", true)
            }
            // Garante que o saldo está atualizado com o que está na BD
            val user = database.utilizadorDao().getUtilizador()
            user?.let { updateSaldoUI(it.saldo) }
        }
    }

    private fun realizarLogout() {
        lifecycleScope.launch {
            database.utilizadorDao().deleteAll(); database.despesaDao().deleteAll()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java)); finish()
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
                            despesasRemotas.forEach { it.isSynced = true; database.despesaDao().insert(it) }
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
                startActivity(Intent(this@MainActivity, LoginActivity::class.java)); finish()
            } else {
                currentUser = user; updateSaldoUI(user.saldo); loadDespesasLocais(); syncDespesasAPI()
            }
        }
    }

    private fun updateSaldoUI(valor: Double) { tvSaldo.text = String.format("%.2f€", valor) }

    private fun mostrarDialogoApagar(despesa: Despesa) {
        AlertDialog.Builder(this).setTitle("Apagar").setMessage("Deseja apagar '${despesa.titulo}'?")
            .setPositiveButton("Sim") { _, _ -> apagarDespesa(despesa) }
            .setNegativeButton("Não", null).show()
    }

    private fun apagarDespesa(despesa: Despesa) {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = database.utilizadorDao().getUtilizador()
            user?.let { u ->
                // Subtraímos o valor da despesa (lembrando que despesas são negativas,
                // logo Saldo - (-50) = Saldo + 50, o que devolve o dinheiro ao saldo)
                val novoSaldo = u.saldo - despesa.valor

                // 1. Atualiza na BD local
                database.utilizadorDao().updateSaldo(u.id, novoSaldo)

                // 2. Atualiza no Servidor
                try {
                    RetrofitClient.instance.updateSaldo(u.id, SaldoRequest(novoSaldo)).execute()
                } catch (e: Exception) { e.printStackTrace() }

                // 3. Atualiza a variável local para a UI mudar instantaneamente
                withContext(Dispatchers.Main) {
                    currentUser = u.copy(saldo = novoSaldo)
                    updateSaldoUI(novoSaldo)
                }
            }

            // 4. Apaga a transação local e remota
            database.despesaDao().delete(despesa)
            if (despesa.id.isNotEmpty()) {
                try {
                    RetrofitClient.instance.deleteDespesa(despesa.id).execute()
                } catch (e: Exception) { e.printStackTrace() }
            }

            withContext(Dispatchers.Main) { loadDespesasLocais() }
        }
    }
}