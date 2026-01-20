package pt.ipt.dam.ecowallet

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.launch
import pt.ipt.dam.ecowallet.database.AppDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import kotlin.math.abs

class DayReportActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DespesaAdapter
    private lateinit var tvDayTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_report)

        val selectedDate = intent.getStringExtra("SELECTED_DATE") ?: ""
        
        tvDayTitle = findViewById(R.id.tvDayTitle)
        tvDayTitle.text = "Relatório de $selectedDate"
        
        pieChart = findViewById(R.id.dayPieChart)
        recyclerView = findViewById(R.id.rvDayTransactions)

        setupPieChart()
        setupRecyclerView()
        loadDayData(selectedDate)
    }

    private fun setupPieChart() {
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = true
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.holeRadius = 40f
        pieChart.transparentCircleRadius = 45f
    }

    private fun setupRecyclerView() {
        adapter = DespesaAdapter(
            lista = emptyList(),
            onDespesaClick = { /* Opcional: ver detalhes */ },
            onDeleteClick = { /* No relatório apenas visualizamos */ }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadDayData(date: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // Filtramos todas as transações da BD pelo dia selecionado
            val todas = db.despesaDao().getAll()
            val transacoesDoDia = todas.filter { it.data == date }

            adapter.updateList(transacoesDoDia)
            updatePieChart(transacoesDoDia)
        }
    }

    private fun updatePieChart(lista: List<Despesa>) {
        val totalReceitas = lista.filter { it.valor > 0 }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.valor < 0 }.sumOf { abs(it.valor) }

        if (totalReceitas == 0.0 && totalDespesas == 0.0) {
            pieChart.clear()
            pieChart.centerText = "Sem transações neste dia"
            return
        }

        val entries = mutableListOf<PieEntry>()
        if (totalReceitas > 0) entries.add(PieEntry(totalReceitas.toFloat(), "Receitas"))
        if (totalDespesas > 0) entries.add(PieEntry(totalDespesas.toFloat(), "Despesas"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        pieChart.data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChart))
        }
        pieChart.centerText = "Balanço\n${String.format("%.2f€", totalReceitas - totalDespesas)}"
        pieChart.animateY(800)
        pieChart.invalidate()
    }
}