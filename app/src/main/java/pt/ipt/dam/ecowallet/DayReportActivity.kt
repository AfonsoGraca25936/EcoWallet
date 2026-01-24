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

/**
 * Atividade de Relatório Diário: Exibe um resumo financeiro (gráfico e lista)
 * para um dia específico selecionado no calendário.
 */
class DayReportActivity : AppCompatActivity() {

    // Componentes da Interface de Utilizador
    private lateinit var pieChart: PieChart
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DespesaAdapter
    private lateinit var tvDayTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_report)

        // Recupera a data selecionada enviada pela CalendarActivity
        val selectedDate = intent.getStringExtra("SELECTED_DATE") ?: ""

        // Configura o título do ecrã com a data escolhida
        tvDayTitle = findViewById(R.id.tvDayTitle)
        tvDayTitle.text = "Relatório de $selectedDate"

        pieChart = findViewById(R.id.dayPieChart)
        recyclerView = findViewById(R.id.rvDayTransactions)

        // Inicializa as configurações visuais e os dados
        setupPieChart()
        setupRecyclerView()
        loadDayData(selectedDate)
    }

    /**
     * Configura as propriedades visuais do gráfico circular (PieChart).
     */
    private fun setupPieChart() {
        pieChart.setUsePercentValues(true) // Mostra valores em percentagem
        pieChart.description.isEnabled = false // Remove a descrição padrão
        pieChart.legend.isEnabled = true // Mostra a legenda (Receitas/Despesas)
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.holeRadius = 40f // Define o tamanho do buraco central (estilo Donut)
        pieChart.transparentCircleRadius = 45f
    }

    /**
     * Configura a lista (RecyclerView) para mostrar as transações do dia.
     */
    private fun setupRecyclerView() {
        adapter = DespesaAdapter(
            lista = emptyList(),
            onDespesaClick = { /* Opcional: ver detalhes da transação */ },
            onDeleteClick = { /* No relatório, a eliminação está desativada por segurança */ }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    /**
     * Procura na base de dados local todas as transações e filtra apenas as do dia selecionado.
     */
    private fun loadDayData(date: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            // Vai buscar todas as transações guardadas no telemóvel
            val todas = db.despesaDao().getAll()

            // Filtra a lista para manter apenas as que têm a data igual à selecionada
            val transacoesDoDia = todas.filter { it.data == date }

            // Atualiza o adaptador da lista e o gráfico com os resultados
            adapter.updateList(transacoesDoDia)
            updatePieChart(transacoesDoDia)
        }
    }

    /**
     * Calcula os totais de Receitas e Despesas do dia e atualiza o gráfico.
     */
    private fun updatePieChart(lista: List<Despesa>) {
        // Separa e soma os valores positivos (receitas) e negativos (despesas)
        val totalReceitas = lista.filter { it.valor > 0 }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.valor < 0 }.sumOf { abs(it.valor) }

        // Se não houver movimentos, limpa o gráfico e avisa o utilizador
        if (totalReceitas == 0.0 && totalDespesas == 0.0) {
            pieChart.clear()
            pieChart.centerText = "Sem transações neste dia"
            return
        }

        // Cria as entradas para o gráfico
        val entries = mutableListOf<PieEntry>()
        if (totalReceitas > 0) entries.add(PieEntry(totalReceitas.toFloat(), "Receitas"))
        if (totalDespesas > 0) entries.add(PieEntry(totalDespesas.toFloat(), "Despesas"))

        // Configura as cores: Verde para Receitas (#4CAF50), Vermelho para Despesas (#F44336)
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        // Aplica os dados ao gráfico e define o texto central com o balanço final
        pieChart.data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChart))
        }
        pieChart.centerText = "Balanço\n${String.format("%.2f€", totalReceitas - totalDespesas)}"
        pieChart.animateY(800) // Animação de entrada do gráfico
        pieChart.invalidate() // Refresca o gráfico no ecrã
    }
}